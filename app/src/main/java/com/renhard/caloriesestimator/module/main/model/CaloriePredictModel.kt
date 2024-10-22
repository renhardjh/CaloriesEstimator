package com.renhard.caloriesestimator.module.main.model

data class CaloriePredictModel(
    val foodName: String,
    val calorie: Int,
    var weight: Float = 1f
)
