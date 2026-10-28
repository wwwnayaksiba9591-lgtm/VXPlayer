package com.vxplayer.player

import kotlin.math.log10
import kotlin.math.roundToInt

fun boundedVolume(request: Int, allowBoost: Boolean, maxBoost: Int): Int = request.coerceIn(0, if (allowBoost) maxBoost.coerceIn(100, 200) else 100)
fun loudnessGainMb(percent: Int): Int = if (percent <= 100) 0 else (2000.0 * log10(percent.coerceAtMost(200) / 100.0)).roundToInt()
fun needsBoostWarning(percent: Int, acknowledged: Boolean): Boolean = percent > 150 && !acknowledged
fun volumeAfterVideoChange(percent: Int, changed: Boolean, remember: Boolean): Int = if (changed && !remember) percent.coerceAtMost(100) else percent
