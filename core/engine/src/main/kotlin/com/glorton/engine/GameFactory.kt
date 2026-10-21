package com.glorton.engine

import com.glorton.engine.ai.AIControl
import com.glorton.engine.camera.VCamera
import com.glorton.engine.fighter.AuberginePlayer
import com.glorton.engine.fighter.CoffeePlayer
import com.glorton.engine.fighter.DefaultPlayer
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.PeachPlayer
import com.glorton.engine.fighter.SBLPlayer
import com.glorton.engine.fighter.TrashPlayer
import com.glorton.engine.input.InputMapper
import com.glorton.engine.select.GameType
import com.glorton.engine.stages.createStage
import com.glorton.engine.world.World

/** Declarative description of one slot of the match. */
data class PlayerSpec(
    /** Library link name of the fighter; any of the six ported ids (see GameFactory). */
    val fighterId: String = "DefaultPlayer",
    /** "red" | "blue" | "green" | "orange" (Colors list from frame_42). */
    val colr: String = "red",
    val team: String = "red",
    val ai: Boolean = false,
    val aiLevel: Float = 7f,
)

/**
 * Builds playable matches from a frame_46 roster and a frame_47 stage pick.
 * The class KDoc of each stage records which parts are real AS2 data
 * (bounds/camera/music) and which are approximations (platform rectangles).
 */
object GameFactory {

    /**
     * Ported roster in frame_46 order: `Fighters = [SBL, Peach, Trash, Coffee,
     * Default, Aubergine]`. Mirrors [createFighter]; the frame_46 select screen
     * (`com.glorton.engine.select.CharacterSelect`) offers exactly this list.
     */
    val ROSTER = listOf(
        "SBLPlayer",
        "PeachPlayer",
        "TrashPlayer",
        "CoffeePlayer",
        "DefaultPlayer",
        "AuberginePlayer",
    )

    /**
     * Builds a match on [stage] (one of [com.glorton.engine.stages.STAGES]).
     */
    fun match(
        players: List<PlayerSpec>,
        limitMode: String = "stock",
        limitVal: Int = 3,
        /** AS2 Settings.ItemFrequency (frame_42 default 3; 0 disables item drops). */
        itemFrequency: Int = 3,
        /**
         * AS2 `Game.Type` from the frame_46 mode buttons; see
         * [com.glorton.engine.select.GameType] for the four ids.
         */
        gameType: String = GameType.ONE_ON_ONE,
        /** AS2 `Game.Level`, picked on frame_47; see [com.glorton.engine.stages.STAGES]. */
        stage: String = "Rooftop",
        sound: SoundSink = NoopSoundSink,
    ): Engine {
        // Endurance overrides the limit settings of every other mode: the AS2
        // run ends on P1's first death through its sticky Dead flag, so the
        // port models P1 as a real one-stock elimination (documented in
        // Fighter.die).
        val matchLimitMode = if (gameType == GameType.ENDURANCE) "stock" else limitMode
        val matchLimitVal = if (gameType == GameType.ENDURANCE) 1 else limitVal
        val world: World = createStage(stage)
        val camera = VCamera().apply {
            boundsCam = world.camBounds
        }
        // AS2 Snd is a global singleton; entities without a fighter reference
        // (items, explosions) reach it through World.sound.
        world.sound = sound

        val fighters = mutableListOf<Fighter>()
        val ais = mutableListOf<AIControl?>()
        val mappers = mutableListOf<InputMapper?>()

        // Endurance starts with P1 alone (frame_51 InitArea passes the single
        // Game.NumPlayers roster); the CPU wave comes from GenerateEndurancePlayer.
        val specs = if (gameType == GameType.ENDURANCE) players.take(1) else players
        specs.forEachIndexed { index, spec ->
            // The original attaches players at SpawnP1..SpawnP4 (frame_51 InitArea).
            val spawn = world.spawnPoints["SpawnP${index + 1}"]
                ?: error("Stage is missing SpawnP${index + 1}")
            val fighter = createFighter(index, spec, sound)
            // Fighter.world is set externally (lateinit): init() resets per-match
            // state, so the reference must exist before any tick uses it.
            fighter.world = world
            fighter.init(spawn.x, spawn.y)
            fighter.team = spec.team
            fighter.colr = spec.colr
            fighter.limitMode = matchLimitMode
            if (matchLimitMode == "stock") {
                fighter.lives = matchLimitVal
            } else {
                fighter.lives = -1
            }
            world.addPlayer(fighter)
            fighters.add(fighter)
            if (spec.ai) {
                val ai = AIControl(fighter, world)
                ai.level = spec.aiLevel
                ais.add(ai)
                mappers.add(null)
            } else {
                ais.add(null)
                mappers.add(InputMapper(fighter))
            }
        }

        // frame_51 InitArea endurance: GenerateEndurancePlayer() builds the
        // first CPU wave up front; later waves come from Engine's
        // regenerateEnemy through the factory below.
        var enemyFactory: (() -> Fighter)? = null
        if (gameType == GameType.ENDURANCE && fighters.isNotEmpty()) {
            val p1 = fighters.first()
            enemyFactory = { createEnduranceEnemy(sound) }
            val first = enemyFactory.invoke()
            first.world = world
            // AS2 first-call branch: attachMovie at SpawnP2 with
            // {PlayNum:1, Weight:0.1} - the 0.1 weight applies to the first
            // wave only (a literal AS2 init argument).
            val spawnP2 = world.spawnPoints["SpawnP2"]
                ?: error("Stage is missing SpawnP2")
            first.init(spawnP2.x, spawnP2.y)
            first.colr = invertColr(p1.colr) // AS2: red unless P1 is red -> blue
            first.team = "blue" // AS2: Players[1].Team = "blue"
            first.lives = 1 // AS2: Players[1].Lives = 1
            first.limitMode = matchLimitMode
            first.weight = 0.1f // AS2: {Weight:0.1} first wave only
            world.addPlayer(first)
            fighters.add(first)
            val ai = AIControl(first, world)
            ai.level = 1f // AS2: Computers[0].Level = 1 on the first call
            ai.forceVictim = true // AS2: Computers[0].ForceVictim = true
            ai.victim = p1 // AS2: Computers[0].Victim = Players[0]
            ais.add(ai)
            mappers.add(null)
        }

        // V1 focus: every fighter from the start (frame_51 reveals them
        // progressively during the countdown; camera pop-in comes later).
        for (fighter in fighters) {
            camera.focusObjs.add(fighter)
        }
        camera.init()
        world.camera = camera

        // frame_51: Snd.PlaySound(Game.Level, true) - looped stage music, keyed
        // by the stage id itself (see com.glorton.engine.stages.STAGES).
        sound.play(stage, loop = true)

        // frame_51 InitArea: ItemGen.init([Mine,Grenade], Settings.ItemFrequency).
        val itemGen = com.glorton.engine.item.ItemGen(world = world, frequency = itemFrequency)

        return Engine(
            world = world,
            camera = camera,
            sound = sound,
            fighters = fighters,
            ais = ais,
            inputMappers = mappers,
            itemGen = itemGen,
            gameType = gameType,
            enemyFactory = enemyFactory,
            limitMode = matchLimitMode,
            limitVal = matchLimitVal,
        )
    }

    /** AS2 GenerateEndurancePlayer: `Fighters[random(Fighters.length)]` at PlayNum 1. */
    private fun createEnduranceEnemy(sound: SoundSink): Fighter =
        createFighter(
            index = 1,
            spec = PlayerSpec(fighterId = ROSTER.random(), team = "blue"),
            sound = sound,
        )

    /** AS2 GenerateEndurancePlayer: enemy color is red unless P1 is red (then blue). */
    private fun invertColr(colr: String): String = if (colr == "red") "blue" else "red"

    private fun createFighter(index: Int, spec: PlayerSpec, sound: SoundSink): Fighter =
        when (spec.fighterId) {
            "SBLPlayer" -> SBLPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            "PeachPlayer" -> PeachPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            "TrashPlayer" -> TrashPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            "CoffeePlayer" -> CoffeePlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            "AuberginePlayer" -> AuberginePlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            "DefaultPlayer" -> DefaultPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            // Roster from frame_46: Fighters = [SBL, Peach, Trash, Coffee,
            // Default, Aubergine] - all six are ported. Unknown ids fall back
            // to DefaultPlayer so a bad spec never kills the match.
            else -> DefaultPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
        }
}
