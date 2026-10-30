package com.example.souris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View

/**
 * Calque de dessin plein écran, jamais tactile : il affiche les traits tracés
 * avec le pointeur par-dessus les autres applis. La gomme efface les traits
 * précédents (mode CLEAR), et chaque trait peut être annulé.
 */
class DrawingView(context: Context) : View(context) {

    private class Stroke(val path: Path, val paint: Paint, var lastX: Float, var lastY: Float) {
        var moved = false
    }

    private val strokes = ArrayList<Stroke>()
    private var current: Stroke? = null

    init {
        // Nécessaire pour que la gomme (PorterDuff.CLEAR) efface réellement les traits
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun beginStroke(x: Float, y: Float, argb: Int, widthPx: Float, erase: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = widthPx
            if (erase) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                color = argb
            }
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = Stroke(path, paint, x, y)
        strokes.add(stroke)
        current = stroke
        invalidate()
    }

    fun extendStroke(x: Float, y: Float) {
        val s = current ?: return
        s.path.quadTo(s.lastX, s.lastY, (s.lastX + x) / 2f, (s.lastY + y) / 2f)
        s.lastX = x
        s.lastY = y
        s.moved = true
        invalidate()
    }

    fun endStroke() {
        val s = current ?: return
        // Un simple appui sans déplacement donne un point (cap rond)
        if (s.moved) s.path.lineTo(s.lastX, s.lastY) else s.path.lineTo(s.lastX + 0.01f, s.lastY)
        current = null
        invalidate()
    }

    fun undo() {
        if (current != null) endStroke()
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clear() {
        current = null
        strokes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for (s in strokes) canvas.drawPath(s.path, s.paint)
    }
}
