package com.glorton.engine.item

import com.glorton.engine.effects.Explosion
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.CollisionResult
import com.glorton.engine.world.World

/**
 * Port of class Mine (Mine.as): the proximity mine pickup.
 *
 * Lifecycle: falls to a surface, **sticks** to it (state 0) recording the
 * platform offsets, arms after 1000 ms and then explodes
 * ([Explosion] size 3) when a fighter's attack box overlaps it. A fighter can
 * pick it up (state 3) and throw it with any punch ([use]); the thrown mine
 * also detonates on fighter contact (guarded once per victim, never on the
 * thrower - [selfDamage] is false).
 *
 * Faithful quirks of the original:
 *  - The thrown hop after touching a fighter reverses direction: `x -= xInc`
 *    then `Throw(|xInc| * 0.6, xInc <= 0 ? 5 : -5)` (the negative angle is
 *    mirrored to 175 deg inside [doThrow]).
 *  - The victim is thrown with the **raw** angle 45 (no facing-sign multiply,
 *    unlike the fighter punches) and knockback power 2, damage 20 - credited
 *    to the mine's thrower ([sender]).
 *  - `HitWall` runs `gotoAndStop("active")` before inspecting the hit (visual
 *    only in the SWF) and snaps to the surface: right wall -> rotation -90,
 *    left wall -> 90, floor -> 0, storing `XDif/YDif/PlatN` so the mine keeps
 *    following a moving platform while stuck.
 *  - AS2 `DoMine` calls `Explode()` **inside** a loop over the candidates;
 *    with several overlapping fighters the original double-splices `Items`
 *    (and can delete an innocent item through the stale `ArrInd`). The port
 *    detonates exactly once per trigger - documented deviation, the
 *    observable blast is identical.
 */
class Mine(worldObj: World) : Item(worldObj, "mine") {

    /** AS2 `Influenced = new Array(4)` - one flag per player slot. */
    private val influenced = BooleanArray(4)

    /** AS2 `SelfDamage = false` - the thrower is never harmed by their own mine. */
    private val selfDamage = false

    /** AS2 `TimeActive` - ms since the mine stuck to a platform. */
    private var timeActiveMs = 0f

    /** AS2 `XDif` - x offset inside the platform it stuck to. */
    private var xDif = 0f

    /** AS2 `YDif` - y offset inside the platform it stuck to. */
    private var yDif = 0f

    /** AS2 `PlatN` - name of the platform it stuck to. */
    private var platName: String? = null

    /** Port of Mine.init (Mine.as); `gotoAndStop("spawn")` is visual only. */
    override fun onInit() {
        y -= 2f
        lifeMs = 10000f
        timeActiveMs = 0f
    }

    /**
     * Port of Mine.HitWall (Mine.as): stick to the touched surface and arm.
     * The AS2 `§§push(gotoAndStop("active"))` line runs first (visual only);
     * side walls take the platform edge as the new position. A result without
     * a platform (stage-border touch in AS2 terms) never sticks.
     */
    override fun onHitWall(hit: CollisionResult) {
        val platform = hit.platform ?: return
        when {
            hit.rightBc && !hit.bottom && !hit.leftBc -> {
                x = platform.x
                rotationDeg = -90f
            }
            hit.leftBc && !hit.bottom && !hit.rightBc -> {
                x = platform.x + platform.width
                rotationDeg = 90f
            }
            else -> {
                // AS2: only land when falling onto the surface; Floor is
                // absolute in this port (platform top).
                if (!(hit.bottom && yInc > 0f)) return
                y = hit.floor ?: return
                rotationDeg = 0f
            }
        }
        worldSound.play("MineActivate")
        xDif = x - platform.x
        yDif = y - platform.y
        platName = platform.name
        visible = true
        setState(0)
    }

    /** Port of Mine.Explode (Mine.as): blast size 3, then destroy. */
    private fun explode() {
        Explosion(
            worldObj = worldObj,
            sender = sender,
            x = x,
            y = y,
            size = 3f,
            sound = worldSound,
        ).also { worldObj.explosions.add(it) }
        destroy()
    }

    /**
     * Port of Mine.HitFighter (Mine.as): damage 20 / Throw(2, 45) on the
     * victim (raw angle, no facing sign), then the mine kicks itself backwards.
     */
    override fun onHitFighter(who: Fighter) {
        if ((who === sender && !selfDamage) || influenced[who.playNum]) {
            return
        }
        // AS2: Which.Throw(2, 45, this.Sender) - angle is NOT facing-scaled.
        who.doThrow(2f, 45f, sender)
        who.damage(20f, sender)
        // AS2: undo this tick's step, then hop back the way the mine came.
        val hopAngle = if (xInc <= 0f) 5f else -5f
        x -= xInc
        doThrow(kotlin.math.abs(xInc) * 0.6f, hopAngle)
        influenced[who.playNum] = true
    }

    /** Port of Mine.DoMine (Mine.as): stick upkeep + arm check (see class KDoc on the detonation loop). */
    override fun onTick() {
        if (state != 0) return
        // Follow the platform it stuck to (works for MOVING platforms too).
        val platform = platName?.let { name -> worldObj.platforms.firstOrNull { it.name == name } }
        if (platform != null) {
            x = platform.x + xDif
            y = platform.y + yDif
        }
        timeActiveMs += com.glorton.engine.GameConfig.TICK_MS
        if (timeActiveMs < 1000f) return
        val victims = worldObj.collisionDetectAttack(caster = this, includeItems = false)
        if (victims.isNotEmpty()) {
            explode()
        }
    }

    /** Port of Mine.GetHit (Mine.as): only a free mine (state 1) can be grabbed. */
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
     * Port of Mine.Use (Mine.as): reposition at the attack hand and throw.
     * The hand/clip offsets live in the SWF clip - assumed values (see the
     * assumptions report), with `clip._x` terms assumed 0 (the AS2 formula
     * adds `clip._x` twice, once scaled and once not - preserved by dropping
     * both zeros).
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
