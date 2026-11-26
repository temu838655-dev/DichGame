package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class OcrGeometryTest {
    @Test fun validNormalizedBoxMapsToPixels() {
        assertArrayEquals(intArrayOf(192, 108, 1728, 972),
            normalizedOcrBox(100.0, 100.0, 900.0, 900.0, 1920, 1080))
    }

    @Test fun tinyValidBoxStillHasAtLeastOnePixel() {
        val box = normalizedOcrBox(0.0, 0.0, 0.01, 0.01, 100, 100)
        assertNotNull(box)
        assertEquals(1, box!![2] - box[0])
        assertEquals(1, box[3] - box[1])
    }

    @Test fun invertedAndEmptyCoordinatesAreRejected() {
        assertNull(normalizedOcrBox(500.0, 100.0, 400.0, 900.0, 1920, 1080))
        assertNull(normalizedOcrBox(100.0, 500.0, 900.0, 400.0, 1920, 1080))
        assertNull(normalizedOcrBox(100.0, 100.0, 100.0, 900.0, 1920, 1080))
    }

    @Test fun nonFiniteAndOutOfRangeCoordinatesAreRejected() {
        assertNull(normalizedOcrBox(Double.NaN, 0.0, 100.0, 100.0, 1920, 1080))
        assertNull(normalizedOcrBox(0.0, 0.0, Double.POSITIVE_INFINITY, 100.0, 1920, 1080))
        assertNull(normalizedOcrBox(-1.0, 0.0, 100.0, 100.0, 1920, 1080))
        assertNull(normalizedOcrBox(0.0, 0.0, 1001.0, 100.0, 1920, 1080))
    }

    @Test fun invalidImageDimensionsAreRejected() {
        assertNull(normalizedOcrBox(0.0, 0.0, 100.0, 100.0, 0, 1080))
        assertNull(normalizedOcrBox(0.0, 0.0, 100.0, 100.0, 1920, -1))
    }

    @Test fun coordinatesAtImageEdgesAreClippedSafely() {
        assertArrayEquals(intArrayOf(1919, 1079, 1920, 1080),
            normalizedOcrBox(999.9, 999.9, 1000.0, 1000.0, 1920, 1080))
    }
}
