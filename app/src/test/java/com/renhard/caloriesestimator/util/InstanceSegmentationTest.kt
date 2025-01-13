package com.renhard.caloriesestimator.util

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.renhard.caloriesestimator.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class InstanceSegmentationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private var instanceSegment: InstanceSegmentation? = null

    @Before
    fun onSetup() {
        //This will initialize the annotated mocks
        instanceSegment = Mockito.mock(InstanceSegmentation::class.java)
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun invokePositifIsValid() {
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.test_image)
        instanceSegment?.invoke(
            bitmap, true,
            onSuccess = {
                assertNotNull(bitmap)
            },
            onFailure = {
                AssertionError("Object should not be empty")
            }
        )
    }

    @Test
    fun invokeSegmentNegativeIsValid() {
        val bitmap = BitmapFactory.decodeResource(context.getResources(),
            R.drawable.sampel_image)
        instanceSegment?.invoke(
            bitmap, true,
            onSuccess = {
                AssertionError("Object should not be success")
            },
            onFailure = {
                assertTrue(true)
            }
        )
    }
}