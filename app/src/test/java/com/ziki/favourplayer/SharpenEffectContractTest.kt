package com.ziki.favourplayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 锐化特效踩过两个坑，都在这里锁住。
 *
 * 背景：media3 的 `BaseGlShaderProgram.queueInputFrame` 在调 `drawFrame` 之前
 * 已经从输出纹理池里取好纹理并绑定了 FBO，画完还会统一把纹理交给下游。
 * 所以自定义 shader **不能**再自己 `useOutputTexture()` 或
 * `onOutputFrameAvailable()` —— 池容量只有 1，多取一次就抛
 * 「Textures are all in use. Please release in-use textures before calling useTexture.」，
 * 表现为 ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED，播放直接失败。
 */
class SharpenEffectContractTest {

    private val source: String =
        File("src/main/java/com/ziki/favourplayer/VideoQuality.kt").readText()

    @Test
    fun doesNotRequestOutputTextureItself() {
        // 父类已经取好并绑定了 FBO，再取一次就会撞上「Textures are all in use」
        val drawFrame = source.substringAfter("override fun drawFrame")
            .substringBefore("private companion object")
        assertTrue(
            "drawFrame 里不能自己调 useOutputTexture()，父类已经取过纹理了",
            !drawFrame.contains("useOutputTexture()"),
        )
    }

    @Test
    fun doesNotNotifyOutputListenerItself() {
        // 父类会在 drawFrame 返回后统一交给下游，重复通知会让纹理状态错乱
        val drawFrame = source.substringAfter("override fun drawFrame")
            .substringBefore("private companion object")
        assertTrue(
            "drawFrame 里不能自己调 onOutputFrameAvailable()，父类会统一通知下游",
            !drawFrame.contains("onOutputFrameAvailable"),
        )
    }

    @Test
    fun doesNotRebindFramebuffer() {
        // 父类已经 focus 过 FBO，再 focus 一次会画到别的纹理上
        val drawFrame = source.substringAfter("override fun drawFrame")
            .substringBefore("private companion object")
        assertTrue(
            "drawFrame 里不需要再 focus FBO，父类已经绑好了",
            !drawFrame.contains("focusFramebufferUsingCurrentContext"),
        )
    }

    @Test
    fun keepsTexturePoolCapacityAtOne() {
        // 池容量必须与父类的取用次数匹配：父类取 1 个，不能多也不能少
        assertTrue(
            "输出纹理池容量应保持为 1",
            source.contains("texturePoolCapacity= */ 1"),
        )
    }
}
