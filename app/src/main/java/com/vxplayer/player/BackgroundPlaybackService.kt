package com.vxplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vxplayer.MainActivity

/** Optional current-video handoff. Closing the player or task explicitly ends the session. */
@androidx.annotation.OptIn(UnstableApi::class)
class BackgroundPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var owned: ExoPlayer? = null
    override fun onCreate() {
        super.onCreate()
        instance = this
        attachPlayer()
    }
    private fun attachPlayer() {
        val player = activePlayer ?: run { stopSelf(); return }
        if (owned === player && session != null) return
        releaseSession()
        owned = player
        val launch = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(launch).build().also { addSession(it) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachPlayer()
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    private fun releaseSession() { session?.let { removeSession(it); it.release() }; session = null }
    override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }
    override fun onDestroy() {
        releaseSession()
        if (activePlayer === owned) {
            activePlayer = null
            val callback = onStopped; onStopped = null
            if (callback != null) callback() else owned?.release()
        }
        if (instance === this) instance = null
        super.onDestroy()
    }
    companion object {
        private var instance: BackgroundPlaybackService? = null
        private var activePlayer: ExoPlayer? = null
        private var onStopped: (() -> Unit)? = null
        fun begin(context: Context, player: ExoPlayer, stopped: () -> Unit): Boolean {
            if (activePlayer != null && activePlayer !== player) return false
            activePlayer = player; onStopped = stopped
            return try { ContextCompat.startForegroundService(context, Intent(context, BackgroundPlaybackService::class.java)); true }
            catch (_: RuntimeException) { activePlayer = null; onStopped = null; false }
        }
        fun detach(context: Context, player: ExoPlayer?) {
            if (activePlayer === player) {
                instance?.releaseSession()
                activePlayer = null; onStopped = null
                context.stopService(Intent(context, BackgroundPlaybackService::class.java))
            }
        }
        /** Swap inside an already running service; never start a hidden new foreground service. */
        fun replaceForRecovery(previous: ExoPlayer, replacement: ExoPlayer): Boolean {
            val service = instance ?: return false
            if (activePlayer !== previous) return false
            activePlayer = replacement
            return try { service.attachPlayer(); true }
            catch (_: RuntimeException) { activePlayer = previous; false }
        }
    }
}
