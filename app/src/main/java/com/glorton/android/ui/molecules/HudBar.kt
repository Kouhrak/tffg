package com.glorton.android.ui.molecules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glorton.android.ui.fighterColor
import com.glorton.engine.FighterSnapshot

/**
 * MOLECULE: per-player HUD entry (v1 port of the OSD.as essentials: color
 * coin, stock lives and damage percentage).
 */
@Composable
fun HudEntry(
    fighter: FighterSnapshot,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(fighterColor(fighter.colr)),
        )
        Text(
            text = "P${fighter.playNum + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
        if (fighter.lives >= 0) {
            Text(
                text = "x${fighter.lives}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        Text(
            text = "${fighter.damagePct.toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = if (fighter.damagePct >= 100f) Color(0xFFFF5252) else Color.White,
        )
    }
}

/** MOLECULE: full OSD bar with one entry per fighter. */
@Composable
fun HudBar(
    fighters: List<FighterSnapshot>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        fighters.forEach { fighter ->
            HudEntry(fighter = fighter)
        }
    }
}

/** Molecule: large centered status text (countdown / game set). */
@Composable
fun CenterBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
        )
    }
}
