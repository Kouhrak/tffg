package com.glorton.engine.fighter

import com.glorton.engine.GameConfig
import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.projectile.EnergyBall
import com.glorton.engine.world.HitType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of class DefaultPlayer (DefaultPlayer.as) - the electric fighter
 * (`CharacterName = "BallLock"`), concrete [Fighter].
 *
 * [fighterId] is a constructor parameter (default [FIGHTER_ID]) because GameFactory
 * passes the library link name per player spec; the AS2 subclass hardcodes its own.
 *
 * Faithful to the AS2 subclass:
 *  - stats from `super(0.5, 0.5, 0.5, "BallLock")`;
 *  - `AnimateAttack` gives `punchAir` a small downward nudge;
 *  - `ParseAttacks` handles the punch/ko/uppercut/electric/back-throw hit table,
 *    including the 20 px behind-the-back check that converts a ground punch into
 *    `specialBackThrow`;
 *  - `ParseSAttacks` carries the back-throw victim on an arc, spawns the EnergyBall
 *    at the end of `specialGround`/`specialAir` and drives the `specialUp` rise.
 *
 * The EnergyBall is created through [spawnProjectile] instead of
 * `attachMovie("EnergyBall", ...)` so the engine can wire the concrete projectile
 * class; the hand offsets below reproduce the AS2 formula
 * `x += attackClip._x + this._x + 5` / `y += attackClip._y + this._y - 7`.
 */
class DefaultPlayer(
    playNum: Int,
    /** Library link name; defaults to this class's own symbol ([FIGHTER_ID]). */
    fighterId: String = FIGHTER_ID,
    sound: SoundSink,
    team: String = "red",
    colr: String = "red",
) : Fighter(
    playNum = playNum,
    fighterId = fighterId,
    characterName = CHARACTER_NAME,
    stats = STATS,
    sound = sound,
) {
    init {
        this.team = team
        this.colr = colr
    }

    /** Port of DefaultPlayer.ThrowVictim (DefaultPlayer.as): victim of the active back-throw. */
    private var throwVictim: Fighter? = null

    /** Port of DefaultPlayer.AnimateAttack (DefaultPlayer.as). */
    override fun animateAttack(which: FighterState) {
        if (which == FighterState.PUNCH_AIR) {
            // AS2: this.yInc -= this.yInc <= -5 ? 0 : 5;
            yInc -= if (yInc <= -5f) 0f else 5f
        }
        super.animateAttack(which)
    }

    /**
     * Port of DefaultPlayer.ParseAttacks (DefaultPlayer.as): the hit table applied
     * to every [AttackCandidate] produced while an attack is active.
     *
     * Damage/throw numbers are verbatim AS2: ground/run/ko punch 5 @ 45,
     * uppercut 10 @ 90 (air: 80), electric special 10 @ 45 with 100 ms paralysis,
     * electric up-special 20 @ 45 with 300 ms paralysis, back-throw 15 @ 5 @ 30.
     */
    override fun parseAttacks(candidate: AttackCandidate) {
        if (candidate.targetType == HitType.PLAYER) {
            val target = candidate.target as Fighter
            if (target.state == FighterState.SPAWN || target.invincible ||
                target.state == FighterState.KO
            ) {
                return
            }
            when (currentAttack) {
                "punchGround", "punchRun" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone
                    ) {
                        return
                    }
                    // AS2: _xscale / 100 * (this._x - target._x) > 0 -> target is behind.
                    if (facingSign * (x - target.x) > 0f) {
                        if (abs(x - target.x) > 20f) return
                        // AS2 sets CurrentAttack and gotoAndStop directly (no AnimateAttack).
                        currentAttack = FighterState.SPECIAL_BACK_THROW.label
                        hasControl = false
                        throwVictim = target
                        attackDone = false
                        animation = FighterState.SPECIAL_BACK_THROW
                        attackTimeMs = 0f
                        return
                    }
                    sound.play("Punch")
                    target.damage(5f, this)
                    target.doThrow(3f, 45f * facingSign, this)
                    attackDone = true
                }
                "koAttack" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone
                    ) {
                        return
                    }
                    sound.play("Punch")
                    target.damage(5f, this)
                    target.doThrow(3f, 45f * facingSign, this)
                    attackDone = true
                }
                "punchUp", "punchAir" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone
                    ) {
                        return
                    }
                    sound.play("Punch")
                    target.damage(10f, this)
                    val angleDeg = if (currentAttack != "punchAir") 90f else 80f
                    target.doThrow(6f, angleDeg * facingSign, this)
                    attackDone = true
                }
                "specialGround", "specialAir" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone ||
                        target.invincible
                    ) {
                        return
                    }
                    sound.play("Electric")
                    target.state = FighterState.ELECTROCUTED
                    target.damage(10f, this)
                    target.electrocuted = true
                    target.paralyzedMs = 100f
                    target.doThrow(2.5f, 45f * facingSign, this)
                    attackDone = true
                }
                "specialUp" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone ||
                        target.invincible
                    ) {
                        return
                    }
                    // AS2: this["specialUp"]._currentframe > 10 && target._y <= this._y + 5
                    if (attackFrame() > 10f && target.y <= y + 5f) {
                        sound.play("Electric")
                        target.state = FighterState.ELECTROCUTED
                        target.damage(20f, this)
                        target.electrocuted = true
                        target.paralyzedMs = 300f
                        target.doThrow(5f, 45f * facingSign, this)
                        attackDone = true
                    }
                }
            }
        } else if (candidate.targetType == HitType.ITEM) {
            when (currentAttack) {
                "punchGround", "punchUp" -> {
                    val item = candidate.target as Item
                    // AS2: item.GetHit(this, Math.abs(item._x - this._x) <= 30)
                    item.getHit(this, abs(item.x - x) <= 30f)
                }
            }
        }
    }

    /**
     * Port of DefaultPlayer.ParseSAttacks (DefaultPlayer.as): per-tick upkeep of the
     * special attacks (AS2 passed a never-read `InfObj`; the base class calls this
     * hook with no argument).
     *
     * Frame gates are mapped with the porting rule: AS2 `_currentframe > N` becomes
     * `attackFrame() > N`, `_currentframe == _totalframes` becomes [attackFinished].
     */
    override fun parseSpecialAttacks() {
        when (currentAttack) {
            "specialBackThrow" -> {
                val victim = throwVictim
                if (victim == null) {
                    // AS2 would dereference an undefined ThrowVictim here.
                    return
                }
                if (attackFinished()) {
                    // AS2: release on the final frame of the back-throw clip.
                    victim.paralyzedMs = 0f
                    // AS2: victim._rotation = 0 (fighters carry no rotation in this port).
                    victim.hasControl = true
                    hasControl = true
                    victim.damage(15f, this)
                    victim.doThrow(5f, 30f * facingSign, this)
                    return
                }
                victim.hasControl = false
                // AS2: victim position on a 0.8*PI arc, frame/totalframes progress.
                val progress = attackTimeMs / attackTotalMs()
                val angle = progress * 0.8f * PI - PI
                victim.x = facingSign * cos(angle) * 25f + x
                victim.y = sin(angle) * 25f + y
            }
            "specialGround", "specialAir" -> {
                // AS2: spawn EnergyBall on the final frame of the attack clip.
                if (attackFinished()) {
                    val handX = if (facingRight) ENERGY_BALL_HAND_X else ENERGY_BALL_HAND_X_FLIPPED
                    val spawnX = handX + ATTACK_CLIP_OFFSET_X + x + 5f
                    val spawnY = ENERGY_BALL_HAND_Y + ATTACK_CLIP_OFFSET_Y + y - 7f
                    spawnProjectile(
                        kind = "EnergyBall",
                        x = spawnX,
                        y = spawnY,
                        xScalePct = 50f * facingSign, // AS2: 50 * (_xscale / 100)
                        yScalePct = 50f,
                    )
                    attackDone = true
                }
            }
            "specialUp" -> {
                // AS2: _currentframe < 10 -> hover damping.
                if (attackTimeMs < 10f * GameConfig.MS_PER_ANIM_FRAME) {
                    yInc *= 0.5f
                    return
                }
                // AS2: _currentframe < 12 -> Thunder on frames 10-11.
                if (attackTimeMs < 12f * GameConfig.MS_PER_ANIM_FRAME) {
                    sound.play("Thunder")
                }
                yInc = -6f // AS2: constant rise from frame 10 to the end of the clip
            }
        }
    }

    /**
     * Wires the AS2 `attachMovie("EnergyBall", ...)` to the concrete [EnergyBall]
     * class (the base [Fighter.spawnProjectile] hook is a no-op). The sign of
     * `xScalePct` carries the facing, matching `xInc = 5 * (_xscale / 100)`
     * applied inside EnergyBall.onInit.
     */
    override fun spawnProjectile(
        kind: String,
        x: Float,
        y: Float,
        xScalePct: Float,
        yScalePct: Float,
        xInc: Float,
        yInc: Float,
        rotDeg: Float,
    ) {
        if (kind != "EnergyBall") return
        val ball = EnergyBall(
            worldObj = world,
            sender = this,
            dirSign = if (xScalePct >= 0f) 1f else -1f,
        )
        ball.init(x, y)
    }

    companion object {
        /** AS2 library linkage name of the symbol (instance of this class). */
        const val FIGHTER_ID = "DefaultPlayer"

        /** AS2 field `CharacterName` of DefaultPlayer. */
        const val CHARACTER_NAME = "BallLock"

        /** `super(0.5, 0.5, 0.5, this.CharacterName)` from DefaultPlayer.as. */
        val STATS = FighterStats(
            weight = 0.5f,
            speed = 0.5f,
            power = 0.5f,
            fighterName = CHARACTER_NAME,
        )

        /** AS2 `PI` used for the back-throw arc. */
        private val PI = Math.PI.toFloat()

        // -- EnergyBall spawn offsets (assumptions; AS2 values live in the SWF clip) ----
        // AS2: hand._x / hand._y inside the attack clip; the clip's own _x/_y offset.
        /** Hand x inside the attack clip while facing right (assumed; ~+18 px). */
        private const val ENERGY_BALL_HAND_X = 18f

        /** AS2 overrides the hand x with -10 when `_xscale == -100` (left-facing). */
        private const val ENERGY_BALL_HAND_X_FLIPPED = -10f

        /** Hand y inside the attack clip (assumed; ~-40 px, above the feet origin). */
        private const val ENERGY_BALL_HAND_Y = -40f

        /** AS2 `this[CurrentAttack]._x` - assumed 0 (attack clip aligned with the fighter). */
        private const val ATTACK_CLIP_OFFSET_X = 0f

        /** AS2 `this[CurrentAttack]._y` - assumed 0. */
        private const val ATTACK_CLIP_OFFSET_Y = 0f
    }
}
