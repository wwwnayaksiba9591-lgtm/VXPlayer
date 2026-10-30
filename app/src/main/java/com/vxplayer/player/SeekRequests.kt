package com.vxplayer.player

/** Latest request wins. Relative taps accumulate against the pending target, not stale player time. */
class SeekRequests(private val quietMs: Long = 90) {
    var pending: Long? = null
        private set
    private var due = 0L
    fun request(target: Long, duration: Long, now: Long) { pending = boundedSeek(target,duration); due = now + quietMs }
    fun take(now: Long): Long? = if (pending != null && now >= due) pending.also { pending = null } else null
    fun cancel() { pending = null }
}
