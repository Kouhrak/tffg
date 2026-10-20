package com.glorton.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.glorton.android.ui.molecules.CenterBanner
import com.glorton.android.ui.molecules.HudBar
import com.glorton.android.ui.molecules.TouchControls
import com.glorton.android.ui.molecules.TouchInputHolder
import com.glorton.engine.GameConfig
import com.glorton.engine.GameSnapshot

/**
 * ORGANISM: the playfield. Draws the world with the camera transform
 * (exactFit stretch like the original Stage.scaleMode), the HUD in screen
 * space (replacing the AS2 still-objects trick of VCamera) and the touch
 * overlay. v1 renders geometric shapes; SWF sprite art comes later.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val touchHolder = remember { TouchInputHolder() }

    // Emit a new InputState to the ViewModel on every touch change; edge
    // detection happens inside the engine's InputMapper (PlayerControl port).
    LaunchedEffect(touchHolder) {
        snapshotFlow { touchHolder.snapshot() }.collect { input ->
            onIntent(GameIntent.UpdateInput(input))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101820)),
    ) {
        val snapshot = state.snapshot
        if (snapshot != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawWorld(snapshot, size.width, size.height)
            }

            HudBar(
                fighters = snapshot.fighters,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )

            snapshot.countdownText?.let { text ->
                CenterBanner(
                    text = text,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            snapshot.gameOverText?.let { text ->
                CenterBanner(
                    text = text,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (state.isPaused) {
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            TextButton(
                onClick = { onIntent(GameIntent.TogglePause) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Text(text = if (state.isPaused) "▶" else "II", color = Color.White)
            }

            TextButton(
                onClick = { onIntent(GameIntent.ExitToMenu) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(text = "X", color = Color.White)
            }

            TouchControls(
                holder = touchHolder,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Keep the holder zeroed when leaving the match.
    LaunchedEffect(state.snapshot == null) {
        if (state.snapshot == null) {
            touchHolder.left.value = false
            touchHolder.right.value = false
            touchHolder.up.value = false
            touchHolder.down.value = false
            touchHolder.punch.value = false
            touchHolder.special.value = false
            touchHolder.shield.value = false
        }
    }
}

/** World rendering: platforms and fighters through the camera transform. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorld(
    snapshot: GameSnapshot,
    canvasW: Float,
    canvasH: Float,
) {
    val cam = snapshot.camera
    val viewW = GameConfig.VIEW_WIDTH
    val viewH = GameConfig.VIEW_HEIGHT

    // exactFit mapping (same stretch behavior as the original Flash stage
    // over the fixed 600x400 VCam viewport).
    fun sx(worldX: Float): Float = (worldX - cam.x) * canvasW / viewW
    fun sy(worldY: Float): Float = (worldY - cam.y) * canvasH / viewH
    fun sw(worldW: Float): Float = worldW * canvasW / viewW
    fun sh(worldH: Float): Float = worldH * canvasH / viewH

    // Sky with a simple parallax band (placeholder for the Rooftop backdrop).
    drawRect(color = Color(0xFF263238), size = Size(canvasW, canvasH))
    val bandY = sy(snapshot.worldBounds.top + 120f + cam.parallaxY * 0.2f)
    drawRect(
        color = Color(0xFF37474F),
        topLeft = Offset(0f, bandY),
        size = Size(canvasW, sh(60f)),
    )

    // Platforms.
    for (platform in snapshot.platforms) {
        val rect = platform.rect
        drawRect(
            color = Color(0xFF6D4C41),
            topLeft = Offset(sx(rect.left), sy(rect.top)),
            size = Size(sw(rect.width), sh(rect.height)),
        )
    }

    // Items: gold diamond for the Star (only item ported so far).
    for (item in snapshot.items) {
        if (!item.visible) continue
        val half = item.width / 2f
        val path = Path().apply {
            moveTo(sx(item.x), sy(item.y - half))
            lineTo(sx(item.x + half), sy(item.y))
            lineTo(sx(item.x), sy(item.y + half))
            lineTo(sx(item.x - half), sy(item.y))
            close()
        }
        drawPath(path = path, color = Color(0xFFFFD54F))
    }

    // Projectiles: colored rotating rectangles keyed by class name.
    val projectileColors = mapOf(
        "Bullet" to Color(0xFFFFF176),
        "EnergyBall" to Color(0xFF4FC3F7),
        "Rocket" to Color(0xFFE53935),
        "Pencil" to Color(0xFF8D6E63),
        "Poop" to Color(0xFF6D4C41),
        "Garbage" to Color(0xFF90A4AE),
    )
    for (projectile in snapshot.projectiles) {
        val cx = sx(projectile.x)
        val cy = sy(projectile.y)
        val w = sw(projectile.width)
        val h = sh(projectile.height)
        withTransform({ rotate(projectile.rotationDeg, pivot = Offset(cx, cy)) }) {
            drawRect(
                color = projectileColors[projectile.kind] ?: Color.White,
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
            )
        }
    }

    // Explosions: expanding ovals fading out over the blast life.
    for (explosion in snapshot.explosions) {
        val progress = (explosion.ageMs / explosion.lifeMs).coerceIn(0f, 1f)
        val radius = explosion.size * 10f * (1f + progress)
        val alpha = (1f - progress) * 0.8f
        drawOval(
            color = Color(0xFFFF6F00).copy(alpha = alpha),
            topLeft = Offset(sx(explosion.x - radius), sy(explosion.y - radius)),
            size = Size(sw(radius * 2f), sh(radius * 2f)),
        )
        drawOval(
            color = Color(0xFFFFE082).copy(alpha = alpha * 0.8f),
            topLeft = Offset(sx(explosion.x - radius / 2f), sy(explosion.y - radius / 2f)),
            size = Size(sw(radius), sh(radius)),
        )
    }

    // Fighters: feet-anchored boxes (v1 placeholder for the SWF sprites).
    for (fighter in snapshot.fighters) {
        if (!fighter.visible || fighter.dead) continue
        val bodyW = 44f
        val bodyH = 64f
        drawRect(
            color = fighterColor(fighter.colr),
            topLeft = Offset(sx(fighter.x - bodyW / 2f), sy(fighter.y - bodyH)),
            size = Size(sw(bodyW), sh(bodyH)),
        )
        // Facing indicator: a small forward nub so mirroring is visible.
        val nubX = if (fighter.facingRight) fighter.x + bodyW / 2f else fighter.x - bodyW / 2f - 10f
        drawRect(
            color = fighterColor(fighter.colr).copy(alpha = 0.6f),
            topLeft = Offset(sx(nubX), sy(fighter.y - bodyH + 16f)),
            size = Size(sw(10f), sh(10f)),
        )
    }
}
