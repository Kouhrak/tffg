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
import com.glorton.engine.stages.RooftopStage
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
 * Builds playable matches. V1 wires the Rooftop stage with DefaultPlayer
 * fighters; more characters and stages are ported later.
 */
object GameFactory {

    /**
     * Ported roster in frame_46 order: `Fighters = [SBL, Peach, Trash, Coffee,
     * Default, Aubergine]`. Mirrors `createFighter`; used by the app to offer
     * character selection before the full frame_46 menu is ported.
     */
    val ROSTER = listOf(
        "SBLPlayer",
        "PeachPlayer",
        "TrashPlayer",
        "CoffeePlayer",
        "DefaultPlayer",
        "AuberginePlayer",
    )

    fun rooftopMatch(
        players: List<PlayerSpec>,
        limitMode: String = "stock",
        limitVal: Int = 3,
        /** AS2 Settings.ItemFrequency (frame_42 default 3; 0 disables item drops). */
        itemFrequency: Int = 3,
        sound: SoundSink = NoopSoundSink,
    ): Engine {
        val world: World = RooftopStage.create()
        val camera = VCamera().apply {
            boundsCam = world.camBounds
        }
        // AS2 Snd is a global singleton; entities without a fighter reference
        // (items, explosions) reach it through World.sound.
        world.sound = sound

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
            limitMode = limitMode,
            limitVal = limitVal,
        )
    }

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
