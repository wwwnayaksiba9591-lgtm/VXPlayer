package com.vxplayer.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.vxplayer.MainActivity
import com.vxplayer.player.PlaybackPositionStore
import kotlinx.coroutines.*

/** Main-thread-only ownership of the optional video session in the shared playback service. */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoSessionHandoff {
    private var engine: Player? = null
    private var sourceUri: String? = null
    private var controls: Player?=null
    private var stopped: (() -> Unit)? = null
    private var host: MusicPlaybackService? = null
    private var checkpoint: Job? = null
    var session: MediaSession? = null
        private set
    val playing get() = engine?.playWhenReady == true
    private val errors = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { if(!isPlaying)saveCheckpoint() }
        override fun onPlayerError(error: PlaybackException) {
            if(engine?.playerError!==error)return // Ignore a retired decoder after an effects recovery swap.
            engine?.pause()
            host?.let { Toast.makeText(it,"Background video could not continue. Open VXPlayer to retry.",Toast.LENGTH_SHORT).show() }
        }
    }
    fun begin(context: Context, player: Player, onStopped: () -> Unit, originalUri: String? = null, sessionPlayer: Player = player): Boolean {
        if(engine!=null && engine!==player)return false
        engine=player; stopped=onStopped; sourceUri=originalUri;controls=sessionPlayer
        return try {
            ContextCompat.startForegroundService(context,Intent(context,MusicPlaybackService::class.java))
            val service=MusicPlaybackService.instance
            if(service!=null && !attach(service)) { detach(player); return false }
            true
        } catch (_: RuntimeException) { detach(player); false }
    }
    fun attach(service: MusicPlaybackService): Boolean {
        val player=engine ?: return false
        if(host===service && session!=null)return true
        releaseSession()
        host=service
        val launch=PendingIntent.getActivity(service,2,Intent(service,MainActivity::class.java)
            .putExtra("openBackgroundVideo",true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            session=MediaSession.Builder(service,controls ?: player).setId("vx.video").setSessionActivity(launch).build().also(service::addSession)
            player.addListener(errors)
            checkpoint=CoroutineScope(Dispatchers.Main.immediate).launch {
                while(isActive) { delay(5000); if(engine?.isPlaying==true)saveCheckpoint() }
            }
            true
        } catch (_: RuntimeException) { releaseSession(); false }
    }
    fun currentVideo(): Pair<String,String>? = engine?.currentMediaItem?.let { item ->
        (sourceUri ?: item.localConfiguration?.uri?.toString())?.let { it to (item.mediaMetadata.title?.toString() ?: "Video") }
    }
    fun saveCheckpoint() {
        val service=host ?: return
        val player=engine ?: return
        val uri=currentVideo()?.first ?: return
        runCatching { PlaybackPositionStore.get(service).save(uri,player.currentPosition.coerceAtLeast(0)) }
    }
    private fun releaseSession() {
        saveCheckpoint(); checkpoint?.cancel(); checkpoint=null
        engine?.removeListener(errors)
        session?.let { host?.removeSession(it); it.release() }
        session=null; host=null
    }
    fun detach(player: Player?) {
        if(engine!==player)return
        releaseSession(); engine=null; stopped=null;sourceUri=null;controls=null
    }
    fun replace(previous: ExoPlayer,replacement: ExoPlayer): Boolean {
        val service=host ?: return false
        if(engine!==previous)return false
        val oldControls=controls
        releaseSession(); engine=replacement;controls=(oldControls as? com.vxplayer.player.SeekSessionPlayer)?.withEngine(replacement) ?: replacement
        if(attach(service))return true
        engine=previous;controls=oldControls
        attach(service)
        return false
    }
    fun fail() {
        val player=engine; val callback=stopped
        detach(player)
        if(callback!=null)callback() else player?.release()
    }
    fun destroy(service: MusicPlaybackService) { if(host===service)fail() }
}
