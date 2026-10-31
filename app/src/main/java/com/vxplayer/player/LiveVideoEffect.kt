package com.vxplayer.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Immutable parameter snapshots cross from the application thread to Media3's GL thread. */
class LiveEnhanceParameters { @Volatile var values = EnhanceValues() }

@androidx.annotation.OptIn(UnstableApi::class)
class LiveVideoEffect(private val parameters: LiveEnhanceParameters) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException(IllegalArgumentException("HDR enhancement is not supported"))
        return try { EnhanceShader(parameters) }
        catch (error: Exception) { throw VideoFrameProcessingException(error) }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class EnhanceShader(private val parameters: LiveEnhanceParameters) : BaseGlShaderProgram(false, 1) {
    private val program = GlProgram(VERTEX, FRAGMENT)
    private val adjustment = FloatArray(4)
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        program.setFloatsUniform("uTexel", floatArrayOf(1f / inputWidth, 1f / inputHeight))
        return Size(inputWidth, inputHeight)
    }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val value = parameters.values
            adjustment[0] = value.brightness; adjustment[1] = value.contrast
            adjustment[2] = value.saturation; adjustment[3] = value.sharpness
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uAdjust", adjustment)
            program.setFloatUniform("uWarmth", value.warmth)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (error: Exception) { throw VideoFrameProcessingException(error, presentationTimeUs) }
    }
    override fun release() {
        try { program.delete() }
        catch (error: Exception) { throw VideoFrameProcessingException(error) }
        finally { super.release() }
    }
    companion object {
        private const val VERTEX = """
            attribute vec4 aFramePosition;
            varying highp vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = (aFramePosition.xy + 1.0) * 0.5;
            }
        """
        private const val FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexel;
            uniform vec4 uAdjust;
            uniform float uWarmth;
            varying highp vec2 vTexSamplingCoord;
            void main() {
                vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 rgb = center.rgb;
                if (uAdjust.w != 0.0) {
                    vec3 nearby = (texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexel.x, 0.0)).rgb
                        + texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexel.x, 0.0)).rgb
                        + texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexel.y)).rgb
                        + texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexel.y)).rgb) * 0.25;
                    rgb += (rgb - nearby) * uAdjust.w * 1.5;
                }
                rgb = (rgb - 0.5) * (1.0 + uAdjust.y) + 0.5;
                float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
                rgb = mix(vec3(luma), rgb, uAdjust.z) + uAdjust.x;
                rgb *= vec3(1.0 + uWarmth, 1.0, 1.0 - uWarmth);
                gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), center.a);
            }
        """
    }
}
