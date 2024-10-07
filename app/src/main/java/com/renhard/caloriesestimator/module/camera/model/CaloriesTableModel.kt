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

    fun getCalorieByClass(cls: String): Int {
        val value = foodTable[cls] ?: 0
        return value
    }

    fun getIconByClass(cls: String): Int {
        val value = imageTable[cls] ?: 0
        return value
    }
}