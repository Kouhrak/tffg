package com.glorton.engine.fighter

import com.glorton.engine.SoundSink
import com.glorton.engine.item.Item
import com.glorton.engine.world.HitType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Port of class SBLPlayer (SBLPlayer.as) - the strawberry fighter
 * (`CharacterName = "StrawberryLock"`), concrete [Fighter]. Melee-focused:
 * its ground/air special is a Kamehameha-style **hit window** (no projectile),
 * and its up-special is a rising headbutt.
 *
 * Faithful to the AS2 subclass:
 *  - stats from `super(0.5, 0.5, 0.5, "StrawberryLock")`;
 *  - `AnimateAttack` gives `punchAir` a small downward nudge;
 *  - `ParseAttacks` adds the special window: frames 9..14 of `specialGround`/
 *    `specialAir`, distance 20..240 px, damage scaled by the AS2 falloff
 *    `1 - 1 / (230 / distance)` (integer-rounded, min 0.2);
 *  - `ParseSAttacks` carries the back-throw victim on an arc, plays the
 *    Kamehameha sound once past frame 5 and drives the `specialUp` rise.
 */
class SBLPlayer(
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

    /** Port of SBLPlayer.ThrowVictim (SBLPlayer.as): victim of the active back-throw. */
    private var throwVictim: Fighter? = null

    /** Port of SBLPlayer.BulletShot (SBLPlayer.as): Kamehameha sound latch. */
    private var bulletShot = false

    /** Port of SBLPlayer.AnimateAttack (SBLPlayer.as). */
    override fun animateAttack(which: FighterState) {
        if (which == FighterState.PUNCH_AIR) {
            // AS2: this.yInc -= this.yInc <= -5 ? 0 : 5;
            yInc -= if (yInc <= -5f) 0f else 5f
        }
        super.animateAttack(which)
    }

    /**
     * Port of SBLPlayer.ParseAttacks (SBLPlayer.as): the hit table applied to
     * every [AttackCandidate] produced while an attack is active.
     *
     * Damage/throw numbers are verbatim AS2: ground/run/ko punch 5 @ 45,
     * uppercut 10 @ 90 (air: 80), special window `round(10 * falloff)` @
     * `5 * falloff @ 45` with `falloff = 1 - 1 / (230 / dist)` (min 0.2),
     * up-special 20 @ 45, back-throw 15 @ 5 @ 30.
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
                    // AS2: |dist| < 240 && |dist| > 20 && frame >= 9 && frame < 15
                    // (the Kamehameha beam window; no projectile is involved).
                    val dist = abs(target.x - x)
                    val frame = attackFrame()
                    if (dist < 240f && dist > 20f && frame >= 9f && frame < 15f) {
                        // AS2: _loc3_ = 1 - 1 / (230 / |dist|)
                        val falloff = 1f - 1f / (230f / dist)
                        if (falloff < 0.2f) {
                            return
                        }
                        sound.play("Boom")
                        // AS2 Math.round -> integer damage points.
                        target.damage(round(10f * falloff), this)
                        target.doThrow(5f * falloff, 45f * facingSign, this)
                        attackDone = true
                    }
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
     * Port of SBLPlayer.ParseSAttacks (SBLPlayer.as): per-tick upkeep of the
     * special attacks. Frame gates follow the porting rule: AS2
     * `_currentframe > N` becomes `attackFrame() > N`, and the sound latch
     * (`BulletShot`) reproduces the one-shot Kamehameha call.
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
                // AS2: play the Kamehameha once the clip passes frame 5; the
                // damage window itself lives in [parseAttacks] (frames 9..14).
                if (attackFrame() >= 5f) {
                    if (bulletShot) {
                        return
                    }
                    bulletShot = true
                    sound.play("Kamehameha")
                } else {
                    bulletShot = false
                }
            }
            "specialUp" -> {
                // AS2: _currentframe <= 2 -> Woosh + damping; then a constant rise.
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

    companion object {
        /** AS2 library linkage name of the symbol (instance of this class). */
        const val FIGHTER_ID = "SBLPlayer"

        /** AS2 field `CharacterName` of SBLPlayer. */
        const val CHARACTER_NAME = "StrawberryLock"

        /** `super(0.5, 0.5, 0.5, this.CharacterName)` from SBLPlayer.as. */
        val STATS = FighterStats(
            weight = 0.5f,
            speed = 0.5f,
            power = 0.5f,
            fighterName = CHARACTER_NAME,
        )

        /** AS2 `PI` used for the back-throw arc. */
        private val PI = Math.PI.toFloat()
    }
}
