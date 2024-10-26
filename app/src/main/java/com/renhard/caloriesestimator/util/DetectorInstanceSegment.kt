package com.renhard.caloriesestimator.util

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.renhard.caloriesestimator.module.camera.model.BoundingBox
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.util.ImageUtils.scaleMask
import com.renhard.caloriesestimator.util.ImageUtils.smooth
import com.renhard.caloriesestimator.util.ImageUtils.toMask
import com.renhard.caloriesestimator.util.MetaData.extractNamesFromLabelFile
import com.renhard.caloriesestimator.util.MetaData.extractNamesFromMetadata
import com.renhard.caloriesestimator.util.Utils.clone
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class DetectorInstanceSegment(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String?,
    private val detectorListener: DetectorListener,
    private val message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0
    private var xPoints = 0
    private var yPoints = 0
    private var maskNumbers = 0
    private val smoothnessKernel: Int = 5
    private val smoothEdges = false

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val options = Interpreter.Options().apply{
            this.setNumThreads(4)
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        labels.addAll(extractNamesFromMetadata(model))
        if (labels.isEmpty()) {
            if (labelPath == null) {
                message("Model not contains metadata, provide LABELS_PATH in Constants.kt")
                labels.addAll(MetaData.TEMP_CLASSES)
            } else {
                labels.addAll(extractNamesFromLabelFile(context, labelPath))
            }
        }

        labels.forEach(::println)

        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape = interpreter.getOutputTensor(0)?.shape()
        val outputMask = interpreter.getOutputTensor(1)?.shape()

        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]

            // If in case input shape is in format of [1, 3, ..., ...]
            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        if (outputShape != null) {
            numElements = outputShape[2]
            numChannel = outputShape[1]
        }

        if (outputMask != null) {
            if (outputMask[1] == 32) {
                maskNumbers = outputMask[1]
                xPoints = outputMask[2]
                yPoints = outputMask[3]
            } else {
                xPoints = outputMask[1]
                yPoints = outputMask[2]
                maskNumbers = outputMask[3]
            }
        }
        val a= 0
    }

    fun restart(isGpu: Boolean) {
        interpreter.close()

        val options = if (isGpu) {
            val compatList = CompatibilityList()
            Interpreter.Options().apply{
                if(compatList.isDelegateSupportedOnThisDevice){
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    this.addDelegate(GpuDelegate(delegateOptions))
                } else {
                    this.setNumThreads(4)
                }
            }
        } else {
            Interpreter.Options().apply{
                this.setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)
    }

    fun close() {
        interpreter.close()
    }

    fun detect(frame: Bitmap, isPreview: Boolean = true) {
        if (tensorWidth == 0 || tensorHeight == 0
            || numChannel == 0 || numElements == 0
            || xPoints == 0 || yPoints == 0 || maskNumbers == 0) {
            return
        }

        var inferenceTime = SystemClock.uptimeMillis()

        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = arrayOf(processedImage.buffer)

        val coordinatesBuffer = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)

        val maskProtoBuffer = TensorBuffer.createFixedSize(
            intArrayOf(1, xPoints, yPoints, maskNumbers),
            OUTPUT_IMAGE_TYPE
        )

        val outputBuffer = mapOf<Int, Any>(
            0 to coordinatesBuffer.buffer.rewind(),
            1 to maskProtoBuffer.buffer.rewind()
        )

        interpreter.runForMultipleInputsOutputs(imageBuffer, outputBuffer)

        val bestBoxes = bestBox(coordinatesBuffer.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (bestBoxes.isEmpty()) {
            detectorListener.onEmptyDetect()
            return
        }

        val maskProto = reshapeMaskOutput(maskProtoBuffer.floatArray)

        if(!isPreview) {
            val segmentationResults = bestBoxes.map {
                SegmentationResult(
                    box = it,
                    mask = getFinalMask(frame.width, frame.height, it, maskProto, smoothEdges)
                )
            }

            detectorListener.onDetect(segmentationResults, inferenceTime)
        } else {
            val segmentationResults = bestBoxes.map {
                SegmentationResult(
                    box = it,
                    mask = emptyArray()
                )
            }
            detectorListener.onDetect(segmentationResults, inferenceTime)
        }
    }

    private fun bestBox(array: FloatArray) : List<BoundingBox> {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1
            var currentInd = 4
            var arrayIdx = c + numElements * currentInd

            while (currentInd < (numChannel - maskNumbers)){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = currentInd - 4
                }
                currentInd++
                arrayIdx = arrayIdx.plus(numElements)
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]
                val x1 = cx - (w/2F)
                val y1 = cy - (h/2F)
                val x2 = cx + (w/2F)
                val y2 = cy + (h/2F)
                if (x1 < 0F || x1 > 1F) continue
                if (y1 < 0F || y1 > 1F) continue
                if (x2 < 0F || x2 > 1F) continue
                if (y2 < 0F || y2 > 1F) continue
                val clsName = labels[maxIdx]
                val calorie = CaloriesTableModel().getCalorieByClass(clsName)

                val maskWeight = mutableListOf<Float>()
                while (currentInd < numChannel){
                    maskWeight.add(array[arrayIdx])
                    currentInd++
                    arrayIdx += numElements
                }

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        calorie = calorie, cls = maxIdx, clsName = clsName,
                        cnf = maxConf, w = w, h = h, maskWeight = maskWeight
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return boundingBoxes

        return applyNMS(boundingBoxes)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(segmentResult: List<SegmentationResult>, inferenceTime: Long)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    private fun getFinalMask(width: Int, height: Int, output0: BoundingBox, output1: List<Array<FloatArray>>, smoothEdges: Boolean): Array<IntArray> {
        val output1Copy = output1.clone()
        val relX1 = output0.x1 * xPoints
        val relY1 = output0.y1 * yPoints
        val relX2 = output0.x2 * xPoints
        val relY2 = output0.y2 * yPoints

        val zero: Array<FloatArray> = Array(yPoints) { FloatArray(xPoints) { 0F } }
        for ((index, proto) in output1Copy.withIndex()) {
            for (y in 0 until yPoints) {
                for (x in 0 until xPoints) {
                    proto[y][x] *= output0.maskWeight[index]
                    if (x + 1  > relX1 && x + 1 < relX2 && y + 1 > relY1 && y + 1 < relY2) {
                        zero[y][x] += proto[y][x]
                    }
                }
            }
        }

        var scaledMask = zero.toMask()
        if (smoothEdges) {
            val smoothHeight = ((height.toDouble() / width.toDouble()) * 640).toInt()
            val smooth = scaledMask.scaleMask(640, smoothHeight)
            scaledMask = smooth.smooth(smoothnessKernel)
        }

        return scaledMask.scaleMask(width, height)
    }

    private fun reshapeMaskOutput(floatArray: FloatArray): List<Array<FloatArray>> {
        val all = mutableListOf<Array<FloatArray>>()
        for (mask in 0 until maskNumbers) {
            val array = Array(xPoints) { FloatArray(xPoints) { 0F } }
            for (c in 0 until xPoints) {
                for (r in 0 until yPoints) {
                    array[r][c] = floatArray[ maskNumbers * yPoints * r + maskNumbers * c + mask]
                }
            }
            all.add(array)
        }
        return all
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F

        private const val IOU_THRESHOLD = 0.5F
    }
}