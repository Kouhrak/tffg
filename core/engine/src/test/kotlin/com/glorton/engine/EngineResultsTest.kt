package com.glorton.engine

import com.glorton.engine.fighter.Fighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Standings and death-semantics tests: frame_101's BestPlayers score,
 * frame_143's Rank, and the respawn-vs-elimination deviation of
 * [Fighter.die] (see Fighter.as:486-491 vs frame_101's `DeathOrd == undefined`).
 */
class EngineResultsTest {

    @Before
    fun resetDeathOrder() {
        // The elimination counter is static across the JVM; reset it so the
        // assertions on deathOrd do not depend on test execution order.
        Fighter.resetDeathOrder()
    }

    private fun match(limitMode: String): Engine =
        GameFactory.match(
            players = listOf(
                PlayerSpec(fighterId = "DefaultPlayer", colr = "red", team = "red"),
                PlayerSpec(fighterId = "PeachPlayer", colr = "blue", team = "blue"),
            ),
            limitMode = limitMode,
            limitVal = 3,
        )

    @Test
    fun `stock survivor ranks first over a player eliminated after several deaths`() {
        val engine = match("stock")
        val p1 = engine.world.players[0]
        val p2 = engine.world.players[1]
        // P2 burned all 3 stocks: the first two deaths respawned (dead stayed
        // false), the third eliminated them (deathOrd = elimination order).
        p2.deaths = 3
        p2.dead = true
        p2.deathOrd = 0
        p1.kos = 3

        val standings = engine.computeStandings()

        assertEquals(0, standings[0].playerIndex)
        assertEquals(1, standings[1].playerIndex)
        assertEquals(1, standings[0].rank)
        assertEquals(2, standings[1].rank)
        assertEquals(3, standings[0].kos)
    }

    @Test
    fun `survivor ties rank by ascending player index`() {
        val engine = match("stock")
        // Both fighters never died (deathOrd == -1): frame_101 substitutes
        // NumDead for both, so the tie keeps input order (lowest player
        // number ranks first).
        val standings = engine.computeStandings()

        assertEquals(listOf(0, 1), standings.map { it.playerIndex })
        assertEquals(listOf(1, 2), standings.map { it.rank })
    }

    @Test
    fun `time mode ranks by kos minus deaths`() {
        val engine = match("time")
        val p1 = engine.world.players[0]
        val p2 = engine.world.players[1]
        p1.kos = 5
        p1.deaths = 3 // score 2
        p2.kos = 4
        p2.deaths = 1 // score 3

        val standings = engine.computeStandings()

        assertEquals(1, standings[0].playerIndex)
        assertEquals(3, standings[0].score)
        assertEquals(2, standings[1].score)
    }

    @Test
    fun `death with remaining stocks respawns without eliminating`() {
        val engine = match("stock")
        val p1 = engine.world.players[0] // lives = 3

        p1.die("stock")

        assertFalse(p1.dead)
        assertEquals(2, p1.lives)
        assertEquals(1, p1.deaths)
        // No attacker: the death counts as a suicide (AS2 SDs).
        assertEquals(1, p1.sds)
        assertEquals(-1, p1.deathOrd)
    }

    @Test
    fun `final stock death eliminates and records the elimination order`() {
        val engine = match("stock")
        val p1 = engine.world.players[0]
        p1.lives = 1

        p1.die("stock")

        assertTrue(p1.dead)
        assertEquals(0, p1.lives)
        assertEquals(0, p1.deathOrd)
        assertFalse(p1.visible)
    }
}
