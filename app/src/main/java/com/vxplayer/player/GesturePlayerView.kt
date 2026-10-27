package com.vxplayer.player

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/** Handles background touches only; controller buttons and the time bar keep their own events. */
@androidx.annotation.OptIn(UnstableApi::class)
class GesturePlayerView(context: Context) : PlayerView(context) {
    var onSeek: (forward: Boolean) -> Unit = {}
    var onSwipeStart: (left: Boolean) -> Unit = {}
    var onSwipe: (fraction: Float) -> Unit = {}
    var onSwipeEnd: () -> Unit = {}
    private var vertical: Boolean? = null
    private var ignoreTouch = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            vertical = null
            return true
        }

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            performClick()
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            onSeek(event.x >= width / 2f)
            return true
        }

        override fun onScroll(start: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (start == null || height == 0) return false
            val deltaY = start.y - current.y
            if (vertical == null) {
                vertical = abs(deltaY) > abs(current.x - start.x)
                if (vertical == true) onSwipeStart(start.x < width / 2f)
            }
            if (vertical == true) onSwipe(deltaY / height)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoreTouch = false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            cancelGesture()
            ignoreTouch = true
        }
        if (!ignoreTouch) detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishSwipe()
        }
        return true
    }

    private fun finishSwipe() {
        if (vertical == true) onSwipeEnd()
        vertical = null
    }

    private fun cancelGesture() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        detector.onTouchEvent(cancel)
        cancel.recycle()
        finishSwipe()
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }
}
