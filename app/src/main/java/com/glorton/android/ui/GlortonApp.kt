package com.glorton.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glorton.engine.select.CharacterSelect
import com.glorton.engine.select.GameType
import com.glorton.engine.stages.STAGES

private enum class AppScreen { MENU, SELECT, STAGE_SELECT, GAME, RESULTS }

/**
 * Root composable: navigation between the frame_42 mode menu, the frame_46
 * player select, the frame_47 stage select, the game and the frame_170 results.
 */
@Composable
fun GlortonApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var screen by remember { mutableStateOf(AppScreen.MENU) }
        // AS2 Game.Type, chosen by the four buttons above PlayerSelect.
        var selectedGameType by remember { mutableStateOf(GameType.ONE_ON_ONE) }
        // frame_46's Players[] state. Rebuilt every time the screen opens,
        // which is when AS2 re-attaches the boxes and re-rolls the CPU fighters.
        var selectState by remember { mutableStateOf(CharacterSelect.create(GameType.ONE_ON_ONE)) }
        // AS2 Game.Level. frame_42 boots it to a random Stages entry, but
        // SelectStage always overwrites it, so pre-selecting the first thumb
        // (Rooftop) only decides what FIGHT would start right now.
        var selectedStage by remember { mutableStateOf(STAGES.first()) }
        val gameViewModel: GameViewModel = viewModel()
        val gameState by gameViewModel.uiState.collectAsState()

        when (screen) {
            AppScreen.MENU -> MainMenuScreen(
                gameType = selectedGameType,
                onSelectGameType = { selectedGameType = it },
                onPlay = {
                    selectState = CharacterSelect.create(selectedGameType)
                    screen = AppScreen.SELECT
                },
            )
            AppScreen.SELECT -> PlayerSelectScreen(
                gameType = selectedGameType,
                select = selectState,
                onSelectFighter = { fighterId ->
                    selectState = selectState.selectFighter(fighterId)
                },
                onArmSlot = { index -> selectState = selectState.armSlot(index) },
                onAdjustAiLevel = { index, delta ->
                    selectState = selectState.adjustAiLevel(index, delta)
                },
                onBack = { screen = AppScreen.MENU },
                // frame_46's gotoStageSelect() lands on the frame_47 stage
                // picker; the roster is only handed to the match afterwards.
                onPlay = { screen = AppScreen.STAGE_SELECT },
            )
            AppScreen.STAGE_SELECT -> StageSelectScreen(
                stage = selectedStage,
                onSelectStage = { selectedStage = it },
                onBack = { screen = AppScreen.SELECT },
                onPlay = {
                    // frame_47 SelectStage() -> frame_51 InitArea.
                    val players = selectState.confirm()
                    if (players != null) {
                        gameViewModel.onIntent(
                            GameIntent.StartMatch(
                                gameType = selectedGameType,
                                players = players,
                                stage = selectedStage,
                            ),
                        )
                        screen = AppScreen.GAME
                    }
                },
            )
            AppScreen.GAME -> {
                GameScreen(
                    state = gameState,
                    onIntent = { intent ->
                        gameViewModel.onIntent(intent)
                        if (intent is GameIntent.ExitToMenu) {
                            screen = AppScreen.MENU
                        } else if (intent is GameIntent.ShowResults) {
                            screen = AppScreen.RESULTS
                        }
                    },
                )
            }
            AppScreen.RESULTS -> {
                ResultsScreen(
                    snapshot = gameState.snapshot,
                    onContinue = {
                        gameViewModel.onIntent(GameIntent.ExitToMenu)
                        screen = AppScreen.MENU
                    },
                )
            }
        }
    }
}
