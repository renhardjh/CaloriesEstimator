package com.renhard.caloriesestimator

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.renhard.caloriesestimator.module.camera.model.SegmentationResult
import com.renhard.caloriesestimator.util.DetectorInstanceSegment
import com.renhard.caloriesestimator.util.DetectorInstanceSegment.DetectorListener
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class DetectorInstanceSegmentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private var detector: DetectorInstanceSegment? = null
    private var segmentedResult: SegmentationResult? = null

    @Before
    fun onSetup() {
        //This will initialize the annotated mocks
        detector = Mockito.mock(DetectorInstanceSegment::class.java)
        segmentedResult = Mockito.mock(SegmentationResult::class.java)
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun detectPositifIsValid() {
        detector?.detectorListener = (object: DetectorListener {
            override fun onEmptyDetect() {
                AssertionError("Object should not be empty")
            }

            override fun onDetect(segmentResult: List<SegmentationResult>, inferenceTime: Long) {
                assertTrue(segmentResult.size > 0)
            }

        })
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.test_image)
        Mockito.`when`(detector?.detect(bitmap)).thenAnswer {
            detector?.detectorListener?.onDetect(listOf(segmentedResult!!), 10)
        }

        detector?.detect(bitmap)
    }

    @Test
    fun detectNegatifIsValid() {
        detector?.detectorListener = (object: DetectorListener {
            override fun onEmptyDetect() {
                AssertionError("Object should not be empty")
            }

            override fun onDetect(segmentResult: List<SegmentationResult>, inferenceTime: Long) {
                assertTrue(segmentResult.isEmpty())
            }

        })
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.test_image)
        Mockito.`when`(detector?.detect(bitmap)).thenAnswer {
            detector?.detectorListener?.onDetect(emptyList(), 20)
        }

        detector?.detect(bitmap)
    }
}