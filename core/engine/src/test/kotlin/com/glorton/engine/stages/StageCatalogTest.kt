package com.glorton.engine.stages

import com.glorton.engine.GameFactory
import com.glorton.engine.PlayerSpec
import com.glorton.engine.SoundSink
import com.glorton.engine.StageBounds
import com.glorton.engine.select.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage catalog invariants. The platform rectangles are documented
 * approximations, so what is asserted here is *playability* rather than exact
 * coordinates: every stage must have the four frame_51 spawn points, they must
 * sit inside the real world bounds, and each must drop onto a platform instead
 * of into the void (Space is the exception only in that its void is the point,
 * so its spawns still have a slab underneath).
 */
class StageCatalogTest {

    private val spawnNames = listOf("SpawnP1", "SpawnP2", "SpawnP3", "SpawnP4")

    @Test
    fun `every stage in Stages resolves to a world`() {
        STAGES.forEach { stage -> createStage(stage) }
        // frame_47 can only store a Stages entry, but an unknown id must not
        // kill the match either.
        createStage("NotAStage")
    }

    @Test
    fun `every stage carries the four frame_51 spawn points`() {
        STAGES.forEach { stage ->
            val world = createStage(stage)
            spawnNames.forEach { name ->
                assertTrue("$stage is missing $name", world.spawnPoints.containsKey(name))
            }
        }
    }

    @Test
    fun `every spawn point sits inside the real world bounds`() {
        STAGES.forEach { stage ->
            val world = createStage(stage)
            spawnNames.forEach { name ->
                val point = world.spawnPoints.getValue(name)
                assertTrue(
                    "$stage $name x=${point.x} outside ${world.bounds}",
                    point.x >= world.bounds.left && point.x <= world.bounds.right,
                )
                assertTrue(
                    "$stage $name y=${point.y} outside ${world.bounds}",
                    point.y >= world.bounds.top && point.y <= world.bounds.bottom,
                )
            }
        }
    }

    @Test
    fun `every spawn point drops onto a platform`() {
        STAGES.forEach { stage ->
            val world = createStage(stage)
            spawnNames.forEach { name ->
                val point = world.spawnPoints.getValue(name)
                val landsOn = world.platforms.any { platform ->
                    point.x >= platform.rect.left &&
                        point.x <= platform.rect.right &&
                        platform.rect.top >= point.y
                }
                assertTrue("$stage $name ($point) has no platform below it", landsOn)
            }
        }
    }

    @Test
    fun `platforms and item bars stay inside the world bounds`() {
        STAGES.forEach { stage ->
            val world = createStage(stage)
            world.platforms.forEach { platform ->
                assertTrue(
                    "$stage platform ${platform.name} escapes ${world.bounds}",
                    world.bounds.contains(platform.rect.left, platform.rect.top) &&
                        world.bounds.contains(platform.rect.right, platform.rect.bottom),
                )
            }
            world.itemSpawns.forEach { bar ->
                assertTrue(
                    "$stage item bar $bar escapes ${world.bounds}",
                    world.bounds.contains(bar.left, bar.top) &&
                        world.bounds.contains(bar.right, bar.bottom),
                )
            }
        }
    }

    @Test
    fun `world bounds stay the exact frame_51 values for every stage`() {
        // The only part of a stage that is not an approximation.
        assertEquals(StageBounds.ROOFTOP.worldBounds, createStage("Rooftop").bounds)
        assertEquals(StageBounds.MOGADISHU.worldBounds, createStage("Mogadishu").bounds)
        assertEquals(StageBounds.B52.worldBounds, createStage("B52").bounds)
        assertEquals(StageBounds.SPACE.worldBounds, createStage("Space").bounds)
    }

    @Test
    fun `GameFactory builds the picked stage and loops its own music`() {
        // frame_51: Snd.PlaySound(Game.Level, true) - the stage id doubles as
        // the music key, so the app registry must answer to the same name.
        STAGES.forEach { stage ->
            val recorder = RecordingSoundSink()
            val engine = GameFactory.match(
                players = quickRoster(),
                gameType = GameType.QUICK,
                stage = stage,
                sound = recorder,
            )

            assertEquals(createStage(stage).bounds, engine.world.bounds)
            assertTrue("$stage music was never started", stage in recorder.loops)
        }
    }

    private fun quickRoster(): List<PlayerSpec> = listOf(
        PlayerSpec(fighterId = "SBLPlayer", ai = false),
        PlayerSpec(fighterId = "PeachPlayer", ai = true),
        PlayerSpec(fighterId = "TrashPlayer", ai = true),
        PlayerSpec(fighterId = "CoffeePlayer", ai = true),
    )

    private class RecordingSoundSink : SoundSink {
        val loops = mutableListOf<String>()
        override fun play(name: String, loop: Boolean) {
            if (loop) loops.add(name)
        }
        override fun stop(name: String) = Unit
        override fun stopAll() = Unit
    }
}
