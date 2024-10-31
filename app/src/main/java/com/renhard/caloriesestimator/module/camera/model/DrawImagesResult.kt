package com.renhard.caloriesestimator.module.camera.model

import android.graphics.Bitmap

data class DrawImagesResult(
    var bitmapSegmentation: List<Pair<Bitmap, Bitmap?>>,
    var areaSegmentation: List<Float>
)
