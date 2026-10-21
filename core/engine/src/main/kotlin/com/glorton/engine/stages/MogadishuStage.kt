package com.glorton.engine.stages

import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.StageBounds
import com.glorton.engine.world.Platform
import com.glorton.engine.world.PlatformType
import com.glorton.engine.world.World

/**
 * Mogadishu stage. Like Rooftop, the real platform rectangles live in the SWF
 * timeline (DefineSprite_832_Mogadishu) and are not recoverable from the AS2
 * text, so this is an approximation guided by the exported stage art: a wide
 * street-level ground with yellow ledges at both ends and buildings rising
 * towards the centre.
 *
 * What is *not* approximated:
 *  - `Bounds` / `BoundsCam` are the exact values frame_51 writes for
 *    `case "Mogadishu"` (see [StageBounds.MOGADISHU]).
 *  - The looped music track is the stage id itself, exactly as
 *    `Snd.PlaySound(Game.Level, true)` resolves it.
 *
 * Mogadishu is the widest stage (world 2700x700), which is what the AS2 camera
 * rectangle already implies - the camera is given room to pan across the street.
 */
object MogadishuStage {

    fun create(): World {
        val bounds: RectF = StageBounds.MOGADISHU.worldBounds
        val platforms = listOf(
            // Street level, spanning the whole world width.
            Platform("FixedGround", PlatformType.FIXED, x = -1000f, y = 400f, width = 2700f, height = 100f),
            // Yellow ledges at both ends, just above street level.
            Platform("FixedLedgeL", PlatformType.FIXED, x = -950f, y = 300f, width = 350f, height = 25f),
            Platform("FixedLedgeR", PlatformType.FIXED, x = 1300f, y = 300f, width = 400f, height = 25f),
            // Buildings stepping up towards the centre.
            Platform("FixedBuildL", PlatformType.FIXED, x = -600f, y = 225f, width = 300f, height = 25f),
            Platform("FixedBuildC", PlatformType.FIXED, x = -150f, y = 125f, width = 400f, height = 25f),
            Platform("FixedBuildR", PlatformType.FIXED, x = 650f, y = 175f, width = 400f, height = 25f),
            Platform("FixedHighR", PlatformType.FIXED, x = 1150f, y = 75f, width = 300f, height = 25f),
        )
        val spawns = mapOf(
            // frame_51 InitArea attaches players at SpawnP1..SpawnP4; the real
            // rects live in the SWF stage timeline - approximations, each one
            // dropped above a platform so the fighter lands instead of falling.
            "SpawnP1" to PointF(-775f, 200f),
            "SpawnP2" to PointF(1500f, 200f),
            "SpawnP3" to PointF(50f, 25f),
            "SpawnP4" to PointF(850f, 75f),
        )
        // AS2 SpawnH1..SpawnH4 bars (item drop zones) above the ledges and roofs.
        val itemSpawns = listOf(
            RectF(-900f, 250f, -650f, 260f),
            RectF(-100f, 75f, 150f, 85f),
            RectF(700f, 125f, 950f, 135f),
            RectF(1350f, 250f, 1600f, 260f),
        )
        return World(
            platforms = platforms,
            spawnPoints = spawns,
            bounds = bounds,
            camBounds = StageBounds.MOGADISHU.camBounds,
            itemSpawns = itemSpawns,
        )
    }
}
