package com.glorton.engine.projectile

import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Garbage (Garbage.as): TrashPlayer's special, thrown with a
 * visual type frame (0..5). Speed 8 with an initial upward arc (yInc -2),
 * gravity 0.5 added per tick, life 500ms, damage 6, Throw(3, 45deg),
 * "punch" sound (lowercase in the AS2 source).
 */
class Garbage(
    worldObj: World,
    sender: Fighter,
    dirSign: Float,
    private val typeFrame: Int,
) : Projectile(worldObj, sender, dirSign) {

    override fun onInit() {
        lifeMs = 500f
        // AS2 init guard `if(this.xInc == undefined)`: movement is only set
        // on the first spawn; rethrows of the same instance keep xInc/yInc.
        if (!movementInitialized) {
            movementInitialized = true
            yInc = -2f
            xInc = 8f * dirSign
        }
    }

    private var movementInitialized = false

    override fun doMovement() {
        x += xInc
        yInc += 0.5f
        y += yInc
        rotationDeg += 20f
    }

    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if (target === sender || target.invincible || target.state == FighterState.KO) {
            return
        }
        sender.sound.play("punch")
        target.damage(6f, sender)
        target.doThrow(3f, 45f * (if (xInc <= 0f) -1f else 1f), sender)
        destroy()
    }

    /** Visual variant frame chosen by the throwing animation (AS2 gotoAndStop(Type)). */
    val visualType: Int get() = typeFrame
}
