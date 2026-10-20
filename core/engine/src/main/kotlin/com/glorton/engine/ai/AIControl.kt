package com.glorton.engine.ai

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.World
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private class ActionQueue {
    var tm: Float = 0f
    var act1: String? = null
    var act2: String? = null
}

/**
 * Port of class AIControl (AIControl.as). Drives one CPU fighter each logic
 * tick: re-picks a victim with 50% probability, walks toward it through AI
 * helper platforms, and queues attacks whose latency depends on [level].
 *
 * Note: the AS2 constructor ends with `this.Level *= 2` over an undefined
 * field (NaN); InitArea always overwrites Level right after construction, so
 * the port simply starts with a sane default of 12.
 */
class AIControl(
    val player: Fighter,
    private val world: World,
) {
    /** Difficulty: higher is faster and more aggressive (AS2 Level, set by InitArea). */
    var level: Float = 12f

    var victim: Fighter? = null

    /** Endurance mode pins the victim (frame_51 GenerateEndurancePlayer). */
    var forceVictim: Boolean = false

    private val actionQ = ActionQueue()

    /** Port of AIControl.DoAI (AIControl.as). */
    fun doAI() {
        if (Random.nextInt(200) < 100 && !forceVictim) {
            pickFighter()
        }
        val target = victim ?: return
        moveTo(target.x, target.y)
        attack()
        checkAct()
    }

    /** Port of AIControl.Attack (AIControl.as). */
    private fun attack() {
        val target = victim ?: return
        // AS2: squared distance; 900 = 30 units of separation.
        val distSq = (target.x - player.x).let { it * it } + (target.y - player.y).let { it * it }

        if (distSq < 900f) {
            // Opportunistic punch when falling onto the victim.
            if (player.y < target.y && !player.onGround && player.yInc > 0) {
                act("punch", "none")
            }
            val roll = Random.nextInt((160f / level).roundToInt().coerceAtLeast(1))
            when (roll) {
                0 -> act("punch", "none")
                3 -> act("punch", "up")
                4 -> player.shielded = !player.shielded
                else -> {
                    if (player.shielded && roll > 12) {
                        player.shielded = false
                    }
                    moveTo(target.x, target.y)
                }
            }
        } else {
            player.shielded = false
            val roll = Random.nextInt((720f / level).roundToInt().coerceAtLeast(1))
            when (roll) {
                0 -> {
                    if (abs(target.y - player.y) > 30f) return
                    act("special", "none")
                }
                1 -> act("special", "up")
                else -> moveTo(target.x, target.y)
            }
        }
    }

    /**
     * Port of AIControl.MoveTo (AIControl.as). Collision probe uses the AS2
     * argument pattern CollisionDetect(Player, true, false, true): basic mode,
     * platforms disabled, AI helpers enabled.
     */
    private fun moveTo(dX: Float, dY: Float, rand: Boolean = false) {
        if (abs(dX - player.x) < 30f) {
            player.move("stop")
            return
        }
        var targetX = dX
        if (rand) {
            targetX += Random.nextInt(40) - 20
        }
        val probe = world.collisionDetect(
            caster = player,
            basic = true,
            platformsEnabled = false,
            helpers = true,
        )
        if (dY - player.y > 40f && player.onGround) {
            player.move("down")
            return
        }
        when {
            targetX > player.x -> player.move("right")
            targetX < player.x -> player.move("left")
        }
        val target = victim
        if (dY < player.y && target != null && target.onGround) {
            player.jump()
        }
        if (probe.hitSomething && probe.aiType == 'J') {
            player.jump()
        }
    }

    /** Port of AIControl.PickFighter (AIControl.as): score = -dist/5 + lives*2 + luck. */
    private fun pickFighter() {
        val scored = mutableListOf<Pair<Float, Int>>()
        for ((index, candidate) in world.players.withIndex()) {
            if (candidate === player) continue
            val dx = player.x - candidate.x
            val dy = player.y - candidate.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            var score = -dist / 5f
            score += candidate.lives * 2
            score += Random.nextInt(100) - 50
            scored.add(score to index)
        }
        if (scored.isEmpty()) return
        val best = scored.maxBy { it.first }
        victim = world.players[best.second]
    }

    /** Port of AIControl.Act (AIControl.as): queue an attack with level-based delay. */
    private fun act(act1: String, act2: String) {
        if (actionQ.tm == 0f) {
            actionQ.tm = (1f / level) * 500f + Random.nextInt(200)
            actionQ.act1 = act1
            actionQ.act2 = act2
        }
    }

    /** Port of AIControl.CheckAct (AIControl.as): execute the queued attack. */
    private fun checkAct() {
        if (actionQ.tm > 0f) {
            actionQ.tm -= com.glorton.engine.GameConfig.TICK_MS
        } else {
            val a1 = actionQ.act1
            if (a1 != null) {
                player.attack(a1, actionQ.act2 ?: "none")
                actionQ.tm = 0f
                actionQ.act1 = null
                actionQ.act2 = null
            }
        }
    }
}
