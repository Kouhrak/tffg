package com.glorton.engine.world

import com.glorton.engine.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Box(private val x: Float, private val y: Float, private val w: Float, private val h: Float) : Body {
    override val bodyRect: RectF = RectF(x, y, x + w, y + h)
}

class WorldTest {

    private fun worldWithGround(): World = World(
        platforms = listOf(
            Platform("FixedGround", PlatformType.FIXED, x = -50f, y = 300f, width = 1200f, height = 200f),
        ),
        spawnPoints = emptyMap(),
        bounds = RectF.fromXyWh(-50f, -200f, 1200f, 700f),
        camBounds = RectF.fromXyWh(100f, -100f, 950f, 450f),
    )

    @Test
    fun `body overlapping ground reports bottom and floor`() {
        val world = worldWithGround()
        // Feet at y=305, inside the ground that starts at y=300.
        val caster = Box(x = 100f, y = 245f, w = 44f, h = 60f)

        val result = world.collisionDetect(caster = caster, basic = false)

        assertTrue(result.hit)
        assertTrue(result.bottom)
        assertEquals(300f, result.floor!!, 0.001f)
    }

    @Test
    fun `body above the platform does not collide`() {
        val world = worldWithGround()
        val caster = Box(x = 100f, y = 200f, w = 44f, h = 60f)

        val result = world.collisionDetect(caster = caster, basic = false)

        assertFalse(result.hit)
        assertNull(result.floor)
    }

    @Test
    fun `body whose bottom-right corner enters a wall from its left face reports rightBc`() {
        val world = World(
            platforms = listOf(
                Platform("FixedWall", PlatformType.FIXED, x = 300f, y = 0f, width = 50f, height = 400f),
            ),
            spawnPoints = emptyMap(),
            bounds = RectF.fromXyWh(-50f, -200f, 1200f, 700f),
            camBounds = RectF.fromXyWh(100f, -100f, 950f, 450f),
        )
        // Right edge of the body (x=300) lands exactly on the wall's left edge;
        // World.as maps the (xMax, yMax) corner test to RightBC.
        val caster = Box(x = 256f, y = 100f, w = 44f, h = 60f)

        val result = world.collisionDetect(caster = caster, basic = false)

        assertTrue(result.hit)
        assertTrue(result.rightBc)
    }

    @Test
    fun `basic mode returns hitSomething without floor details`() {
        val world = worldWithGround()
        val caster = Box(x = 100f, y = 245f, w = 44f, h = 60f)

        val result = world.collisionDetect(caster = caster, basic = true)

        assertTrue(result.hitSomething)
        assertNull(result.floor)
    }

    @Test
    fun `ai helper platforms are skipped unless helpers enabled`() {
        val world = World(
            platforms = listOf(
                Platform("AI1", PlatformType.AI, x = 100f, y = 100f, width = 60f, height = 10f, aiSubtype = 'J'),
            ),
            spawnPoints = emptyMap(),
            bounds = RectF.fromXyWh(-50f, -200f, 1200f, 700f),
            camBounds = RectF.fromXyWh(100f, -100f, 950f, 450f),
        )
        val caster = Box(x = 110f, y = 95f, w = 44f, h = 60f)

        val withoutHelpers = world.collisionDetect(caster = caster, basic = true, helpers = false)
        val withHelpers = world.collisionDetect(caster = caster, basic = true, helpers = true)

        assertFalse(withoutHelpers.hitSomething)
        assertTrue(withHelpers.hitSomething)
        assertEquals('J', withHelpers.aiType)
    }
}
