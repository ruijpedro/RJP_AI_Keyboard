package pt.rjp.aikeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class RjpKeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var rows: List<List<KeySpec>> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }
    var onKey: ((KeySpec) -> Unit)? = null

    private data class Hit(val rect: RectF, val key: KeySpec)
    private val hits = ArrayList<Hit>()
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val specialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDE2E8.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF17202A.toInt(); textAlign = Paint.Align.CENTER }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8D5E3.toInt() }
    private var pressed: Hit? = null
    private val density = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowHeight = (54 * density).toInt()
        val h = max(suggestedMinimumHeight, rowHeight * max(1, rows.size) + (6 * density).toInt())
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFFF1F3F6.toInt())
        hits.clear()
        if (rows.isEmpty()) return
        val gap = 4f * density
        val rowH = height.toFloat() / rows.size
        textPaint.textSize = 20f * density

        rows.forEachIndexed { r, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val available = width - gap * (row.size + 1)
            var x = gap
            row.forEach { key ->
                val kw = available * (key.weight / totalWeight)
                val rect = RectF(x, r * rowH + gap / 2, x + kw, (r + 1) * rowH - gap / 2)
                val hit = Hit(rect, key)
                hits.add(hit)
                val paint = if (pressed?.key === key && pressed?.rect == rect) pressedPaint else if (key.value.startsWith("{")) specialPaint else keyPaint
                canvas.drawRoundRect(rect, 9f * density, 9f * density, paint)
                val fm = textPaint.fontMetrics
                val ty = rect.centerY() - (fm.ascent + fm.descent) / 2
                canvas.drawText(key.label, rect.centerX(), ty, textPaint)
                x += kw + gap
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                pressed = hits.firstOrNull { it.rect.contains(event.x, event.y) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = hits.firstOrNull { it.rect.contains(event.x, event.y) }
                if (hit != null && hit.key == pressed?.key) onKey?.invoke(hit.key)
                pressed = null
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressed = null; invalidate(); return true }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
