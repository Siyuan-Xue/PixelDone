package com.milesxue.pixeldone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.milesxue.pixeldone.ui.todo.calculatePreviewTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoImagePreviewTransformTest {
    @Test
    fun calculatePreviewTransform_allowsZoomingOutAfterReachingMaximum() {
        val zoomedToMaximum = calculatePreviewTransform(
            currentScale = 5.8f,
            currentOffset = Offset.Zero,
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(160f, 160f),
            viewportSize = Size(320f, 240f),
            imageWidth = 2_048,
            imageHeight = 1_536,
        )

        val zoomedBackOut = calculatePreviewTransform(
            currentScale = zoomedToMaximum.scale,
            currentOffset = zoomedToMaximum.offset,
            zoomChange = 0.75f,
            panChange = Offset.Zero,
            centroid = Offset(160f, 160f),
            viewportSize = Size(320f, 240f),
            imageWidth = 2_048,
            imageHeight = 1_536,
        )

        assertEquals(6f, zoomedToMaximum.scale, 0.001f)
        assertEquals(4.5f, zoomedBackOut.scale, 0.001f)
    }

    @Test
    fun calculatePreviewTransform_ignoresInvalidZoomChange() {
        val transform = calculatePreviewTransform(
            currentScale = 3f,
            currentOffset = Offset(40f, -20f),
            zoomChange = Float.NaN,
            panChange = Offset.Zero,
            centroid = Offset(160f, 160f),
            viewportSize = Size(320f, 240f),
            imageWidth = 2_048,
            imageHeight = 1_536,
        )

        assertEquals(3f, transform.scale, 0.001f)
    }
}
