package com.glorton.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glorton.engine.select.GameType

/**
 * PAGE: the mode menu - frame_42's `Game.Type = "menugame"` attract screen,
 * where the four buttons below PlayerSelect live.
 *
 * Each entry keeps the AS2 rollover copy verbatim (the `Hit.text` assignments
 * in DefineButton2_1011/1016/1022/1006); a mouse rolls over a button, a finger
 * cannot, so the port shows the hint of the mode the user currently has
 * selected instead.
 *
 * `vsmode` (DefineButton2_1022, "every man for himself") is selectable so its
 * copy is readable, but PLAY stays disabled for it: driving four humans needs a
 * local multiplayer input mapping the app does not have yet (GameViewModel
 * feeds a single touch input into Engine.tick).
 */
@Composable
fun MainMenuScreen(
    gameType: String,
    onSelectGameType: (String) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = MODE_BUTTONS.firstOrNull { it.id == gameType } ?: MODE_BUTTONS.first()
    val playEnabled = gameType != GameType.VS_MODE
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "THE FIGHT FOR GLORTON",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Text(
            text = "Android replica",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF90A4AE),
            modifier = Modifier.padding(bottom = 32.dp),
        )
        MODE_BUTTONS.forEach { mode ->
            val isSelected = mode.id == gameType
            if (isSelected) {
                Button(
                    onClick = { onSelectGameType(mode.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(text = mode.label)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectGameType(mode.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(text = mode.label, color = Color(0xFF90A4AE))
                }
            }
        }
        Text(
            text = selected.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF90A4AE),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onPlay, enabled = playEnabled) {
            Text(text = "SELECT FIGHTER")
        }
        if (!playEnabled) {
            Text(
                text = "4 PLAYERS needs local multiplayer input (pending)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF607D8B),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One AS2 mode button: id (`Game.Type`), label and its verbatim rollover copy. */
private data class ModeButton(
    val id: String,
    val label: String,
    val hint: String,
)

private val MODE_BUTTONS = listOf(
    ModeButton(
        id = GameType.ONE_ON_ONE,
        label = "1 VS 1",
        hint = "Play against only one fighter.",
    ),
    ModeButton(
        id = GameType.QUICK,
        label = "QUICK",
        hint = "Want to play right now? Just choose a fighter and you're ready to fight!",
    ),
    ModeButton(
        id = GameType.VS_MODE,
        label = "4 PLAYERS",
        hint = "The classical mode: every man for himself!",
    ),
    ModeButton(
        id = GameType.ENDURANCE,
        label = "ENDURANCE",
        hint = "How long can you last against a never ending flow of enemies?",
    ),
)

/** Human label of an AS2 `Game.Type`; the select screen shows it as its header. */
internal fun gameTypeLabel(gameType: String): String =
    MODE_BUTTONS.firstOrNull { it.id == gameType }?.label ?: gameType.uppercase()
