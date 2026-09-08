package com.registry.coach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.registry.coach.data.NativeWorkflow
import com.registry.coach.ui.theme.AccentEmerald
import com.registry.coach.ui.theme.DarkBorder
import com.registry.coach.ui.theme.DarkSurfaceElevated
import com.registry.coach.ui.theme.MutedText
import com.registry.coach.ui.theme.PrimaryPurple
import com.registry.coach.ui.theme.TextLight

@Composable
fun WorkflowCard(
    workflow: NativeWorkflow,
    onToggleEnabled: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play button with circle container
            IconButton(
                onClick = onRunNow,
                enabled = workflow.enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (workflow.enabled) PrimaryPurple.copy(alpha = 0.2f) else Color(0xFF201D35),
                    contentColor = if (workflow.enabled) PrimaryPurple else MutedText
                ),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run Now",
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workflow.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (workflow.enabled) TextLight else MutedText
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (workflow.enabled) "Active" else "Paused",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (workflow.enabled) AccentEmerald else MutedText
                    )
                    Text(
                        text = "·",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    Text(
                        text = "${workflow.runCount} runs",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    if (workflow.successCount > 0) {
                        Text(
                            text = "(${workflow.successCount} ok)",
                            fontSize = 12.sp,
                            color = AccentEmerald.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Switch to toggle active/paused
            Switch(
                checked = workflow.enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryPurple,
                    uncheckedThumbColor = MutedText,
                    uncheckedTrackColor = Color(0xFF2C274B)
                )
            )
        }
    }
}
