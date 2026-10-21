package com.glorton.engine.stages

import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.StageBounds
import com.glorton.engine.world.Platform
import com.glorton.engine.world.PlatformType
import com.glorton.engine.world.World

/**
 * Space stage. The real platform rectangles live in the SWF timeline
 * (DefineSprite_886_Space) and are not recoverable from the AS2 text, so this
 * is an approximation guided by the exported art: floating slabs at very
 * different heights with **no ground at all**, so a KO by falling out of the
 * world is the stage's main hazard. That also matches its `Bounds`
 * (`y = -500..700`, the tallest of the four) - there is simply a lot of empty
 * vertical space to fall through.
 *
 * What is *not* approximated:
 *  - `Bounds` / `BoundsCam` are the exact values frame_51 writes for
 *    `case "Space"` (see [StageBounds.SPACE]).
 *  - The looped music track is the stage id itself, exactly as
 *    `Snd.PlaySound(Game.Level, true)` resolves it.
 */
object SpaceStage {

    fun create(): World {
        val bounds: RectF = StageBounds.SPACE.worldBounds
        val platforms = listOf(
            // Wide central slab.
            Platform("FixedCentral", PlatformType.FIXED, x = -250f, y = 50f, width = 600f, height = 30f),
            // Upper corner slabs.
            Platform("FixedTopLeft", PlatformType.FIXED, x = -700f, y = -300f, width = 300f, height = 25f),
            Platform("FixedTopRight", PlatformType.FIXED, x = 350f, y = -250f, width = 400f, height = 30f),
            // Lower corner slabs.
            Platform("FixedMidRight", PlatformType.FIXED, x = 425f, y = 300f, width = 325f, height = 25f),
            Platform("FixedBottomLeft", PlatformType.FIXED, x = -750f, y = 400f, width = 300f, height = 25f),
        )
        val spawns = mapOf(
            // frame_51 InitArea attaches players at SpawnP1..SpawnP4; the real
            // rects live in the SWF stage timeline - approximations, each one
            // dropped above a slab so the fighter lands instead of falling out.
            "SpawnP1" to PointF(-550f, -400f),
            "SpawnP2" to PointF(550f, -350f),
            "SpawnP3" to PointF(50f, -50f),
            "SpawnP4" to PointF(585f, 200f),
        )
        // AS2 SpawnH1..SpawnH4 bars (item drop zones) above the slabs.
        val itemSpawns = listOf(
            RectF(-650f, -350f, -400f, -340f),
            RectF(400f, -300f, 650f, -290f),
            RectF(-150f, 0f, 100f, 10f),
            RectF(450f, 250f, 700f, 260f),
        )
        return World(
            platforms = platforms,
            spawnPoints = spawns,
            bounds = bounds,
            camBounds = StageBounds.SPACE.camBounds,
            itemSpawns = itemSpawns,
        )
    }
}
