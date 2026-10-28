package com.vxplayer.player

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class GesturePlayerView(context: Context) : PlayerView(context) {
    var locked = false
    var onTap: () -> Unit = {}
    var onDoubleTapZone: (Int) -> Unit = {}
    var onVerticalStart: (Boolean) -> Unit = {}
    var onVertical: (Float) -> Unit = {}
    var onHorizontalStart: () -> Unit = {}
    var onHorizontal: (Float) -> Unit = {}
    var onGestureEnd: () -> Unit = {}
    var onZoom: (Float) -> Unit = {}
    var onHold: (Boolean) -> Unit = {}
    private var axis = 0
    private var scaling = false
    private var holding = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { axis = 0; return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!locked) onDoubleTapZone(if (e.x < width / 3f) -1 else if (e.x > width * 2f / 3f) 1 else 0)
            return true
        }
        override fun onLongPress(e: MotionEvent) { if (!locked && !scaling) { holding = true; onHold(true) } }
        override fun onScroll(start: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
            if (start == null || locked || holding || scaling || width == 0 || height == 0) return false
            if (axis == 0) {
                axis = if (abs(current.y - start.y) > abs(current.x - start.x)) 1 else 2
                if (axis == 1) onVerticalStart(start.x < width / 2f) else onHorizontalStart()
            }
            if (axis == 1) onVertical((start.y - current.y) / height) else onHorizontal((current.x - start.x) / width)
            return true
        }
    })
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (locked) return false
            cancelGesture(); scaling = true; return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean { if (!locked) onZoom(detector.scaleFactor); return true }
    })
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scaling = false
        if (!locked) scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) { cancelGesture(); scaling = true }
        if (!scaling) detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) finish()
        return true
    }
    private fun finish() {
        if (holding) { holding = false; onHold(false) }
        if (axis != 0) onGestureEnd()
        axis = 0
    }
    private fun cancelGesture() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        detector.onTouchEvent(event); event.recycle(); finish()
    }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
