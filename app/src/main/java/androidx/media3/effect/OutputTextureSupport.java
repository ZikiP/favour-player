package androidx.media3.effect;

import androidx.annotation.OptIn;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.UnstableApi;

/**
 * 桥接类：BaseGlShaderProgram 的 outputTexturePool 是 protected，
 * 但它的类型 TexturePool 是包私有，外部 Kotlin 子类无法引用。
 * 放在同一包下才能合法地把输出纹理取出来给自定义 shader 用。
 */
@OptIn(markerClass = UnstableApi.class)
public abstract class OutputTextureSupport extends BaseGlShaderProgram {

  protected OutputTextureSupport(
      boolean useHighPrecisionColorComponents, int texturePoolCapacity) {
    super(useHighPrecisionColorComponents, texturePoolCapacity);
  }

  protected final GlTextureInfo useOutputTexture() {
    return outputTexturePool.useTexture();
  }
}
