package com.glorton.engine.select

import com.glorton.engine.GameFactory
import com.glorton.engine.PlayerSpec

/** frame_46 `Players[i].Level = 7` - the starting AI difficulty. */
const val DEFAULT_AI_LEVEL = 7

/** AISetter buttons (DefineButton2_804 minus / DefineButton2_805 plus) clamp. */
const val MIN_AI_LEVEL = 1
const val MAX_AI_LEVEL = 20

/** One `PlayerPoseBox` slot of the frame_46 select screen. */
data class SelectSlot(
    /**
     * AS2 `Players[i].Fighter` - null until a SelectFighter ran, which is
     * frame_46's `Embed == undefined` test for "this slot never picked".
     */
    val fighterId: String? = null,
    /** AS2 `Players[i].CP`: false = human (`PText` "P{n}"), true = computer ("CP"). */
    val cp: Boolean,
    /** AS2 `Players[i].Enabled` - false drops the slot when the match is built. */
    val enabled: Boolean = true,
    /** AS2 `Players[i].Colr` index into [COLORS]; written only by SetColor. */
    val colrIndex: Int = 0,
    /** AS2 `Players[i].Level` - AI difficulty, moved by the AISetter buttons. */
    val level: Int = DEFAULT_AI_LEVEL,
) {
    /** AS2 `Players[i].Colr`: the color name consumed by `Game.Players[i].Colr`. */
    val colr: String get() = COLORS[colrIndex]
}

/**
 * Port of frame_46 (the frame labelled "PlayerSelect"): the fighter + color
 * select screen the four mode buttons jump to.
 *
 * AS2 source of truth:
 *  - `Fighters = [SBL, Peach, Trash, Coffee, Default, Aubergine]` and
 *    `Colors = [red, blue, green, orange]` come from frame_42.
 *  - Slots are pushed in order with `Team = Colors[i]` and `Level = 7`; the
 *    leading `Game.NumGamers` slots are humans and the rest are CPUs, and CPU
 *    slots roll a random fighter when `Game.Rand != undefined`.
 *  - `SelectFighter` stores the fighter and re-runs `SetColor(slot, 0)`.
 *  - `SetColor` walks 0..3 and skips any color another player already holds
 *    **with the same fighter**, so colors only collide across *different*
 *    fighters.
 *  - `gotoStageSelect` drops slots that never picked a fighter and keeps the
 *    original slot's `Team`, while `frame_51` later drops `Enabled == false`.
 *
 * Deviations, documented on purpose:
 *  - AS2 picks a fighter by *dragging* PlayerCoin over a FighterBox and reading
 *    the coin's drop X. Touch has no cursor, so this model exposes [armedSlot]:
 *    the UI arms a slot (tap its coin) and assigns the next FighterBox tap to
 *    it. Same result, no drag.
 *  - [confirm] returns null when no slot can fight, where AS2 counts *picked*
 *    slots (even disabled ones) for its gate and would then build a match with
 *    an empty roster. Only reachable through the multi-mode Toggle.
 *
 * The model is immutable so the Compose UI can hold it in a `mutableStateOf`;
 * every mutation returns the next state, mirroring the AS2 in-place writes.
 */
data class CharacterSelect(
    /** AS2 `Game.Type` of the mode that opened the screen. */
    val gameType: String,
    /** AS2 `Game.Mode`; [GameMode.SINGLE] makes [toggleSlot] a no-op. */
    val mode: String = GameMode.SINGLE,
    /** Slots in PlayerPoseBox order; `slots.size == Game.NumPlayers`. */
    val slots: List<SelectSlot>,
    /**
     * Port replacement for the draggable PlayerCoin: the slot the next
     * FighterBox tap assigns to (AS2 `PlayerCoin.Player`).
     */
    val armedSlot: Int = 0,
) {

    /**
     * Port of frame_46 `SelectFighter(Fighter, PlayerNum)` for [playerIndex]
     * (defaults to the armed coin). Returns the next state; the color restarts
     * at 0 through frame_46's SetColor.
     */
    fun selectFighter(fighterId: String, playerIndex: Int = armedSlot): CharacterSelect {
        require(playerIndex in slots.indices) { "No select slot at index $playerIndex" }
        val next = slots.toMutableList()
        next[playerIndex] = next[playerIndex].copy(fighterId = fighterId)
        // AS2 runs SetColor right after storing the fighter, so the store and the
        // color walk must see the *new* fighter: mutate first, then re-color.
        next[playerIndex] = next[playerIndex].copy(
            colrIndex = firstFreeColor(next, playerIndex, fighterId),
        )
        return copy(slots = next)
    }

    /**
     * Arms a slot's coin. AS2 has no equivalent call - the coin *is* the drag
     * gesture - so this only feeds [armedSlot]; see the class note on touch.
     */
    fun armSlot(index: Int): CharacterSelect {
        require(index in slots.indices) { "No select slot at index $index" }
        return copy(armedSlot = index)
    }

    /**
     * Port of the AISetter buttons: DefineButton2_804 refuses to go below
     * [MIN_AI_LEVEL], DefineButton2_805 clamps at [MAX_AI_LEVEL]. AS2 only
     * shows the setter on CP slots (PlayerPoseBox hides it otherwise).
     */
    fun adjustAiLevel(index: Int, delta: Int): CharacterSelect {
        require(index in slots.indices) { "No select slot at index $index" }
        val slot = slots[index]
        val next = slot.copy(level = (slot.level + delta).coerceIn(MIN_AI_LEVEL, MAX_AI_LEVEL))
        return copy(slots = slots.toMutableList().also { it[index] = next })
    }

    /**
     * Port of PlayerPoseBox `Toggle.onRelease`: cycles P -> CP -> Disabled -> P.
     *
     * AS2 bails out first when `Game.Mode == "single"`, so in the only mode
     * this port drives today the cycle never runs; the rule is implemented (and
     * tested) for the pending `vsmode` / local multiplayer slice.
     */
    fun toggleSlot(index: Int): CharacterSelect {
        require(index in slots.indices) { "No select slot at index $index" }
        if (mode == GameMode.SINGLE) return this
        val slot = slots[index]
        val next = when {
            // Same order as the AS2 if-chain: !CP && Enabled, then CP && Enabled, then !Enabled.
            !slot.cp && slot.enabled -> slot.copy(cp = true)
            slot.cp && slot.enabled -> slot.copy(enabled = false)
            !slot.enabled -> slot.copy(enabled = true, cp = false)
            else -> slot
        }
        return copy(slots = slots.toMutableList().also { it[index] = next })
    }

    /**
     * Port of frame_46 `gotoStageSelect` followed by frame_51's `PlayersL`
     * build: slots that never picked a fighter are dropped (AS2 marks them
     * `Enabled = false` and does not push them), `Enabled == false` slots are
     * dropped by frame_51's `Enabled != false` filter, `Team` keeps the *slot's*
     * color while the match position comes from the compaction order, and an
     * empty result returns null so the screen refuses to start (AS2's
     * `return undefined`).
     */
    fun confirm(): List<PlayerSpec>? {
        val specs = slots.mapIndexedNotNull { index, slot ->
            val fighterId = slot.fighterId ?: return@mapIndexedNotNull null
            if (!slot.enabled) return@mapIndexedNotNull null
            PlayerSpec(
                fighterId = fighterId,
                colr = slot.colr,
                team = COLORS[index],
                ai = slot.cp,
                aiLevel = slot.level.toFloat(),
            )
        }
        return specs.ifEmpty { null }
    }

    companion object {
        /**
         * Builds the screen for [gameType], including frame_46's initial CPU
         * fighter rolls (`SelectFighter(Fighters[random(n)], i)` per CPU slot
         * when `Game.Rand != undefined`).
         *
         * @param random AS2 `random(n)` (0 until n); injectable for tests.
         */
        fun create(
            gameType: String,
            mode: String = GameMode.SINGLE,
            random: (Int) -> Int = { n -> (0 until n).random() },
        ): CharacterSelect {
            val layout = modeLayout(gameType)
            var gamers = layout.numGamers
            val slots = ArrayList<SelectSlot>(layout.numPlayers)
            repeat(layout.numPlayers) { index ->
                val isHuman = gamers > 0
                if (isHuman) gamers--
                // frame_46 pushes the PlayerPoseBox first and only then labels it
                // P{n} or CP out of the remaining Gamer/Computer budget.
                slots.add(SelectSlot(fighterId = null, cp = !isHuman))
                if (!isHuman && layout.rollsCpuFighters) {
                    // frame_46: `SelectFighter(Fighters[random(Fighters.length)], i)`
                    val rolled = GameFactory.ROSTER[random(GameFactory.ROSTER.size)]
                    slots[index] = slots[index].copy(
                        fighterId = rolled,
                        colrIndex = firstFreeColor(slots, index, rolled),
                    )
                }
            }
            return CharacterSelect(gameType = gameType, mode = mode, slots = slots)
        }
    }
}

/**
 * frame_46 `SetColor(PlayerNum, Col)`: first color in 0..3 that no *other*
 * player holding the same fighter already owns. [current] is the slot list as
 * AS2 sees it at call time - during [CharacterSelect.create] that is the prefix
 * built so far, because frame_46 only pushes slots up to the one being filled.
 *
 * A [fighterId] equal to the slot's own is required by the caller; the walk is
 * bounded by [COLORS] instead of recursing like AS2, which terminates because a
 * slot never has more than three peers (both counts cap at 4).
 */
private fun firstFreeColor(current: List<SelectSlot>, playerIndex: Int, fighterId: String): Int {
    var col = 0
    repeat(COLORS.size) {
        val taken = current.indices.any { other ->
            other != playerIndex &&
                current[other].fighterId == fighterId &&
                current[other].colrIndex == col
        }
        if (!taken) return col
        // AS2: `Col != 3 ? Col + 1 : 0`
        col = if (col != COLORS.lastIndex) col + 1 else 0
    }
    // Unreachable while `slots.size <= COLORS.size` (both are at most 4).
    return current[playerIndex].colrIndex
}
