package com.glorton.engine

/** Axis-aligned rectangle in logical game coordinates (Flash stage units). */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /**
     * Inclusive overlap test. The AS2 original has no AABB gate — its point
     * `hitTest`s register edge contact (Flash hitTests include boundary
     * points), so the port's gate must be inclusive too: a fighter resting
     * exactly on a platform top (`bottom == top`) still touches it, exactly
     * like `hitTest(midX, yMax)` does in World.as.
     */
    fun intersects(o: RectF): Boolean =
        left <= o.right && right >= o.left && top <= o.bottom && bottom >= o.top

    companion object {
        /** flash.geom.Rectangle(x, y, width, height) equivalent. */
        fun fromXyWh(x: Float, y: Float, w: Float, h: Float): RectF =
            RectF(x, y, x + w, y + h)
    }
}

data class PointF(val x: Float, val y: Float)
