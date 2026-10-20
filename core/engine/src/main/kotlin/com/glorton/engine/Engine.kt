package com.glorton.engine

import com.glorton.engine.ai.AIControl
import com.glorton.engine.camera.VCamera
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterState
import com.glorton.engine.input.InputMapper
import com.glorton.engine.world.Platform
import com.glorton.engine.world.World

enum class Phase { COUNTDOWN, FIGHT, GAMESET }

/** Read-only view of one fighter for the UI layer. */
data class FighterSnapshot(
    val playNum: Int,
    val fighterId: String,
    val x: Float,
    val y: Float,
    val state: FighterState,
    val facingRight: Boolean,
    val colr: String,
    val team: String,
    val lives: Int,
    val damagePct: Float,
    val dead: Boolean,
    val visible: Boolean,
    val currentItem: String,
)

data class CameraSnapshot(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val parallaxX: Float,
    val parallaxY: Float,
)

/** Read-only view of one world item (Star and later Mine/Grenade). */
data class ItemSnapshot(
    val name: String,
    val x: Float,
    val y: Float,
    val rotationDeg: Float,
    val visible: Boolean,
    val state: Int,
    val width: Float,
    val height: Float,
)

/** Read-only view of one projectile; [kind] is the class name ("Bullet", ...). */
data class ProjectileSnapshot(
    val kind: String,
    val x: Float,
    val y: Float,
    val rotationDeg: Float,
    val width: Float,
    val height: Float,
)

/** Read-only view of one explosion (visual radius grows with [ageMs]). */
data class ExplosionSnapshot(
    val x: Float,
    val y: Float,
    val size: Float,
    val ageMs: Float,
    val lifeMs: Float,
)

/** Immutable per-frame view of the whole match for the renderer. */
data class GameSnapshot(
    val phase: Phase,
    val countdownText: String?,
    val camera: CameraSnapshot,
    val worldBounds: com.glorton.engine.RectF,
    val platforms: List<Platform>,
    val fighters: List<FighterSnapshot>,
    val items: List<ItemSnapshot>,
    val projectiles: List<ProjectileSnapshot>,
    val explosions: List<ExplosionSnapshot>,
    val gameOverText: String?,
    val nowMs: Float,
)

/**
 * Match orchestrator: ports the frame_51 timeline logic (DoMain, FightTimer,
 * PlayerDeath, EndGame) into a fixed-step tick driven by the app layer.
 *
 * Deviations from the AS2 original (documented on purpose):
 *  - Input/AI is applied before doFighter within a tick; the AS2 order is
 *    DoFighter then DoAI, which delays AI commands by one 25ms tick.
 *  - The AS2 loop `while (_loc1_ <= NPlayers)` had an off-by-one that also
 *    ticked a phantom player; the port iterates exactly the real fighters.
 *  - Endurance mode regeneration and the menugame/tutorial flows are not
 *    ported yet (versus stock/time only).
 */
class Engine(
    val world: World,
    val camera: VCamera,
    private val sound: SoundSink,
    private val fighters: List<Fighter>,
    private val ais: List<AIControl?>,
    private val inputMappers: List<InputMapper?>,
    /** frame_51 `ItemGen` cadence; null when the stage has no item spawn bars. */
    val itemGen: com.glorton.engine.item.ItemGen? = null,
    val limitMode: String,
    var limitVal: Int,
) {
    var phase: Phase = Phase.COUNTDOWN
        private set
    var paused: Boolean = false
    var gameOn: Boolean = false
        private set

    /** ReadySet from frame_51: counts 5..0 then starts the fight. */
    var readySet: Int = 5
        private set

    var countdownText: String? = "5"
        private set

    var nowMs: Float = 0f
        private set

    /** GameTime from frame_51: elapsed whole seconds of match time. */
    var gameTime: Int = 0
        private set

    var gameOverText: String? = null
        private set

    private var countdownAccMs = 0f
    private var nextDeathOrd = 0

    /** One 25ms logic tick. [inputs] is one InputState per human player. */
    fun tick(inputs: List<InputState>) {
        if (phase == Phase.GAMESET) return

        nowMs += GameConfig.TICK_MS

        // FightTimer cadence: countdown, match clock and time limit.
        countdownAccMs += GameConfig.TICK_MS
        if (countdownAccMs >= 1000f) {
            countdownAccMs -= 1000f
            fightTimerTick()
        }

        if (paused) return

        if (phase == Phase.FIGHT) {
            for (i in fighters.indices) {
                val ai = ais[i]
                if (ai != null) {
                    ai.doAI()
                } else {
                    inputMappers[i]?.update(inputs.getOrElse(i) { InputState.NONE })
                }
                fighters[i].doFighter()
            }
            // World entities tick with their own AS2 intervals; iterate over
            // copies because a tick may remove its own entity from the list.
            for (item in world.items.toList()) {
                item.doItemTick()
            }
            // frame_51 InitArea: ItemGen.init([Mine,Grenade], Settings.ItemFrequency).
            // Tick placement inside FIGHT (documented): the AS2 interval also runs
            // during the countdown, but items do not tick during the countdown in
            // this port, so spawning there would freeze them mid-air.
            itemGen?.tick()
            for (projectile in world.projectiles.toList()) {
                projectile.tick()
            }
            for (explosion in world.explosions.toList()) {
                explosion.tick(nowMs)
            }
            checkDeaths()
        }

        camera.doCamera(nowMs)
    }

    /** Port of the FightTimer interval body (frame_51). */
    private fun fightTimerTick() {
        if (phase == Phase.COUNTDOWN) {
            readySet--
            countdownText = when {
                readySet > 0 -> readySet.toString()
                readySet == 0 -> "GO!"
                else -> {
                    phase = Phase.FIGHT
                    gameOn = true
                    null
                }
            }
        }
        gameTime++
        if (limitMode == "time" && gameOn && limitVal != 0) {
            limitVal--
            if (limitVal == 0) {
                endGame()
            }
        }
    }

    /** Port of PlayerDeath (frame_51), stock mode only for now. */
    private fun checkDeaths() {
        if (limitMode != "stock") return
        val deadCount = fighters.count { it.dead }
        if (fighters.size == 1) {
            if (deadCount == 1) endGame()
        } else if (deadCount == fighters.size - 1) {
            endGame()
        }
    }

    /**
     * Port of the winner computation of frame_170/frame_49: time mode ranks by
     * KOs - Deaths, stock mode by DeathOrd (players who never died get the
     * highest value, so the survivor wins).
     */
    private fun endGame() {
        sound.stopAll()
        phase = Phase.GAMESET
        gameOverText = if (limitMode == "time") {
            val winner = fighters.withIndex().maxBy { (i, f) ->
                (f.kos - f.deaths).toFloat().let { score -> score * 1000f - i }
            }
            "PLAYER ${winner.index + 1} WINS!"
        } else {
            val ranked = fighters.withIndex()
                .sortedBy { (_, f) -> f.rankingDeathOrd() }
            "PLAYER ${ranked.last().index + 1} WINS!"
        }
    }

    /** Registers a death order for the stock ranking (AS2 NumDead/DeathOrd). */
    fun noteDeath(fighter: Fighter) {
        if (fighter.deathOrd < 0) {
            fighter.deathOrd = nextDeathOrd++
        }
    }

    fun snapshot(): GameSnapshot = GameSnapshot(
        phase = phase,
        countdownText = countdownText,
        camera = CameraSnapshot(
            x = camera.x,
            y = camera.y,
            scaleX = camera.scaleX,
            scaleY = camera.scaleY,
            parallaxX = camera.parallaxX,
            parallaxY = camera.parallaxY,
        ),
        worldBounds = world.bounds,
        platforms = world.platforms,
        fighters = fighters.map { f ->
            FighterSnapshot(
                playNum = f.playNum,
                fighterId = f.fighterId,
                x = f.x,
                y = f.y,
                state = f.state,
                facingRight = f.facingRight,
                colr = f.colr,
                team = f.team,
                lives = f.lives,
                damagePct = f.damagePct,
                dead = f.dead,
                visible = f.visible,
                currentItem = f.currentItem,
            )
        },
        items = world.items.map { item ->
            ItemSnapshot(
                name = item.name,
                x = item.x,
                y = item.y,
                rotationDeg = item.rotationDeg,
                visible = item.visible,
                state = item.state,
                width = item.width,
                height = item.height,
            )
        },
        projectiles = world.projectiles.map { projectile ->
            ProjectileSnapshot(
                kind = projectile::class.simpleName ?: "Projectile",
                x = projectile.x,
                y = projectile.y,
                rotationDeg = projectile.rotationDeg,
                width = projectile.width,
                height = projectile.height,
            )
        },
        explosions = world.explosions.map { explosion ->
            ExplosionSnapshot(
                x = explosion.x,
                y = explosion.y,
                size = explosion.size,
                ageMs = explosion.ageMs,
                lifeMs = com.glorton.engine.effects.Explosion.BOOM_LIFE_MS,
            )
        },
        gameOverText = gameOverText,
        nowMs = nowMs,
    )

    companion object {
        /**
         * Stock-mode ranking treats a negative DeathOrd (player never died)
         * as the highest possible order, matching frame_170 where survivors
         * receive NumDead at the end.
         */
        internal fun Fighter.rankingDeathOrd(): Int =
            if (deathOrd >= 0) deathOrd else Int.MAX_VALUE
    }
}
