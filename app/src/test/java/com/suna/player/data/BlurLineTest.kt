package com.suna.player.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BlurLineTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun flatColorStaysTheSame() {
        val src = IntArray(10) { grey(120) }
        val dst = IntArray(10)
        blurLine(src, dst, start = 0, step = 1, n = 10, radius = 3)
        assertArrayEquals(src, dst)
    }

    @Test
    fun singleBrightPixelIsSpreadEvenlyOverTheWindow() {
        val src = IntArray(9) { grey(0) }
        src[4] = grey(90)
        val dst = IntArray(9)
        blurLine(src, dst, start = 0, step = 1, n = 9, radius = 1)
        assertEquals(grey(0), dst[2])
        assertEquals(grey(30), dst[3])
        assertEquals(grey(30), dst[4])
        assertEquals(grey(30), dst[5])
        assertEquals(grey(0), dst[6])
    }

    @Test
    fun worksDownAColumn() {
        // 2 columns x 3 rows; blur the second column only.
        val src = intArrayOf(
            grey(0), grey(30),
            grey(0), grey(60),
            grey(0), grey(90),
        )
        val dst = IntArray(6)
        blurLine(src, dst, start = 1, step = 2, n = 3, radius = 1)
        assertEquals(grey(40), dst[1]) // (30 + 30 + 60) / 3
        assertEquals(grey(60), dst[3]) // (30 + 60 + 90) / 3
        assertEquals(grey(80), dst[5]) // (60 + 90 + 90) / 3
    }
}
