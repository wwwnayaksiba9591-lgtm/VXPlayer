package com.vxplayer.cast

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.vxplayer.music.VideoSessionHandoff
import com.vxplayer.player.PlaybackCoordinator
import kotlinx.coroutines.*

/** Owns remote playback and its temporary local server until the Cast session ends. */
@androidx.annotation.OptIn(UnstableApi::class)
class CastBridge(private val context: Context,private var native: ()->ExoPlayer?,
    private var changed: (Boolean)->Unit,private var restore: (Long,Boolean)->Unit) {
    companion object {
        private var shared: CastBridge?=null
        fun obtain(owner: Any,context: Context,native: ()->ExoPlayer?,changed: (Boolean)->Unit,restore: (Long,Boolean)->Unit): CastBridge {
            val existing=shared
            if(existing!=null) {
                existing.owner=owner;existing.native=native;existing.changed=changed;existing.restore=restore;existing.abandoned=false
                val local=native()
                if(existing.active && local?.currentMediaItem?.localConfiguration?.uri?.toString()==existing.sourceUri) {
                    val play=local?.playWhenReady==true || existing.player.playWhenReady
                    local?.pause();if(play)existing.player.play();changed(true)
                } else if(existing.player.isCastSessionAvailable)existing.load()
                return existing
            }
            return CastBridge(context,native,changed,restore).also {it.owner=owner;shared=it}
        }
    }
    val player=CastPlayer(CastContext.getSharedInstance(context))
    var active=false; private set
    private var abandoned=false
    private var owner: Any?=null
    fun ownedBy(value: Any)=owner===value
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var work: Job?=null
    private var server: LocalCastServer?=null
    private var wake: android.os.PowerManager.WakeLock?=null
    private fun closeServer() {
        server?.close();server=null
        runCatching {wake?.takeIf {it.isHeld}?.release()};wake=null
    }
    private var position=0L
    private var playing=false
    private var sourceUri: String?=null
    private var targetUri: String?=null
    private var released=false
    private var remoteClient: com.google.android.gms.cast.framework.media.RemoteMediaClient?=null
    private val remoteErrors=object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status=remoteClient?.mediaStatus ?: return
            if(status.mediaInfo?.contentId!=targetUri)return
            if(active && status.idleReason==com.google.android.gms.cast.MediaStatus.IDLE_REASON_ERROR) {
                message("The TV cannot load or decode this media. Returning to phone.");returnToPhone()
            }
        }
    }
    init {
        player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {load()}
            override fun onCastSessionUnavailable() {returnToPhone()}
        })
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player,events: Player.Events) {
                if(active) {
                    if(player.playbackState==Player.STATE_READY) {position=player.currentPosition.coerceAtLeast(0);playing=player.playWhenReady}
                    if(player.playbackState==Player.STATE_ENDED)playing=false
                    changed(true)
                }
            }
            override fun onPlayerError(error: PlaybackException) {message("The receiver could not play this format. Returning to phone.");returnToPhone()}
        })
        if(player.isCastSessionAvailable)load()
    }
    fun load() {
        work?.cancel()
        val local=native() ?: return
        val source=local.currentMediaItem ?: return
        val uri=source.localConfiguration?.uri ?: return
        sourceUri=uri.toString()
        val resume=local.currentPosition.coerceAtLeast(0);val autoplay=local.playWhenReady
        work=scope.launch {
            try {
                check(local.videoFormat?.drmInitData==null) {"Protected media cannot use the default receiver."}
                remoteClient?.unregisterCallback(remoteErrors)
                closeServer()
                val mime=source.localConfiguration?.mimeType ?: when {
                    uri.path.orEmpty().endsWith(".m3u8",true)->MimeTypes.APPLICATION_M3U8
                    uri.path.orEmpty().endsWith(".mpd",true)->MimeTypes.APPLICATION_MPD
                    else->local.videoFormat?.containerMimeType ?: withContext(Dispatchers.IO){context.contentResolver.getType(uri)} ?: "video/mp4"
                }
                val remote=if(uri.scheme in listOf("http","https"))uri.toString() else {
                    val endpoint=LocalCastServer(context,uri,mime);server=endpoint
                    withContext(Dispatchers.IO) {try {endpoint.start()}catch(failure: Exception){endpoint.close();throw failure}}
                }
                if(server!=null)runCatching {
                    val power=context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    wake=power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"VXPlayer:local-cast").apply {acquire(35_000)}
                }
                PlaybackCoordinator.startingVideo(this@CastBridge)
                local.pause()
                active=true;position=resume;playing=autoplay
                targetUri=remote
                remoteClient=CastContext.getSharedInstance(context).sessionManager.currentCastSession?.remoteMediaClient
                remoteClient?.registerCallback(remoteErrors)
                player.setMediaItem(MediaItem.Builder().setUri(remote).setMimeType(mime).setMediaMetadata(source.mediaMetadata).build(),resume)
                player.prepare();player.playWhenReady=autoplay
                // Do not start a foreground service with an empty/idle remote timeline: Android
                // otherwise expects a notification before the receiver has finished loading.
                val loadDeadline=android.os.SystemClock.elapsedRealtime()+30_000
                while(active && player.playbackState!=Player.STATE_READY) {
                    delay(100)
                    check(android.os.SystemClock.elapsedRealtime()<loadDeadline) {"Receiver timed out."}
                }
                if(!active)return@launch
                check(VideoSessionHandoff.begin(context,player,{stop()},uri.toString())) {"Background Cast session could not start."}
                // A local server must keep serving with the screen off. Renew a bounded lease;
                // every exit also releases it, even if the receiver disconnects while loading.
                PlaybackCoordinator.registerVideo(this@CastBridge){player.pause()}
                changed(true)
                var deadline=android.os.SystemClock.elapsedRealtime()+30_000
                while(isActive && active) {
                    delay(500)
                    if(server!=null)runCatching {wake?.takeIf { !it.isHeld }?.acquire(35_000)}
                    if(player.playbackState==Player.STATE_READY) {position=player.currentPosition.coerceAtLeast(0);playing=player.playWhenReady}
                    if(player.playbackState==Player.STATE_ENDED)playing=false
                    if(player.playbackState==Player.STATE_READY || !player.playWhenReady)deadline=android.os.SystemClock.elapsedRealtime()+30_000
                    else if(player.playbackState!=Player.STATE_ENDED && android.os.SystemClock.elapsedRealtime()>deadline)error("Receiver timed out.")
                }
            } catch(cancelled: CancellationException){throw cancelled}
            catch(_: Exception){message("Casting failed. Check Wi-Fi, file access and the receiver's supported formats.");returnToPhone()}
        }
    }
    private fun returnToPhone() {
        work?.cancel();work=null
        val wasActive=active;active=false
        if(wasActive && player.isCastSessionAvailable)runCatching {player.pause()}
        remoteClient?.unregisterCallback(remoteErrors);remoteClient=null
        VideoSessionHandoff.detach(player)
        closeServer()
        PlaybackCoordinator.unregisterVideo(this)
        changed(false)
        if(wasActive && !abandoned)restore(position,playing)
        if(abandoned)release()
    }
    fun stop() {
        runCatching {CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)}
        returnToPhone()
    }
    fun abandon(value: Any) {if(owner!==value)return;owner=null;abandoned=true;native={null};changed={};restore={_,_->};if(!active)release()}
    private fun release() {if(released)return;released=true;if(shared===this)shared=null;work?.cancel();closeServer();PlaybackCoordinator.unregisterVideo(this);runCatching {player.release()};scope.cancel()}
    private fun message(value: String){Toast.makeText(context,value,Toast.LENGTH_LONG).show()}
}
