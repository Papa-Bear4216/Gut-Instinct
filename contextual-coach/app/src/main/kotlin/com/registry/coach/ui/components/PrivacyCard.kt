package com.registry.coach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.registry.coach.ui.theme.AccentEmerald
import com.registry.coach.ui.theme.DarkBorder
import com.registry.coach.ui.theme.DarkSurfaceElevated
import com.registry.coach.ui.theme.MutedText
import com.registry.coach.ui.theme.TextLight

@Composable
fun PrivacyCard(modifier: Modifier = Modifier) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Privacy Shield",
                    tint = AccentEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Privacy Guarantees",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextLight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrivacyItem("Sensitive apps (banking, medical, passwords) are blocked before reading any screen node.")
            PrivacyItem("Ephemeral visible text is processed in memory by on-device Gemini Nano and discarded immediately.")
            PrivacyItem("Raw screen text and personal data are never written to disk or sent to the cloud.")
            PrivacyItem("Firebase synchronizes only sanitized, anonymized workflow metadata and execution counters.")
            PrivacyItem("Every automated shortcut requires your explicit approval before it can run.")
        }
    }
}

@Composable
private fun PrivacyItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = AccentEmerald,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MutedText,
            lineHeight = 16.sp
        )
    }
}
