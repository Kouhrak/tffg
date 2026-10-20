package com.glorton.android.ui.molecules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.glorton.android.ui.atoms.TouchButton
import com.glorton.engine.InputState

/**
 * MOLECULE: on-screen controls mirroring the original keyboard layout
 * (frame_42): directions on the left; punch / special / shield on the right.
 * The holder is screen-space state; the ViewModel forwards a snapshot of it
 * to the engine every tick, where PlayerControl semantics are applied.
 */
class TouchInputHolder {
    val left = mutableStateOf(false)
    val right = mutableStateOf(false)
    val up = mutableStateOf(false)
    val down = mutableStateOf(false)
    val punch = mutableStateOf(false)
    val special = mutableStateOf(false)
    val shield = mutableStateOf(false)

    fun snapshot(): InputState = InputState(
        left = left.value,
        right = right.value,
        up = up.value,
        down = down.value,
        punch = punch.value,
        special = special.value,
        shield = shield.value,
    )
}

@Composable
fun TouchControls(
    holder: TouchInputHolder,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        // D-pad.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TouchButton(label = "^", color = Color(0xFF9E9E9E), onPressed = { holder.up.value = it })
            Row {
                TouchButton(label = "<", color = Color(0xFF9E9E9E), onPressed = { holder.left.value = it })
                Box(modifier = Modifier.padding(8.dp))
                TouchButton(label = ">", color = Color(0xFF9E9E9E), onPressed = { holder.right.value = it })
            }
            TouchButton(label = "v", color = Color(0xFF9E9E9E), onPressed = { holder.down.value = it })
        }

        // Action buttons.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                TouchButton(label = "SP", color = Color(0xFFFFB300), onPressed = { holder.special.value = it })
                Box(modifier = Modifier.padding(8.dp))
                TouchButton(label = "SH", color = Color(0xFF00ACC1), onPressed = { holder.shield.value = it })
            }
            TouchButton(label = "PU", color = Color(0xFFE53935), onPressed = { holder.punch.value = it })
        }
    }
}
