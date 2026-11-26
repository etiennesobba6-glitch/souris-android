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

/** Ce que fait un glissement sur le pavé. */
enum class Mode(val label: String) {
    CLICK("Clic"),
    DRAG("Glisser")
}

/**
 * Service d'accessibilité. Fenêtres flottantes indépendantes :
 *  - le POINTEUR    : jamais tactile (FLAG_NOT_TOUCHABLE), ne bloque rien dessous
 *  - le PAVÉ        : petit, déplaçable (poignée), les touches hors du pavé passent
 *  - le BOUTON ROND : toujours au-dessus, déplaçable
 *        appui court = souris on/off, appui long = arrêt complet (arrêt d'urgence)
 *
 * Modes du pavé : Clic, Glisser (glisser-déposer).
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
        private const val DRAG_HOLD_MS = 500L
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // --- fenêtres
    private var cursor: CursorView? = null
    private lateinit var cursorParams: WindowManager.LayoutParams
    private var pad: View? = null
    private var padParams: WindowManager.LayoutParams? = null
    private var padX = -1
    private var padY = -1
    private lateinit var toggle: TextView
    private lateinit var toggleParams: WindowManager.LayoutParams

    // --- état
    private var mouseOn = false
    private var mode = Mode.CLICK
    private var cursorType = CursorType.ARROW
    private var cx = 300f
    private var cy = 400f
    private var screenW = 0
    private var screenH = 0
    private var density = 1f

    private val sensitivity = 1.8f

    // --- glisser-déposer
    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragActive = false
    private var dragBusy = false
    private var dragFinishing = false
    private var dragX = 0f
    private var dragY = 0f

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
            padX = -1 // la taille de l'écran change : position par défaut
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
        if (dragActive) finishDrag()
        removeQuietly(cursor)
        cursor = null
        removeQuietly(pad)
        pad = null
        padParams = null
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

    private fun smallButton(label: String, onClick: (Button) -> Unit): Button =
        Button(this).apply {
            isAllCaps = false
            textSize = 11f
            text = label
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                onClick(this)
                resetIdle()
            }
        }

    private fun addPad() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(150, 30, 30, 30))
        }
        val rowH = (38 * density).toInt()

        // Poignée de déplacement
        val handle = TextView(this).apply {
            text = "≡  Glisser pour déplacer  ≡"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 70, 70, 70))
            minHeight = (36 * density).toInt()
            setOnTouchListener(PadMoveListener())
        }
        container.addView(
            handle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Ligne : mode + forme du pointeur
        val modeBtn = smallButton("Mode : ${mode.label}") { b ->
            val all = Mode.values()
            setMode(all[(mode.ordinal + 1) % all.size])
            b.text = "Mode : ${mode.label}"
        }
        val typeBtn = smallButton("Forme : ${cursorType.label}") { b ->
            val all = CursorType.values()
            cursorType = all[(cursorType.ordinal + 1) % all.size]
            cursor?.type = cursorType
            b.text = "Forme : ${cursorType.label}"
            updateCursor()
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(modeBtn, LinearLayout.LayoutParams(0, rowH, 1f))
        topRow.addView(typeBtn, LinearLayout.LayoutParams(0, rowH, 1f))
        container.addView(
            topRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Zone tactile
        val touchArea = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(90, 255, 255, 255))
            setOnTouchListener(PadTouchListener())
        }
        container.addView(
            touchArea,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val w = (screenW * PAD_WIDTH_RATIO).toInt()
        val h = (screenH * PAD_HEIGHT_RATIO).toInt()
        if (padX < 0 || padY < 0) {
            // Position par défaut : en bas à droite, au-dessus de la barre de navigation
            padX = screenW - w - (8 * density).toInt()
            padY = screenH - h - (48 * density).toInt()
        }
        padX = padX.coerceIn(0, (screenW - w).coerceAtLeast(0))
        padY = padY.coerceIn(0, (screenH - h).coerceAtLeast(0))

        val params = WindowManager.LayoutParams(
            w,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = padX
            y = padY
        }
        padParams = params
        pad = container
        wm.addView(container, params)
    }

    private fun setMode(m: Mode) {
        if (dragActive) finishDrag()
        mode = m
        resetIdle()
    }

    /** Poignée en haut du pavé : la faire glisser déplace tout le pavé. */
    private inner class PadMoveListener : View.OnTouchListener {
        private var startRawX = 0f
        private var startRawY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = padParams ?: return false
            val view = pad ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = e.rawX
                    startRawY = e.rawY
                    startX = p.x
                    startY = p.y
                    resetIdle()
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (startX + (e.rawX - startRawX)).toInt()
                        .coerceIn(0, (screenW - p.width).coerceAtLeast(0))
                    p.y = (startY + (e.rawY - startRawY)).toInt()
                        .coerceIn(0, (screenH - p.height).coerceAtLeast(0))
                    padX = p.x
                    padY = p.y
                    wm.updateViewLayout(view, p)
                    resetIdle()
                }
            }
            return true
        }
    }

    /**
     * Zone tactile. Selon le mode :
     *  Clic    : glisser = déplacer, toucher = clic, appui long = clic long
     *  Glisser : appui maintenu à la position du pointeur, puis déplacement (glisser-déposer)
     */
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
                    if (mode == Mode.DRAG) startDrag()
                }
                MotionEvent.ACTION_MOVE -> {
                    cx = (cx + (e.rawX - lastX) * sensitivity).coerceIn(0f, screenW.toFloat())
                    cy = (cy + (e.rawY - lastY) * sensitivity).coerceIn(0f, screenH.toFloat())
                    lastX = e.rawX
                    lastY = e.rawY
                    if (abs(e.rawX - downX) > 12 || abs(e.rawY - downY) > 12) moved = true
                    updateCursor()
                    if (mode == Mode.DRAG) pumpDrag()
                    resetIdle()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when (mode) {
                        Mode.CLICK -> {
                            if (e.actionMasked == MotionEvent.ACTION_UP && !moved) {
                                val held = e.eventTime - downTime
                                tap(cx, cy, if (held > 500) 700 else 50)
                            }
                        }
                        Mode.DRAG -> finishDrag()
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

    // ---------------------------------------------------------------- glisser-déposer

    /**
     * Un seul geste continu, envoyé en tronçons : appui maintenu (DRAG_HOLD_MS) à la
     * position du pointeur, puis de petits tronçons qui suivent le pointeur
     * (continueStroke). Le doigt se relève au dernier tronçon.
     */
    private fun startDrag() {
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, DRAG_HOLD_MS, true)
        dragStroke = stroke
        dragX = cx
        dragY = cy
        dragActive = true
        dragFinishing = false
        sendDrag(stroke)
    }

    private fun finishDrag() {
        if (!dragActive) return
        dragFinishing = true
        pumpDrag()
    }

    private fun pumpDrag() {
        if (!dragActive || dragBusy) return
        val prev = dragStroke ?: return
        val tx = cx
        val ty = cy
        val movedEnough = abs(tx - dragX) > 1f || abs(ty - dragY) > 1f
        if (!movedEnough && !dragFinishing) return

        val path = Path().apply {
            moveTo(dragX, dragY)
            if (movedEnough) lineTo(tx, ty)
        }
        val next = prev.continueStroke(
            path, 0, if (dragFinishing) 30L else 16L, !dragFinishing
        )
        dragStroke = next
        if (movedEnough) {
            dragX = tx
            dragY = ty
        }
        sendDrag(next)
    }

    private fun sendDrag(stroke: GestureDescription.StrokeDescription) {
        dragBusy = true
        val accepted = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dragBusy = false
                    if (!stroke.willContinue()) endDragState() else pumpDrag()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    dragBusy = false
                    endDragState()
                }
            },
            null
        )
        if (!accepted) {
            dragBusy = false
            endDragState()
        }
    }

    private fun endDragState() {
        dragActive = false
        dragFinishing = false
        dragStroke = null
    }
}
