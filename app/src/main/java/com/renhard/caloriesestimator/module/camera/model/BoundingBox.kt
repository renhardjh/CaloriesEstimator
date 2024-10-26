package com.renhard.caloriesestimator.module.camera.model

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val calorie: Int,
    val cls: Int,
    val clsName: String,
    val cnf: Float,
    val w: Float,
    val h: Float,
    val maskWeight: List<Float>
)