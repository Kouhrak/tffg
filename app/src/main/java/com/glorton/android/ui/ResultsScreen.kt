package com.glorton.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glorton.engine.GameSnapshot

/**
 * Port of the end-of-match results scene: frame_101's WinnerText on top and
 * frame_170's table below (legend column + one column per player, ranks from
 * frame_143).
 *
 * Column layouts are verbatim frame_170:
 *  - stock: legend "Rank / (blank) / KOs / (blank) / Suicides" with
 *    "P{n} / Rank / (blank) / KOs / (blank) / SDs";
 *  - time: legend "Rank / KOs / Deaths / (blank) / Score" with
 *    "P{n} / Rank / +KOs / -Deaths / (blank) / Score";
 *  - endurance: legend "Kills / (blank) / Deaths / (blank) / Time" with a
 *    single P1 column; the clock is frame_170's GameTime as "M:SS"
 *    (`Mins + ":" + (Secs >= 10 ? Secs : "0" + Secs)`).
 *
 * The fighter name caption under each P-column is a port extension (the SWF
 * shows the fighter sprite on the podium instead, which needs extracted art).
 */
@Composable
fun ResultsScreen(
    snapshot: GameSnapshot?,
    onContinue: () -> Unit,
) {
    val standings = snapshot?.standings.orEmpty()
    val limitMode = snapshot?.limitMode ?: "stock"
    val isEndurance = snapshot?.gameType == "endurance"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = snapshot?.gameOverText.orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Top) {
            val legend = when {
                isEndurance -> listOf("Kills", "", "Deaths", "", "Time")
                limitMode == "time" -> listOf("Rank", "KOs", "Deaths", "", "Score")
                else -> listOf("Rank", "", "KOs", "", "Suicides")
            }
            ResultColumn(header = null, fighterId = null, colr = null, cells = legend, isLegend = true)
            // Endurance tracks only P1 against the CPU waves (frame_170 prints
            // a single P1 column when Game.Type == "endurance").
            val columns = if (isEndurance) standings.take(1) else standings
            columns.forEach { standing ->
                ResultColumn(
                    header = "P${standing.playerIndex + 1}",
                    fighterId = standing.fighterId,
                    colr = standing.colr,
                    cells = when {
                        isEndurance -> listOf(
                            snapshot?.killedPlayers?.toString() ?: "0",
                            "",
                            standing.deaths.toString(),
                            "",
                            gameTimeClock(snapshot?.gameTimeSec ?: 0),
                        )
                        limitMode == "time" -> listOf(
                            standing.rank.toString(),
                            "+${standing.kos}",
                            "-${standing.deaths}",
                            "",
                            standing.score.toString(),
                        )
                        else -> listOf(
                            standing.rank.toString(),
                            "",
                            standing.kos.toString(),
                            "",
                            standing.sds.toString(),
                        )
                    },
                    isLegend = false,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onContinue) {
            Text(text = "CONTINUE")
        }
    }
}

/**
 * frame_170's clock expression: `var Secs = GameTime % 60; var Mins =
 * (GameTime - Secs) / 60` rendered as `Mins + ":" + (Secs >= 10 ? Secs : "0" + Secs)`.
 */
private fun gameTimeClock(gameTimeSec: Int): String {
    val secs = gameTimeSec % 60
    val mins = (gameTimeSec - secs) / 60
    return "$mins:${if (secs >= 10) secs else "0$secs"}"
}

/** One table column: optional "P{n}" header with the player-color dot, then fixed-height cells. */
@Composable
private fun ResultColumn(
    header: String?,
    fighterId: String?,
    colr: String?,
    cells: List<String>,
    isLegend: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.height(32.dp),
        ) {
            if (header != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (colr != null) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(playerColor(colr)),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = header, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (fighterId != null) {
            Text(
                text = fighterId.removeSuffix("Player"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
        }
        cells.forEach { cell ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isLegend) Color.Gray else Color.White,
                )
            }
        }
    }
}

/** AS2 frame_51 `Colors = ["red","blue","green","orange"]` player tints. */
private fun playerColor(colr: String): Color = when (colr) {
    "red" -> Color(0xFFE53935)
    "blue" -> Color(0xFF1E88E5)
    "green" -> Color(0xFF43A047)
    "orange" -> Color(0xFFFB8C00)
    else -> Color.White
}
