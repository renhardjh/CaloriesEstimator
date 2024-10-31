package com.renhard.caloriesestimator.module.camera.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.renhard.caloriesestimator.R
import com.renhard.caloriesestimator.module.camera.model.BoundingBox
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<SegmentationResult>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var bounds = Rect()

    private var currentColorBox = 0
    private val boxColor = listOf(
        R.color.overlay_orange,
        R.color.overlay_blue,
        R.color.overlay_green,
        R.color.overlay_red,
        R.color.overlay_pink,
        R.color.overlay_cyan,
        R.color.overlay_purple,
        R.color.overlay_gray
    )

    private fun getNextColor(): Int {
        val color = boxColor[currentColorBox]
        currentColorBox = (currentColorBox + 1) % boxColor.size
        return color
    }

    init {
        initPaints()
    }

    fun clear() {
        results = listOf()
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.WHITE
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 36f

        textPaint.color = Color.BLACK
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 36f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.white)
        boxPaint.strokeWidth = 6F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach {

            val box = it.box
            val left = box.x1 * width
            val top = box.y1 * height
            val right = box.x2 * width
            val bottom = box.y2 * height

            canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, boxPaint)
            val drawableText = "${box.clsName}"

            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            val textBackgroundRect = RectF(
                left,
                top,
                left + textWidth + BOUNDING_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_RECT_TEXT_PADDING
            )
            canvas.drawRoundRect(textBackgroundRect, 8f, 8f, textBackgroundPaint)

            canvas.drawText(drawableText, left, top + textHeight, textPaint)

        }
    }

    fun setResults(segmentResult: List<SegmentationResult>) {
        results = segmentResult
        invalidate()
    }

    private fun applyTransparentOverlayColor(color: Int): Int {
        val alpha = 96
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(alpha, red, green, blue)
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}