package com.glorton.engine.projectile

import com.glorton.engine.effects.Explosion
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Rocket (Rocket.as): Peach's rocket special. Gravitational
 * arc (`yInc += 0.5` per tick), rotates toward the flight direction,
 * life 3000ms, initial xInc/yInc come from the aiming animation.
 *
 * Faithful quirks of the original:
 *  - The rocket flies *through* platforms: `HitWall` does nothing unless the
 *    rocket is aged out or force-removed, so plain wall contacts don't stop it.
 *  - When the lifetime expires the rocket just disappears; the Explosion
 *    (size 5) is created **only** when it hits a fighter (`HitFighter`).
 *  - `SelfDamage = false`: it never damages its shooter.
 */
class Rocket(
    worldObj: World,
    sender: Fighter,
    dirSign: Float,
    private val initialXInc: Float = 0f,
    private val initialYInc: Float = 0f,
) : Projectile(worldObj, sender, dirSign) {

    /** AS2 field SelfDamage. */
    private val selfDamage = false

    override fun onInit() {
        lifeMs = 3000f
        xInc = initialXInc
        yInc = initialYInc
    }

    /** Port of DoRocket: movement + shared DoCommon checks. */
    override fun doMovement() {
        x += xInc
        yInc += 0.5f
        y += yInc
        rotationDeg = Math.toDegrees(
            kotlin.math.atan2(yInc.toDouble(), xInc.toDouble()),
        ).toFloat() + 90f
    }

    /** Port of Rocket.HitWall: only ends the rocket when aged out or forced. */
    override fun hitWall(force: Boolean) {
        if (ageMs > lifeMs || force) {
            destroy()
        }
    }

    /** Port of Rocket.HitFighter: explodes (size 5) on the first valid victim. */
    override fun hitFighter(candidate: HitCandidate) {
        val target = targetOf(candidate)
        if ((target === sender && !selfDamage) || target.invincible || target.state == FighterState.KO) {
            return
        }
        worldObj.explosions.add(
            Explosion(
                worldObj = worldObj,
                sender = sender,
                x = x,
                y = y,
                size = 5f,
                sound = sender.sound,
            ),
        )
        destroy()
    }
}
