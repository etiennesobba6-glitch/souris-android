package com.example.souris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.View

enum class CursorType(val label: String) {
    ARROW("Flèche"),
    HAND("Main"),
    TEXT("Texte"),
    CROSS("Croix"),
    DOT("Point")
}

/**
 * Vue SIZE x SIZE px qui dessine le pointeur. Le "point chaud" (hotspot) est
 * l'endroit précis de l'écran où le clic est envoyé ; il dépend du type.
 */
class CursorView(context: Context) : View(context) {

    companion object {
        const val SIZE = 96
    }

    var type: CursorType = CursorType.ARROW
        set(value) {
            field = value
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    fun hotspot(): PointF = when (type) {
        CursorType.ARROW -> PointF(6f, 4f)
        CursorType.HAND -> PointF(30f, 6f)
        CursorType.TEXT, CursorType.CROSS, CursorType.DOT -> PointF(SIZE / 2f, SIZE / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        when (type) {
            CursorType.ARROW -> drawArrow(canvas)
            CursorType.HAND -> drawHand(canvas)
            CursorType.TEXT -> drawText(canvas)
            CursorType.CROSS -> drawCross(canvas)
            CursorType.DOT -> drawDot(canvas)
        }
    }

    private fun drawArrow(c: Canvas) {
        val p = Path().apply {
            moveTo(6f, 4f)
            lineTo(6f, 70f)
            lineTo(24f, 54f)
            lineTo(38f, 84f)
            lineTo(48f, 79f)
            lineTo(34f, 50f)
            lineTo(58f, 50f)
            close()
        }
        c.drawPath(p, fill)
        c.drawPath(p, stroke)
    }

    private fun drawHand(c: Canvas) {
        val finger = RectF(24f, 6f, 38f, 46f)
        val palm = RectF(14f, 40f, 66f, 86f)
        c.drawRoundRect(palm, 14f, 14f, fill)
        c.drawRoundRect(palm, 14f, 14f, stroke)
        c.drawRoundRect(finger, 7f, 7f, fill)
        c.drawRoundRect(finger, 7f, 7f, stroke)
    }

    // Contour blanc puis trait noir : reste visible sur tout fond
    private fun drawText(c: Canvas) {
        val cx = SIZE / 2f
        for (p in listOf(halo, stroke)) {
            c.drawLine(cx, 20f, cx, 76f, p)
            c.drawLine(cx - 10f, 20f, cx + 10f, 20f, p)
            c.drawLine(cx - 10f, 76f, cx + 10f, 76f, p)
        }
    }

    private fun drawCross(c: Canvas) {
        val m = SIZE / 2f
        for (p in listOf(halo, stroke)) {
            c.drawLine(m, 16f, m, SIZE - 16f, p)
            c.drawLine(16f, m, SIZE - 16f, m, p)
        }
    }

    private fun drawDot(c: Canvas) {
        val m = SIZE / 2f
        c.drawCircle(m, m, 17f, fill)
        c.drawCircle(m, m, 12f, black)
    }
}
