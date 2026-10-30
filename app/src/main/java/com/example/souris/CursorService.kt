package com.example.souris

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Service d'accessibilité. Trois fenêtres flottantes indépendantes :
 *  - le POINTEUR  : jamais tactile (FLAG_NOT_TOUCHABLE), ne bloque rien dessous
 *  - le PAVÉ      : petit (40 % x 25 %), les touches hors du pavé passent normalement
 *  - le BOUTON    : petit rond déplaçable, toujours au-dessus de tout
 *        appui court  = activer / désactiver la souris
 *        appui long   = arrêt complet du service (arrêt d'urgence)
 *
 * Extinction automatique de la souris après 2 minutes sans utilisation.
 * Les clics sont envoyés à la position du pointeur via dispatchGesture().
 */
class CursorService : AccessibilityService() {

    companion object {
        /** Permet à l'écran de l'appli d'arrêter le service. */
        var instance: CursorService? = null

        private const val IDLE_TIMEOUT_MS = 2 * 60 * 1000L
        private const val LONG_PRESS_STOP_MS = 900L
        private const val PAD_WIDTH_RATIO = 0.40f
        private const val PAD_HEIGHT_RATIO = 0.25f
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var cursor: CursorView? = null
    private lateinit var cursorParams: WindowManager.LayoutParams
    private var pad: View? = null
    private lateinit var toggle: TextView
    private lateinit var toggleParams: WindowManager.LayoutParams

    private var mouseOn = false
    private var cursorType = CursorType.ARROW
    private var cx = 300f
    private var cy = 400f
    private var screenW = 0
    private var screenH = 0
    private var density = 1f

    private val sensitivity = 1.8f

    private val idleOff = Runnable {
        if (mouseOn) {
            setMouse(false)
            Toast.makeText(this, "Souris désactivée (inactivité)", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- cycle de vie

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        readScreenSize()
        addToggle()
        setMouse(true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::wm.isInitialized || !::toggle.isInitialized) return
        readScreenSize()
        cx = cx.coerceIn(0f, screenW.toFloat())
        cy = cy.coerceIn(0f, screenH.toFloat())
        toggleParams.x = toggleParams.x.coerceIn(0, (screenW - toggle.width).coerceAtLeast(0))
        toggleParams.y = toggleParams.y.coerceIn(0, (screenH - toggle.height).coerceAtLeast(0))
        wm.updateViewLayout(toggle, toggleParams)
        if (mouseOn) {
            hideMouse()
            showMouse()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(idleOff)
        if (::wm.isInitialized) {
            hideMouse()
            if (::toggle.isInitialized) removeQuietly(toggle)
        }
        instance = null
        super.onDestroy()
    }

    /** Arrêt d'urgence : coupe complètement le service. */
    fun stopMouse() {
        Toast.makeText(this, "Souris arrêtée", Toast.LENGTH_SHORT).show()
        disableSelf()
    }

    // ---------------------------------------------------------------- écran

    @Suppress("DEPRECATION")
    private fun readScreenSize() {
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels
        screenH = dm.heightPixels
    }

    // ---------------------------------------------------------------- on / off

    private fun setMouse(on: Boolean) {
        handler.removeCallbacks(idleOff)
        mouseOn = on
        if (on) {
            showMouse()
            handler.postDelayed(idleOff, IDLE_TIMEOUT_MS)
        } else {
            hideMouse()
        }
        refreshToggle()
    }

    private fun resetIdle() {
        if (!mouseOn) return
        handler.removeCallbacks(idleOff)
        handler.postDelayed(idleOff, IDLE_TIMEOUT_MS)
    }

    private fun showMouse() {
        if (cursor == null) addCursor()
        if (pad == null) addPad()
        bringToggleToFront()
    }

    private fun hideMouse() {
        removeQuietly(cursor)
        cursor = null
        removeQuietly(pad)
        pad = null
    }

    private fun removeQuietly(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (_: Exception) {
        }
    }

    /** Le bouton doit rester accessible quoi qu'il arrive : on le replace au-dessus. */
    private fun bringToggleToFront() {
        removeQuietly(toggle)
        wm.addView(toggle, toggleParams)
    }

    // ---------------------------------------------------------------- bouton flottant

    private fun addToggle() {
        val size = (52 * density).toInt()
        toggle = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        toggleParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - size - (8 * density).toInt()
            y = (screenH * 0.40f).toInt()
        }
        toggle.setOnTouchListener(ToggleTouchListener())
        wm.addView(toggle, toggleParams)
        refreshToggle()
    }

    private fun refreshToggle() {
        toggle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (mouseOn) Color.argb(220, 200, 40, 40)
                else Color.argb(200, 60, 60, 60)
            )
            setStroke((2 * density).toInt(), Color.WHITE)
        }
        toggle.text = if (mouseOn) "✕" else "M"
    }

    /** Glisser = déplacer le bouton, appui court = on/off, appui long = arrêt complet. */
    private inner class ToggleTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@CursorService).scaledTouchSlop
        private var startRawX = 0f
        private var startRawY = 0f
        private var startX = 0
        private var startY = 0
        private var downTime = 0L
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = e.rawX
                    startRawY = e.rawY
                    startX = toggleParams.x
                    startY = toggleParams.y
                    downTime = e.eventTime
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startRawX
                    val dy = e.rawY - startRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        toggleParams.x = (startX + dx).toInt()
                            .coerceIn(0, (screenW - v.width).coerceAtLeast(0))
                        toggleParams.y = (startY + dy).toInt()
                            .coerceIn(0, (screenH - v.height).coerceAtLeast(0))
                        wm.updateViewLayout(toggle, toggleParams)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val held = e.eventTime - downTime
                        // post : on ne retire/ajoute pas une vue pendant son propre événement
                        if (held >= LONG_PRESS_STOP_MS) handler.post { stopMouse() }
                        else handler.post { setMouse(!mouseOn) }
                    }
                }
            }
            return true
        }
    }

    // ---------------------------------------------------------------- pointeur

    private fun addCursor() {
        val view = CursorView(this).apply { type = cursorType }
        cursorParams = WindowManager.LayoutParams(
            CursorView.SIZE,
            CursorView.SIZE,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        cursor = view
        wm.addView(view, cursorParams)
        updateCursor()
    }

    private fun updateCursor() {
        val c = cursor ?: return
        val h = c.hotspot()
        cursorParams.x = (cx - h.x).toInt()
        cursorParams.y = (cy - h.y).toInt()
        wm.updateViewLayout(c, cursorParams)
    }

    // ---------------------------------------------------------------- pavé tactile

    private fun addPad() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(150, 30, 30, 30))
        }

        val typeButton = Button(this).apply {
            isAllCaps = false
            textSize = 12f
            text = "Pointeur : ${cursorType.label}"
            setOnClickListener {
                val all = CursorType.values()
                cursorType = all[(cursorType.ordinal + 1) % all.size]
                cursor?.type = cursorType
                text = "Pointeur : ${cursorType.label}"
                updateCursor()
                resetIdle()
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
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val params = WindowManager.LayoutParams(
            (screenW * PAD_WIDTH_RATIO).toInt(),
            (screenH * PAD_HEIGHT_RATIO).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (8 * density).toInt()
            y = (8 * density).toInt()
        }
        pad = container
        wm.addView(container, params)
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
                    lastX = e.rawX
                    lastY = e.rawY
                    downX = e.rawX
                    downY = e.rawY
                    downTime = e.eventTime
                    moved = false
                    resetIdle()
                }
                MotionEvent.ACTION_MOVE -> {
                    cx = (cx + (e.rawX - lastX) * sensitivity).coerceIn(0f, screenW.toFloat())
                    cy = (cy + (e.rawY - lastY) * sensitivity).coerceIn(0f, screenH.toFloat())
                    lastX = e.rawX
                    lastY = e.rawY
                    if (abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12) moved = true
                    updateCursor()
                    resetIdle()
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val held = e.eventTime - downTime
                        tap(cx, cy, if (held > 500) 700 else 50)
                    }
                    resetIdle()
                }
            }
            return true
        }
    }

    // ---------------------------------------------------------------- clic

    private fun tap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}
