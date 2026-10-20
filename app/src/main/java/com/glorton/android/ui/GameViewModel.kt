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
    /** Starts a match with [fighterId] chosen by P1 on the menu. */
    data class StartMatch(val fighterId: String = "DefaultPlayer") : GameIntent
    data object TogglePause : GameIntent
    data object ExitToMenu : GameIntent
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
            is GameIntent.StartMatch -> startMatch(intent.fighterId)
            GameIntent.TogglePause -> togglePause()
            GameIntent.ExitToMenu -> exitToMenu()
            is GameIntent.UpdateInput -> inputFlow.update { intent.input }
        }
    }

    private fun startMatch(fighterId: String) {
        stopLoop()
        // P1 picks the fighter on the menu; the CPU takes the first roster
        // entry that differs, so any pick can be playtested against variety
        // (the original assigns both fighters in frame_46 select).
        val cpuFighterId = GameFactory.ROSTER.firstOrNull { it != fighterId } ?: fighterId
        val match = GameFactory.rooftopMatch(
            players = listOf(
                PlayerSpec(fighterId = fighterId, colr = "red", team = "red", ai = false),
                PlayerSpec(fighterId = cpuFighterId, colr = "blue", team = "blue", ai = true, aiLevel = 7f),
            ),
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
