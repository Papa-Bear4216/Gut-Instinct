package com.registry.coach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.ui.theme.AccentEmerald
import com.registry.coach.ui.theme.AccentPurple
import com.registry.coach.ui.theme.DarkBorder
import com.registry.coach.ui.theme.DarkSurfaceElevated
import com.registry.coach.ui.theme.MutedText
import com.registry.coach.ui.theme.PrimaryPurple
import com.registry.coach.ui.theme.TextLight

@Composable
fun SuggestionCard(
    item: WorkflowSuggestion,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fromLabel = getAppLabel(context, item.fromPackage)
    val toLabel = getAppLabel(context, item.toPackage)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header chips: Routine Suggestion, Observed count, Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ROUTINE SUGGESTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = AccentPurple
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2C274E))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Observed ${item.evidenceCount}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextLight
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${(item.confidence * 100).toInt()}% Confident",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = item.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextLight
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Reason
            Text(
                text = item.reason,
                fontSize = 13.sp,
                color = MutedText,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Flow: App A Icon + Name -> App B Icon + Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141223))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconView(packageName = item.fromPackage, size = 38.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = fromLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextLight,
                            maxLines = 1
                        )
                        Text(
                            text = "Trigger app",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "To",
                    tint = AccentPurple,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(18.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppIconView(packageName = item.toPackage, size = 38.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = toLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextLight,
                            maxLines = 1
                        )
                        Text(
                            text = "Next action",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MutedText
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dismiss", fontSize = 13.sp)
                }

                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Approve",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve Routine", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
