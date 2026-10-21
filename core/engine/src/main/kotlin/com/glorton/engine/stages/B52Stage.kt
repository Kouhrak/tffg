package com.glorton.engine.stages

import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.StageBounds
import com.glorton.engine.world.Platform
import com.glorton.engine.world.PlatformType
import com.glorton.engine.world.World

/**
 * B52 stage. The real platform rectangles live in the SWF timeline
 * (DefineSprite_873_B52, an animated sprite with 170 frames) and are not
 * recoverable from the AS2 text, so this is an approximation guided by the
 * exported art: one long flat deck - the bomber's fuselage - with raised
 * sections above it.
 *
 * What is *not* approximated:
 *  - `Bounds` / `BoundsCam` are the exact values frame_51 writes for
 *    `case "B52"` (see [StageBounds.B52]). Note the world sits higher than the
 *    other stages (`y = -300..400`), so the camera starts lower on the deck.
 *  - The looped music track is the stage id itself, exactly as
 *    `Snd.PlaySound(Game.Level, true)` resolves it.
 */
object B52Stage {

    fun create(): World {
        val bounds: RectF = StageBounds.B52.worldBounds
        val platforms = listOf(
            // The deck, spanning the world width and sitting on the world bottom.
            Platform("FixedDeck", PlatformType.FIXED, x = -50f, y = 275f, width = 1200f, height = 125f),
            // Raised sections above the deck.
            Platform("FixedDeckUpper", PlatformType.FIXED, x = 150f, y = 150f, width = 450f, height = 25f),
            Platform("FixedRightPad", PlatformType.FIXED, x = 725f, y = 175f, width = 350f, height = 25f),
            Platform("FixedLeftPad", PlatformType.FIXED, x = 25f, y = 100f, width = 200f, height = 25f),
            Platform("FixedHighPad", PlatformType.FIXED, x = 400f, y = 25f, width = 300f, height = 25f),
        )
        val spawns = mapOf(
            // frame_51 InitArea attaches players at SpawnP1..SpawnP4; the real
            // rects live in the SWF stage timeline - approximations, each one
            // dropped above a platform so the fighter lands instead of falling.
            "SpawnP1" to PointF(250f, 25f),
            "SpawnP2" to PointF(900f, 75f),
            "SpawnP3" to PointF(125f, 0f),
            "SpawnP4" to PointF(550f, -75f),
        )
        // AS2 SpawnH1..SpawnH4 bars (item drop zones) above the deck sections.
        val itemSpawns = listOf(
            RectF(175f, 100f, 425f, 110f),
            RectF(750f, 125f, 1000f, 135f),
            RectF(425f, -25f, 675f, -15f),
            RectF(200f, 225f, 450f, 235f),
        )
        return World(
            platforms = platforms,
            spawnPoints = spawns,
            bounds = bounds,
            camBounds = StageBounds.B52.camBounds,
            itemSpawns = itemSpawns,
        )
    }
}
