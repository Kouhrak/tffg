package com.glorton.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glorton.engine.GameFactory

/**
 * PAGE: main menu (v1). The original boots into a "menugame" attract mode
 * (frame_42 sets Game.Type = "menugame"); the full menu flow with character
 * and stage select is ported in a later slice.
 *
 * The fighter row is a minimal stand-in for frame_46 character select: it
 * offers the ported roster ([GameFactory.ROSTER]) so every fighter can be
 * playtested; the real select screen (colors, both players) comes later.
 */
@Composable
fun MainMenuScreen(
    selectedFighterId: String,
    onSelectFighter: (String) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameFactory.ROSTER.forEach { fighterId ->
                val selected = fighterId == selectedFighterId
                if (selected) {
                    Button(onClick = { onSelectFighter(fighterId) }) {
                        Text(text = fighterId.removeSuffix("Player"))
                    }
                } else {
                    OutlinedButton(onClick = { onSelectFighter(fighterId) }) {
                        Text(
                            text = fighterId.removeSuffix("Player"),
                            color = Color(0xFF90A4AE),
                        )
                    }
                }
            }
        }
        Button(onClick = onPlay) {
            Text(text = "PLAY  (${selectedFighterId.removeSuffix("Player")} vs CPU - Rooftop)")
        }
    }
}
