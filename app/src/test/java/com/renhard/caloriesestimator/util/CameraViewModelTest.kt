package com.renhard.caloriesestimator.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.renhard.caloriesestimator.module.camera.model.CaloriesTableModel
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.camera.viewmodel.CameraViewModel
import com.renhard.caloriesestimator.module.main.model.CaloriePredictModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class CameraViewModelTest {
    private var viewModel = CameraViewModel()
    private var caloriePredictModel = CaloriePredictModel("Ayam", 120, 1f)

    @Before
    fun onSetup() {
        //This will initialize the annotated mocks
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun calculateCaloriePositifIsValid() {
        viewModel.predictList = listOf(caloriePredictModel)
        val index = 0
        val segmentationArea = 255f
        val distanceCm = 20f
        val focalLength = 0.5f
        viewModel.distanceCm = distanceCm
        viewModel.focalLength = focalLength
        viewModel.calculateCalorie(index, segmentationArea)

        val height = CaloriesTableModel().getHeightByClass(viewModel.predictList[index].foodName)
        val realAreaSize = segmentationArea * distanceCm / focalLength
        val totalVolume = realAreaSize * 2 * height

        assertTrue(viewModel.predictList[0].weight == totalVolume)
    }

    @Test
    fun calculateCalorieNegatifIsValid() {
        viewModel.predictList = emptyList()
        viewModel.calculateCalorie(0, 255f)

        assertTrue(viewModel.predictList.isEmpty())
    }
}