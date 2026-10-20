package com.glorton.engine.fighter

import com.glorton.engine.GameConfig
import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.world.Body
import com.glorton.engine.world.CollisionResult
import com.glorton.engine.world.HitType
import com.glorton.engine.world.World
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port of class Fighter (Fighter.as) - the core fighter of "The Fight for Glorton".
 *
 * Mapping notes (AS2 MovieClip -> plain class):
 *  - AS2 `PlayNum` is 1-based; [playNum] is the 0-based player index
 *    (`playNum = PlayNum - 1`).
 *  - AS2 keeps two parallel things: the `State` string and the timeline frame
 *    (`gotoAndStop(label)`). They map to [state] and [animation] respectively; both
 *    use [FighterState].
 *  - AS2 `_xscale = +/-100` mirrors -> [facingRight] / [facingSign]; x offsets and
 *    throw angles are mirrored with them.
 *  - Body AABB: feet-anchored origin (Flash registration point at the feet, see the
 *    landing code `_y = Floor`), width 44 x height 64 world units.
 *  - Animation gating: the SWF runs at 30 fps while logic ticks at
 *    [GameConfig.TICK_MS] ms. AS2 `this[attack]._currentframe` / `_totalframes`
 *    conditions are reproduced with the [attackTimeMs] accumulator: `_currentframe > N`
 *    becomes `attackTimeMs > N * MS_PER_ANIM_FRAME`, `_currentframe == _totalframes`
 *    becomes `attackTimeMs >= attackTotalMs()` (durations in [attackDurationMs]).
 *  - `World.WorldMC._y` (stage clip offset) and MovieClip depth/z-order bookkeeping
 *    (`getDepth`, `swapDepths`, `OldDepth`) do not port: coordinates are unified in
 *    one space and rendering order belongs to the app layer.
 *
 * The class is abstract because AS2's polymorphic `ParseAttacks(InfObj)` is declared
 * as `parseAttacks` here (see the porting spec); every concrete fighter must override
 * it (DefaultPlayer does).
 */
abstract class Fighter(
    /** 0-based player index (AS2 field PlayNum is 1-based; this is PlayNum - 1). */
    val playNum: Int,
    /** Library linkage name of the fighter symbol, e.g. `"DefaultPlayer"`. */
    val fighterId: String,
    /** AS2 field `CharacterName` of the subclass, e.g. `"BallLock"`. */
    val characterName: String,
    /** Stats from the AS2 constructor arguments (`super(Weight, Speed, Power, Name)`). */
    val stats: FighterStats,
    /** Sink for the AS2 `Snd` class; sound name strings are kept verbatim. */
    val sound: SoundSink,
) : Body, ThrowSource {

    /** AS2 field `Weight`; exposed through [ThrowSource] for the Throw formula. */
    override val weight: Float get() = stats.weight

    // ------------------------------------------------------------------ position
    /** Feet-anchored world position (AS2 `_x`). */
    var x: Float = 0f

    /** Feet-anchored world position (AS2 `_y`). */
    var y: Float = 0f

    /** Horizontal velocity per logic tick (AS2 field `xInc`). */
    var xInc: Float = 0f

    /** Vertical velocity per logic tick (AS2 field `yInc`). */
    var yInc: Float = 0f

    // ------------------------------------------------------------------ flags
    /** Port of AS2 field `onGround` (undefined at construction -> false). */
    var onGround: Boolean = false

    /** Port of AS2 field `HasControl`. */
    var hasControl: Boolean = true

    /**
     * Port of AS2 field `Dead`. NOTE: Fighter.Die sets this to true even when it
     * respawns (Spawn(true)) - in the original the flag belongs to the match/results
     * bookkeeping, not to the tick loop. The engine must keep calling [doFighter]
     * while a fighter is respawning.
     */
    var dead: Boolean = false

    /** Port of AS2 field `Invincible` (set while spawn protection is active). */
    var invincible: Boolean = false

    /** Port of AS2 field `Blinking` - the fighter flashes while spawn protection lasts. */
    var blinking: Boolean = false

    /** Port of AS2 field `Shielded` (shield/dodge stance). */
    var shielded: Boolean = false

    /** Port of AS2 field `Paralized` - paralysis countdown in milliseconds (AS2 units). */
    var paralyzedMs: Float = 0f

    /**
     * Port of AS2 field `Lives`. -1 = unlimited (time mode), N = stock lives.
     * The AS2 constructor starts it at 0; the engine sets it per match mode.
     */
    var lives: Int = 0

    /** Port of AS2 field `DamageAmnt` - damage percentage (0..999). */
    var damagePct: Float = 0f

    /** Port of AS2 field `Deaths`. */
    var deaths: Int = 0

    /** Port of AS2 field `KOs` - eliminations credited to the last attacker. */
    var kos: Int = 0

    /**
     * Port of AS2 field `DeathOrd` (`_parent.NumDead++` in Fighter.Die) - order of
     * death within the match. Use [resetDeathOrder] at match start.
     */
    var deathOrd: Int = 0

    /** Port of AS2 field `SDs` - deaths without a last attacker (self destructs). */
    var sds: Int = 0

    // ------------------------------------------------------------------ items
    /** Port of AS2 field `CurrentItem` - library name of the carried item, "" if none. */
    var currentItem: String = ""

    /** Port of AS2 field `CurrentItemObj` - the carried Item instance. */
    var currentItemObj: Item? = null

    // ------------------------------------------------------------------ match/UI data
    /** Team tag (`"red"`, `"blue"`, ...); assigned by the engine (not present in Fighter.as). */
    var team: String = "red"

    /** Port of AS2 field `Colr` - color label of this player's shield/HUD accents. */
    var colr: String = "red"

    /** Port of AS2 `_visible`. */
    var visible: Boolean = true

    /** Port of AS2 field `CamIn` - index in the camera focus list; managed by the engine. */
    var camIn: Int = 0

    /** Port of AS2 field `CurrentAttack` - "" or the attack label, e.g. `"punchGround"`. */
    var currentAttack: String = ""

    /**
     * Port of AS2 field `State` (see [FighterState]) - the state-machine value.
     * Use [animation] for the timeline pose the AS2 code would `gotoAndStop` to.
     */
    var state: FighterState = FighterState.STOP

    /**
     * The AS2 timeline label currently shown (`gotoAndStop(label)`), separate from
     * [state] exactly as in Fighter.as. Updated by [animate], [animateAttack], [spawn].
     */
    var animation: FighterState = FighterState.STILL

    /** The active world; assigned by the engine before ticking (AS2 field `WorldObj`). */
    lateinit var world: World

    // ------------------------------------------------------------------ extra ports (AS2 fields read by subclasses / renderer)
    /** Port of AS2 field `AttckDne` - false while an attack has not connected yet. */
    var attackDone: Boolean = true

    /** Port of AS2 field `Combo` - hit-combo counter used by Damage/Throw formulas (starts at 2). */
    var combo: Int = 2

    /** Port of AS2 field `CtrlLoss` - input lockout countdown in milliseconds. */
    var ctrlLossMs: Float = 0f

    /** Port of AS2 field `SpecUpOk` - upward special allowed (set true on landing; AS2 starts undefined = false). */
    var specUpOk: Boolean = false

    /** Port of AS2 field `Modifiers.Electrocuted` - victim was hit by an electric special. */
    var electrocuted: Boolean = false

    /** Port of AS2 field `ShieldSize` (0..100; drains while shielding, regenerates otherwise). */
    var shieldSize: Float = 100f

    /** Port of AS2 field `Dying` - set by the engine (explosions/boom), sinks and dies. */
    var dying: Boolean = false

    /** Port of AS2 field `Gravity` - gravity/jump multiplier (always 1 in the AS2 sources). */
    var gravityRatio: Float = 1f

    /** Port of `_root.GameOn` (read as `this._parent.GameOn` in Fighter.DoFighter); default true so the engine can tick unconditionally. */
    var gameOn: Boolean = true

    /** Port of `_parent.Game.LimitMode` read inside Fighter.Die ("stock" or "time"). The engine sets it at match start; [die] also updates it. */
    var limitMode: String = "stock"

    /**
     * AS2 `DeathType` argument of Fighter.Die (`"bot"`, `"top"`, `"lef"`, `"rig"`,
     * `"beh"` for the internal deaths). Our [die] takes the limit mode instead, so
     * internal callers store the cause here for the HUD/results layer.
     */
    var lastDeathCause: String = ""

    /** Port of AS2 field `LastSender` - the fighter that last damaged/threw this one. */
    var lastSender: Fighter? = null

    /** Port of AS2 `Blinky` color offsets (renderer applies `cos(BlinkyCos) * 100` flashes). */
    var blinkOffset: Float = 0f

    /** Port of AS2 `_alpha` (0..100); spawn fades it from 1 back to 100. */
    var alpha: Float = 100f

    /** Port of AS2 `_yscale`/`_xscale` magnitude (dying shrinks to 40%, Die restores 100). */
    var scalePct: Float = 100f

    /** Port of AS2 field `IsOB` - fighter left the camera bounds (off-screen indicator flag). */
    var isOb: Boolean = false

    /**
     * Effective visibility of the AS2 `Shield` MovieClip
     * (`Shield._visible = Shielded` and hidden again while moving).
     */
    val shieldVisible: Boolean
        get() = shielded && xInc == 0f

    /** True while the AS2 `this.dodge` child clip exists (fighter timeline on the "dodge" label). */
    val dodging: Boolean
        get() = animation == FighterState.DODGE

    /** Mirrors AS2 `_xscale > 0`; true = facing right. Updated by [animate] like the original. */
    val facingRight: Boolean
        get() = facingFlag

    /** AS2 `_xscale / 100` - +1 facing right, -1 facing left; throw angles are multiplied by it. */
    val facingSign: Float
        get() = if (facingFlag) 1f else -1f

    // ------------------------------------------------------------------ internals
    private var facingFlag: Boolean = true
    private var jumpState: Int = 0
    private var moveQueue: String = ""
    private var lastPf: PointF? = null
    private var blinkCos: Float = 0f
    private var timeKoMs: Float = 0f
    private var hitTesterX: Float = 0f
    private var hitTesterY: Float = 0f
    private var oldXMov: Float? = null
    private var oldXN: String? = null
    private var goThrough: Boolean = false
    private var obCalled: Boolean = false
    private var spawning: Boolean = false
    private var spawnInvMs: Float = 0f
    private var spawnAnimMs: Float = 0f
    private var spawnType: Int = 1

    /**
     * Port of AS2 `attack._currentframe` timing (see the class KDoc). Advanced by
     * [GameConfig.TICK_MS] every logic tick while an attack is active; reset by
     * [animateAttack] / the back-throw conversion in DefaultPlayer.
     */
    protected var attackTimeMs: Float = 0f

    /** Time the "dodge" label has been active (AS2 `dodge._currentframe`). */
    private var dodgeTimeMs: Float = 0f

    /**
     * AABB approximation of the fighter (see class KDoc): feet-anchored origin,
     * width [BODY_WIDTH] x height [BODY_HEIGHT].
     */
    override val bodyRect: RectF
        get() = RectF(x - BODY_WIDTH / 2f, y - BODY_HEIGHT, x + BODY_WIDTH / 2f, y)

    /** The AS2 `HitTester` duplicate MovieClip: same AABB, offset by one velocity step during DoFighter. */
    private val hitTester: Body = object : Body {
        override val bodyRect: RectF
            get() = RectF(
                hitTesterX - BODY_WIDTH / 2f,
                hitTesterY - BODY_HEIGHT,
                hitTesterX + BODY_WIDTH / 2f,
                hitTesterY,
            )
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Placement/reset entry point (the AS2 `init(_WorldObj)` only wired the world and
     * the HitTester; positions came from the stage). Resets everything the AS2
     * constructor initializes plus the spawn position, so a fighter can be reused for
     * a new match. Call it first, then configure [lives], [team], [colr], [limitMode],
     * [gameOn] and assign [world].
     */
    fun init(spawnX: Float, spawnY: Float) {
        x = spawnX
        y = spawnY
        hitTesterX = spawnX
        hitTesterY = spawnY
        xInc = 0f
        yInc = 0f
        onGround = true
        hasControl = true
        dead = false
        dying = false
        invincible = false
        blinking = false
        shielded = false
        shieldSize = 100f
        paralyzedMs = 0f
        ctrlLossMs = 0f
        timeKoMs = 0f
        lives = 0
        damagePct = 0f
        deaths = 0
        sds = 0
        kos = 0
        deathOrd = 0
        combo = 2
        currentItem = ""
        currentItemObj = null
        state = FighterState.STOP
        animation = FighterState.STILL
        currentAttack = ""
        attackTimeMs = 0f
        attackDone = true
        moveQueue = ""
        jumpState = 0
        specUpOk = false
        electrocuted = false
        lastSender = null
        lastPf = null
        facingFlag = true
        scalePct = 100f
        alpha = 100f
        visible = true
        isOb = false
        obCalled = false
        spawning = false
        spawnInvMs = 0f
        spawnAnimMs = 0f
        spawnType = 1
        blinkCos = 0f
        blinkOffset = 0f
        oldXMov = null
        oldXN = null
        goThrough = false
    }

    // ------------------------------------------------------------------ input actions

    /**
     * Port of Fighter.Move (Fighter.as).
     *
     * @param dir "left", "right", "stop" or "down". Movement while a ground attack is
     *   active gets queued in `MoveQueue` and is replayed when the attack ends.
     */
    fun move(dir: String) {
        if (!hasControl || ctrlLossMs > 0f) return
        if (state == FighterState.KO && timeKoMs < 1000f * damagePct / 100f) return
        if (currentAttack != "" && onGround && dir != "stop") {
            moveQueue = dir
            return
        }
        if (currentAttack != "" && dir == "stop") {
            moveQueue = "stop"
            return
        }
        when (dir) {
            "right" -> {
                xInc = 8f * stats.speed
                state = FighterState.GO_RIGHT
            }
            "left" -> {
                xInc = -8f * stats.speed
                state = FighterState.GO_LEFT
            }
            "stop" -> {
                if (dodging && shielded) return
                if (onGround) xInc = 0f
                state = FighterState.STOP
            }
            "down" -> {
                if (onGround) {
                    xInc = 0f
                    state = FighterState.CROUCH
                } else {
                    yInc = 8f // fast fall; Gravitron keeps 8 uncapped, exactly as AS2
                }
            }
        }
    }

    /**
     * Port of Fighter.Jump (Fighter.as). Two-stage jump: the first press launches
     * (`yInc = -9 * Gravity`), the second press re-launches while still rising fast
     * enough (`jumpstate == 1 && yInc >= -3`).
     */
    fun jump() {
        if (!hasControl || ctrlLossMs > 0f || state == FighterState.KO || shielded) return
        if (currentAttack != "") return
        oldXMov = null
        goThrough = false
        if (state == FighterState.THROWN) state = FighterState.STOP
        if (jumpState == 0) {
            jumpState = 1
            yInc = -9f * gravityRatio
            // AS2 spawned a "Puff" MovieClip at the feet (visual only, omitted).
        } else if (jumpState == 1 && yInc >= -3f) {
            jumpState = 2
            yInc = -9f * gravityRatio
            // AS2 spawned a second "Puff" (visual only, omitted).
        }
    }

    /**
     * Port of Fighter.Attack (Fighter.as).
     *
     * @param act1 "punch" or "special".
     * @param act2 "none" or "up".
     */
    fun attack(act1: String, act2: String) {
        // AS2: get-up attack from the ko state once TimeKo passed 1000 * Damage / 100.
        if (state == FighterState.KO && timeKoMs > 1000f * damagePct / 100f) {
            state = FighterState.STOP
            animateAttack(FighterState.KO_ATTACK)
            return
        }
        if (currentAttack != "" || !hasControl || ctrlLossMs > 0f ||
            state == FighterState.THROWN || dying || shielded
        ) {
            return
        }
        when (act1) {
            "punch" -> when (act2) {
                "none" -> if (onGround) {
                    if (abs(xInc) <= 0f) {
                        animateAttack(FighterState.PUNCH_GROUND)
                    } else {
                        animateAttack(FighterState.PUNCH_RUN)
                        // AS2 attached a "Puff" MovieClip at the feet (visual only, omitted).
                    }
                } else {
                    animateAttack(FighterState.PUNCH_AIR)
                }
                "up" -> if (onGround && abs(xInc) <= 0f) {
                    animateAttack(FighterState.PUNCH_UP)
                }
            }
            "special" -> when (act2) {
                "none" -> {
                    if (onGround) {
                        state = FighterState.STOP
                        xInc = 0f
                        animateAttack(FighterState.SPECIAL_GROUND)
                    }
                    if (!onGround) {
                        animateAttack(FighterState.SPECIAL_AIR)
                    }
                }
                "up" -> {
                    if (!specUpOk) return
                    animateAttack(FighterState.SPECIAL_UP)
                    specUpOk = false
                }
            }
        }
    }

    /**
     * Port of Fighter.AnimateAttack (Fighter.as): starts the attack clip child
     * (`this[Which].play()` -> reset [attackTimeMs]) and jumps the fighter timeline
     * to the attack label. Subclasses override this (DefaultPlayer nudges `yInc`
     * for `punchAir`); if a carried item's `use` consumes the punch, the AS2 code
     * jumps the timeline back to the previous label while `CurrentAttack` stays set.
     */
    protected open fun animateAttack(which: FighterState) {
        currentAttack = which.label
        attackDone = false
        val previousAnimation = animation
        animation = which
        attackTimeMs = 0f
        if (currentItem != "" && which.label.startsWith("punch")) {
            if (currentItemObj?.use(which.label) == true) {
                animation = previousAnimation // AS2: gotoAndStop(_loc2_)
            }
        }
    }

    /**
     * Port of Fighter.Damage (Fighter.as). Exact AS2 formula:
     * `DamageAmnt += floor(Amnt * (Combo / 2))`, then `Combo++` when a
     * [lastSender] exists. At 1000+ the damage clamps to 999 and the fighter
     * self-throws with `Throw(30, 45, Sender)`.
     *
     * @param fromExplosion maps to the AS2 `Force` parameter (explosions call
     *   `Damage(Size * 5, Sender, true)`): bypasses the ko-stun guard and the shield.
     */
    fun damage(amount: Float, sender: Fighter?, fromExplosion: Boolean = false) {
        if ((state == FighterState.KO && !fromExplosion) ||
            state == FighterState.SPAWN ||
            dying ||
            (shielded && dodging) ||
            invincible
        ) {
            return
        }
        if (shielded) {
            if (shieldSize > 0f) {
                shieldSize -= amount / 5f
                return
            }
            shielded = false
        }
        if (sender != null) lastSender = sender
        currentAttack = ""
        attackDone = true
        specUpOk = true
        electrocuted = false
        sound.stop("Electric")
        damagePct += floor(amount * (combo / 2f))
        if (lastSender != null) combo += 1
        if (damagePct > 999f) {
            damagePct = 999f
            // AS2: this.Throw(30, 45, Sender) - deliberately WITHOUT the Force flag,
            // so this forced throw still obeys the ko/shield guards.
            doThrow(30f, 45f, sender)
        }
        // AS2: WorldObj.OSD.UpdateDamage(this) - HUD refresh, handled by the app layer.
        paralyzedMs = amount
        // AS2: Sender.Paralized = Amnt * 5 (the attacker is locked out too).
        // The AS2 code would throw if Sender were undefined; the port skips it.
        sender?.paralyzedMs = amount * 5f
        // AS2 swapped MovieClip depths here (getDepth/swapDepths) - renderer concern.
        if (sender != null && state != FighterState.ELECTROCUTED &&
            !sender.currentAttack.startsWith("special")
        ) {
            // AS2 attached a "PunchDamage" MovieClip scaled by Math.log(Amnt) * 50 at
            // Sender[CurrentAttack].hand - pure impact visual, omitted.
        }
    }

    /**
     * Port of Fighter.Throw (Fighter.as). Knockback launch; AS2 formula:
     * `Power + Power * Sender.Weight - Power * this.Weight + DamageAmnt/100 * ln(Power) * Power + Combo/4`
     * (with a substitute `{Weight: 1}` sender when none is given), then
     * `CtrlLoss = Power^2 * 5`. Sets the THROWN state.
     *
     * @param angleDeg degrees, mirrored by callers with `* facingSign`; negative
     *   angles are remapped to `180 + angle` exactly like AS2.
     * @param fromExplosion maps to the AS2 `Force` parameter (bypasses the ko guard
     *   and the "shielded always blocks" guard).
     */
    fun doThrow(
        power: Float,
        angleDeg: Float,
        sender: ThrowSource?,
        fromExplosion: Boolean = false,
    ) {
        if ((state == FighterState.KO && !fromExplosion) ||
            state == FighterState.SPAWN ||
            dying ||
            (shielded && dodging) ||
            invincible
        ) {
            return
        }
        if (shielded && !fromExplosion) return
        // AS2 quirk: Throw(2.5, 45 * dir, this) passes the EnergyBall itself as
        // Sender, which overwrites LastSender with a MovieClip and turns the
        // later kill credit into NaN. The port only upgrades LastSender when a
        // real Fighter is the source, keeping the shooter's credit.
        if (sender is Fighter) lastSender = sender
        var angle = angleDeg
        if (angle < 0f) angle = 180f + angle
        // AS2: when Sender is undefined the formula uses a stand-in object {Weight: 1}.
        val senderWeight = sender?.weight ?: 1f
        val rad = Math.toRadians(angle.toDouble())
        var newPower = power + power * senderWeight - power * stats.weight +
            damagePct / 100f * ln(power) * power + combo / 4f
        if (newPower < 0f) newPower = 0f
        xInc = cos(rad).toFloat() * newPower
        yInc = -sin(rad).toFloat() * newPower
        ctrlLossMs = newPower * newPower * 5f
        if (newPower > 10f) sound.play("Thrown")
        if (newPower > 10f && ctrlLossMs > 100f) {
            lastPf = PointF(x, y)
            if (newPower > 20f) {
                // AS2: _parent.VCam.Shake(500) - camera shake hook, app layer.
            }
        } else {
            lastPf = null
        }
        state = FighterState.THROWN
        animation = FighterState.THROWN
    }

    /**
     * Port of Fighter.Die (Fighter.as). The AS2 signature took a death cause
     * (`"bot"`, `"top"`, `"lef"`, `"rig"`, `"beh"`) forwarded to the OSD; here the
     * parameter is the limit mode, so internal callers store the cause in
     * [lastDeathCause] first.
     *
     * Endurance mode (`Game.Type == "endurance"`) is not portable and only the
     * `"stock"` / `"time"` modes are implemented. Camera focus, results snapshots and
     * `PlayerDeath()` callbacks belong to the app layer; the exposed counters
     * ([deaths], [kos], [sds], [lives], [deathOrd]) carry the data they consume.
     */
    fun die(limitMode: String) {
        this.limitMode = limitMode
        // AS2: WorldObj.OSD.Die(this, DeathType, LastSender) - HUD, omitted.
        val killer = lastSender
        if (killer != null) killer.kos += 1 else sds += 1
        // AS2 removed the off-screen display clip + indicator and touched VCam.FocusObjs (UI/camera).
        if (limitMode == "stock") lives -= 1 // AS2 also decremented for endurance type
        damagePct = 0f
        // AS2: WorldObj.OSD.UpdateDamage(this) - HUD, omitted.
        y = 0f
        x = (Random.nextInt(400) + 100).toFloat() // AS2: random(400) + 100
        facingFlag = true // AS2: _xscale = 100
        scalePct = 100f // AS2: _yscale = 100
        xInc = 0f
        yInc = 0f
        state = FighterState.STOP
        hasControl = true
        dying = false
        alpha = 100f // AS2: _alpha = 100
        // AS2: swapDepths(OldDepth) - z-order, renderer concern.
        ctrlLossMs = 0f
        paralyzedMs = 0f
        deaths += 1
        if (lives > 0 || limitMode == "time") {
            spawn(mkSpawn = true)
            // AS2: _parent.PlayerDeath() - first call, before Dead is set.
        }
        // AS2: VCam.FocusObjs.splice(CamIn, 1) - camera, omitted.
        dead = true
        visible = false
        hasControl = false
        deathOrd = deathOrderCounter++ // AS2: DeathOrd = _parent.NumDead++
        // AS2 replaced _parent.Players[PlayNum] with a results snapshot
        // {Deaths, SDs, KOs, Lives, DeathOrd, Dead, Depth} and called PlayerDeath()
        // again - match bookkeeping belongs to the app layer.
    }

    /**
     * Hook for subclasses: creates a projectile in the world. The base Fighter never
     * spawns projectiles (AS2 Fighter.as has none); DefaultPlayer routes its
     * EnergyBall through this so the engine can wire the concrete Projectile classes.
     *
     * @param kind library link name, e.g. `"EnergyBall"`.
     * @param xScalePct AS2 `_xscale` (sign mirrors the facing, `-50..50`).
     * @param yScalePct AS2 `_yscale`.
     */
    open fun spawnProjectile(
        kind: String,
        x: Float,
        y: Float,
        xScalePct: Float,
        yScalePct: Float,
        xInc: Float = 0f,
        yInc: Float = 0f,
        rotDeg: Float = 0f,
    ) {
        // Default: no-op (AS2 base class spawns nothing).
    }

    // ------------------------------------------------------------------ tick

    /**
     * Port of Fighter.DoFighter (Fighter.as): one 25 ms logic tick - shield upkeep,
     * paralysis, physics against the [World] platforms, out-of-bounds checks, attack
     * hit detection ([parseAttacks] / [parseSpecialAttacks]), animation and spawn
     * upkeep. The engine calls it once per [GameConfig.TICK_MS].
     */
    fun doFighter() {
        if (!gameOn) return

        // AS2 animation clips advance on the SWF clock; the port advances their
        // equivalents here so they tick regardless of which branch runs below.
        if (currentAttack != "") attackTimeMs += GameConfig.TICK_MS
        if (animation == FighterState.DODGE) dodgeTimeMs += GameConfig.TICK_MS
        if (spawning) spawnAnimMs += GameConfig.TICK_MS

        // AS2: Shield MovieClip visible/scaled by ShieldSize (data only, renderer draws it).
        if (shieldSize > 100f) {
            shieldSize = 100f
        } else if (shieldSize < 0f) {
            shieldSize = 0f
            shielded = false
        }
        if (shielded) {
            // AS2 hides the shield while xInc != 0 (see shieldVisible) and drains it.
            shieldSize -= 0.2f
        } else {
            shieldSize += 0.1f
            if (dodging) {
                xInc = 0f
                state = FighterState.STOP
            }
        }

        blinker()

        if (paralyzedMs > 0f) {
            paralyzedMs -= GameConfig.TICK_MS
            animate()
            return
        }
        if (ctrlLossMs > 0f) ctrlLossMs -= GameConfig.TICK_MS

        // AS2 moved the HitTester duplicate one velocity step ahead before probing.
        hitTesterX += xInc
        hitTesterY += yInc

        if (dying) {
            hasControl = false
            scalePct = 40f // AS2: _xscale = _yscale = 40
            alpha = 100f
            gravitron()
            if (y > 30f) {
                lastDeathCause = "beh"
                die(limitMode)
            }
            return
        }

        if (state == FighterState.THROWN) {
            val pf = lastPf
            if (pf != null && ctrlLossMs > 0f) {
                val dx = pf.x - x
                val dy = pf.y - y
                if (dx * dx + dy * dy > 400f) {
                    // AS2 spawned a "Puff" trail MovieClip every 20 px (visual, omitted).
                    lastPf = PointF(x, y)
                }
            }
            // AS2: HitTester.gotoAndStop("thrown") - the tester's hit shape changes with
            // the timeline; the port keeps one fixed AABB (assumption, see class KDoc).
        }

        if (state == FighterState.STOP) {
            if (!onGround) xInc *= 0.7f else xInc = 0f
        }
        if (xInc == 0f && jumpState > 0 && onGround) state = FighterState.STOP
        if (abs(xInc) < 0.5f) xInc = 0f

        if (state == FighterState.KO) {
            if (timeKoMs > damagePct) {
                state = FighterState.STOP
                hasControl = true
            }
            timeKoMs += GameConfig.TICK_MS
        }

        // AS2 `_loc7_`: moving platforms are excluded while a thrown fighter rises.
        val movingC = !(state == FighterState.THROWN && yInc < 0f)
        // (AS2 had an empty `if (this.OldXMov != undefined) {}` block here - dead code.)
        val collision: CollisionResult = world.collisionDetect(
            caster = hitTester,
            basic = false,
            platformsEnabled = true,
            helpers = false,
            movingC = movingC,
            lowRes = false,
        )
        val hit = collision.hit

        if (!hit || goThrough) {
            x += xInc
            gravitron()
            if (goThrough) {
                yInc = 5f
                xInc = 0f
            }
            onGround = false
            if (!hit) goThrough = false
        } else {
            resolveCollision(collision)
        }

        checkBounds()

        if (currentAttack != "" || state == FighterState.THROWN) {
            if (currentAttack.startsWith("special")) {
                // AS2 called `ParseSAttacks(_loc4_[ok])` here, before `_loc4_` was
                // filled - an empty-array lookup the subclass never reads. The port
                // calls the hook directly with no argument (DefaultPlayer never used it).
                parseSpecialAttacks()
            }
            val targets = world.collisionDetectAttack(this, includeItems = true)
            for (candidate in targets) {
                if (state == FighterState.THROWN && THROWN_BOUNCE_ENABLED &&
                    candidate.type == HitType.PLAYER
                ) {
                    // AS2 condition was `_loc4_.type == "player"`, but World.as only
                    // sets `objType` - this branch never ran in the original game.
                    // Gated behind the flag so the integrator can enable the intended
                    // behavior; disabled = faithful to the shipped game.
                    xInc = -xInc
                    x += xInc
                    val other = candidate.obj as Fighter
                    other.xInc = -other.xInc
                    other.x += other.xInc
                    return
                }
                parseAttacks(AttackCandidate(candidate.obj, candidate.type))
            }
        }

        animate()
        spawn(mkSpawn = false)
        // AS2 stored getDepth() in OldDepth each tick (z-order bookkeeping, renderer).
        hitTesterX = x
        hitTesterY = y
    }

    /**
     * Port of the flag-by-flag collision response block inside Fighter.DoFighter
     * (Fighter.as), executed when the HitTester probe reported `Hit`. Conditions are
     * reproduced statement-for-statement, including the AS2 precedence of `^` vs `&&`.
     */
    private fun resolveCollision(collision: CollisionResult) {
        if ((collision.leftBc xor collision.rightBc) && onGround && collision.bottom &&
            !collision.left && !collision.right
        ) {
            if (shielded && dodging) {
                x -= xInc
                xInc = 0f
            }
        }

        val landing =
            ((collision.bottom || collision.leftBc || collision.rightBc) && !goThrough &&
                yInc >= 0f && !collision.left && !collision.right) ||
                (collision.bottom &&
                    ((collision.left && collision.rightBc) || (collision.right && collision.leftBc)))
        if (landing) {
            if (state == FighterState.THROWN) {
                sound.play("HitGround")
                state = FighterState.KO
                timeKoMs = 0f
                xInc = 0f
                hasControl = false
            }
            if (!collision.right && !collision.left) x += xInc
            // AS2: _y = Floor + World.WorldMC._y (stage clip offset is 0 in this port).
            y = collision.floor ?: y
            hitTesterY = y
            jumpState = 0
            yInc = 0f
            onGround = true
            specUpOk = true
            lastSender = null
            combo = 2
            if (collision.moving && state == FighterState.CROUCH) goThrough = true
        }
        if (collision.bottom && yInc < 0f && collision.moving) {
            x += xInc
            gravitron()
        }
        if (collision.top && collision.moving) {
            x += xInc
            gravitron()
        }
        if (collision.bottom && !collision.moving && !collision.left && !collision.right && !onGround) {
            x += xInc
            y += yInc
        }
        if (!collision.leftBc && !collision.rightBc && (collision.left || collision.right) && !onGround) {
            bounceThrown()
            gravitron()
        }
        if ((collision.leftBc || collision.rightBc) && onGround &&
            !collision.bottom && !collision.left && !collision.right
        ) {
            if (!(shielded && dodging)) x += xInc
        }
        if ((collision.leftBc || collision.rightBc) && !onGround &&
            !collision.left && !collision.right && yInc != 0f
        ) {
            bounceThrown()
            gravitron()
        } else if ((collision.leftBc && collision.left || collision.rightBc && collision.right) &&
            !collision.bottom
        ) {
            bounceThrown()
            if (collision.moving) x += xInc
            gravitron()
        }
        if ((collision.leftBc || collision.rightBc) && !onGround &&
            !collision.left && !collision.right && !collision.bottom
        ) {
            bounceThrown()
            gravitron()
        }
        if (collision.left && collision.right && collision.moving) x += xInc
        if (collision.top && !collision.moving) {
            x += xInc
            yInc = 0f
            // AS2: _y = Floor + H + _height + 5 + World.WorldMC._y. With the
            // feet-anchored AABB this places the body top 5 px under the ceiling.
            y = (collision.floor ?: y) + (collision.platformHeight ?: 0f) + BODY_HEIGHT + 5f
            hitTesterY = y
        }
        if (collision.top && collision.moving) gravitron()
        if (!collision.bottom && !(collision.leftBc && collision.rightBc) && yInc != 0f) {
            onGround = false
        }
        if (onGround && collision.moving) {
            val platform = collision.platform
            if (platform != null) {
                // AS2 carried the fighter with the moving platform: only if the same
                // platform was tracked last tick, its shift was < 10 and != 0.
                val previousX = oldXN?.let { name ->
                    world.platforms.firstOrNull { it.name == name }
                }?.x
                val delta = (previousX ?: Float.NaN) - (oldXMov ?: Float.NaN)
                if (delta < 10f && delta != 0f && oldXN == platform.name) {
                    val recorded = oldXMov
                    if (recorded != null) x += platform.x - recorded
                }
                oldXMov = platform.x
                oldXN = platform.name
            }
        }
    }

    /** AS2 repeated `_xscale *= -1; xInc *= -1` for thrown fighters touching walls. */
    private fun bounceThrown() {
        if (state == FighterState.THROWN) {
            facingFlag = !facingFlag
            xInc = -xInc
        }
    }

    /**
     * Port of Fighter.Animate (Fighter.as). Mirrors the AS2 statement order exactly,
     * including the quirk where the locomotion branches at the end overwrite the
     * hurt poses set earlier in the same call (that is why DefaultPlayer sets
     * `State = "electrocuted"` and why throws keep the electrocuted pose: the
     * THROWN early-return runs before the locomotion branches).
     */
    private fun animate() {
        // AS2 _xscale handling: thrown fighters invert the velocity-derived facing.
        if (xInc > 0f) {
            facingFlag = state != FighterState.THROWN
        } else if (xInc < 0f) {
            facingFlag = state == FighterState.THROWN
        }

        if (paralyzedMs > 0f && currentAttack == "") {
            if (!electrocuted && animation != FighterState.TAKING_HIT) {
                animation = FighterState.TAKING_HIT // AS2: frame 10
            }
            if (electrocuted && animation != FighterState.ELECTROCUTED) {
                animation = FighterState.ELECTROCUTED // AS2: frame 12
            }
        }
        if (state == FighterState.THROWN && paralyzedMs <= 0f &&
            animation != FighterState.THROWN
        ) {
            animation = FighterState.THROWN // AS2: frame 7
            return
        }
        if (state == FighterState.THROWN) return
        if (state == FighterState.KO) animation = FighterState.KO

        if (currentAttack != "") {
            // AS2: if (this[attack]._currentframe != this[attack]._totalframes) return;
            if (attackTimeMs < attackTotalMs()) return
            currentAttack = ""
            hasControl = true
            if (moveQueue != "") {
                val queued = moveQueue
                moveQueue = ""
                move(queued)
            }
            if (jumpState > 0) {
                jumpState = 3
            }
            // AS2 executed `this[this.CurrentAttack].gotoAndStop(1)` with
            // CurrentAttack already cleared (a no-op/error in the original) - skipped.
            attackDone = true
        }

        if (onGround) {
            when {
                state == FighterState.GO_RIGHT || state == FighterState.GO_LEFT -> {
                    if (shielded) {
                        if (animation != FighterState.DODGE) {
                            animation = FighterState.DODGE
                            dodgeTimeMs = 0f
                        } else if (dodgeTimeMs >= DODGE_DURATION_MS) {
                            xInc = 0f
                            state = FighterState.STOP
                        }
                    } else {
                        animation = FighterState.RUN
                    }
                }
                state == FighterState.CROUCH -> {
                    if (animation != FighterState.CROUCH) animation = FighterState.CROUCH // frame 5
                }
                jumpState > 0 -> {
                    if (animation != FighterState.RUN) animation = FighterState.RUN // frame 2
                }
                state == FighterState.STOP -> animation = FighterState.STILL
            }
        } else {
            if (jumpState == 1 && animation != FighterState.JUMP1) {
                animation = FighterState.JUMP1 // frame 3
            } else if (jumpState == 2 && animation != FighterState.JUMP2) {
                animation = FighterState.JUMP2 // frame 4
            } else if ((jumpState == 3 && animation != FighterState.FALLING) ||
                (animation != FighterState.JUMP1 && animation != FighterState.JUMP2)
            ) {
                animation = FighterState.FALLING // frame 15
            }
        }
    }

    /**
     * Port of Fighter.Gravitron (Fighter.as): applies position + gravity.
     * AS2 fighters accelerate at `0.5 * Gravity` per tick (note: [GameConfig.GRAVITY]
     * = 0.6 is the Item value from Item.as; Fighter.as uses 0.5) and cap at
     * [GameConfig.MAX_FALL_SPEED]; the fast-fall value 8 bypasses the cap, and a
     * dying fighter sinks at a constant 2.
     */
    private fun gravitron() {
        y += yInc
        if (yInc < GameConfig.MAX_FALL_SPEED) {
            yInc = if (!dying) {
                yInc + 0.5f * gravityRatio
            } else {
                2f
            }
        } else {
            if (yInc == 8f) return // AS2 fast fall from Move("down") stays uncapped
            yInc = GameConfig.MAX_FALL_SPEED
        }
    }

    /** Port of Fighter.Blinker (Fighter.as): color-flash offset for ko/spawn blinking. */
    private fun blinker() {
        if (state == FighterState.KO || blinking) {
            blinkCos += 1f
            blinkOffset = cos(blinkCos) * 100f
        } else {
            blinkOffset = 0f
        }
        // AS2 applied the color transform to the MovieClip here - renderer reads blinkOffset.
    }

    /** Port of Fighter.GoOutBounds (Fighter.as): off-camera flag, HUD marker omitted. */
    private fun goOutBounds() {
        if (!obCalled) {
            // AS2: WorldObj.OSD.OutBounds(this) - off-screen indicator, app layer.
            obCalled = true
            isOb = true
        }
    }

    /** Port of Fighter.CheckBounds (Fighter.as). */
    private fun checkBounds() {
        if (!world.camBounds.contains(x, y)) {
            goOutBounds()
        } else {
            visible = true
            isOb = false
            obCalled = false
        }
        if (!world.bounds.contains(x, y)) {
            val edge = when {
                y > world.bounds.bottom -> "bot"
                y < world.bounds.top -> "top"
                x < world.bounds.left -> "lef"
                x > world.bounds.right -> "rig"
                else -> ""
            }
            lastDeathCause = edge // AS2 passed this to OSD.Die as the death cause
            die(limitMode) // AS2: Die(edge); the limit mode comes from Game.LimitMode
        }
    }

    /**
     * Port of Fighter.Spawn (Fighter.as). AS2 attaches a "Spawn1"/"Spawn2" MovieClip
     * at the fighter (it never moves, so position tracking is a no-op here), grants
     * 3000 ms of invulnerability and fades the fighter back in.
     */
    private fun spawn(mkSpawn: Boolean) {
        if (state != FighterState.SPAWN && mkSpawn) {
            visible = false
            alpha = 1f
            spawnType = Random.nextInt(2) + 1 // AS2: random(2) + 1
            state = FighterState.SPAWN
            hasControl = false
            blinkOffset = 255f
            jumpState = 0
            onGround = true
            spawnInvMs = 3000f
            invincible = true
            spawning = true
            spawnAnimMs = 0f
            animation = FighterState.STILL
        }
        if (spawnInvMs > 0f) spawnInvMs -= GameConfig.TICK_MS
        if (state == FighterState.SPAWN) {
            if (spawnInvMs <= 0f) move("stop")
            animation = FighterState.STILL
            if (spawnAnimMs >= 10f * spawnType * GameConfig.MS_PER_ANIM_FRAME) {
                visible = true
                if (blinkOffset > 5f) {
                    blinkOffset /= 2f
                    alpha = if (alpha < 100f) alpha * 4f else 100f
                } else {
                    blinking = true
                    hasControl = true
                }
            }
        }
        if (state != FighterState.SPAWN && spawning) {
            // AS2 removes the Spawn MovieClip here.
            if (spawnInvMs <= 0f) {
                spawning = false
                invincible = false
                blinking = false
            }
        }
    }

    // ------------------------------------------------------------------ subclass hooks

    /**
     * Port of Fighter.ParseAttacks (Fighter.as): processes one hit candidate while
     * an attack (or the thrown state) is active. The AS2 base implementation is
     * empty; declared abstract per the porting spec so every fighter decides its
     * damage/throw numbers (DefaultPlayer implements them).
     */
    protected abstract fun parseAttacks(candidate: AttackCandidate)

    /**
     * Port of Fighter.ParseSAttacks (Fighter.as): per-tick special-attack upkeep
     * (spawning projectiles, carrying the back-throw victim, specialUp movement).
     * The AS2 base implementation is empty; DefaultPlayer overrides it.
     */
    protected open fun parseSpecialAttacks() {
        // AS2 base class: empty.
    }

    // ------------------------------------------------------------------ attack timing helpers

    /** AS2 `this[attack]._totalframes` for the active attack, in milliseconds. */
    protected fun attackTotalMs(): Float = attackDurationMs(currentAttack)

    /** AS2 `this[attack]._currentframe` of the active attack (0 at attack start). */
    protected fun attackFrame(): Float = attackTimeMs / GameConfig.MS_PER_ANIM_FRAME

    /** True while the active attack has reached `this[attack]._totalframes`. */
    protected fun attackFinished(): Boolean = attackTimeMs >= attackTotalMs()

    // ------------------------------------------------------------------ companion

    companion object {
        /** Fighter AABB width in world units (assumption; see class KDoc). */
        private const val BODY_WIDTH = 44f

        /** Fighter AABB height in world units (assumption; see class KDoc). */
        private const val BODY_HEIGHT = 64f

        /** AS2 Fighter.Animate: shield dash length (assumption, ~13.5 anim frames). */
        private const val DODGE_DURATION_MS = 450f

        /**
         * AS2 line 1054 condition (`.type`, which World.as never sets). Disabled =
         * faithful to the shipped game; set to true to enable the apparent intended
         * mid-air bounce of thrown fighters off other players.
         */
        private const val THROWN_BOUNCE_ENABLED = false

        private var deathOrderCounter = 0

        /** Resets the AS2 `_root.NumDead` counter; call at match start. */
        fun resetDeathOrder() {
            deathOrderCounter = 0
        }

        /**
         * Per-attack clip durations replacing the AS2 `_totalframes` values, which
         * live in the SWF timelines and are not recoverable from the decompiled text.
         * All values are multiples of [GameConfig.TICK_MS] so the
         * `attackTimeMs >= totalMs` end-of-attack test fires on an exact tick.
         * Frame counts are the equivalents at [GameConfig.SWF_FPS] (30 fps).
         * THIS TABLE IS AN ASSUMPTION - see the assumptions report.
         */
        fun attackDurationMs(attackLabel: String): Float = when (attackLabel) {
            "punchGround" -> 350f // ~10.5 anim frames
            "punchRun" -> 350f // ~10.5 anim frames
            "punchUp" -> 400f // ~12 anim frames
            "punchAir" -> 400f // ~12 anim frames
            "punchAirDown" -> 400f // ~12 anim frames (unused by DefaultPlayer)
            "koAttack" -> 400f // ~12 anim frames
            "specialGround" -> 1000f // ~30 anim frames (EnergyBall spawns on the last frame)
            "specialAir" -> 1000f // ~30 anim frames
            "specialUp" -> 800f // ~24 anim frames (hit window opens after frame 10)
            "specialBackThrow" -> 650f // ~19.5 anim frames (victim arc + release)
            else -> 400f // ~12 anim frames, safe default for unknown attacks
        }
    }
}
