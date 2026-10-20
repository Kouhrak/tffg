package com.glorton.engine.item

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.CollisionResult
import com.glorton.engine.world.World
import kotlin.random.Random

/**
 * Port of class Star (Star.as): the invincibility pickup. Bounces around
 * while airborne; the first fighter that touches it becomes [Fighter.invincible]
 * and [Fighter.blinking] for 9 seconds, after which the star destroys itself.
 *
 * Faithful quirks of the original:
 *  - Spawn: `y -= 10`, `yInc = -8`, `xInc = (random(3) - 1) * 5` (i.e. -5, 0
 *    or +5), `Life = 20000` and `SetState(2)` immediately.
 *  - `HitFighter` writes `this.State = 0` directly (not `SetState`), so no
 *    carried-item bookkeeping runs; the star is "absorbed", not held.
 *  - The lifetime restarts at 9000ms when absorbed (`Age = 0, Life = 9000`).
 */
class Star(worldObj: World) : Item(worldObj, "star") {

    override fun onInit() {
        y -= 10f
        yInc = -8f
        xInc = (Random.nextInt(3) - 1) * 5f
        lifeMs = 20000f
        setState(2)
    }

    /** Port of Star.HitWall (Star.as): corner touch destroys, edges bounce. */
    override fun onHitWall(hit: CollisionResult) {
        if (hit.rightBc && hit.leftBc && hit.bottom) {
            destroy()
            return
        }
        when {
            hit.rightBc && !hit.bottom && !hit.leftBc -> xInc *= -1f
            hit.leftBc && !hit.bottom && !hit.rightBc -> xInc *= -1f
            hit.bottom && yInc > 0f -> yInc *= -1f
        }
    }

    /** Port of Star.HitFighter (Star.as): first toucher gets the buff. */
    override fun onHitFighter(who: Fighter) {
        if (sender != null) return
        // AS2 assigns State directly (bypasses SetState on purpose).
        state = 0
        ageMs = 0f
        lifeMs = 9000f
        visible = false
        who.invincible = true
        who.blinking = true
        sender = who
    }

    /** Port of DoStar: release the buff when the absorbed lifetime expires. */
    override fun onTick() {
        if (ageMs > lifeMs) {
            sender?.let {
                it.invincible = false
                it.blinking = false
            }
            destroy()
        }
    }
}
