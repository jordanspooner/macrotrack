package com.macrotrack.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OcrLineReconstructorTest {

    /**
     * Two overlapping boxes can each become the "left of" the other:
     *   box A: left=0  right=100
     *   box B: left=50 right=150
     * If the chain walker doesn't guard against cycles, the backward walk
     * `start = leftOf[start]` loops forever and the main thread hangs.
     */
    @Test
    fun `overlapping boxes do not cause infinite loop`() {
        val elements = listOf(
            OcrElement(text = "a", left = 0, top = 0, right = 100, bottom = 20),
            OcrElement(text = "b", left = 50, top = 0, right = 150, bottom = 20)
        )

        val latch = CountDownLatch(1)
        var rows: List<OcrRow>? = null
        val thread = Thread {
            rows = OcrLineReconstructor().toRows(elements)
            latch.countDown()
        }
        thread.start()

        assertTrue(
            "toRows() hung — likely an infinite loop on a left/right cycle",
            latch.await(3, TimeUnit.SECONDS)
        )
        assertEquals(2, rows!!.size)
    }
}
