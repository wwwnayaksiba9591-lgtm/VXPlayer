package com.vxplayer.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import androidx.annotation.RequiresApi

@RequiresApi(28)
internal fun createNightCompression(session: Int): AudioEffect {
    val effect = DynamicsProcessing(session)
    try {
        val config = effect.config
        require(config.mbcBandCount > 0)
        for (band in 0 until config.mbcBandCount) {
            val parameters = effect.getMbcBandByChannelIndex(0, band)
            parameters.ratio = 4f; parameters.threshold = -24f; parameters.kneeWidth = 6f
            parameters.attackTime = 10f; parameters.releaseTime = 120f
            parameters.preGain = 0f; parameters.postGain = 0f
            effect.setMbcBandAllChannelsTo(band, parameters)
        }
        effect.setInputGainAllChannelsTo(-3f)
        effect.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 5f, 100f, 10f, -3f, 0f))
        effect.enabled = false
        return effect
    } catch (error: RuntimeException) { effect.release(); throw error }
}
