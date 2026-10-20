package com.glorton.engine

import com.glorton.engine.ai.AIControl
import com.glorton.engine.camera.VCamera
import com.glorton.engine.fighter.DefaultPlayer
import com.glorton.engine.fighter.Fighter
import com.glorton.engine.fighter.FighterStats
import com.glorton.engine.input.InputMapper
import com.glorton.engine.stages.RooftopStage
import com.glorton.engine.world.World

/** Declarative description of one slot of the match. */
data class PlayerSpec(
    /** Library link name of the fighter; only "DefaultPlayer" is wired in v1. */
    val fighterId: String = "DefaultPlayer",
    /** "red" | "blue" | "green" | "orange" (Colors list from frame_42). */
    val colr: String = "red",
    val team: String = "red",
    val ai: Boolean = false,
    val aiLevel: Float = 7f,
)

/**
 * Builds playable matches. V1 wires the Rooftop stage with DefaultPlayer
 * fighters; more characters and stages are ported later.
 */
object GameFactory {

    fun rooftopMatch(
        players: List<PlayerSpec>,
        limitMode: String = "stock",
        limitVal: Int = 3,
        sound: SoundSink = NoopSoundSink,
    ): Engine {
        val world: World = RooftopStage.create()
        val camera = VCamera().apply {
            boundsCam = world.camBounds
        }

        val fighters = mutableListOf<Fighter>()
        val ais = mutableListOf<AIControl?>()
        val mappers = mutableListOf<InputMapper?>()

        players.forEachIndexed { index, spec ->
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
            fighter.limitMode = limitMode
            if (limitMode == "stock") {
                fighter.lives = limitVal
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

        // V1 focus: every fighter from the start (frame_51 reveals them
        // progressively during the countdown; camera pop-in comes later).
        for (fighter in fighters) {
            camera.focusObjs.add(fighter)
        }
        camera.init()
        world.camera = camera

        // frame_51: Snd.PlaySound(Game.Level, true) — looped stage music.
        sound.play("Rooftop", loop = true)

        return Engine(
            world = world,
            camera = camera,
            sound = sound,
            fighters = fighters,
            ais = ais,
            inputMappers = mappers,
            limitMode = limitMode,
            limitVal = limitVal,
        )
    }

    private fun createFighter(index: Int, spec: PlayerSpec, sound: SoundSink): Fighter =
        when (spec.fighterId) {
            // TODO: wire the remaining fighters as they are ported
            // (SBLPlayer, PeachPlayer, TrashPlayer, CoffeePlayer, AuberginePlayer).
            "DefaultPlayer" -> DefaultPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
            else -> DefaultPlayer(
                playNum = index,
                fighterId = spec.fighterId,
                sound = sound,
            )
        }
}
