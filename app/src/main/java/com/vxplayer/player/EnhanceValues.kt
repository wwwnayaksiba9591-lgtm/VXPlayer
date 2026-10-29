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
    "Auto" to EnhanceValues(.02f, .10f, 1.10f, .10f),
    "Vivid" to EnhanceValues(.02f, .14f, 1.22f, .18f),
    "Cinema" to EnhanceValues(-.02f, .08f, .9f, .06f, .06f),
    "Sports/Action" to EnhanceValues(.025f, .16f, 1.16f, .30f, -.015f),
    "Anime/Cartoon" to EnhanceValues(.015f, .10f, 1.25f, .12f),
    "Night" to EnhanceValues(-.08f, -.1f, .85f, 0f, .04f),
    "Original" to EnhanceValues(),
)

fun smartMode(name: String?): String = when (name) {
    "Soft" -> "Cinema"
    "Sharp" -> "Sports/Action"
    "Custom" -> "Custom"
    in ENHANCE_PRESETS.keys -> name ?: "Auto"
    else -> "Auto"
}

data class SmartEnhanceState(val enabled: Boolean = false, val lastMode: String = "Auto") {
    fun toggle() = copy(enabled = !enabled)
    fun select(mode: String) = if (mode == "Original") copy(enabled = false) else SmartEnhanceState(true, smartMode(mode))
    fun restored(remember: Boolean) = if (remember) this else SmartEnhanceState()
}

data class FrameSummary(val luminance: Float, val saturation: Float)
fun summarizeFrame(pixels: IntArray): FrameSummary? {
    if (pixels.isEmpty()) return null
    var luma = 0.0; var saturation = 0.0
    for (pixel in pixels) {
        val r = ((pixel shr 16) and 255) / 255f
        val g = ((pixel shr 8) and 255) / 255f
        val b = (pixel and 255) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        luma += r * .2126 + g * .7152 + b * .0722
        saturation += if (max > 0f) (max - min) / max else 0f
    }
    return FrameSummary((luma / pixels.size).toFloat(), (saturation / pixels.size).toFloat())
}

fun smoothAutoEnhance(current: EnhanceValues, frame: FrameSummary?, alpha: Float = .18f): EnhanceValues {
    val gentle = ENHANCE_PRESETS.getValue("Auto")
    val target = if (frame == null || !frame.luminance.isFinite() || !frame.saturation.isFinite()) gentle else EnhanceValues(
        ((.45f - frame.luminance.coerceIn(0f, 1f)) * .22f).coerceIn(-.06f, .10f),
        (.12f - kotlin.math.abs(frame.luminance - .5f) * .1f).coerceIn(.04f, .16f),
        (1.12f + (.35f - frame.saturation.coerceIn(0f, 1f)) * .15f).coerceIn(.95f, 1.18f), .10f)
    val amount = if (alpha.isFinite()) alpha.coerceIn(0f, 1f) else .18f
    fun blend(a: Float, b: Float) = a + (b - a) * amount
    val safe = current.bounded()
    return EnhanceValues(blend(safe.brightness, target.brightness).coerceIn(-.06f, .10f),
        blend(safe.contrast, target.contrast).coerceIn(.04f, .16f),
        blend(safe.saturation, target.saturation).coerceIn(.95f, 1.18f), .10f)
}

data class SmartAudioProfile(val spatial: Int, val bass: Int, val lowMb: Int, val midMb: Int, val highMb: Int, val night: Boolean = false)
fun smartAudioProfile(mode: String) = when (mode) {
    "Cinema" -> SmartAudioProfile(300, 180, 100, 50, 50)
    "Sports/Action" -> SmartAudioProfile(220, 220, 150, 25, 125)
    "Anime/Cartoon" -> SmartAudioProfile(150, 100, 50, 100, 150)
    "Night" -> SmartAudioProfile(80, 60, -100, 100, -100, true)
    "Original" -> SmartAudioProfile(0, 0, 0, 0, 0)
    else -> SmartAudioProfile(160, 120, 75, 25, 100)
}

fun shouldEnhance(enabled: Boolean, values: EnhanceValues, available: Boolean, inPip: Boolean, background: Boolean): Boolean =
    enabled && values != EnhanceValues() && available && !inPip && !background
