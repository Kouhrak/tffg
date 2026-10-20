package com.glorton.engine.fighter

import com.glorton.engine.world.HitType

/**
 * Numeric tuning of one fighter class.
 *
 * Port of the AS2 constructor arguments `super(_Weight, _Speed, _Power, _FighterName)`
 * (Fighter.as `function Fighter(...)` and the player subclasses):
 *
 *  - DefaultPlayer: `super(0.5, 0.5, 0.5, "BallLock")`
 *  - AuberginePlayer: `super(0.5, 0.6, 0.4, "AubergineLock")` (reference only, not ported)
 *
 * Semantics discovered from Fighter.as:
 *  - [weight] (AS2 `Weight`): used by [Fighter.doThrow] — the knockback formula is
 *    `Power + Power * Sender.Weight - Power * this.Weight + Damage/100 * ln(Power) * Power + Combo/4`,
 *    so a higher weight means the fighter is *thrown less*.
 *  - [speed] (AS2 `Speed`): used by [Fighter.move] — walking speed is `±8 * Speed` per tick.
 *  - [power] (AS2 `Power`): stored by the Fighter constructor; Fighter.as itself never
 *    reads it back. Kept for AI/balance code that wants the original stat.
 *  - [fighterName] (AS2 `FighterName`, the `_FighterName` constructor argument): the
 *    per-character identity string; the subclass passes its `CharacterName`
 *    (e.g. `"BallLock"`).
 */
data class FighterStats(
    /** AS2 `_Weight` / `Weight` — inverse knockback factor in [Fighter.doThrow]. */
    val weight: Float,
    /** AS2 `_Speed` / `Speed` — multiplier of the 8 px/tick walk speed in [Fighter.move]. */
    val speed: Float,
    /** AS2 `_Power` / `Power` — stored, never read by Fighter.as itself. */
    val power: Float,
    /** AS2 `_FighterName` / `FighterName` — per-character identity, e.g. `"BallLock"`. */
    val fighterName: String = "",
)

/**
 * Every AS2 state string (`Fighter.State`) and timeline label (`gotoAndStop(...)` label)
 * found in Fighter.as / DefaultPlayer.as / AuberginePlayer.as, in SCREAMING_SNAKE case.
 *
 * [Fighter.state] holds the AS2 `State` field values; [Fighter.animation] holds the
 * timeline label the AS2 code would reach with `gotoAndStop(label)`. The two overlap on
 * values that exist as both a State and a label ("stop"/"ko"/"spawn"/"thrown"/"crouch"/
 * "electrocuted"). [label] preserves the exact AS2 string, which is also the string the
 * AS2 code compares `CurrentAttack` against (e.g. `"punchGround"`).
 *
 * Frame numbers of the labels inside the fighter symbol (recovered from the frame guards
 * in Fighter.Animate): still=1, run=2, jump1=3, jump2=4, crouch=5, thrown=7,
 * takingHit=10, electrocuted=12, falling=15.
 */
enum class FighterState(val label: String) {
    // ---- AS2 Fighter.State strings -------------------------------------------------
    /** AS2 `State = "stop"` — idle or generic "not doing anything" state. */
    STOP("stop"),

    /** AS2 `State = "spawn"` — invulnerable entrance (Fighter.Spawn). */
    SPAWN("spawn"),

    /** AS2 `State = "ko"` — lying down after a hard landing; get-up driven by TimeKo. */
    KO("ko"),

    /** AS2 `State = "thrown"` — airborne after Fighter.Throw until hitting the floor. */
    THROWN("thrown"),

    /**
     * AS2 `State = "electrocuted"` — set on the victim by electric specials
     * (DefaultPlayer.ParseAttacks). Also the timeline label of frame 12 that
     * Fighter.Animate selects while `Modifiers.Electrocuted` is true.
     */
    ELECTROCUTED("electrocuted"),

    /** AS2 `State = "goleft"`. */
    GO_LEFT("goleft"),

    /** AS2 `State = "goright"`. */
    GO_RIGHT("goright"),

    /** AS2 `State = "crouch"` (also the timeline label of the crouch pose). */
    CROUCH("crouch"),

    // ---- Timeline labels driven by Fighter.Animate / Fighter.Spawn ------------------
    /** Label `"still"` — idle pose. */
    STILL("still"),

    /** Label `"run"` — running cycle. */
    RUN("run"),

    /** Label `"dodge"` — shield dash; also defines the `this.dodge != undefined` checks. */
    DODGE("dodge"),

    /** Label `"jump1"` — first jump rise (frame 3). */
    JUMP1("jump1"),

    /** Label `"jump2"` — double-jump rise (frame 4). */
    JUMP2("jump2"),

    /** Label `"falling"` — airborne, not rising (frame 15). */
    FALLING("falling"),

    /** Label `"takingHit"` — hurt pose while paralyzed without Electrocuted (frame 10). */
    TAKING_HIT("takingHit"),

    // ---- Attack labels (child MovieClips, used as Fighter.State AND CurrentAttack) ---
    /** Label `"koAttack"` — get-up attack from the ko state (Fighter.Attack). */
    KO_ATTACK("koAttack"),

    /** Label/clip `"punchGround"` — standing punch (frame guard: stationary on ground). */
    PUNCH_GROUND("punchGround"),

    /** Label/clip `"punchRun"` — punch while walking. */
    PUNCH_RUN("punchRun"),

    /** Label/clip `"punchUp"` — standing upward punch. */
    PUNCH_UP("punchUp"),

    /** Label/clip `"punchAir"` — jump punch. */
    PUNCH_AIR("punchAir"),

    /**
     * Label/clip `"punchAirDown"` — no fighter in the decompiled set starts it, but the
     * items (Mine.as, Grenade.as `Use(Attack)`) explicitly handle it, so it exists on
     * some SWF timeline; kept for item code and future characters.
     */
    PUNCH_AIR_DOWN("punchAirDown"),

    /** Label/clip `"specialGround"` — grounded special (electric ball for DefaultPlayer). */
    SPECIAL_GROUND("specialGround"),

    /** Label/clip `"specialAir"` — airborne special. */
    SPECIAL_AIR("specialAir"),

    /** Label/clip `"specialUp"` — vertical special (rises, then strikes above). */
    SPECIAL_UP("specialUp"),

    /** Label/clip `"specialBackThrow"` — grab performed when the victim is behind the attacker. */
    SPECIAL_BACK_THROW("specialBackThrow");

    companion object {
        /**
         * Resolves an AS2 label string to its constant, e.g. `"punchGround"` →
         * [PUNCH_GROUND]. Note that State strings and labels share constants where they
         * are identical (`"electrocuted"` → [ELECTROCUTED], `"crouch"` → [CROUCH]).
         */
        fun fromLabel(label: String): FighterState? = values().firstOrNull { it.label == label }
    }
}

/**
 * Port of the `InfObj` object built by World.CollisionDetectAttack (World.as):
 * `{object: <Fighter|Item>, objType: "player" | "item"}`.
 *
 * Passed to [Fighter.parseAttacks] (the AS2 polymorphic `ParseAttacks(InfObj)`).
 */
data class AttackCandidate(
    /** AS2 field `object` — the Fighter or Item whose AABB touched the attacker. */
    val target: Any,
    /** AS2 field `objType` — `PLAYER` for fighters, `ITEM` for items. */
    val targetType: HitType,
)
