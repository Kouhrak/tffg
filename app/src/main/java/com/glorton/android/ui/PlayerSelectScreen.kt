package com.glorton.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glorton.engine.GameFactory
import com.glorton.engine.select.CharacterSelect
import com.glorton.engine.select.COLORS
import com.glorton.engine.select.SelectSlot

/**
 * PAGE: frame_46 (the frame labelled "PlayerSelect") - fighter + color select.
 *
 * Layout follows the original: a row of PlayerCoins, then the six FighterBoxes
 * (`x = i * 94 + 6, y = 70`), then one PlayerPoseBox per slot
 * (`x = i * (550 / N) + 25, y = 188, Team = Colors[i]`).
 *
 * Ported interactions:
 *  - FighterBox tap -> `SelectFighter(Fighters[i], armedSlot)`; the AS2 original
 *    drops PlayerCoin over the box instead, which touch cannot express, so the
 *    coin is armed by tapping it first (see [CharacterSelect]).
 *  - Coin color is `Colors[i]` (the slot's Team color); the pose box label is
 *    `P{n}` for humans and `CP` for computers, plus `Disabled` when the Toggle
 *    turned the slot off.
 *  - The AISetter `+`/`-` on CP slots ports DefineButton2_805/804 (clamp 1..20).
 *  - The pose itself is not drawn: `FighterBox_Pose` / the 350%-scaled embed
 *    need extracted SWF art, which is still a pending item. Fighters are named
 *    instead, and `FName.text = Fighter` is the AS2 equivalent field.
 */
@Composable
fun PlayerSelectScreen(
    gameType: String,
    select: CharacterSelect,
    onSelectFighter: (String) -> Unit,
    onArmSlot: (Int) -> Unit,
    onAdjustAiLevel: (Int, Int) -> Unit,
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
                text = gameTypeLabel(gameType),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Text(
            text = "Tap a coin to arm its slot, then tap a fighter. " +
                "(The original drags the coin over the fighter instead.)",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF607D8B),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // PlayerCoin row: one draggable token per slot in the AS2 original.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            select.slots.forEachIndexed { index, slot ->
                PlayerCoin(
                    label = "P${index + 1}",
                    teamIndex = index,
                    armed = select.armedSlot == index,
                    onClick = { onArmSlot(index) },
                )
            }
        }

        // FighterBox row: the six frame_42 Fighters in roster order.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            GameFactory.ROSTER.forEach { fighterId ->
                FighterBox(
                    fighterId = fighterId,
                    selected = select.slots.getOrNull(select.armedSlot)?.fighterId == fighterId,
                    onClick = { onSelectFighter(fighterId) },
                )
            }
        }

        select.slots.forEachIndexed { index, slot ->
            PlayerSlotRow(
                index = index,
                slot = slot,
                onAdjustAiLevel = onAdjustAiLevel,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onPlay,
            // AS2 `gotoStageSelect` bails out with `return undefined` when no
            // slot has a fighter; the port keeps you on this screen instead.
            enabled = select.confirm() != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "FIGHT")
        }
    }
}

/** PlayerCoin: team-colored token with the AS2 `PNum.text` label. */
@Composable
private fun PlayerCoin(
    label: String,
    teamIndex: Int,
    armed: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fighterColor(COLORS[teamIndex]))
            .border(
                width = if (armed) 3.dp else 1.dp,
                color = if (armed) Color.White else Color(0x66FFFFFF),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** FighterBox: one entry of the fighter row; highlights for the armed slot. */
@Composable
private fun FighterBox(
    fighterId: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF37474F) else Color(0xFF1C2833),
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color.White else Color(0xFF455A64),
        ),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .width(96.dp)
            .height(64.dp),
    ) {
        Text(
            text = fighterId.removeSuffix("Player"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** PlayerPoseBox: slot label, current fighter, SetColor dot and AISetter. */
@Composable
private fun PlayerSlotRow(
    index: Int,
    slot: SelectSlot,
    onAdjustAiLevel: (Int, Int) -> Unit,
) {
    val label = when {
        // AS2 frame_46 only shows "Disabled" through gotoAndStop("Disabled").
        !slot.enabled -> "DISABLED"
        slot.cp -> "CP"
        else -> "P${index + 1}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = fighterColor(COLORS[index]).copy(alpha = if (slot.enabled) 0.18f else 0.06f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        // PlayerPoseBox frame is the Team color: Colors[i] for this slot.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fighterColor(COLORS[index])),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (slot.enabled) Color.White else Color(0xFF607D8B),
        )
        Spacer(modifier = Modifier.width(12.dp))
        // `FName.text = Fighter` - the AS2 field the original writes here.
        Text(
            text = slot.fighterId?.removeSuffix("Player") ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (slot.fighterId == null) Color(0xFF607D8B) else Color.White,
            modifier = Modifier.weight(1f),
        )
        // SetColor result (Embed.Colr), shown as the coin-style color dot.
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(fighterColor(slot.colr))
                .border(1.dp, Color(0x66FFFFFF), CircleShape),
        )
        if (slot.cp && slot.enabled) {
            // AISetter (DefineButton2_804/805); AS2 hides it on human slots.
            TextButton(onClick = { onAdjustAiLevel(index, -1) }) {
                Text(text = "−", color = Color(0xFF90A4AE))
            }
            Text(
                text = "${slot.level}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            TextButton(onClick = { onAdjustAiLevel(index, +1) }) {
                Text(text = "+", color = Color(0xFF90A4AE))
            }
        }
    }
}
