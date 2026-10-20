package com.glorton.engine.camera

import com.glorton.engine.RectF
import com.glorton.engine.world.Body
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Focus(private val x: Float, private val y: Float) : Body {
    override val bodyRect: RectF = RectF(x - 22f, y - 64f, x + 22f, y)
}

class VCameraTest {

    private fun camera(): VCamera = VCamera(screenW = 600f, screenH = 400f).apply {
        boundsCam = RectF.fromXyWh(100f, -100f, 950f, 450f)
    }

    @Test
    fun `camera converges toward a single focused fighter`() {
        val cam = camera()
        cam.focusObjs.add(Focus(x = 500f, y = 300f))
        cam.init()

        repeat(200) { cam.doCamera(nowMs = it * 25f) }

        // Union of the body rect (478..522 x 236..300) + margin: 428..572 x 186..350.
        assertEquals(428f, cam.x, 1f)
        assertEquals(186f, cam.y, 1f)
    }

    @Test
    fun `view aspect ratio is enforced`() {
        val cam = camera()
        cam.focusObjs.add(Focus(x = 500f, y = 300f))
        cam.init()

        repeat(200) { cam.doCamera(nowMs = it * 25f) }

        assertEquals(600f / 400f, cam.width / cam.height, 0.01f)
    }

    @Test
    fun `camera stays inside BoundsCam`() {
        val cam = camera()
        cam.focusObjs.add(Focus(x = 40f, y = 120f))
        cam.init()

        repeat(400) { cam.doCamera(nowMs = it * 25f) }

        assertTrue(cam.x >= cam.boundsCam.left - 0.01f)
        assertTrue(cam.y >= cam.boundsCam.top - 0.01f)
        assertTrue(cam.x + cam.width <= cam.boundsCam.right + 0.01f)
        assertTrue(cam.y + cam.height <= cam.boundsCam.bottom + 0.01f)
    }

    @Test
    fun `worldToScreen maps the view origin to zero`() {
        val cam = camera()
        cam.init()
        // Force the view without convergence (set state directly).
        cam.x = 100f
        cam.y = -100f
        cam.width = 600f
        cam.height = 400f

        assertEquals(0f, cam.worldToScreenX(100f), 0.001f)
        assertEquals(0f, cam.worldToScreenY(-100f), 0.001f)
        assertEquals(600f, cam.worldToScreenX(700f), 0.001f)
        assertEquals(400f, cam.worldToScreenY(300f), 0.001f)
    }
}
