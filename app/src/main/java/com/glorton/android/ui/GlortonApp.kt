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

private enum class AppScreen { MENU, GAME, RESULTS }

/** Root composable: navigation between the menu, the game and the results. */
@Composable
fun GlortonApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var screen by remember { mutableStateOf(AppScreen.MENU) }
        var selectedFighterId by remember { mutableStateOf("DefaultPlayer") }
        val gameViewModel: GameViewModel = viewModel()
        val gameState by gameViewModel.uiState.collectAsState()

        when (screen) {
            AppScreen.MENU -> MainMenuScreen(
                selectedFighterId = selectedFighterId,
                onSelectFighter = { selectedFighterId = it },
                onPlay = {
                    gameViewModel.onIntent(GameIntent.StartMatch(fighterId = selectedFighterId))
                    screen = AppScreen.GAME
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
