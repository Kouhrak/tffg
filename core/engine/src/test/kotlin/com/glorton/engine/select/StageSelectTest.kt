package com.glorton.engine.select

import com.glorton.engine.stages.STAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * frame_47 (StageSelect) thumbnail grid: the `Stages` order from frame_42 and
 * the `xP`/`yP` formula the original uses to lay the thumbs out.
 */
class StageSelectTest {

    @Test
    fun `stages follow the frame_42 array order`() {
        // frame_42: var Stages = new Array("Rooftop","Mogadishu","B52","Space");
        assertEquals(listOf("Rooftop", "Mogadishu", "B52", "Space"), STAGES)
    }

    @Test
    fun `thumb positions match the frame_47 formula`() {
        val positions = stageThumbPositions()

        assertEquals(4, positions.size)
        assertEquals(30f, positions[0].x, 0.001f)
        assertEquals(90f, positions[0].y, 0.001f)
        assertEquals(213.33334f, positions[1].x, 0.01f)
        assertEquals(90f, positions[1].y, 0.001f)
        assertEquals(396.66666f, positions[2].x, 0.01f)
        assertEquals(90f, positions[2].y, 0.001f)
        // The 4th thumb wraps to the second row, left aligned at x = 30.
        assertEquals(30f, positions[3].x, 0.001f)
        assertEquals(210f, positions[3].y, 0.001f)
    }

    @Test
    fun `three thumbs fit the first row and the rest wrap`() {
        // AS2 divides the 600-wide stage into three columns of 550 / 3, so
        // exactly three thumbs fit before the row wraps.
        val positions = stageThumbPositions(count = 6)

        val firstRow = positions.filter { it.y == 90f }
        val secondRow = positions.filter { it.y == 210f }
        assertEquals(3, firstRow.size)
        assertEquals(3, secondRow.size)
        // Wrapped thumbs start over at the first column, exactly like `i - 3`.
        assertEquals(firstRow.map { it.x }, secondRow.map { it.x })
    }

    @Test
    fun `thumb spacing keeps ActionScript float arithmetic`() {
        // Kotlin's `550 / 3` is 183; AS2 works in floats, so the second column
        // must sit at 183.33, not 183.
        val second = stageThumbPositions()[1]

        assertTrue(Math.abs(second.x - 30f - 183.3333f) < 0.01f)
    }
}
