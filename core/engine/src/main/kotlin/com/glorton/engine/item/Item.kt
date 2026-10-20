package com.glorton.engine.item

import com.glorton.engine.GameConfig
import com.glorton.engine.RectF
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.world.Body
import com.glorton.engine.world.CollisionResult
import com.glorton.engine.world.World

/**
 * Port of class Item (Item.as): pick-upable world objects (Mine, Grenade, Star).
 *
 * State machine (AS2 field State):
 *  1 = spawn/idle on the ground (blinks before expiring),
 *  2 = thrown/physics active,
 *  3 = carried by a fighter,
 *  0 = parked (a Mine stuck to a platform).
 *
 * The AS2 version drives every item with its own setInterval(..., IntTime);
 * here the engine calls [doItemTick] once per logic tick instead.
 */
abstract class Item(
    val worldObj: World,
    val name: String,
) : Body {

    var arrInd: Int = -1
    var ageMs: Float = 0f
    var state: Int = 1
    var x: Float = 0f
    var y: Float = 0f
    var xInc: Float = 0f
    var yInc: Float = 0f
    var sender: Fighter? = null
    var lifeMs: Float = 0f
    var visible: Boolean = true
    var rotationDeg: Float = 0f

    /** Sprite footprint in world units; subclasses may override. */
    open val width: Float = 24f
    open val height: Float = 24f

    override val bodyRect: RectF
        get() = RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)

    /** Port of Item.init (Item.as). */
    fun init(spawnX: Float, spawnY: Float) {
        arrInd = worldObj.items.size
        worldObj.items.add(this)
        x = spawnX
        y = spawnY
        ageMs = 0f
        state = 1
        onInit()
    }

    protected abstract fun onInit()

    /** Port of Item.DoCommon, called by every item subclass before its extras. */
    protected fun doCommon() {
        ageMs += GameConfig.TICK_MS
        when (state) {
            1 -> {
                // Blink during the last 3 seconds of life, then expire.
                if (ageMs + 3000f > lifeMs) {
                    visible = ((ageMs / GameConfig.TICK_MS).toInt() % 2) == 0
                }
                if (ageMs > lifeMs) {
                    destroy()
                }
            }
            2 -> {
                doMovement()
                val hit = worldObj.collisionDetect(
                    caster = this,
                    basic = false,
                    platformsEnabled = true,
                    helpers = false,
                    movingC = true,
                    lowRes = true,
                )
                if (hit.hit) {
                    onHitWall(hit)
                }
                val victims = worldObj.collisionDetectAttack(caster = this, includeItems = false)
                for (candidate in victims) {
                    onHitFighter(candidate.obj as Fighter)
                }
                if (!worldObj.bounds.contains(x, y)) {
                    destroy()
                }
            }
        }
    }

    /** Port of Item.DoMovement: gravity 0.6, fall speed cap 6. */
    protected fun doMovement() {
        x += xInc
        yInc = if (yInc > GameConfig.MAX_FALL_SPEED) {
            GameConfig.MAX_FALL_SPEED
        } else {
            yInc + GameConfig.GRAVITY
        }
        y += yInc
    }

    /** Port of Item.Throw (Item.as). Angle in degrees; negative angles mirror to 180+angle. */
    fun doThrow(power: Float, angleDeg: Float) {
        var angle = angleDeg
        if (angle < 0) {
            angle = 180f + angle
        }
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val from = sender
        xInc = kotlin.math.cos(rad) * power + (from?.xInc ?: 0f)
        yInc = -kotlin.math.sin(rad) * power + (from?.yInc ?: 0f)
        setState(2)
    }

    /**
     * Port of Item.SetState (Item.as). `@JvmName` avoids the JVM clash with the
     * `state` property setter (`setState(I)V`); the Kotlin-facing name keeps the
     * AS2 spelling for traceability.
     */
    @JvmName("setStateRule")
    fun setState(newState: Int) {
        state = newState
        val from = sender
        if (state != 3 && from != null && from.currentItemObj === this) {
            from.currentItem = ""
        }
        if (state == 2) {
            visible = true
        }
    }

    /** Port of Item.Destroy (Item.as); indices are repaired after the splice. */
    open fun destroy() {
        worldObj.items.removeAt(arrInd)
        for (i in arrInd until worldObj.items.size) {
            worldObj.items[i].arrInd = i
        }
        onDestroy()
    }

    protected open fun onDestroy() = Unit

    /** Per-tick entry point replacing the AS2 setInterval callbacks. */
    fun doItemTick() {
        doCommon()
        onTick()
    }

    protected abstract fun onTick()

    protected abstract fun onHitWall(hit: CollisionResult)

    protected abstract fun onHitFighter(who: Fighter)

    /** Port of Item.GetHit: only items in state 1 can be grabbed. */
    open fun getHit(by: Fighter, forceGrab: Boolean = false) = Unit

    /** Port of Item.Use: throw the held item with the attack's hand position. */
    open fun use(attack: String): Boolean = false
}
