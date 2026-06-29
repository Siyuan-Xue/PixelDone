package com.milesxue.pixeldone

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoImageStoreTest {
    @Test
    fun calculatePreviewSampleSize_keepsSmallImagesAtFullSize() {
        assertEquals(
            1,
            calculatePreviewSampleSize(
                imageWidth = 1_024,
                imageHeight = 768,
                maxLongEdgePx = 2_048,
            ),
        )
    }

    @Test
    fun calculatePreviewSampleSize_downsamplesLargeImagesToPreviewSize() {
        assertEquals(
            4,
            calculatePreviewSampleSize(
                imageWidth = 8_000,
                imageHeight = 6_000,
                maxLongEdgePx = 2_048,
            ),
        )
    }

    @Test
    fun calculatePreviewSampleSize_usesSafeDefaultForInvalidSizes() {
        assertEquals(1, calculatePreviewSampleSize(0, 6_000, 2_048))
        assertEquals(1, calculatePreviewSampleSize(8_000, -1, 2_048))
        assertEquals(1, calculatePreviewSampleSize(8_000, 6_000, 0))
    }
}
