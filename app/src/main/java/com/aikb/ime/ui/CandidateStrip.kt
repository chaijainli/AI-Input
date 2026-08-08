package com.aikb.ime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CandidateStrip : View {

    private val items = mutableListOf<String>()
    private var padding = 0f
    private var itemWidth = 0f
    private var itemHeight = 0f
    private var callback: ((Int) -> Unit)? = null
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp2px(13f)
        typeface = Typeface.DEFAULT
    }

    constructor(ctx: Context) : super(ctx) { init() }
    constructor(ctx: Context, attrs: AttributeSet?) : super(ctx, attrs, 0) { init() }

    fun setSuggestionsCallback(cb: (Int) -> Unit) { callback = cb }
    fun getSuggestion(pos: Int): String? = items.getOrNull(pos)

    private fun init() { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setSuggestions(suggestions: List<String>) {
        items.clear()
        items.addAll(suggestions)
        if (measuredWidth > 0) itemWidth = (measuredWidth - 2 * padding) / 3f
        postInvalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val reqWidth = dp2px(480f)
        val reqHeight = dp2px(50f)
        val width = if (MeasureSpec.getSize(wSpec) > 0) MeasureSpec.getSize(wSpec) else reqWidth.toInt()
        val height = if (MeasureSpec.getSize(hSpec) > 0) MeasureSpec.getSize(hSpec) else reqHeight.toInt()
        itemHeight = height.toFloat()
        padding = dp2px(8f)
        itemWidth = (width - 2 * padding) / 3f
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visible = items.size.coerceAtMost(3)
        for (i in 0 until visible) {
            val x = padding + i * itemWidth
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp2px(8f)
                setColor(Color.parseColor(if (i == 0) "#2563EB" else "#1E3A5F"))
                setBounds(x.toInt(), 0, (x + itemWidth).toInt(), itemHeight.toInt())
                draw(canvas)
            }
            val text = items[i]
            val display = if (text.length > 20) text.take(18) + "…" else text
            paint.color = Color.WHITE
            val ty = itemHeight / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(display, x + itemWidth / 2f, ty, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val pos = ((event.x - padding) / itemWidth).toInt().coerceIn(0, items.size - 1)
            callback?.invoke(pos)
        }
        return true
    }

    private fun dp2px(dp: Float) = dp * context.resources.displayMetrics.density
    private fun sp2px(sp: Float) = sp * context.resources.displayMetrics.scaledDensity
}