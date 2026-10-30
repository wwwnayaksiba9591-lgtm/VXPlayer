package com.vxplayer.player

/** Called on the main thread; each owner unregisters only its own callback. */
object PlaybackCoordinator {
    var pauseMusic: (() -> Unit)? = null
    private val videos = linkedMapOf<Any,() -> Unit>()
    fun registerVideo(owner: Any, pause: () -> Unit) { videos[owner] = pause }
    fun unregisterVideo(owner: Any) { videos.remove(owner) }
    fun startingVideo(owner: Any? = null) { pauseMusic?.invoke(); videos.filterKeys { it !== owner }.values.toList().forEach { it() } }
    fun startingMusic() { videos.values.toList().forEach { it() } }
}
