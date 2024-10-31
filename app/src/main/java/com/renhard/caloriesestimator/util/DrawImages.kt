package com.renhard.caloriesestimator.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.content.ContextCompat
import com.renhard.caloriesestimator.R
import com.renhard.caloriesestimator.module.camera.model.DrawImagesResult
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.camera.model.Success
import com.renhard.caloriesestimator.util.ImageUtils.pxAreaToCm
import com.renhard.caloriesestimator.util.ImageUtils.pxToCm

class DrawImages(private val context: Context) {
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
    private var currentColorBox = 0
    private fun getNextColor(): Int {
        val color = boxColor[currentColorBox]
        currentColorBox = (currentColorBox + 1) % boxColor.size
        return color
    }

    fun invoke(original: Bitmap, success: Success, isSeparateOut: Boolean, isMaskOut: Boolean) : DrawImagesResult {
        if (isSeparateOut) {
             if (isMaskOut) {
                 val result = DrawImagesResult(
                     success.results.map { Pair(maskOut(original, it.mask), null) },
                     emptyList()
                 )
                 return result
            } else {
                val results = success.results
                if (results.isEmpty()) {
                    val result = DrawImagesResult(
                        emptyList(),
                        emptyList()
                    )
                    return result
                }

                val width = results.first().mask[0].size
                val height = results.first().mask.size
                 val listArea = mutableListOf<Float>()

                 val result = DrawImagesResult(
                     success.results.map {
                         val new = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                         listArea += applyTransparentOverlay(context, new, it, R.color.overlay_pink)
                         Pair(original, new)
                     },
                     listArea
                 )
                return result
            }
        } else {
            if (isMaskOut) {
                val list = success.results.map { it.mask }.toTypedArray()
                val result = DrawImagesResult(
                    listOf(Pair(maskOut(original, list.combineMasks()), null)),
                    emptyList()
                )
                return result
            } else {
                val results = success.results
                if (results.isEmpty()) {
                    val result = DrawImagesResult(
                        emptyList(),
                        emptyList()
                    )
                    return result
                }

                val colorPairs: MutableMap<Int, Int> = mutableMapOf()
                results.forEach {
                    colorPairs[it.box.cls] = getNextColor()
                }

                val width = results.first().mask[0].size
                val height = results.first().mask.size

                val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val listArea = mutableListOf<Float>()

                results.forEach {
                    listArea += applyTransparentOverlay(context, combined, it, colorPairs[it.box.cls] ?: R.color.overlay_red)
                }
                val result = DrawImagesResult(
                    listOf(Pair(original, combined)),
                    listArea
                )
                return result
            }
        }
    }


    private fun maskOut(image: Bitmap, mask: Array<IntArray>) : Bitmap {
        if (image.height != mask.size || image.width != mask[0].size) {
            throw IllegalArgumentException("Mask dimensions must match image dimensions")
        }

        val result = Bitmap.createBitmap(image.width, image.height, image.config)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val maskValue = mask[y][x]
                val pixel = image.getPixel(x, y)
                val resultPixel = when (maskValue) {
                    1-> pixel
                    0 -> Color.BLACK
                    else -> throw IllegalArgumentException("Mask values must be either 0.0 or 255.0")
                }
                result.setPixel(x, y, resultPixel)
            }
        }

        return result
    }

    private fun Array<Array<IntArray>>.combineMasks(): Array<IntArray> {
        if (this.isEmpty() || this.first().isEmpty()) {
            return emptyArray()
        }

        val numArrays = this.first().size
        val arraySize = this.first().first().size

        val result = Array(numArrays) { IntArray(arraySize) }

        for (mask in this) {
            for ((index, array) in mask.withIndex()) {
                if (array.size != arraySize) {
                    throw IllegalArgumentException("All DoubleArrays must be of the same size.")
                }
                for (i in array.indices) {
                    if (result[index][i] == 0) {
                        result[index][i] += array[i]
                    }
                }
            }
        }

        return result
    }

    private fun applyTransparentOverlay(context: Context, overlay: Bitmap, segmentationResult: SegmentationResult, overlayColorResId: Int)
    : Float {
        val width = overlay.width
        val height = overlay.height

        val overlayColor = ContextCompat.getColor(context, overlayColorResId)

        var area = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskValue = segmentationResult.mask[y][x]
                if (maskValue > 0) {
                    area += 1
                    overlay.setPixel(x, y, applyTransparentOverlayColor(overlayColor))
                }
            }
        }
        Log.d("AreaX ${segmentationResult.box.clsName}:", "${area.pxAreaToCm(context)} cm")
        return area.pxAreaToCm(context)

//        val canvas = Canvas(overlay)
//
//        val boxPaint = Paint().apply {
//            color = ContextCompat.getColor(context, overlayColorResId)
//            strokeWidth = 4F
//            style = Paint.Style.STROKE
//        }
//
//        val box = segmentationResult.box
//
//        val left = (box.x1 * width).toInt()
//        val top = (box.y1 * height).toInt()
//        val right = (box.x2 * width).toInt()
//        val bottom = (box.y2 * height).toInt()
//
//        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), boxPaint)
//
//        val textBackgroundPaint = Paint().apply {
//            color = ContextCompat.getColor(context, overlayColorResId)
//            style = Paint.Style.FILL
//        }
//
//        val textPaint = Paint().apply {
//            color = Color.WHITE
//            style = Paint.Style.FILL
//            textSize = 24f
//        }
//
//        val bounds = android.graphics.Rect()
//        textPaint.getTextBounds(box.clsName, 0, box.clsName.length, bounds)
//
//        val textWidth = bounds.width()
//        val textHeight = bounds.height()
//        val padding = 8
//
//        canvas.drawRect(
//            left.toFloat(),
//            top.toFloat() - textHeight - 2 * padding,
//            left + textWidth + 2 * padding.toFloat(),
//            top.toFloat(),
//            textBackgroundPaint
//        )
//        canvas.drawText(box.clsName, left.toFloat() + padding, top.toFloat() - padding.toFloat(), textPaint)
    }

    private fun applyTransparentOverlayColor(color: Int): Int {
        val alpha = 96
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(alpha, red, green, blue)
    }

}