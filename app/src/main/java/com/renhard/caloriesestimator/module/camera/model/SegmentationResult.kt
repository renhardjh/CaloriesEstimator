package com.renhard.caloriesestimator.module.camera.model

data class SegmentationResult(
    val box: BoundingBox,
    val mask: Array<IntArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentationResult

        return mask.contentDeepEquals(other.mask)
    }

    override fun hashCode(): Int {
        return mask.contentDeepHashCode()
    }
}