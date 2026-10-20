package com.glorton.engine.camera

import com.glorton.engine.GameConfig
import com.glorton.engine.RectF
import com.glorton.engine.world.Body
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of class VCamera (VCamera.as). Tracks the union rectangle of every
 * focused body, smooths it with a per-tick lerp, enforces the 600:400 view
 * ratio, clamps to the stage camera bounds and applies a decaying shake.
 *
 * The AS2 version also repositions "still objects" (the HUD MovieClips) in
 * stage coordinates; the Compose renderer draws the HUD in screen space, so
 * that part is intentionally omitted. Backdrop parallax is exposed through
 * [parallaxX]/[parallaxY] (AS2: Backdrop._x = x / 2).
 */
class VCamera(
    private val screenW: Float = GameConfig.VIEW_WIDTH,
    private val screenH: Float = GameConfig.VIEW_HEIGHT,
) {
    val focusObjs = mutableListOf<Body>()

    /** View rectangle in world coordinates (AS2 x, y, Width, Height). */
    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var height: Float = 0f

    var boundsCam: RectF = RectF(0f, 0f, 0f, 0f)

    private var ratio: Float = GameConfig.VIEW_WIDTH / GameConfig.VIEW_HEIGHT

    /** Absolute time in ms of the end of the current shake (null = no shake). */
    private var shakeUntilMs: Float? = null
    private var shakeStartMs: Float = 0f

    /** Port of VCamera.init (VCamera.as): locks the ratio and zeroes the view. */
    fun init() {
        ratio = GameConfig.VIEW_WIDTH / GameConfig.VIEW_HEIGHT
        x = 0f
        y = 0f
        width = 0f
        height = 0f
    }

    /** Port of VCamera.Shake (VCamera.as): negative offsets start mid-shake. */
    fun shake(offSetMs: Float = 0f, nowMs: Float) {
        shakeStartMs = nowMs + offSetMs
        shakeUntilMs = nowMs + offSetMs + 1000f
    }

    /** Port of VCamera.DoCamera (VCamera.as). [nowMs] replaces getTimer(). */
    fun doCamera(nowMs: Float) {
        var viewLeft: Float? = null
        var viewTop: Float? = null
        var viewRight: Float? = null
        var viewBottom: Float? = null

        val iterator = focusObjs.iterator()
        while (iterator.hasNext()) {
            val focus = iterator.next()
            val rect = focus.bodyRect
            if (viewLeft == null) {
                viewLeft = rect.left
                viewTop = rect.top
                viewRight = rect.right
                viewBottom = rect.bottom
            } else {
                viewLeft = minOf(viewLeft, rect.left)
                viewTop = minOf(viewTop ?: rect.top, rect.top)
                viewRight = maxOf(viewRight ?: rect.right, rect.right)
                viewBottom = maxOf(viewBottom ?: rect.bottom, rect.bottom)
            }
        }

        if (viewLeft == null || viewTop == null || viewRight == null || viewBottom == null) {
            return
        }

        // AS2: margin of 100 around the union (+100 width/height, -50 offset).
        var viewX = viewLeft - GameConfig.CAMERA_MARGIN_OFFSET
        var viewY = viewTop - GameConfig.CAMERA_MARGIN_OFFSET
        var viewW = (viewRight - viewLeft) + GameConfig.CAMERA_MARGIN
        var viewH = (viewBottom - viewTop) + GameConfig.CAMERA_MARGIN

        val shakeEnd = shakeUntilMs
        if (shakeEnd != null && nowMs < shakeEnd) {
            val elapsed = nowMs - shakeStartMs
            val decay = (1000f - elapsed) / 7f
            viewX += cos(elapsed) * decay
            viewY += sin(elapsed) * decay
        }

        // Enforce the view ratio by expanding the smaller axis.
        val viewRatio = viewW / viewH
        if (viewRatio > ratio) {
            viewH = viewW / ratio
            viewY -= viewH / 4f
        } else if (viewRatio < ratio) {
            viewW = viewH * ratio
        }

        // Per-tick lerp toward the target (AS2: += (target - current) / 5).
        x += (viewX - x) / GameConfig.CAMERA_LERP
        y += (viewY - y) / GameConfig.CAMERA_LERP
        width += (viewW - width) / GameConfig.CAMERA_LERP
        height += (viewH - height) / GameConfig.CAMERA_LERP

        // Clamp to BoundsCam.
        if (y + height > boundsCam.bottom) y = boundsCam.bottom - height
        if (y < boundsCam.top) y = boundsCam.top
        if (x < boundsCam.left) x = boundsCam.left
        if (x + width > boundsCam.right) x = boundsCam.right - width
    }

    /** World-to-screen scale factors (AS2 scaleX = Stage.width / Width). */
    val scaleX: Float get() = if (width == 0f) 1f else screenW / width
    val scaleY: Float get() = if (height == 0f) 1f else screenH / height

    /** Backdrop parallax offset (AS2: Backdrop._x = x / 2). */
    val parallaxX: Float get() = x / 2f
    val parallaxY: Float get() = y / 2f

    /** Maps a world X coordinate to screen pixels for the renderer. */
    fun worldToScreenX(worldX: Float): Float = (worldX - x) * scaleX

    /** Maps a world Y coordinate to screen pixels for the renderer. */
    fun worldToScreenY(worldY: Float): Float = (worldY - y) * scaleY
}
