package com.camelcreatives.rekodi.recorder.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.ColorUtils

class ZoomOverlayView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var overlayView: ZoomDrawView? = null
    private var isShowing = false
    private var rippleColor: Long = 0xFFE8A33D

    fun show() {
        if (isShowing) return
        overlayView = ZoomDrawView(context).apply {
            setRippleColor(rippleColor)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(overlayView, params)
        isShowing = true
    }

    fun hide() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        isShowing = false
    }

    fun setRippleColor(colorHex: String) {
        try {
            rippleColor = Color.parseColor(colorHex).toLong()
            overlayView?.setRippleColor(rippleColor)
        } catch (_: Exception) {}
    }

    private class ZoomDrawView(context: Context) : View(context) {
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val magnifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        private var ripples = mutableListOf<RippleData>()
        private val handler = Handler(Looper.getMainLooper())

        private data class RippleData(
            val x: Float, val y: Float,
            var radius: Float = 10f,
            var alpha: Float = 0.6f,
            val maxRadius: Float = 120f
        )

        fun setRippleColor(color: Long) {
            ripplePaint.color = ColorUtils.setAlphaComponent(color.toInt(), 150)
            fillPaint.color = ColorUtils.setAlphaComponent(color.toInt(), 60)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                addRipple(event.x, event.y)
            }
            return super.onTouchEvent(event)
        }

        fun simulateTap(x: Float, y: Float) {
            addRipple(x, y)
        }

        private fun addRipple(x: Float, y: Float) {
            val ripple = RippleData(x, y)
            ripples.add(ripple)
            animateRipple(ripple)
        }

        private fun animateRipple(ripple: RippleData) {
            val anim = ValueAnimator.ofFloat(10f, ripple.maxRadius)
            anim.duration = 500
            anim.addUpdateListener {
                ripple.radius = it.animatedValue as Float
                ripple.alpha = 0.6f * (1f - (ripple.radius / ripple.maxRadius))
                invalidate()
            }
            anim.start()
            handler.postDelayed({
                ripples.remove(ripple)
                invalidate()
            }, 600)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (ripple in ripples) {
                ripplePaint.alpha = (ripple.alpha * 255).toInt()
                fillPaint.alpha = (ripple.alpha * 100).toInt()
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius, fillPaint)
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius * 1.5f, ripplePaint)
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            handler.removeCallbacksAndMessages(null)
        }
    }
}
