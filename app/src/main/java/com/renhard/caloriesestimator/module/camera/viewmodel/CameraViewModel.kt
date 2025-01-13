package com.renhard.caloriesestimator.module.camera.viewmodel

import android.util.Log
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel

class CameraViewModel {
    var predictList = listOf<CaloriePredictModel>()
    var distanceCm: Float = 0f
    var focalLength: Float = 0f
    var magnification: Float = 0f

    fun updatePredictionList(segmentResult: List<SegmentationResult>) {
        predictList = segmentResult
            .map { CaloriePredictModel(it.box.clsName, it.box.calorie, it.weight) }
    }

    fun calculateCalorie(index: Int, areaSegmentations: Float) {
        if (predictList.isEmpty()) {
            return
        }
        val height = CaloriesTableModel().getHeightByClass(predictList[index].foodName)
        val realAreaSize = areaSegmentations * distanceCm / focalLength
        val totalVolume = realAreaSize * 2 * height
        predictList[index].weight = totalVolume
        Log.d("WeightX ${predictList[index].foodName}:", "${totalVolume} cm3, ${height}, ${realAreaSize}")
    }
}