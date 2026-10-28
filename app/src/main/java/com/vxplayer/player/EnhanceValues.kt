package com.vxplayer.player

data class EnhanceValues(val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 1f,
    val sharpness: Float = 0f, val warmth: Float = 0f) {
    fun bounded(): EnhanceValues {
        fun value(input: Float, fallback: Float, min: Float, max: Float) = if (input.isFinite()) input.coerceIn(min, max) else fallback
        return EnhanceValues(value(brightness, 0f, -.25f, .25f), value(contrast, 0f, -.5f, .5f),
            value(saturation, 1f, 0f, 2f), value(sharpness, 0f, -.5f, 1f), value(warmth, 0f, -.2f, .2f))
    }
}

val ENHANCE_PRESETS = linkedMapOf(
    "Original" to EnhanceValues(),
    "Vivid" to EnhanceValues(.02f, .14f, 1.22f, .18f),
    "Cinema" to EnhanceValues(-.02f, .08f, .9f, .06f, .06f),
    "Soft" to EnhanceValues(.03f, -.08f, .96f, -.12f),
    "Sharp" to EnhanceValues(0f, .05f, 1.02f, .38f),
    "Night" to EnhanceValues(-.08f, -.1f, .85f, 0f, .04f),
)

fun shouldEnhance(enabled: Boolean, values: EnhanceValues, available: Boolean, inPip: Boolean, background: Boolean): Boolean =
    enabled && values != EnhanceValues() && available && !inPip && !background
