package com.glorton.engine.projectile

import com.glorton.engine.GameConfig
import com.glorton.engine.RectF
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.ThrowSource
import com.glorton.engine.world.Body
import com.glorton.engine.world.HitCandidate
import com.glorton.engine.world.World

/**
 * Port of class Projectile (Projectile.as): fired attacks that travel until
 * their lifetime expires, a platform is touched, a fighter is hit or they
 * leave the stage bounds. The AS2 subclasses drive themselves with
 * setInterval(..., IntTime); here the engine calls [tick] once per logic tick.
 */
abstract class Projectile(
    val worldObj: World,
    val sender: Fighter,
    /** Direction sign of the firing fighter (+1 right, -1 left; AS2 _xscale/100). */
    val dirSign: Float,
) : Body, ThrowSource {

    var x: Float = 0f
    var y: Float = 0f
    var xInc: Float = 0f
    var yInc: Float = 0f
    var ageMs: Float = 0f
    var lifeMs: Float = 0f
    var rotationDeg: Float = 0f
    var active: Boolean = true
        internal set

    /**
     * AS2 field `Weight`: every subclass copies `this.Sender.Weight` at init
     * because EnergyBall throws *itself* as the Throw sender, and
     * Fighter.Throw reads `Sender.Weight` from whatever object it receives.
     */
    override var weight: Float = 0f

    open val width: Float = 16f
    open val height: Float = 16f

    override val bodyRect: RectF
        get() = RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)

    /** Registers the projectile and applies subclass initial values. */
    fun init(spawnX: Float, spawnY: Float) {
        worldObj.projectiles.add(this)
        x = spawnX
        y = spawnY
        ageMs = 0f
        active = true
        // AS2: this.Weight = this.Sender.Weight in every subclass init.
        weight = sender.stats.weight
        onInit()
    }

    protected abstract fun onInit()

    /** Per-tick entry: subclass movement first, then the shared checks. */
    fun tick() {
        if (!active) return
        doMovement()
        doCommon()
    }

    protected abstract fun doMovement()

    /** Port of Projectile.DoCommon (Projectile.as). */
    private fun doCommon() {
        ageMs += GameConfig.TICK_MS
        if (ageMs > lifeMs) {
            hitWall(force = false)
        }
        val wallHit = worldObj.collisionDetect(
            caster = this,
            basic = true,
            platformsEnabled = true,
            helpers = false,
            movingC = false,
        )
        if (wallHit.hitSomething) {
            hitWall(force = false)
        }
        val victims = worldObj.collisionDetectAttack(caster = this, includeItems = false)
        for (candidate in victims) {
            hitFighter(candidate)
        }
        if (!worldObj.bounds.contains(x, y)) {
            hitWall(force = false)
        }
    }

    /** Port of Projectile.HitWall (Projectile.as). */
    protected open fun hitWall(force: Boolean) {
        destroy()
    }

    protected abstract fun hitFighter(candidate: HitCandidate)

    protected fun destroy() {
        active = false
        val index = worldObj.projectiles.indexOf(this)
        if (index >= 0) {
            worldObj.projectiles.removeAt(index)
        }
    }

    protected fun targetOf(candidate: HitCandidate): Fighter = candidate.obj as Fighter
}
