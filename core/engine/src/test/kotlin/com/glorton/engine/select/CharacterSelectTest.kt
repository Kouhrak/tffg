package com.glorton.engine.select

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * frame_46 (PlayerSelect) rules: slot layout per mode button, the CPU fighter
 * roll when `Game.Rand != undefined`, SetColor's same-fighter collision rule,
 * the AISetter clamp, the PlayerPoseBox Toggle and gotoStageSelect's compaction.
 *
 * Every test injects `random` so the CPU rolls are deterministic; the default
 * rollout would make assertions on colors order-dependent.
 */
class CharacterSelectTest {

    /** AS2 `random(n)` pinned to 0: every CPU roll picks ROSTER[0] (SBLPlayer). */
    private val firstRosterEntry: (Int) -> Int = { 0 }

    private fun select(
        gameType: String,
        mode: String = GameMode.SINGLE,
        random: (Int) -> Int = firstRosterEntry,
    ): CharacterSelect = CharacterSelect.create(gameType = gameType, mode = mode, random = random)

    // ------------------------------------------------------------------ layout

    @Test
    fun `oneonone lays out one human and one rolled cpu`() {
        val select = select(GameType.ONE_ON_ONE)

        assertEquals(2, select.slots.size)
        assertFalse(select.slots[0].cp)
        assertNull(select.slots[0].fighterId) // humans pick on screen
        assertTrue(select.slots[1].cp)
        assertEquals("SBLPlayer", select.slots[1].fighterId)
        assertEquals(DEFAULT_AI_LEVEL, select.slots[1].level)
        // Frame_46 assigns Team = Colors[i] per slot position, not per color.
        assertEquals("red", COLORS[0])
        assertEquals("blue", COLORS[1])
    }

    @Test
    fun `quick lays out one human and three rolled cpus`() {
        val select = select(GameType.QUICK, random = { 1 })

        assertEquals(4, select.slots.size)
        assertFalse(select.slots[0].cp)
        assertTrue(select.slots[1].cp)
        assertTrue(select.slots[2].cp)
        assertTrue(select.slots[3].cp)
        // ROSTER[1] is PeachPlayer; three identical rolls still walk SetColor.
        select.slots.drop(1).forEach { assertEquals("PeachPlayer", it.fighterId) }
        assertEquals(0, select.slots[1].colrIndex)
        assertEquals(1, select.slots[2].colrIndex)
        assertEquals(2, select.slots[3].colrIndex)
    }

    @Test
    fun `endurance lays out a single human slot with no cpu roll`() {
        val select = select(GameType.ENDURANCE)

        assertEquals(1, select.slots.size)
        assertFalse(select.slots[0].cp)
        // Game.Rand is undefined for endurance, so nothing is rolled.
        assertNull(select.slots[0].fighterId)
    }

    @Test
    fun `vsmode lays out four human slots and never rolls fighters`() {
        val select = select(GameType.VS_MODE)

        assertEquals(4, select.slots.size)
        select.slots.forEach {
            assertFalse(it.cp)
            assertNull(it.fighterId)
        }
        // PENDING: vsmode needs a local multiplayer input mapping; Engine.tick
        // currently indexes the single touch input by fighter slot.
    }

    // ------------------------------------------------------------------ colors

    @Test
    fun `the same fighter on two slots takes consecutive colors`() {
        // vsmode has no Game.Rand, so no CPU slot pre-rolls a fighter+color and
        // both slots start from SetColor's clean slate.
        val select = select(GameType.VS_MODE)
            .selectFighter("SBLPlayer", playerIndex = 0)
            .selectFighter("SBLPlayer", playerIndex = 1)

        // AS2 SetColor(0) -> 0 for the first, then skips red for the second.
        assertEquals(0, select.slots[0].colrIndex)
        assertEquals(1, select.slots[1].colrIndex)
        assertEquals("red", select.slots[0].colr)
        assertEquals("blue", select.slots[1].colr)
    }

    @Test
    fun `different fighters are allowed to share a color`() {
        val select = select(GameType.VS_MODE)
            .selectFighter("SBLPlayer", playerIndex = 0)
            .selectFighter("PeachPlayer", playerIndex = 1)

        // SetColor only compares against players holding the SAME fighter.
        assertEquals(0, select.slots[0].colrIndex)
        assertEquals(0, select.slots[1].colrIndex)
    }

    @Test
    fun `repicking a fighter restarts the color walk at zero`() {
        val select = select(GameType.VS_MODE)
            .selectFighter("SBLPlayer", playerIndex = 0)
            .selectFighter("PeachPlayer", playerIndex = 1)
            // P1 switches to Peach: slot 1 already owns red with Peach, so P1
            // walks to blue while slot 1 keeps its own color untouched.
            .selectFighter("PeachPlayer", playerIndex = 0)

        assertEquals(1, select.slots[0].colrIndex)
        assertEquals(0, select.slots[1].colrIndex)
    }

    @Test
    fun `arming a slot routes the next fighter pick to it`() {
        val select = select(GameType.ONE_ON_ONE)
            .armSlot(1)
            .selectFighter("CoffeePlayer")

        assertEquals(1, select.armedSlot)
        assertEquals("CoffeePlayer", select.slots[1].fighterId)
        assertNull(select.slots[0].fighterId)
    }

    // ------------------------------------------------------------------ confirm

    @Test
    fun `confirm builds one spec per picked slot with the slot team color`() {
        // random = 4 -> the CPU rolls ROSTER[4] (DefaultPlayer) so its color
        // does not compete with P1's SBLPlayer pick.
        val select = select(GameType.ONE_ON_ONE, random = { 4 })
            .selectFighter("SBLPlayer", playerIndex = 0)

        val specs = select.confirm()

        assertNotNull(specs)
        assertEquals(2, specs!!.size)
        assertEquals("SBLPlayer", specs[0].fighterId)
        assertFalse(specs[0].ai)
        assertEquals("red", specs[0].colr)
        assertEquals("red", specs[0].team)
        assertTrue(specs[1].ai)
        assertEquals("DefaultPlayer", specs[1].fighterId)
        assertEquals("blue", specs[1].team) // Team comes from the slot position
        assertEquals(DEFAULT_AI_LEVEL.toFloat(), specs[1].aiLevel, 0.0001f)
    }

    @Test
    fun `confirm compacts unpicked slots but keeps their original team`() {
        // AS2 pushes only slots whose Embed exists, so an unpicked P1 shifts the
        // CPU into Game.Players[0] while Team still reads Colors[1].
        val specs = select(GameType.ONE_ON_ONE).confirm()

        assertNotNull(specs)
        assertEquals(1, specs!!.size)
        assertEquals("SBLPlayer", specs[0].fighterId)
        assertTrue(specs[0].ai)
        assertEquals("blue", specs[0].team)
    }

    @Test
    fun `confirm returns null when nobody picked a fighter`() {
        assertNull(select(GameType.ENDURANCE).confirm())
    }

    @Test
    fun `confirm drops slots disabled by the toggle`() {
        val select = select(GameType.VS_MODE, mode = GameMode.MULTI)
            .selectFighter("SBLPlayer", playerIndex = 0)
            .selectFighter("PeachPlayer", playerIndex = 1)
            .toggleSlot(1) // P -> CP
            .toggleSlot(1) // CP -> Disabled

        val specs = select.confirm()

        assertNotNull(specs)
        assertEquals(1, specs!!.size)
        assertEquals("SBLPlayer", specs[0].fighterId)
    }

    // ------------------------------------------------------------------ AISetter

    @Test
    fun `ai level clamps to the AISetter range`() {
        var select = select(GameType.ONE_ON_ONE)

        select = select.adjustAiLevel(1, +100)
        assertEquals(MAX_AI_LEVEL, select.slots[1].level)

        select = select.adjustAiLevel(1, -100)
        assertEquals(MIN_AI_LEVEL, select.slots[1].level)

        select = select.adjustAiLevel(1, +3)
        assertEquals(4, select.slots[1].level)
    }

    // ------------------------------------------------------------------ Toggle

    @Test
    fun `toggle is a no-op while Game Mode is single`() {
        val select = select(GameType.ONE_ON_ONE, mode = GameMode.SINGLE)

        assertEquals(select, select.toggleSlot(0))
    }

    @Test
    fun `toggle cycles player cpu and disabled in multi mode`() {
        var select = select(GameType.VS_MODE, mode = GameMode.MULTI)

        select = select.toggleSlot(0) // P -> CP
        assertTrue(select.slots[0].cp)
        assertTrue(select.slots[0].enabled)

        select = select.toggleSlot(0) // CP -> Disabled
        assertTrue(select.slots[0].cp) // AS2 leaves CP set while disabled
        assertFalse(select.slots[0].enabled)

        select = select.toggleSlot(0) // Disabled -> P
        assertFalse(select.slots[0].cp)
        assertTrue(select.slots[0].enabled)
    }
}
