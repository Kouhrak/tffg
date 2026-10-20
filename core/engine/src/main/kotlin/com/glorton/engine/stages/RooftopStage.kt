package com.glorton.engine.stages

import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.StageBounds
import com.glorton.engine.world.Platform
import com.glorton.engine.world.PlatformType
import com.glorton.engine.world.World

/**
 * Rooftop stage definition. The original platform layout lives in the SWF
 * timeline (DefineSprite_726_Rooftop) and is not recoverable from the AS2
 * text, so this vertical slice uses an approximation with the correct world
 * bounds from frame_51 (Bounds/CamBounds for "Rooftop"). Refine the platform
 * rectangles against the exported art later.
 */
object RooftopStage {

    fun create(): World {
        val bounds: RectF = StageBounds.ROOFTOP.worldBounds
        val platforms = listOf(
            // Main rooftop floor.
            Platform("FixedGround", PlatformType.FIXED, x = -50f, y = 300f, width = 1200f, height = 200f),
            // Left and right low roofs.
            Platform("FixedRoofL", PlatformType.FIXED, x = 120f, y = 190f, width = 230f, height = 30f),
            Platform("FixedRoofR", PlatformType.FIXED, x = 650f, y = 190f, width = 230f, height = 30f),
            // Central high platform.
            Platform("FixedRoofC", PlatformType.FIXED, x = 385f, y = 100f, width = 230f, height = 30f),
        )
        val spawns = mapOf(
            "SpawnP1" to PointF(235f, 260f),
            "SpawnP2" to PointF(765f, 260f),
            "SpawnH1" to PointF(235f, 150f),
            "SpawnH2" to PointF(500f, 60f),
            "SpawnH3" to PointF(765f, 150f),
            "SpawnH4" to PointF(500f, 260f),
        )
        return World(
            platforms = platforms,
            spawnPoints = spawns,
            bounds = bounds,
            camBounds = StageBounds.ROOFTOP.camBounds,
        )
    }
}
