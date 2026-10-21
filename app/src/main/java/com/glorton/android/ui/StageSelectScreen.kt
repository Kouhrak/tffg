package com.glorton.android.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glorton.engine.stages.STAGES

/**
 * PAGE: frame_47 (the frame labelled "StageSelect") - the stage picker that
 * sits between the character select and the match.
 *
 * frame_47 lays its thumbnails out with `xP = (i <= 2 ? i : i - 3) * 550 / 3 + 30`
 * and `yP = i > 2 ? 210 : 90`: three on a first row, whatever spills over onto a
 * second row, left aligned. That grouping is kept (three thumbs, then the rest);
 * the exact Flash coordinates are not, because the port draws with Compose dp
 * rather than on a fixed 600x400 stage. The formula itself lives in
 * `stageThumbPositions()` where it is unit tested.
 *
 * Tapping a thumb runs AS2 `SelectStage(StageName)`: it stores the name into
 * `Game.Level` and only then does frame_51 pick `Bounds`/`BoundsCam` and the
 * looped music for that stage. The thumbnails are the real exported SWF art
 * (`DefineSprite_718/835/879/905_*_Thumb`), not placeholders.
 *
 * frame_47 also carries a `Game.Rand.Level` shortcut straight to a random stage;
 * nothing in the decompiled scripts ever sets that flag, so the port omits it
 * (see `stageThumbPositions()`).
 */
@Composable
fun StageSelectScreen(
    stage: String,
    onSelectStage: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onBack) {
                Text(text = "‹ BACK", color = Color(0xFF90A4AE))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SELECT STAGE",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Balances the back button so the title stays centred.
            Text(text = "‹ BACK", color = Color.Transparent)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            STAGES.take(3).forEach { name ->
                StageThumb(name = name, selected = name == stage, onClick = { onSelectStage(name) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            STAGES.drop(3).forEach { name ->
                StageThumb(name = name, selected = name == stage, onClick = { onSelectStage(name) })
            }
        }

        Text(
            text = "Bounds, camera and music follow the stage id, exactly as " +
                "frame_51 resolves `Game.Level`.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF607D8B),
            modifier = Modifier.padding(top = 16.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Text(text = "FIGHT")
        }
    }
}

/** One frame_47 StageThumb: real exported art inside the selection frame. */
@Composable
private fun StageThumb(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val art = remember(context, name) { loadThumb(context, name) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(104.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C2833))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color.White else Color(0xFF455A64),
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick),
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Asset missing at runtime: keep the screen usable instead of
                // showing an empty tile.
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF90A4AE),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFF90A4AE),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Loads `stages/{name}.png` (lowercased asset path) from the exported
 * `DefineSprite_*_Thumb` art. Returns null when the asset is missing or
 * undecodable so the caller can fall back to a text label.
 */
private fun loadThumb(context: Context, name: String): ImageBitmap? =
    runCatching {
        context.assets.open("stages/${name.lowercase()}.png").use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
