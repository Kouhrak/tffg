package com.glorton.engine.fighter

import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.projectile.Bullet
import com.glorton.engine.projectile.Rocket
import com.glorton.engine.world.HitType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of class PeachPlayer (PeachPlayer.as) - the gunner fighter
 * (`CharacterName = "PeachLock"`), concrete [Fighter].
 *
 * Faithful to the AS2 subclass:
 *  - stats from `super(0.4, 0.5, 0.6, "PeachLock")`;
 *  - `AnimateAttack` gives `punchAir` a small downward nudge;
 *  - `ParseAttacks` adds the melee special: `specialGround`/`specialAir`
 *    deal 10 dmg @ 45 on contact; `specialUp` has no melee hit (the rocket does);
 *  - `ParseSAttacks` carries the back-throw victim on an arc, spawns the Bullet
 *    past frame 5 of the ground/air special and the Rocket past frame 14 of
 *    `specialUp`, aiming it from the held rocket's rotation.
 *
 * The rocket aim reproduces the AS2 math `xInc = cos((rot - 90)deg) * 15` /
 * `yInc = sin((rot - 90)deg) * 15`; `ROCKET_HOLD_ROT_DEG` replaces the SWF
 * clip's `_rotation` (assumed 0 = pointing up, see assumptions report).
 */
class PeachPlayer(
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

    /** Port of PeachPlayer.ThrowVictim (PeachPlayer.as): victim of the active back-throw. */
    private var throwVictim: Fighter? = null

    /** Port of PeachPlayer.BulletShot (PeachPlayer.as): one-shot spawn latch. */
    private var bulletShot = false

    /** Port of PeachPlayer.AnimateAttack (PeachPlayer.as). */
    override fun animateAttack(which: FighterState) {
        if (which == FighterState.PUNCH_AIR) {
            // AS2: this.yInc -= this.yInc <= -5 ? 0 : 5;
            yInc -= if (yInc <= -5f) 0f else 5f
        }
        super.animateAttack(which)
    }

    /**
     * Port of PeachPlayer.ParseAttacks (PeachPlayer.as): the hit table applied
     * to every [AttackCandidate] produced while an attack is active.
     *
     * Damage/throw numbers are verbatim AS2: ground/run/ko punch 5 @ 45,
     * uppercut 10 @ 90 (air: 80), special melee 10 @ 45, back-throw 15 @ 5 @ 30.
     * Note: AS2 Peach has no `specialUp` melee case - the rocket deals the damage.
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
                "specialGround", "specialAir" -> {
                    if ((target.state == FighterState.THROWN ||
                            target.state == FighterState.KO) && attackDone
                    ) {
                        return
                    }
                    sound.play("Punch")
                    target.damage(10f, this)
                    target.doThrow(3f, 45f * facingSign, this)
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
     * Port of PeachPlayer.ParseSAttacks (PeachPlayer.as): per-tick upkeep of the
     * special attacks - back-throw arc, Bullet spawn (ground/air special) and
     * Rocket spawn from the up-special.
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
                if (attackFrame() >= 5f) {
                    if (bulletShot) {
                        return
                    }
                    bulletShot = true
                    // AS2: hide the bullet child clip (visual only; the renderer
                    // draws fighters without attack sub-clips in this port).
                    // AS2 spawn: hand offsets + Gun sound, scale 50.
                    val handX = if (facingRight) BULLET_HAND_X else BULLET_HAND_X_FLIPPED
                    val spawnX = handX + ATTACK_CLIP_OFFSET_X + x + 5f
                    val spawnY = BULLET_HAND_Y + ATTACK_CLIP_OFFSET_Y + y - 7f
                    sound.play("Gun")
                    spawnProjectile(
                        kind = "Bullet",
                        x = spawnX,
                        y = spawnY,
                        xScalePct = 50f * facingSign, // AS2: 50 * (_xscale / 100)
                        yScalePct = 50f,
                    )
                    attackDone = true
                } else {
                    bulletShot = false
                }
            }
            "specialUp" -> {
                // AS2: frames < 7 damping, frames 7..10 constant rise, then wait.
                if (attackFrame() < 7f) {
                    yInc *= 0.5f
                } else if (attackFrame() <= 10f) {
                    yInc = -6f
                }
                // AS2: past frame 14, fire the held rocket once and hover.
                if (attackFrame() > 14f) {
                    if (bulletShot) {
                        return
                    }
                    yInc = -5f
                    bulletShot = true
                    // AS2: rocket aim from the held clip's _rotation; flipped
                    // facing mirrors both the x offset and the rotation.
                    var rot = ROCKET_HOLD_ROT_DEG
                    val handX = if (facingRight) ROCKET_HAND_X else ROCKET_HAND_X_FLIPPED
                    if (!facingRight) {
                        rot = -rot
                    }
                    val xIncRad = Math.toRadians((rot - 90.0))
                    val rocketXInc = cos(xIncRad).toFloat() * 15f
                    val rocketYInc = sin(xIncRad).toFloat() * 15f
                    val spawnX = handX + ATTACK_CLIP_OFFSET_X + x + 5f
                    val spawnY = ROCKET_HAND_Y + ATTACK_CLIP_OFFSET_Y + y - 7f
                    sound.play("Rocket")
                    spawnProjectile(
                        kind = "Rocket",
                        x = spawnX,
                        y = spawnY,
                        xScalePct = 100f * facingSign, // AS2: 100 * (_xscale / 100)
                        yScalePct = 100f,
                        xInc = rocketXInc,
                        yInc = rocketYInc,
                        rotDeg = rot,
                    )
                    return
                }
                bulletShot = false
            }
        }
    }

    /**
     * Wires the AS2 `attachMovie("Bullet" | "Rocket", ...)` calls to the
     * concrete [Bullet] / [Rocket] classes. The Rocket receives the aim
     * velocities computed in [parseSpecialAttacks] (AS2 passes them as
     * `xInc`/`yInc` attachMovie props).
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
        val dirSign = if (xScalePct >= 0f) 1f else -1f
        when (kind) {
            "Bullet" -> {
                val bullet = Bullet(worldObj = world, sender = this, dirSign = dirSign)
                bullet.init(x, y)
            }
            "Rocket" -> {
                val rocket = Rocket(
                    worldObj = world,
                    sender = this,
                    dirSign = dirSign,
                    initialXInc = xInc,
                    initialYInc = yInc,
                )
                rocket.init(x, y)
            }
        }
    }

    companion object {
        /** AS2 library linkage name of the symbol (instance of this class). */
        const val FIGHTER_ID = "PeachPlayer"

        /** AS2 field `CharacterName` of PeachPlayer. */
        const val CHARACTER_NAME = "PeachLock"

        /** `super(0.4, 0.5, 0.6, this.CharacterName)` from PeachPlayer.as. */
        val STATS = FighterStats(
            weight = 0.4f,
            speed = 0.5f,
            power = 0.6f,
            fighterName = CHARACTER_NAME,
        )

        /** AS2 `PI` used for the back-throw arc. */
        private val PI = Math.PI.toFloat()

        // -- Bullet spawn offsets (AS2: hand child clip inside the attack clip) ------
        /** Hand x inside the attack clip while facing right (assumed; AS2 reads `bullet._x`). */
        private const val BULLET_HAND_X = 18f

        /** AS2 overrides the hand x with -54 when `_xscale == -100` (left-facing). */
        private const val BULLET_HAND_X_FLIPPED = -54f

        /** Hand y inside the attack clip (assumed; ~-40 px above the feet origin). */
        private const val BULLET_HAND_Y = -40f

        // -- Rocket spawn offsets (AS2: rocket child clip inside the attack clip) ----
        /** Rocket x inside the attack clip while facing right (assumed; AS2 reads `rocket._x`). */
        private const val ROCKET_HAND_X = 18f

        /** AS2 overrides the rocket x with -12 when `_xscale == -100` (left-facing). */
        private const val ROCKET_HAND_X_FLIPPED = -12f

        /** Rocket y inside the attack clip (assumed; ~-40 px above the feet origin). */
        private const val ROCKET_HAND_Y = -40f

        /**
         * AS2 reads `rocket._rotation` from the held clip - a SWF value not
         * present in the decompiled scripts. Assumed 0 deg (pointing up), which
         * makes the AS2 aim formula yield `xInc = 0, yInc = -15` (straight up).
         */
        private const val ROCKET_HOLD_ROT_DEG = 0f

        /** AS2 `this[CurrentAttack]._x` - assumed 0 (attack clip aligned with the fighter). */
        private const val ATTACK_CLIP_OFFSET_X = 0f

        /** AS2 `this[CurrentAttack]._y` - assumed 0. */
        private const val ATTACK_CLIP_OFFSET_Y = 0f
    }
}
