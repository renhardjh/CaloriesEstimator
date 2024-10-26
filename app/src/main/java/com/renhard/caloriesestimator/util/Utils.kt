package com.renhard.caloriesestimator.util

object Utils {

    fun List<Array<FloatArray>>.clone(): List<Array<FloatArray>> {
        return this.map { array -> array.map { it.clone() }.toTypedArray() }
    }
}