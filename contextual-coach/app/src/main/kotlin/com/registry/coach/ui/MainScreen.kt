package com.registry.coach.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.registry.coach.data.NativeWorkflow
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.ui.components.ObservationHero
import com.registry.coach.ui.components.PrivacyCard
import com.registry.coach.ui.components.SuggestionCard
import com.registry.coach.ui.components.WorkflowCard
import com.registry.coach.ui.theme.AccentEmerald
import com.registry.coach.ui.theme.AccentPurple
import com.registry.coach.ui.theme.DarkBorder
import com.registry.coach.ui.theme.DarkSurfaceElevated
import com.registry.coach.ui.theme.DeepObsidian
import com.registry.coach.ui.theme.MutedText
import com.registry.coach.ui.theme.TextLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isAccessibilityEnabled: Boolean,
    isScreenContextEnabled: Boolean,
    suggestions: List<WorkflowSuggestion>,
    workflows: List<NativeWorkflow>,
    onToggleAccessibility: () -> Unit,
    onToggleScreenContext: (Boolean) -> Unit,
    onApproveSuggestion: (String) -> Unit,
    onRejectSuggestion: (String) -> Unit,
    onToggleWorkflow: (String, Boolean) -> Unit,
    onRunWorkflow: (NativeWorkflow) -> Unit,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DeepObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SecondGuess",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentPurple.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Nano AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentPurple
                                )
                            }
                        }
                        Text(
                            text = "On-device routine observation & automation",
                            fontSize = 12.sp,
                            color = MutedText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MutedText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepObsidian
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            // 1. Observation Engine Hero
            ObservationHero(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isScreenContextEnabled = isScreenContextEnabled,
                onToggleAccessibility = onToggleAccessibility,
                onToggleScreenContext = onToggleScreenContext
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Suggestions Section
            SectionHeader(
                title = "Routine Suggestions",
                badge = if (suggestions.isNotEmpty()) "${suggestions.size}" else null
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (suggestions.isEmpty()) {
                EmptyStateCard(
                    title = "Observing Patterns",
                    message = if (isAccessibilityEnabled)
                        "Continue using your apps normally. SecondGuess suggests an automation shortcut once you repeat a transition 3 times within 90 seconds."
                    else
                        "Enable observation above to let SecondGuess learn your common app transitions."
                )
            } else {
                suggestions.forEach { suggestion ->
                    SuggestionCard(
                        item = suggestion,
                        onApprove = { onApproveSuggestion(suggestion.id) },
                        onReject = { onRejectSuggestion(suggestion.id) },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Active Automations Section
            SectionHeader(
                title = "Active Automations",
                badge = if (workflows.isNotEmpty()) "${workflows.size}" else null
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (workflows.isEmpty()) {
                EmptyStateCard(
                    title = "No Approved Automations",
                    message = "Approved suggestions appear here. You can pause, resume, or trigger them instantly with the play button."
                )
            } else {
                workflows.forEach { workflow ->
                    WorkflowCard(
                        workflow = workflow,
                        onToggleEnabled = { enabled -> onToggleWorkflow(workflow.id, enabled) },
                        onRunNow = { onRunWorkflow(workflow) },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Privacy Card
            PrivacyCard()

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.2f))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurfaceElevated)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextLight
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = MutedText,
                lineHeight = 17.sp
            )
        }
    }
}
