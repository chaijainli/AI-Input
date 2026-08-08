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
        textAlign = Paint.Align.CENTER
    }

    private var drawableBg0: GradientDrawable? = null
    private var drawableBg1: GradientDrawable? = null

    private var pageNum = 0
    private var scrollOffset = 0f
    private var isSwiping = false
    private var swipeStartX = 0f
    private val SWIPE_THRESHOLD = 30f
    private var firstTouchX = 0f
    private var firstTouchY = 0f

    constructor(ctx: Context) : this(ctx, 0xFF2563EB.toInt(), 0xFF1E3A5F.toInt(), false)
    constructor(ctx: Context, attrs: AttributeSet?) : this(ctx, 0xFF2563EB.toInt(), 0xFF1E3A5F.toInt(), false)
    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(ctx, 0xFF2563EB.toInt(), 0xFF1E3A5F.toInt(), false)
    constructor(ctx: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(ctx, 0xFF2563EB.toInt(), 0xFF1E3A5F.toInt(), false)
    constructor(ctx: Context, primaryBg: Int, secondaryBg: Int) : this(ctx, primaryBg, secondaryBg, false)
    constructor(ctx: Context, primaryBg: Int, secondaryBg: Int, autoHide: Boolean) : super(ctx) {
        this.primaryBg = primaryBg
        this.secondaryBg = secondaryBg
        this.autoHide = autoHide
        init()
    }

    private var autoHide = false

    private var primaryBg: Int = 0xFF2563EB.toInt()
    private var secondaryBg: Int = 0xFF1E3A5F.toInt()

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
            setColor(primaryBg)
        }
        drawableBg1 = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(secondaryBg)
        }
    }

    fun setSuggestions(suggestions: List<String>) {
        items.clear()
        items.addAll(suggestions)
        pageNum = 0
        scrollOffset = 0f
        if (measuredWidth > 0) itemWidth = (measuredWidth - 2 * padding) / 3f
        if (autoHide) {
            visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
        }
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
        val totalItems = items.size
        val baseX = padding + scrollOffset

        for (i in 0 until 3) {
            val itemIdx = pageNum * 3 + i
            if (itemIdx >= totalItems) break
            val x = baseX + i * itemWidth
            val bg = if (i == 0) bg0 else bg1
            bg.setBounds(x.toInt(), 0, (x + itemWidth).toInt(), itemHeight.toInt())
            bg.draw(canvas)
            val text = items[itemIdx]
            val display = if (text.length > 20) text.take(18) + "..." else text
            paint.color = Color.WHITE
            val ty = itemHeight / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(display, x + itemWidth / 2f, ty, paint)
        }

        if (scrollOffset < 0) {
            val peekIdx = pageNum * 3 + 3
            if (peekIdx < totalItems) {
                val x = baseX + 3 * itemWidth
                bg1.setBounds(x.toInt(), 0, (x + itemWidth).toInt(), itemHeight.toInt())
                bg1.draw(canvas)
                val text = items[peekIdx]
                val display = if (text.length > 20) text.take(18) + "..." else text
                paint.color = Color.WHITE
                val ty = itemHeight / 2f - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(display, x + itemWidth / 2f, ty, paint)
            }
        } else if (scrollOffset > 0) {
            val peekIdx = pageNum * 3 - 1
            if (peekIdx >= 0) {
                val x = baseX - itemWidth
                bg1.setBounds(x.toInt(), 0, (x + itemWidth).toInt(), itemHeight.toInt())
                bg1.draw(canvas)
                val text = items[peekIdx]
                val display = if (text.length > 20) text.take(18) + "..." else text
                paint.color = Color.WHITE
                val ty = itemHeight / 2f - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(display, x + itemWidth / 2f, ty, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                firstTouchX = event.x
                firstTouchY = event.y
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - swipeStartX
                if (Math.abs(dx) > SWIPE_THRESHOLD && Math.abs(dx) > Math.abs(event.y - firstTouchY)) {
                    isSwiping = true
                    scrollOffset = dx
                    postInvalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isSwiping) {
                    val dx = event.x - swipeStartX
                    if (dx < -SWIPE_THRESHOLD) {
                        val maxPage = (items.size - 1) / 3
                        if (pageNum < maxPage) {
                            pageNum++
                            scrollOffset = 0f
                            postInvalidate()
                            return true
                        }
                    } else if (dx > SWIPE_THRESHOLD) {
                        if (pageNum > 0) {
                            pageNum--
                            scrollOffset = 0f
                            postInvalidate()
                            return true
                        }
                    }
                    scrollOffset = 0f
                    postInvalidate()
                    return true
                }
                val x = firstTouchX - padding
                val slot = x / itemWidth
                if (slot >= 0 && slot < 3) {
                    val itemIdx = pageNum * 3 + slot.toInt()
                    if (itemIdx >= 0 && itemIdx < items.size) {
                        callback?.invoke(itemIdx)
                    }
                }
            }
        }
        return true
    }

    private fun dp2pxF(dp: Float) = dp * context.resources.displayMetrics.density
    private fun sp2pxF(sp: Float) = sp * context.resources.displayMetrics.scaledDensity
}