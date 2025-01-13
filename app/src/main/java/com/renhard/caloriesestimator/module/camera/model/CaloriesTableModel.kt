package com.renhard.caloriesestimator.module.camera.model

import com.renhard.caloriesestimator.R

class CaloriesTableModel {
    val foodTable = hashMapOf(
        "Nasi" to 129,
        "Ayam" to 260,
        "Daging" to 288,
        "Ikan" to 84,
        "Tahu" to 78,
        "Telur" to 74,
        "Tempe" to 193,
        "Sayur" to 60
    )

    val imageTable = hashMapOf(
        "Nasi" to R.drawable.nasi,
        "Ayam" to R.drawable.ayam,
        "Daging" to R.drawable.daging,
        "Ikan" to R.drawable.ikan,
        "Tahu" to R.drawable.tahu,
        "Telur" to R.drawable.telur,
        "Tempe" to R.drawable.tempe,
        "Sayur" to R.drawable.sayur
    )

    val foodHeight = hashMapOf(
        "Nasi" to 3f,
        "Ayam" to 1.5f,
        "Daging" to 1.1f,
        "Ikan" to 1.65f,
        "Tahu" to 1.8f,
        "Telur" to 0.8f,
        "Tempe" to 0.8f,
        "Sayur" to 0.5f
    )

    val foodDensity = hashMapOf(
        "Nasi" to 0.87f,
        "Ayam" to 1.04f,
        "Daging" to 1.04f,
        "Ikan" to 1.04f,
        "Tahu" to 1.07f,
        "Telur" to 0.93f,
        "Tempe" to 0.7f,
        "Sayur" to 0.77f
    )

    fun getCalorieByClass(cls: String): Int {
        val value = foodTable[cls] ?: 0
        return value
    }

    fun getIconByClass(cls: String): Int {
        val value = imageTable[cls] ?: 0
        return value
    }

    fun getHeightByClass(cls: String): Float {
        val value = foodHeight[cls] ?: 0f
        return value
    }
}