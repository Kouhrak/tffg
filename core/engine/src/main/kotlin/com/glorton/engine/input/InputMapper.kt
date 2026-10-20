package com.glorton.engine.input

import com.glorton.engine.GameConfig
import com.glorton.engine.InputState
import com.glorton.engine.fighter.Fighter

/**
 * Port of the per-player key handlers of PlayerControl.init (PlayerControl.as).
 * Converts the polled [InputState] of one tick into the edge-triggered calls
 * the original keyboard listeners made:
 *
 *  - Up: tapping releases into Jump(); holding sets Trc.Up only for the first
 *    50ms (Reflex) and jumps every tick while held beyond that.
 *  - Punch/Special: if the up-flag is pending, an "up" variant is issued first
 *    and the flag cleared; the plain attack is always issued afterwards.
 *  - Shield: engages only while the fighter is stationary (xInc == 0),
 *    disengages on release.
 *  - Left/Right: press starts moving; release stops unless the other
 *    direction is held. Down follows the held state each tick.
 */
class InputMapper(private val fighter: Fighter) {

    private var prevUp = false
    private var prevDown = false
    private var prevLeft = false
    private var prevRight = false
    private var prevPunch = false
    private var prevSpecial = false
    private var prevShield = false

    /** PlayerControl.Reflex: milliseconds the up key counts as "pending up attack". */
    private var upHeldMs = 0f
    private var trcUp = false

    /** Applied once per logic tick before Fighter.doFighter. */
    fun update(input: InputState) {
        // --- Up (jump) -------------------------------------------------------
        if (input.up) {
            upHeldMs += GameConfig.TICK_MS
            trcUp = upHeldMs < REFLEX_MS
            if (!trcUp) {
                fighter.jump()
            }
            if (!prevUp) {
                // Fresh press: nothing yet, the release or hold decides.
            }
        } else {
            if (prevUp) {
                // keyup U: Jump + clear pending flag.
                fighter.jump()
                trcUp = false
            }
            upHeldMs = 0f
        }
        prevUp = input.up

        // --- Shield (B) ------------------------------------------------------
        if (input.shield) {
            if (!prevShield && fighter.xInc == 0f) {
                fighter.shielded = true
            }
        } else {
            if (prevShield) {
                fighter.shielded = false
            }
        }
        prevShield = input.shield

        // --- Down ------------------------------------------------------------
        if (input.down) {
            fighter.move("down")
        } else if (prevDown) {
            fighter.move("stop")
        }
        prevDown = input.down

        // --- Left / Right ----------------------------------------------------
        if (input.left) {
            if (!prevLeft) {
                fighter.move("left")
            }
        } else if (prevLeft && !input.right) {
            fighter.move("stop")
        }
        prevLeft = input.left

        if (input.right) {
            if (!prevRight) {
                fighter.move("right")
            }
        } else if (prevRight && !input.left) {
            fighter.move("stop")
        }
        prevRight = input.right

        // --- Punch -----------------------------------------------------------
        if (input.punch && !prevPunch) {
            if (trcUp) {
                fighter.attack("punch", "up")
                trcUp = false
            }
            fighter.attack("punch", "none")
        }
        prevPunch = input.punch

        // --- Special ---------------------------------------------------------
        if (input.special && !prevSpecial) {
            if (trcUp) {
                fighter.attack("special", "up")
                trcUp = false
            }
            fighter.attack("special", "none")
        }
        prevSpecial = input.special
    }

    companion object {
        /** AS2: PlayerControl.Reflex = 50. */
        const val REFLEX_MS = 50f
    }
}
