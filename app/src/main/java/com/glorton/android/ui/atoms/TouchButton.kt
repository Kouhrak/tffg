package com.glorton.android.ui.atoms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * ATOM: round touch button used by the on-screen controls. Reports press and
 * release through [onPressed] so the input mapper can treat it exactly like a
 * keyboard key (edge detection happens in the engine's InputMapper).
 */
@Composable
fun TouchButton(
    label: String,
    color: Color,
    onPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.55f),
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        tryAwaitRelease()
                        onPressed(false)
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
    }
}
