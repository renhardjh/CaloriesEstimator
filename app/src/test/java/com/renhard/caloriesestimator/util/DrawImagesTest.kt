package com.renhard.caloriesestimator.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.renhard.caloriesestimator.R
import com.renhard.caloriesestimator.module.camera.model.DrawImagesResult
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.module.camera.model.Success
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class DrawImagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private var drawImages: DrawImages? = null
    private var segmentedResult: SegmentationResult? = null
    private var drawImageResult: DrawImagesResult? = null

    @Before
    fun onSetup() {
        //This will initialize the annotated mocks
        drawImages = Mockito.mock(DrawImages::class.java)
        segmentedResult = Mockito.mock(SegmentationResult::class.java)
        drawImageResult = Mockito.mock(DrawImagesResult::class.java)
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun invokePositifIsValid() {
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.test_image)
        val segmentResult = listOf(segmentedResult!!)
        val success = Success(10, 10, 10, segmentResult)

        Mockito.`when`(drawImages?.invoke(
            original = bitmap,
            success = success,
            isSeparateOut = false,
            isMaskOut = false
        )).thenAnswer {
            drawImageResult
        }

        val result = drawImages?.invoke(
            original = bitmap,
            success = success,
            isSeparateOut = false,
            isMaskOut = false
        )

        assertTrue(result != null)
    }

    @Test
    fun invokeNegatifIsValid() {
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.test_image)
        val success = Success(10, 10, 10, emptyList())

        val result = drawImages?.invoke(
            original = bitmap,
            success = success,
            isSeparateOut = false,
            isMaskOut = false
        )

        assertTrue(result == null)
    }
}