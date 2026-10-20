package com.glorton.engine.item

import com.glorton.engine.effects.Explosion
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.CollisionResult
import com.glorton.engine.world.World

/**
 * Port of class Grenade (Grenade.as): the impact grenade pickup.
 *
 * Unlike the Mine, the Grenade detonates ([Explosion] size 4) the instant it
 * touches **anything** - a platform ([onHitWall]) or a fighter
 * ([onHitFighter]) - both while thrown by a fighter and while bouncing from
 * its own spawn (hitWall fires regardless of who threw it).
 *
 * Faithful quirks of the original:
 *  - The spin: `rotation += (State == 2) * 10` per 25 ms tick while airborne
 *    (400 deg/s).
 *  - `HitFighter` has the same self/once-per-victim guards as the Mine
 *    (`SelfDamage = false`), but the contact itself is the fuse - there is no
 *    melee-damage path; the explosion's own radius does the work.
 *  - Pickup ([getHit]) and throw ([use]) are verbatim the Mine's AS2 code
 *    (both subclasses carry the same copy-pasted block).
 */
class Grenade(worldObj: World) : Item(worldObj, "grenade") {

    /** AS2 `Influenced = new Array(4)` - one flag per player slot. */
    private val influenced = BooleanArray(4)

    /** AS2 `SelfDamage = false` - the thrower is never harmed by their own grenade. */
    private val selfDamage = false

    /** Port of Grenade.init (Grenade.as); `gotoAndStop("spawn")` is visual only. */
    override fun onInit() {
        y -= 2f
        lifeMs = 10000f
    }

    /** Port of Grenade.HitWall (Grenade.as): any surface contact explodes. */
    override fun onHitWall(hit: CollisionResult) {
        explode()
    }

    /** Port of Grenade.Explode (Grenade.as): blast size 4, then destroy. */
    private fun explode() {
        Explosion(
            worldObj = worldObj,
            sender = sender,
            x = x,
            y = y,
            size = 4f,
            sound = worldSound,
        ).also { worldObj.explosions.add(it) }
        destroy()
    }

    /** Port of Grenade.HitFighter (Grenade.as): guards, then explode. */
    override fun onHitFighter(who: Fighter) {
        if ((who === sender && !selfDamage) || influenced[who.playNum]) {
            return
        }
        influenced[who.playNum] = true
        explode()
    }

    /** Port of Grenade.DoGrenade (Grenade.as): DoCommon (via the tick) + airborne spin. */
    override fun onTick() {
        if (state == 2) {
            rotationDeg += 10f
        }
    }

    /** Port of Grenade.GetHit (Grenade.as): verbatim Mine.GetHit (same AS2 block). */
    override fun getHit(by: Fighter, forceGrab: Boolean) {
        if (state != 1) return
        if (by.currentItem != "") return
        setState(3)
        by.currentItem = name
        by.currentItemObj = this
        sender = by
        visible = false
    }

    /**
     * Port of Grenade.Use (Grenade.as): verbatim Mine.Use (same AS2 block);
     * hand/clip offsets assumed - see Mine.use and the assumptions report.
     */
    override fun use(attack: String): Boolean {
        val holder = sender ?: return false
        x = USE_HAND_X * holder.facingSign + holder.x
        y = USE_HAND_Y + holder.y
        var pitchDeg = 10f
        var power = 8f
        when (attack) {
            "punchUp" -> {
                pitchDeg = 90f
                power = 15f
                // AS2: the up-punch throws the item straight up from the body.
                x = holder.x
            }
            "punchAirDown" -> pitchDeg = -90f
        }
        doThrow(power, pitchDeg * holder.facingSign)
        holder.currentItem = ""
        holder.currentItemObj = null
        return false
    }

    companion object {
        /** Hand x inside the attack clip while throwing (assumed; AS2 reads `hand._x`). */
        private const val USE_HAND_X = 18f

        /** Hand y inside the attack clip while throwing (assumed; ~-40 px). */
        private const val USE_HAND_Y = -40f
    }
}
