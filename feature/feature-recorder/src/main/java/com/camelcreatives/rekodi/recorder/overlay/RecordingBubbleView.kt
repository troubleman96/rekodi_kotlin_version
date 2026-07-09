package com.camelcreatives.rekodi.recorder.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.camelcreatives.rekodi.recorder.R
import com.camelcreatives.rekodi.recorder.service.RecordingForegroundService
import com.camelcreatives.rekodi.recorder.model.RecordingState

class RecordingBubbleView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var bubbleView: ViewGroup? = null
    private var miniPanel: View? = null
    private var expandedPanel: View? = null
    private var idleTimer: Handler? = null
    private var isIdle = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var stateCallback: ((RecordingState) -> Unit)? = null
    private var closeListener: (() -> Unit)? = null
    private var tapCountText: TextView? = null

    fun show(state: RecordingState = RecordingState.IDLE) {
        if (bubbleView != null) return

        bubbleView = LayoutInflater.from(context).inflate(
            com.camelcreatives.rekodi.recorder.R.layout.bubble_layout, null
        ) as ViewGroup

        miniPanel = bubbleView?.findViewById(R.id.mini_panel)
        expandedPanel = bubbleView?.findViewById(R.id.expanded_panel)
        tapCountText = bubbleView?.findViewById(R.id.tap_count)

        updateState(state)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        windowManager.addView(bubbleView, params)
        setupTouchListener(params)
        setupClickListeners()
        startIdleTimer()
    }

    fun updateState(state: RecordingState) {
        val mini = miniPanel ?: return
        val expanded = expandedPanel ?: return

        when (state) {
            RecordingState.IDLE -> {
                mini.visibility = View.VISIBLE
                expanded.visibility = View.GONE
                bubbleView?.findViewById<TextView>(R.id.state_text)?.text = "Rekodi"
                bubbleView?.findViewById<TextView>(R.id.timer_text)?.text = "Ready"
                showExpandedPanel()
            }
            RecordingState.COUNTDOWN -> {
                mini.visibility = View.VISIBLE
                expanded.visibility = View.GONE
                bubbleView?.findViewById<TextView>(R.id.state_text)?.text = "Starting..."
                bubbleView?.alpha = 1f
            }
            RecordingState.RECORDING -> {
                mini.visibility = View.VISIBLE
                expanded.visibility = View.GONE
                bubbleView?.findViewById<TextView>(R.id.state_text)?.text = "● Recording"
            }
            RecordingState.PAUSED -> {
                mini.visibility = View.VISIBLE
                expanded.visibility = View.GONE
                bubbleView?.findViewById<TextView>(R.id.state_text)?.text = "⏸ Paused"
            }
            else -> {}
        }
    }

    fun updateTimerText(text: String) {
        bubbleView?.findViewById<TextView>(R.id.timer_text)?.text = text
    }

    fun updateTapCount(count: Int) {
        tapCountText?.text = if (count > 0) count.toString() else ""
        tapCountText?.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    fun setStateCallback(callback: (RecordingState) -> Unit) {
        stateCallback = callback
    }

    fun setOnCloseListener(listener: () -> Unit) {
        closeListener = listener
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    resetIdleTimer()
                    isIdle = false
                    bubbleView?.alpha = 1f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    snapToEdge(params)
                    startIdleTimer()
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                        bubbleView?.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupClickListeners() {
        bubbleView?.findViewById<View>(R.id.mini_panel)?.setOnClickListener {
            if (!isIdle) {
                bubbleView?.findViewById<View>(R.id.expanded_panel)?.let {
                    it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
        }

        bubbleView?.findViewById<View>(R.id.btn_record)?.setOnClickListener {
            stateCallback?.invoke(RecordingState.RECORDING)
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    context.packageName,
                    "com.camelcreatives.rekodi.service.MediaProjectionTrampolineActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        bubbleView?.findViewById<View>(R.id.btn_stop)?.setOnClickListener {
            context.startService(Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_STOP
            })
        }

        bubbleView?.findViewById<View>(R.id.btn_pause)?.setOnClickListener {
            context.startService(Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_PAUSE
            })
        }

        bubbleView?.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            hide()
            closeListener?.invoke()
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val display = windowManager.defaultDisplay
        val size = android.graphics.Point()
        display.getSize(size)
        val targetX = if (params.x < size.x / 2) 0 else size.x - bubbleView!!.width
        val anim = ValueAnimator.ofInt(params.x, targetX)
        anim.addUpdateListener {
            params.x = it.animatedValue as Int
            windowManager.updateViewLayout(bubbleView, params)
        }
        anim.duration = 200
        anim.start()
    }

    private fun startIdleTimer() {
        idleTimer?.removeCallbacksAndMessages(null)
        idleTimer = Handler(Looper.getMainLooper())
        idleTimer?.postDelayed({
            isIdle = true
            ObjectAnimator.ofFloat(bubbleView, "alpha", 0.4f).apply {
                duration = 500
                start()
            }
        }, 3000)
    }

    private fun resetIdleTimer() {
        idleTimer?.removeCallbacksAndMessages(null)
        isIdle = false
        bubbleView?.alpha = 1f
    }

    private fun showExpandedPanel() {
        resetIdleTimer()
        bubbleView?.findViewById<View>(R.id.expanded_panel)?.let {
            it.visibility = View.VISIBLE
            it.postDelayed({
                it.visibility = View.GONE
            }, 5000)
        }
    }

    fun hide() {
        idleTimer?.removeCallbacksAndMessages(null)
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
    }
}
