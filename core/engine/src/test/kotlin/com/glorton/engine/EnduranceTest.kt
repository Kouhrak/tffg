package com.glorton.engine

import com.glorton.engine.fighter.Fighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Endurance mode tests: frame_51 InitArea (P1 alone at one stock plus the
 * first CPU wave), PlayerDeath's endurance branch (P1 dies -> EndGame, wave
 * dies -> GenerateEndurancePlayer) and the frame_101 "N KILLS!" headline.
 */
class EnduranceTest {

    @Before
    fun resetDeathOrder() {
        // The elimination counter is static across the JVM; keep every test's
        // expectations order-independent.
        Fighter.resetDeathOrder()
    }

    private fun endurance(): Engine =
        GameFactory.rooftopMatch(
            players = listOf(PlayerSpec(fighterId = "DefaultPlayer", colr = "red", team = "red")),
            gameType = "endurance",
        )

    /** Ticks through the 5s countdown (25ms tick, 1s ready interval -> ~240 ticks). */
    private fun fight(engine: Engine) {
        var guard = 0
        while (engine.phase != Phase.FIGHT && guard++ < 400) {
            engine.tick(listOf(InputState.NONE))
        }
        assertEquals(Phase.FIGHT, engine.phase)
    }

    @Test
    fun `endurance spawns p1 alone with one stock and a cpu wave`() {
        val engine = endurance()

        assertEquals("endurance", engine.gameType)
        assertEquals(2, engine.world.players.size)
        val p1 = engine.world.players[0]
        val wave = engine.world.players[1]
        // The port models P1's run as one stock (the AS2 run ends on the first
        // death through its sticky Dead flag - documented in Fighter.die).
        assertEquals(1, p1.lives)
        assertEquals(1, wave.lives)
        assertEquals("blue", wave.team)
        assertEquals("blue", wave.colr) // P1 is red -> enemy flips to blue
        assertTrue(wave.fighterId in GameFactory.ROSTER)
        // AS2 GenerateEndurancePlayer first-call branch: {Weight:0.1}.
        assertEquals(0.1f, wave.weight, 0.0001f)
        // frame_51: the first wave runs at AI level 1
        // (Computers[0].Level = 1 on the first call).
        assertEquals(1f, engine.ais[1]!!.level, 0.0001f)
        assertTrue(engine.ais[1]!!.forceVictim)
        assertSame(p1, engine.ais[1]!!.victim)
    }

    @Test
    fun `p1 death ends the run with a kill count headline`() {
        val engine = endurance()
        fight(engine)
        val p1 = engine.world.players[0]

        p1.die("stock") // lives = 1 -> eliminated

        engine.tick(listOf(InputState.NONE))

        assertEquals(Phase.GAMESET, engine.phase)
        // frame_101: endurance headline is "{KilledPlayers} KILLS!".
        assertEquals("0 KILLS!", engine.gameOverText)
        assertEquals(0, engine.killedPlayers)
        assertEquals("endurance", engine.snapshot().gameType)
        assertTrue(engine.snapshot().standings.isNotEmpty())
    }

    @Test
    fun `defeating the wave regenerates the next enemy`() {
        val engine = endurance()
        fight(engine)
        val p1 = engine.world.players[0]
        val oldWave = engine.world.players[1]

        oldWave.die("stock") // Lives 1 -> eliminated (Fighter.as:460/480)

        engine.tick(listOf(InputState.NONE))

        // frame_51 GenerateEndurancePlayer: KilledPlayers++, fresh roster
        // fighter at SpawnP2 with Lives 1 and Team "blue".
        assertEquals(1, engine.killedPlayers)
        val next = engine.world.players[1]
        assertNotSame(oldWave, next)
        assertEquals(1, next.lives)
        assertEquals("blue", next.team)
        assertTrue(next.fighterId in GameFactory.ROSTER)
        // The wave does not carry the first-spawn weight quirk (AS2 passes
        // {Weight:0.1} only on the first attachMovie).
        assertTrue(next.weight != 0.1f)
        // frame_51: Computers[0].Level = EnduranceLevel on every regeneration,
        // so wave 2 runs at AI level 2.
        assertEquals(2f, engine.ais[1]!!.level, 0.0001f)
        assertSame(p1, engine.ais[1]!!.victim)
        // Only P1's death ends the run.
        assertEquals(Phase.FIGHT, engine.phase)
    }

    @Test
    fun `versus matches never regenerate the second player`() {
        val engine = GameFactory.rooftopMatch(
            players = listOf(
                PlayerSpec(fighterId = "DefaultPlayer", colr = "red", team = "red"),
                PlayerSpec(fighterId = "PeachPlayer", colr = "blue", team = "blue"),
            ),
            limitMode = "stock",
            limitVal = 3,
        )
        fight(engine)
        val p2 = engine.world.players[1]

        p2.die("stock") // 3 stocks: respawns, not eliminated

        engine.tick(listOf(InputState.NONE))

        assertEquals(0, engine.killedPlayers)
        assertSame(p2, engine.world.players[1])
        assertEquals(Phase.FIGHT, engine.phase)
    }
}
