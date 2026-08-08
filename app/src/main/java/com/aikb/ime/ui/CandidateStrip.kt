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
        typeface = Typeface.DEFAULT
    }

    private var drawableBg0: GradientDrawable? = null
    private var drawableBg1: GradientDrawable? = null

    constructor(ctx: Context) : super(ctx) { init() }
    constructor(ctx: Context, attrs: AttributeSet?) : super(ctx, attrs) { init() }
    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr) { init() }
    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(ctx, attrs, defStyleAttr, defStyleRes) { init() }

    fun setSuggestionsCallback(cb: (Int) -> Unit) { callback = cb }
    fun getSuggestion(pos: Int): String? = items.getOrNull(pos)

    private fun init() { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            paint.textSize = sp2pxF(13f)
        } catch (e: Exception) {
            paint.textSize = 13f * resources.displayMetrics.scaledDensity
        }
        val corner = dp2pxF(8f)
        drawableBg0 = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(Color.parseColor("#2563EB"))
        }
        drawableBg1 = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(Color.parseColor("#1E3A5F"))
        }
    }

    fun setSuggestions(suggestions: List<String>) {
        items.clear()
        items.addAll(suggestions)
        if (measuredWidth > 0) itemWidth = (measuredWidth - 2 * padding) / 3f
        postInvalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val reqWidth = dp2pxF(480f)
        val reqHeight = dp2pxF(50f)
        val width = if (MeasureSpec.getSize(wSpec) > 0) MeasureSpec.getSize(wSpec) else reqWidth.toInt()
        val height = if (MeasureSpec.getSize(hSpec) > 0) MeasureSpec.getSize(hSpec) else reqHeight.toInt()
        itemHeight = height.toFloat()
        padding = dp2pxF(8f)
        itemWidth = (width - 2 * padding) / 3f
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bg0 = drawableBg0 ?: return
        val bg1 = drawableBg1 ?: return
        val visible = items.size.coerceAtMost(3)
        for (i in 0 until visible) {
            val x = padding + i * itemWidth
            val bg = if (i == 0) bg0 else bg1
            bg.setBounds(x.toInt(), 0, (x + itemWidth).toInt(), itemHeight.toInt())
            bg.draw(canvas)
            val text = items[i]
            val display = if (text.length > 20) text.take(18) + "..." else text
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

    private fun dp2pxF(dp: Float) = dp * context.resources.displayMetrics.density
    private fun sp2pxF(sp: Float) = sp * context.resources.displayMetrics.scaledDensity
}