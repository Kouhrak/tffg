package com.glorton.engine.select

/**
 * AS2 `Game.Type` ids. frame_42 boots with [MENU_GAME] (the attract match) and
 * the four mode buttons that open PlayerSelect overwrite it before frame_46
 * runs, so the id that reaches the engine is always one of the four below.
 */
object GameType {
    /** DefineButton2_1011: `NumGamers=1, NumPlayers=2, Rand.CP=1`. */
    const val ONE_ON_ONE = "oneonone"

    /** DefineButton2_1016: `NumGamers=1, NumPlayers=4, Rand.CP=3` ("play right now"). */
    const val QUICK = "quick"

    /** DefineButton2_1022: `NumGamers=4, NumPlayers=4` - pending local multiplayer input. */
    const val VS_MODE = "vsmode"

    /** DefineButton2_1006: `NumGamers=1, NumPlayers=1` - endless CPU waves. */
    const val ENDURANCE = "endurance"

    /** frame_42 `Game.Type` at boot: the menu's IA-vs-IA demo (not ported). */
    const val MENU_GAME = "menugame"
}

/** AS2 `Game.Mode` - orthogonal to [GameType]; gates the PlayerPoseBox Toggle. */
object GameMode {
    /** frame_42 default: slots are fixed by the mode button, Toggle is a no-op. */
    const val SINGLE = "single"

    /** DefineButton2_974: local multiplayer menu, where the Toggle is live. */
    const val MULTI = "multi"
}

/** frame_42 `Colors`: index -> color name; slot index also picks the Team color. */
val COLORS = listOf("red", "blue", "green", "orange")

/** frame_46 slot layout derived from a mode button's `Game.Num*` assignments. */
data class ModeLayout(
    /** `Game.NumGamers` - leading human slots. */
    val numGamers: Int,
    /** `Game.NumPlayers` - total PlayerPoseBox slots. */
    val numPlayers: Int,
    /**
     * `Game.Rand.CP`. `Game.Rand` is an object only for the modes that set it,
     * and frame_46 rolls a random CPU fighter exactly when `Game.Rand != undefined`.
     */
    val randCp: Int,
) {
    /** frame_46: `Computers = Game.NumPlayers - Game.NumGamers`. */
    val numComputers: Int get() = numPlayers - numGamers

    /** Whether CPU slots roll their fighter at select time (`Game.Rand != undefined`). */
    val rollsCpuFighters: Boolean get() = randCp > 0
}

/** Port of the four mode buttons above PlayerSelect (frame_46's entry points). */
fun modeLayout(gameType: String): ModeLayout = when (gameType) {
    GameType.ONE_ON_ONE -> ModeLayout(numGamers = 1, numPlayers = 2, randCp = 1)
    GameType.QUICK -> ModeLayout(numGamers = 1, numPlayers = 4, randCp = 3)
    GameType.VS_MODE -> ModeLayout(numGamers = 4, numPlayers = 4, randCp = 0)
    GameType.ENDURANCE -> ModeLayout(numGamers = 1, numPlayers = 1, randCp = 0)
    else -> error("Unknown AS2 Game.Type: $gameType")
}
