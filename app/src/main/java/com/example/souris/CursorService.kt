package com.example.souris

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Service d'accessibilité : affiche deux fenêtres flottantes
 *  - le pointeur (non tactile, les touches passent à travers)
 *  - un pavé tactile avec boutons pour piloter le pointeur
 * Les clics sont envoyés à la position du pointeur via dispatchGesture().
 */
class CursorService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var cursor: CursorView
    private lateinit var cursorParams: WindowManager.LayoutParams
    private lateinit var pad: View

    private var cx = 300f
    private var cy = 400f
    private var screenW = 0
    private var screenH = 0

    private val sensitivity = 1.8f

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels

        addCursor()
        addPad()
    }

    private fun addCursor() {
        cursor = CursorView(this)
        cursorParams = WindowManager.LayoutParams(
            CursorView.SIZE,
            CursorView.SIZE,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(cursor, cursorParams)
        updateCursor()
    }

    private fun updateCursor() {
        val h = cursor.hotspot()
        cursorParams.x = (cx - h.x).toInt()
        cursorParams.y = (cy - h.y).toInt()
        wm.updateViewLayout(cursor, cursorParams)
    }

    private fun addPad() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(150, 30, 30, 30))
        }

        val typeButton = Button(this).apply {
            text = "Pointeur"
            setOnClickListener {
                val all = CursorType.values()
                cursor.type = all[(cursor.type.ordinal + 1) % all.size]
                updateCursor()
            }
        }

        val touchArea = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(90, 255, 255, 255))
            setOnTouchListener(PadTouchListener())
        }

        container.addView(
            typeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            touchArea,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        pad = container
        val params = WindowManager.LayoutParams(
            (screenW * 0.55f).toInt(),
            (screenH * 0.28f).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END }
        wm.addView(pad, params)
    }

    /** Glisser = déplacer, toucher = clic, appui long = clic long. */
    private inner class PadTouchListener : View.OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var moved = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX; lastY = e.rawY
                    downX = e.rawX; downY = e.rawY
                    downTime = e.eventTime
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    cx = (cx + (e.rawX - lastX) * sensitivity).coerceIn(0f, screenW.toFloat())
                    cy = (cy + (e.rawY - lastY) * sensitivity).coerceIn(0f, screenH.toFloat())
                    lastX = e.rawX; lastY = e.rawY
                    if (abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12) moved = true
                    updateCursor()
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val held = e.eventTime - downTime
                        tap(cx, cy, if (held > 500) 700 else 50)
                    }
                }
            }
            return true
        }
    }

    private fun tap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        try {
            wm.removeView(cursor)
            wm.removeView(pad)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
