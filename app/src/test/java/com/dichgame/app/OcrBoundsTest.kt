package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test

class OcrBoundsTest {
    @Test fun convertsNormalizedYxCoordinatesToPixelLtrb() {
        assertArrayEquals(
            intArrayOf(200, 50, 800, 250),
            normalizedOcrBounds(listOf(100.0, 200.0, 500.0, 800.0), 1000, 500)
        )
    }

    @Test fun clampsCoordinatesToImageBounds() {
        assertArrayEquals(
            intArrayOf(0, 0, 100, 50),
            normalizedOcrBounds(listOf(-10.0, -20.0, 100.0, 100.0), 1000, 500)
        )
    }

    @Test fun rejectsInvertedEmptyOrNonFiniteBoxes() {
        assertNull(normalizedOcrBounds(listOf(500.0, 100.0, 100.0, 900.0), 1000, 500))
        assertNull(normalizedOcrBounds(listOf(100.0, 900.0, 500.0, 100.0), 1000, 500))
        assertNull(normalizedOcrBounds(listOf(1.0, 1.0, 1.0, 2.0), 1000, 500))
        assertNull(normalizedOcrBounds(listOf(Double.NaN, 1.0, 2.0, 3.0), 1000, 500))
    }

    @Test fun rejectsInvalidImageDimensionsAndCoordinateCount() {
        assertNull(normalizedOcrBounds(listOf(1.0, 1.0, 2.0, 2.0), 0, 500))
        assertNull(normalizedOcrBounds(listOf(1.0, 1.0, 2.0), 1000, 500))
    }
}
