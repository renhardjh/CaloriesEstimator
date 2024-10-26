package com.renhard.caloriesestimator.module.camera.model

data class Success(
    val preProcessTime: Long,
    val interfaceTime: Long,
    val postProcessTime: Long,
    val results: List<SegmentationResult>
)