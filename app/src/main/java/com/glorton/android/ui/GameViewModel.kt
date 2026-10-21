package com.glorton.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glorton.android.audio.AndroidSoundSink
import com.glorton.engine.Engine
import com.glorton.engine.GameConfig
import com.glorton.engine.GameFactory
import com.glorton.engine.GameSnapshot
import com.glorton.engine.InputState
import com.glorton.engine.PlayerSpec
import com.glorton.engine.select.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** MVI intents of the game screen. */
sealed interface GameIntent {
    /**
     * Starts the frame_51 match from the frame_46/47 select result.
     * [gameType] is the AS2 `Game.Type`; [stage] is the AS2 `Game.Level` picked
     * on frame_47; [players] is what `CharacterSelect.confirm()` produced
     * (only the slots that can fight).
     */
    data class StartMatch(
        val gameType: String = GameType.ONE_ON_ONE,
        val players: List<PlayerSpec> = emptyList(),
        val stage: String = "Rooftop",
    ) : GameIntent
    data object TogglePause : GameIntent
    data object ExitToMenu : GameIntent
    /** Shows the frame_170 results screen after GAME SET (navigation only). */
    data object ShowResults : GameIntent
    data class UpdateInput(val input: InputState) : GameIntent
}

/** MVI state of the game screen. */
data class GameUiState(
    val snapshot: GameSnapshot? = null,
    val isPaused: Boolean = false,
    val input: InputState = InputState.NONE,
)

/**
 * Holds the [Engine] and drives it at the original 40Hz fixed step
 * (GameConfig.TICK_MS = 25, the AS2 IntTime from frame_51) on a background
 * dispatcher, publishing immutable snapshots for the Compose renderer.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val soundSink = AndroidSoundSink(application).also { it.preload() }

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var engine: Engine? = null
    private var loopJob: Job? = null

    private val inputFlow = MutableStateFlow(InputState.NONE)

    fun onIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.StartMatch ->
                startMatch(intent.gameType, intent.players, intent.stage)
            GameIntent.TogglePause -> togglePause()
            GameIntent.ExitToMenu -> exitToMenu()
            // Navigation-only: GlortonApp switches to the results screen; the
            // standings travel inside the last published GameSnapshot.
            GameIntent.ShowResults -> Unit
            is GameIntent.UpdateInput -> inputFlow.update { intent.input }
        }
    }

    private fun startMatch(gameType: String, players: List<PlayerSpec>, stage: String) {
        // The select screen refuses to start an empty roster (frame_46's
        // `return undefined`); keep the guard so a stray intent cannot build a
        // match nobody can fight.
        if (players.isEmpty()) return
        stopLoop()
        val match = GameFactory.match(
            players = players,
            gameType = gameType,
            stage = stage,
            limitMode = "stock",
            limitVal = 3,
            sound = soundSink,
        )
        engine = match
        _uiState.update { GameUiState(snapshot = match.snapshot()) }
        loopJob = viewModelScope.launch(Dispatchers.Default) {
            val tickNanos = GameConfig.TICK_MS * 1_000_000L
            var last = System.nanoTime()
            var accumulator = 0L
            while (isActive) {
                val now = System.nanoTime()
                accumulator += now - last
                last = now
                var steps = 0
                val current = engine ?: break
                while (accumulator >= tickNanos && steps < MAX_CATCHUP_STEPS) {
                    current.tick(listOf(inputFlow.value))
                    accumulator -= tickNanos
                    steps++
                }
                _uiState.update { it.copy(snapshot = current.snapshot(), input = inputFlow.value) }
            }
        }
    }

    private fun togglePause() {
        val current = engine ?: return
        current.paused = !current.paused
        _uiState.update { it.copy(isPaused = current.paused) }
    }

    private fun exitToMenu() {
        stopLoop()
        engine = null
        inputFlow.value = InputState.NONE
        _uiState.update { GameUiState() }
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    override fun onCleared() {
        stopLoop()
        soundSink.release()
        super.onCleared()
    }

    companion object {
        /** Drop up to 4 catch-up ticks after a stall to avoid a spiral of death. */
        private const val MAX_CATCHUP_STEPS = 4
    }
}
