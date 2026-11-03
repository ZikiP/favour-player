@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ziki.favourplayer

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.OutputTextureSupport
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 锐化档位。只增强细节，不做放大，所以与片源分辨率无关：
 * 1080p 片源在手机上放大倍率接近 1，超分没意义，锐化才有。
 */
enum class SharpenLevel(val amount: Float, val label: String) {
    Off(0f, "关闭"),
    Soft(0.35f, "轻"),
    Medium(0.7f, "中"),
    ;

    val enabled: Boolean get() = this != Off

    companion object {
        fun fromOrdinal(value: Int): SharpenLevel = values().getOrElse(value) { Off }
    }
}

/**
 * 对比度自适应的亮度锐化：
 * - 只在亮度通道做，色度不动，避免出现彩边；
 * - 小幅度细节（压缩噪点、蚊噪）被 gate 抑制，不会越锐越脏；
 * - 已经很强的边缘再减半，防止过冲白边。
 */
internal class SharpenEffect(private val amount: Float) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        // HDR 走 PQ/HLG 编码，在这种非线性值上做锐化会失真，直接透传
        SharpenShaderProgram(if (useHdr) 0f else amount, useHdr)

    override fun isNoOp(width: Int, height: Int): Boolean = false
}

private class SharpenShaderProgram(
    private val amount: Float,
    useHdr: Boolean,
) : OutputTextureSupport(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = try {
        GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    } catch (error: GlUtil.GlException) {
        throw VideoFrameProcessingException(error)
    }

    /** 输出纹理尺寸在 configure 之后才确定，drawFrame 时用它算 texel 大小。 */
    private var outputWidth = 1
    private var outputHeight = 1

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // 输出尺寸就是输入尺寸：锐化不做缩放
        outputWidth = inputWidth
        outputHeight = inputHeight
        return Size(inputWidth, inputHeight)
    }

    /**
     * 绘制到 [BaseGlShaderProgram] 已经准备好的输出纹理上。
     *
     * 这里**不能**再调 `useOutputTexture()`：父类在调 drawFrame 之前已经从
     * 输出纹理池里取好了一个纹理并绑定了 FBO，池容量只有 1，再取一次必然抛
     * 「Textures are all in use」。同理也不能自己调 onOutputFrameAvailable()，
     * 父类会在 drawFrame 返回后统一把纹理交给下游。
     *
     * 官方 AlphaScaleShaderProgram 就是这个写法：只管画，画完即返回。
     */
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform(
                "uTexelSize",
                floatArrayOf(1f / outputWidth, 1f / outputHeight),
            )
            program.setFloatUniform("uAmount", amount)
            program.setBufferAttribute("aFramePosition", FULLSCREEN_QUAD, /* size= */ 2)
            program.bindAttributesAndUniforms()
            GlUtil.clearFocusedBuffers()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    private companion object {
        val FULLSCREEN_QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        val VERTEX_SHADER = """#version 100
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
    gl_Position = aFramePosition;
    vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        val FRAGMENT_SHADER = """#version 100
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uAmount;
varying vec2 vTexCoord;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 center = texture2D(uTexSampler, vTexCoord).rgb;
    vec3 up = texture2D(uTexSampler, vTexCoord + vec2(0.0, -uTexelSize.y)).rgb;
    vec3 down = texture2D(uTexSampler, vTexCoord + vec2(0.0, uTexelSize.y)).rgb;
    vec3 left = texture2D(uTexSampler, vTexCoord + vec2(-uTexelSize.x, 0.0)).rgb;
    vec3 right = texture2D(uTexSampler, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb;

    vec3 blur = (up + down + left + right) * 0.25;
    float detail = dot(center - blur, LUMA);
    float magnitude = abs(detail);

    float gate = smoothstep(0.005, 0.04, magnitude);
    float edgeGuard = 1.0 - 0.5 * smoothstep(0.30, 0.70, magnitude);

    vec3 result = center + detail * uAmount * gate * edgeGuard;
    gl_FragColor = vec4(result, 1.0);
}
"""
    }
}

/** 与 android.view.Display.Mode 解耦，方便单测。 */
data class RefreshMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * 帧率匹配：挑一个能整除片源帧率的刷新率，让每帧停留的屏幕刷新次数相同，
 * 消除 24p 在 60Hz 上 3:2 pulldown 造成的规律性抖动。
 *
 * 两条约束：
 * - 只在分辨率相同的模式里挑，避免切换后画面尺寸变化；
 * - 刷新率不低于 48Hz，否则控件动画会明显变卡（24Hz 屏看 24p 虽然完美，但 UI 不能看）。
 */
object RefreshRatePicker {

    private const val MIN_REFRESH_HZ = 48f
    private const val COMFORT_REFRESH_HZ = 60f
    private const val MULTIPLE_TOLERANCE = 0.015f

    fun pick(modes: List<RefreshMode>, currentModeId: Int, videoFps: Float): Int? {
        if (videoFps <= 1f || modes.isEmpty()) return null
        val current = modes.firstOrNull { it.id == currentModeId } ?: return null
        val candidates = modes.filter {
            it.width == current.width &&
                it.height == current.height &&
                it.refreshRate >= MIN_REFRESH_HZ &&
                isMultipleOf(it.refreshRate, videoFps)
        }
        if (candidates.isEmpty()) return null
        // 优先 60Hz 以上里最小的那个：既整除又省电，UI 也不卡
        val comfortable = candidates.filter { it.refreshRate >= COMFORT_REFRESH_HZ }
        val best = (comfortable.ifEmpty { candidates }).minByOrNull { it.refreshRate } ?: return null
        return best.id.takeIf { it != currentModeId }
    }

    private fun isMultipleOf(refreshRate: Float, fps: Float): Boolean {
        val ratio = refreshRate / fps
        val nearest = ratio.roundToInt()
        if (nearest < 1) return false
        return abs(ratio - nearest) <= MULTIPLE_TOLERANCE * nearest
    }
}
