package com.glorton.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PAGE: main menu (v1). The original boots into a "menugame" attract mode
 * (frame_42 sets Game.Type = "menugame"); the full menu flow with character
 * and stage select is ported in a later slice.
 */
@Composable
fun MainMenuScreen(
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
        Button(onClick = onPlay) {
            Text(text = "PLAY  (P1 vs CPU - Rooftop)")
        }
    }
}
