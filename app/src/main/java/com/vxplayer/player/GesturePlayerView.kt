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
    var displayMode=AspectMode.Fit
    private var nativeRatio=0f
    override fun onContentAspectRatioChanged(frame: androidx.media3.ui.AspectRatioFrameLayout?,ratio: Float) {
        nativeRatio=ratio
        super.onContentAspectRatioChanged(frame,displayMode.ratio.takeIf {it>0} ?: ratio)
    }
    fun applyDisplayMode(mode: AspectMode) {
        displayMode=mode
        resizeMode=when(mode) {AspectMode.Fill,AspectMode.Original->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;AspectMode.Zoom->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;else->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT}
        findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.setAspectRatio(mode.ratio.takeIf {it>0} ?: nativeRatio)
    }
    var locked = false
    var onTap: () -> Unit = {}
    var onOpenActions: () -> Unit = {}
    private var edge = false
    private var edgeX = 0f
    private var edgeY = 0f
    var onDoubleTapZone: (Int) -> Unit = {}
    var onVerticalStart: (Boolean) -> Unit = {}
    var onVertical: (Float) -> Unit = {}
    var onHorizontalStart: () -> Unit = {}
    var onHorizontal: (Float, Float) -> Unit = { _, _ -> }
    var onGestureEnd: (cancelled: Boolean) -> Unit = {}
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
                val density = resources.displayMetrics.density
                axis = swipeAxis((current.x - start.x) / density, (current.y - start.y) / density)
                if (axis == 0) return true
                if (axis == 1) onVerticalStart(start.x < width / 2f) else onHorizontalStart()
            }
            if (axis == 1) onVertical((start.y - current.y) / height) else {
                val density = resources.displayMetrics.density
                val distance = (current.x - start.x) / density
                val outsideSlop = kotlin.math.sign(distance) * (abs(distance) - 16f).coerceAtLeast(0f)
                onHorizontal(outsideSlop, width / density)
            }
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            edge = !locked && event.x >= width - 24 * resources.displayMetrics.density
            edgeX = event.x; edgeY = event.y
        }
        if (edge) {
            val density = resources.displayMetrics.density
            if (event.actionMasked == MotionEvent.ACTION_MOVE && com.vxplayer.ui.opensActionPanel(
                    edgeX/density,width/density,(event.x-edgeX)/density,(event.y-edgeY)/density)) {
                onOpenActions(); edge = false
                cancelGesture()
                scaling = true
            }
            if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) edge=false
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scaling = false
        if (!locked) scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) { cancelGesture(); scaling = true }
        if (!scaling) detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) finish(event.actionMasked == MotionEvent.ACTION_CANCEL)
        return true
    }
    private fun finish(cancelled: Boolean = false) {
        if (holding) { holding = false; onHold(false) }
        if (axis != 0) onGestureEnd(cancelled)
        axis = 0
    }
    private fun cancelGesture() {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        detector.onTouchEvent(event); event.recycle(); finish(true)
    }
    override fun onDetachedFromWindow() { cancelGesture(); super.onDetachedFromWindow() }
}
