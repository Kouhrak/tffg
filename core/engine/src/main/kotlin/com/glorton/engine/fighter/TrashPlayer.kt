package com.glorton.engine.fighter

import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.projectile.Garbage
import com.glorton.engine.world.HitType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of class TrashPlayer (TrashPlayer.as) - the trash fighter
 * (`CharacterName = "TrashLock"`), concrete [Fighter].
 *
 * Faithful to the AS2 subclass:
 *  - stats from `super(0.6, 0.4, 0.5, "TrashLock")` (the heaviest fighter);
 *  - `AnimateAttack` gives `punchAir` a small downward nudge;
 *  - `ParseAttacks` is the plain punch table plus an **empty** `specialUp`
 *    case (the up-special deals no melee damage in the original);
 *  - `ParseSAttacks` carries the back-throw victim on an arc, throws the
 *    Garbage bag past frame 6 of the ground/air special and drives the
 *    `specialUp` rise.
 *
 * AS2 quirk preserved: the `specialUp` "garbage burst" (20 Garbage pieces in
 * a ring, TrashPlayer.as:157-169) reads `InfObj._y`, but the base class calls
 * `ParseSAttacks(_loc4_[ok])` with `_loc4_` still the **empty array** created
 * at Fighter.as:821 - the InfObj is always `undefined`, AVM1 resolves
 * `undefined._y` to `NaN`, and `NaN <= y + 5` is always false. The burst is
 * unreachable dead code in the original; it stays unported here (see KDoc at
 * [parseSpecialAttacks]).
 */
class TrashPlayer(
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

    /** Port of TrashPlayer.ThrowVictim (TrashPlayer.as): victim of the active back-throw. */
    private var throwVictim: Fighter? = null

    /** Port of TrashPlayer.BulletShot (TrashPlayer.as): one-shot spawn latch. */
    private var bulletShot = false

    /** Port of TrashPlayer.AnimateAttack (TrashPlayer.as). */
    override fun animateAttack(which: FighterState) {
        if (which == FighterState.PUNCH_AIR) {
            // AS2: this.yInc -= this.yInc <= -5 ? 0 : 5;
            yInc -= if (yInc <= -5f) 0f else 5f
        }
        super.animateAttack(which)
    }

    /**
     * Port of TrashPlayer.ParseAttacks (TrashPlayer.as): the hit table applied
     * to every [AttackCandidate] produced while an attack is active.
     *
     * Damage/throw numbers are verbatim AS2: ground/run/ko punch 5 @ 45,
     * uppercut 10 @ 90 (air: 80), back-throw 15 @ 5 @ 30. The AS2
     * `case "specialUp":` in this method has an **empty body** - no melee
     * damage; the up-special's damage comes from the Garbage ring, which is
     * itself unreachable (see the class KDoc).
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
                    // AS2 order: PlaySound, Damage, Throw.
                    sound.play("Punch")
                    target.damage(10f, this)
                    val angleDeg = if (currentAttack != "punchAir") 90f else 80f
                    target.doThrow(6f, angleDeg * facingSign, this)
                    attackDone = true
                }
                // AS2: `case "specialUp":` exists with an empty body (no melee hit).
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
     * Port of TrashPlayer.ParseSAttacks (TrashPlayer.as): per-tick upkeep of
     * the special attacks - back-throw arc, Garbage spawn (ground/air special)
     * and the `specialUp` rise.
     *
     * The AS2 `specialUp` garbage burst (TrashPlayer.as:151-169: Boom + 20
     * Garbage pieces in an 18 deg ring at velocity 15) is **not ported**: its
     * guard `frame > 10 && InfObj._y <= this._y + 5` reads a property of an
     * always-undefined InfObj (see the class KDoc), which AVM1 turns into a
     * permanently-false `NaN` comparison. Observable behavior matches: the
     * burst never fires in the original either.
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
                if (attackFrame() >= 6f) {
                    if (bulletShot) {
                        return
                    }
                    bulletShot = true
                    // AS2: hide the garbage child clip (visual only; the renderer
                    // draws fighters without attack sub-clips in this port).
                    // AS2 spawn: hand offsets, scale 100, Garbage type frame from
                    // the held clip (`garbage._currentframe` - a SWF value, see
                    // GARBAGE_VARIANT assumption).
                    val handX = if (facingRight) GARBAGE_HAND_X else GARBAGE_HAND_X_FLIPPED
                    val spawnX = handX + ATTACK_CLIP_OFFSET_X + x + 5f
                    val spawnY = GARBAGE_HAND_Y + ATTACK_CLIP_OFFSET_Y + y - 7f
                    spawnProjectile(
                        kind = "Garbage",
                        x = spawnX,
                        y = spawnY,
                        xScalePct = 100f * facingSign, // AS2: 100 * (_xscale / 100)
                        yScalePct = 100f,
                        variant = GARBAGE_VARIANT,
                    )
                    attackDone = true
                } else {
                    bulletShot = false
                }
            }
            "specialUp" -> {
                // AS2: frames < 10 damping, then a constant rise.
                if (attackFrame() < 10f) {
                    yInc *= 0.5f
                } else {
                    yInc = -6f
                }
                // AS2: the garbage-burst branch (frame > 10 && InfObj._y <= y + 5)
                // is unreachable dead code - see the KDoc of this method.
                bulletShot = false
            }
        }
    }

    /**
     * Wires the AS2 `attachMovie("Garbage", ...)` call to the concrete
     * [Garbage] class; `variant` is the AS2 `garbage._currentframe` visual
     * frame passed as the third `.init(...)` argument.
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
        variant: Int,
    ) {
        if (kind != "Garbage") return
        val garbage = Garbage(
            worldObj = world,
            sender = this,
            dirSign = if (xScalePct >= 0f) 1f else -1f,
            typeFrame = variant,
        )
        garbage.init(x, y)
    }

    companion object {
        /** AS2 library linkage name of the symbol (instance of this class). */
        const val FIGHTER_ID = "TrashPlayer"

        /** AS2 field `CharacterName` of TrashPlayer. */
        const val CHARACTER_NAME = "TrashLock"

        /** `super(0.6, 0.4, 0.5, this.CharacterName)` from TrashPlayer.as. */
        val STATS = FighterStats(
            weight = 0.6f,
            speed = 0.4f,
            power = 0.5f,
            fighterName = CHARACTER_NAME,
        )

        /** AS2 `PI` used for the back-throw arc. */
        private val PI = Math.PI.toFloat()

        // -- Garbage spawn offsets (AS2: hand child clip inside the attack clip) ------
        /** Hand x inside the attack clip while facing right (assumed; AS2 reads `garbage._x`). */
        private const val GARBAGE_HAND_X = 18f

        /** AS2 overrides the hand x with -12 when `_xscale == -100` (left-facing). */
        private const val GARBAGE_HAND_X_FLIPPED = -12f

        /** Hand y inside the attack clip (assumed; ~-40 px above the feet origin). */
        private const val GARBAGE_HAND_Y = -40f

        /**
         * AS2 passes `garbage._currentframe` (the visual frame of the garbage
         * child clip in the SWF attack animation) as the Garbage `Type`.
         * Assumed 0 (first variant); the value only selects the thrown visual.
         */
        private const val GARBAGE_VARIANT = 0

        /** AS2 `this[CurrentAttack]._x` - assumed 0 (attack clip aligned with the fighter). */
        private const val ATTACK_CLIP_OFFSET_X = 0f

        /** AS2 `this[CurrentAttack]._y` - assumed 0. */
        private const val ATTACK_CLIP_OFFSET_Y = 0f
    }
}
