package com.glorton.engine

/**
 * Tuning constants extracted from the original ActionScript 2 sources of
 * "The Fight for Glorton" (tffg.swf, decompiled with JPEXS/FFDec).
 *
 * Source references:
 *  - IntTime = 25 in scripts/frame_51/DoAction.as (main loop tick, 40 Hz).
 *  - TimerFight = setInterval(FightTimer, 1000) in frame_51 (countdown + match timer).
 *  - Stage bounds set per level in frame_51's switch(Game.Level).
 *  - VCam 600x400 in InitArea (frame_51).
 */
object GameConfig {
    /** _root.IntTime: fixed logic step in milliseconds (40 ticks/second). */
    const val TICK_MS = 25

    /** FightTimer interval: countdown "5..GO" and match clock. */
    const val COUNTDOWN_TICK_MS = 1000

    /** VCam.Width / VCam.Height set in InitArea. */
    const val VIEW_WIDTH = 600f
    const val VIEW_HEIGHT = 400f

    /**
     * SWF header frame rate (parsed from tffg.swf: CWS v8, 30 fps, 170 main-timeline frames).
     * Animation frame conditions in the AS2 code (_currentframe > N) run at this rate,
     * while logic ticks run at 1000/TICK_MS = 40 Hz. Convert with:
     *   msForFrame(n) = n * (1000f / SWF_FPS)
     */
    const val SWF_FPS = 30f

    /** Milliseconds of animation time represented by one SWF timeline frame. */
    val MS_PER_ANIM_FRAME: Float = 1000f / SWF_FPS

    /** Item.DoMovement: yInc cap and gravity 0.6 (Item.as). Note: fighters use
     * their own gravity of 0.5 * gravityRatio per tick (Fighter.as), not this
     * value — see Fighter's port. */
    const val MAX_FALL_SPEED = 6f
    const val GRAVITY = 0.6f

    /** Camera lerp factor per tick in VCamera.DoCamera (x += (target - x) / 5). */
    const val CAMERA_LERP = 5f

    /** Camera focus rectangle margin in VCamera.DoCamera (+100 wide, +100 high, -50 offset). */
    const val CAMERA_MARGIN = 100f
    const val CAMERA_MARGIN_OFFSET = 50f
}

/**
 * Stage geometry from frame_51. Rectangles are flash.geom.Rectangle(x, y, width, height);
 * they are converted with [RectF.fromXyWh] to keep the original numbers visible.
 */
data class StageBounds(
    val camBounds: RectF,
    val worldBounds: RectF,
) {
    companion object {
        val ROOFTOP = StageBounds(
            camBounds = RectF.fromXyWh(100f, -100f, 950f, 450f),
            worldBounds = RectF.fromXyWh(-50f, -200f, 1200f, 700f),
        )

        val MOGADISHU = StageBounds(
            camBounds = RectF.fromXyWh(-900f, -100f, 2600f, 500f),
            worldBounds = RectF.fromXyWh(-1000f, -200f, 2700f, 700f),
        )

        val B52 = StageBounds(
            camBounds = RectF.fromXyWh(50f, -200f, 950f, 550f),
            worldBounds = RectF.fromXyWh(-50f, -300f, 1200f, 700f),
        )

        val SPACE = StageBounds(
            camBounds = RectF.fromXyWh(-800f, -400f, 1500f, 1000f),
            worldBounds = RectF.fromXyWh(-900f, -500f, 1700f, 1200f),
        )
    }
}
