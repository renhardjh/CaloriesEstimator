package com.renhard.caloriesestimator.module.camera.viewmodel

import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel

class CameraViewModel {
    var predictList = listOf<CaloriePredictModel>()

    fun updatePredictionList(segmentResult: List<SegmentationResult>) {
        predictList = segmentResult
            .map { CaloriePredictModel(it.box.clsName, it.box.calorie, it.weight) }

//        predictList = segmentResult
//            .map { CaloriePredictModel(it.box.clsName, it.box.calorie, it.weight) }
//            .groupBy { it.foodName }
//            .values
//            .map {
//                it.reduce { acc, item ->
//                    CaloriePredictModel(acc.foodName, acc.calorie, acc.weight + item.weight)
//                }
//            }
    }
}