package com.glorton.engine.fighter

import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.projectile.Poop
import com.glorton.engine.world.HitType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of class CoffeePlayer (CoffeePlayer.as) - the coffee fighter
 * (`CharacterName = "CoffeeLock"`), concrete [Fighter].
 *
 * Faithful to the AS2 subclass:
 *  - stats from `super(0.4, 0.7, 0.4, "CoffeeLock")` (the fastest fighter);
 *  - `AnimateAttack` gives `punchAir` a small downward nudge;
 *  - `ParseAttacks` adds the up-special melee hit: 20 dmg @ 45;
 *  - `ParseSAttacks` carries the back-throw victim on an arc, spawns the Poop
 *    past frame 9 of the ground/air special (fart sound) and drives the
 *    `specialUp` rise.
 *
 * AS2 quirk preserved: the ground/air special reads the offsets from a child
 * clip named `bullet` (CoffeePlayer.as:135-136) while hiding and spawning the
 * `poop` clip - a copy-paste leftover in the original; the port uses the poop
 * offsets consistently (identical formula, same assumed hand position).
 */
class CoffeePlayer(
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

    /** Port of CoffeePlayer.ThrowVictim (CoffeePlayer.as): victim of the active back-throw. */
    private var throwVictim: Fighter? = null

    /** Port of CoffeePlayer.BulletShot (CoffeePlayer.as): one-shot spawn latch. */
    private var bulletShot = false

    /** Port of CoffeePlayer.AnimateAttack (CoffeePlayer.as). */
    override fun animateAttack(which: FighterState) {
        if (which == FighterState.PUNCH_AIR) {
            // AS2: this.yInc -= this.yInc <= -5 ? 0 : 5;
            yInc -= if (yInc <= -5f) 0f else 5f
        }
        super.animateAttack(which)
    }

    /**
     * Port of CoffeePlayer.ParseAttacks (CoffeePlayer.as): the hit table applied
     * to every [AttackCandidate] produced while an attack is active.
     *
     * Damage/throw numbers are verbatim AS2: ground/run/ko punch 5 @ 45,
     * uppercut 10 @ 90 (air: 80), up-special 20 @ 45, back-throw 15 @ 5 @ 30.
     * Note: the ground/air special has no melee case - the Poop deals the damage.
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
                    // AS2 order: Damage, angle, PlaySound, Throw.
                    target.damage(10f, this)
                    val angleDeg = if (currentAttack != "punchAir") 90f else 80f
                    sound.play("Punch")
                    target.doThrow(6f, angleDeg * facingSign, this)
                    attackDone = true
                }
                "specialUp" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone
                    ) {
                        return
                    }
                    sound.play("Punch")
                    target.damage(20f, this)
                    target.doThrow(4f, 45f * facingSign, this)
                    attackDone = true
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
     * Port of CoffeePlayer.ParseSAttacks (CoffeePlayer.as): per-tick upkeep of
     * the special attacks - back-throw arc, Poop spawn (ground/air special)
     * and the `specialUp` rise.
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
                if (attackFrame() >= 9f) {
                    if (bulletShot) {
                        return
                    }
                    bulletShot = true
                    // AS2: hide the poop child clip (visual only; the renderer
                    // draws fighters without attack sub-clips in this port).
                    // AS2 spawn: hand offsets, scale 40, Fart sound (AndroidSoundSink
                    // falls back to the fart2 asset - Fart.wav is Flash-ADPCM FLV).
                    val handX = if (facingRight) POOP_HAND_X else POOP_HAND_X_FLIPPED
                    val spawnX = handX + ATTACK_CLIP_OFFSET_X + x + 5f
                    val spawnY = POOP_HAND_Y + ATTACK_CLIP_OFFSET_Y + y - 5f
                    sound.play("Fart")
                    spawnProjectile(
                        kind = "Poop",
                        x = spawnX,
                        y = spawnY,
                        xScalePct = 40f * facingSign, // AS2: 40 * (_xscale / 100)
                        yScalePct = 40f,
                    )
                    attackDone = true
                } else {
                    bulletShot = false
                }
            }
            "specialUp" -> {
                // AS2: frames <= 2 -> Woosh + damping; then a constant rise.
                if (attackFrame() <= 2f) {
                    sound.play("Woosh")
                    yInc *= 0.5f
                    return
                }
                if (attackFrame() > 2f) {
                    yInc = -8f
                }
            }
        }
    }

    /**
     * Wires the AS2 `attachMovie("Poop", ...)` call to the concrete [Poop]
     * class. The hand x uses the poop clip consistently (see the AS2 quirk in
     * the class KDoc: the original reads a `bullet` child that does not exist
     * in the Coffee clip; both are assumed at the same right-hand position).
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
        if (kind != "Poop") return
        val poop = Poop(worldObj = world, sender = this, dirSign = if (xScalePct >= 0f) 1f else -1f)
        poop.init(x, y)
    }

    companion object {
        /** AS2 library linkage name of the symbol (instance of this class). */
        const val FIGHTER_ID = "CoffeePlayer"

        /** AS2 field `CharacterName` of CoffeePlayer. */
        const val CHARACTER_NAME = "CoffeeLock"

        /** `super(0.4, 0.7, 0.4, this.CharacterName)` from CoffeePlayer.as. */
        val STATS = FighterStats(
            weight = 0.4f,
            speed = 0.7f,
            power = 0.4f,
            fighterName = CHARACTER_NAME,
        )

        /** AS2 `PI` used for the back-throw arc. */
        private val PI = Math.PI.toFloat()

        // -- Poop spawn offsets (AS2: hand child clip inside the attack clip) ---------
        /** Hand x inside the attack clip while facing right (assumed; AS2 reads `bullet._x`). */
        private const val POOP_HAND_X = 18f

        /** AS2 overrides the hand x with -7 when `_xscale == -100` (left-facing). */
        private const val POOP_HAND_X_FLIPPED = -7f

        /** Hand y inside the attack clip (assumed; ~-40 px above the feet origin). */
        private const val POOP_HAND_Y = -40f

        /** AS2 `this[CurrentAttack]._x` - assumed 0 (attack clip aligned with the fighter). */
        private const val ATTACK_CLIP_OFFSET_X = 0f

        /** AS2 `this[CurrentAttack]._y` - assumed 0. */
        private const val ATTACK_CLIP_OFFSET_Y = 0f
    }
}
