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
    /**
     * frame_101/frame_143 standings consumed by the results screen
     * (frame_170 table); empty until the match ends.
     */
    val standings: List<StandingSnapshot> = emptyList(),
    /** `"stock"` or `"time"` - selects the frame_170 legend and columns. */
    val limitMode: String = "stock",
    /** AS2 `Game.Type`: `"versus"` or `"endurance"`. */
    val gameType: String = "versus",
    /** frame_51 `KilledPlayers` - enemies defeated in an endurance run. */
    val killedPlayers: Int = 0,
    /** frame_51 `GameTime` - elapsed whole seconds (endurance results clock). */
    val gameTimeSec: Int = 0,
    val nowMs: Float,
)

/**
 * Read-only standings row for one player: the frame_170 results table
 * (frame_143 Rank over frame_101's sorted BestPlayers).
 */
data class StandingSnapshot(
    /** AS2 `Game.Players` index (0-based; "P1" is index 0). */
    val playerIndex: Int,
    val fighterId: String,
    val colr: String,
    /** frame_143: `Game.Players[Ind].Rank = i + 1` over the sorted list. */
    val rank: Int,
    val kos: Int,
    val deaths: Int,
    /** AS2 `SDs` - deaths without an attacker (stock legend: "Suicides"). */
    val sds: Int,
    /** frame_101 Score: time -> KOs - Deaths; stock -> DeathOrd (NumDead for survivors). */
    val score: Int,
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
 *  - The menugame/tutorial attract flows are not ported yet; versus
 *    (stock/time) and endurance modes are.
 */
class Engine(
    val world: World,
    val camera: VCamera,
    private val sound: SoundSink,
    private val fighters: MutableList<Fighter>,
    /** AI driver per slot (null for human players); internal so tests can assert wave levels. */
    internal val ais: MutableList<AIControl?>,
    private val inputMappers: List<InputMapper?>,
    /** frame_51 `ItemGen` cadence; null when the stage has no item spawn bars. */
    val itemGen: com.glorton.engine.item.ItemGen? = null,
    /** AS2 `Game.Type`: `"versus"` (default) or `"endurance"`. */
    val gameType: String = "versus",
    /**
     * Builds the next endurance wave's CPU fighter (AS2
     * GenerateEndurancePlayer's `Fighters[random(...)]` attachMovie); null in
     * versus mode. Apply team/lives/colr after [Fighter.init].
     */
    private val enemyFactory: (() -> Fighter)? = null,
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

    /** Standings computed at endGame (frame_101/frame_143); empty before that. */
    var standings: List<StandingSnapshot> = emptyList()
        private set

    /** frame_51 `KilledPlayers`: enemies defeated during an endurance run. */
    var killedPlayers: Int = 0
        private set

    /**
     * frame_51 `EnduranceLevel`: wave number that scales the CPU AI level.
     * Starts at 1 because GameFactory spawns wave 1 (AS2 InitArea already ran
     * GenerateEndurancePlayer once, incrementing the counter from 0 to 1).
     */
    private var enduranceLevel = 1

    private var countdownAccMs = 0f

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

    /**
     * Port of PlayerDeath (frame_51): versus stock ends when all but one
     * player are eliminated; endurance ends when P1 dies and regenerates the
     * CPU wave otherwise (`GenerateEndurancePlayer`).
     */
    private fun checkDeaths() {
        if (gameType == "endurance") {
            val p1 = fighters.first()
            if (p1.dead) {
                endGame()
                return
            }
            val enemy = fighters.getOrNull(1)
            if (enemy != null && enemy.dead) {
                regenerateEnemy()
            }
            return
        }
        if (limitMode != "stock") return
        val deadCount = fighters.count { it.dead }
        if (fighters.size == 1) {
            if (deadCount == 1) endGame()
        } else if (deadCount == fighters.size - 1) {
            endGame()
        }
    }

    /**
     * Port of the replacement branch of frame_51 GenerateEndurancePlayer:
     * count the kill, scale the AI level with the wave number and swap the
     * CPU fighter for a fresh random roster fighter at SpawnP2 (y = 0).
     */
    private fun regenerateEnemy() {
        killedPlayers++
        enduranceLevel++
        val old = fighters[1]
        // AS2 removes the old Player2 clip + HitTester/FarInd/OSD graphics.
        world.players.remove(old)
        val enemy = enemyFactory?.invoke() ?: return
        enemy.world = world
        // AS2 replacement attachMovie: {_x: SpawnP2._x, _y: 0, PlayNum: 1}.
        val spawnP2 = world.spawnPoints["SpawnP2"] ?: return
        enemy.init(spawnP2.x, 0f)
        enemy.colr = invertColr(fighters[0].colr) // AS2: red unless P1 is red -> blue
        enemy.team = "blue" // AS2: Players[1].Team = "blue"
        enemy.lives = 1 // AS2: Players[1].Lives = 1
        enemy.limitMode = limitMode
        world.addPlayer(enemy)
        fighters[1] = enemy
        // AS2 re-pushes the HitTester onto VCam.FocusObjs at the old depth.
        val focusIndex = camera.focusObjs.indexOf(old)
        if (focusIndex >= 0) {
            camera.focusObjs[focusIndex] = enemy
        } else {
            camera.focusObjs.add(enemy)
        }
        // AS2 reuses Computers[0] and only updates Level/Victim/ForceVictim,
        // which leaves `this.Player` pointing at the removed clip; the port
        // builds a fresh AIControl for the new fighter so the wave actually
        // fights (documented deviation).
        val ai = AIControl(enemy, world)
        ai.level = enduranceLevel.toFloat() // AS2: Computers[0].Level = EnduranceLevel
        ai.forceVictim = true // AS2: Computers[0].ForceVictim = true
        ai.victim = fighters[0] // AS2: Computers[0].Victim = Players[0]
        if (ais.size > 1) {
            ais[1] = ai
        } else {
            ais.add(ai)
        }
        // inputMappers[1] stays null: the wave is AI-driven.
    }

    /** AS2 GenerateEndurancePlayer: `_loc3_ = "red"; if (Players[0].Colr == "red") _loc3_ = "blue"`. */
    private fun invertColr(colr: String): String = if (colr == "red") "blue" else "red"

    /**
     * Port of the end-game results: frame_101 (BestPlayers + WinnerText),
     * frame_143 (Rank = position in that order) and frame_170 (table rows the
     * app's results screen renders). The winner line text is frame_101's
     * `"PLAYER {BestPlayers[0].Index + 1} WINS!"` for both limit modes.
     */
    private fun endGame() {
        sound.stopAll()
        phase = Phase.GAMESET
        standings = computeStandings()
        // frame_101: endurance shows "{KilledPlayers} KILLS!"; versus shows
        // "PLAYER {BestPlayers[0].Index + 1} WINS!" for stock and time.
        gameOverText = if (gameType == "endurance") {
            "$killedPlayers KILLS!"
        } else {
            "PLAYER ${standings.first().playerIndex + 1} WINS!"
        }
    }

    /**
     * Port of frame_101's BestPlayers sort + frame_143's Rank assignment:
     *  - time mode: Score = KOs - Deaths;
     *  - stock mode: Score = DeathOrd, where a player who never died
     *    (`deathOrd == -1`, the AS2 `DeathOrd == undefined`) takes NumDead
     *    (frame_101: `Game.Players[i].DeathOrd = NumDead`) - the highest
     *    possible value, so every survivor ranks above every dead player.
     *
     * Sorted descending; equal scores keep ascending player-index order: the
     * AS2 `sortOn("Score", DESCENDING)` runs over the input array and the port
     * assumes input-order stability among ties, so the lowest player number
     * wins (documented assumption - AVM1 sort stability is unspecified).
     */
    internal fun computeStandings(): List<StandingSnapshot> {
        val numDead = fighters.count { it.dead }
        fun scoreOf(f: Fighter): Int = when {
            limitMode == "time" -> f.kos - f.deaths
            f.deathOrd >= 0 -> f.deathOrd
            else -> numDead
        }
        return fighters.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Fighter>> { (_, f) -> scoreOf(f) }
                    .thenBy { it.index }
            )
            .mapIndexed { position, (index, f) ->
                StandingSnapshot(
                    playerIndex = index,
                    fighterId = f.fighterId,
                    colr = f.colr,
                    rank = position + 1,
                    kos = f.kos,
                    deaths = f.deaths,
                    sds = f.sds,
                    score = scoreOf(f),
                )
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
        standings = standings,
        limitMode = limitMode,
        gameType = gameType,
        killedPlayers = killedPlayers,
        gameTimeSec = gameTime,
        nowMs = nowMs,
    )
}
