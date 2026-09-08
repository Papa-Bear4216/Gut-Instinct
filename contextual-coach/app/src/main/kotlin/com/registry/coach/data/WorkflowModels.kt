package com.registry.coach.data

import kotlinx.serialization.Serializable

@Serializable
data class ObservedTransition(
    val fromPackage: String,
    val toPackage: String,
    val count: Int,
    val medianGapMs: Long,
)

@Serializable
data class WorkflowSuggestion(
    val id: String,
    val fromPackage: String,
    val toPackage: String,
    val title: String,
    val evidenceCount: Int,
    val confidence: Double,
    val estimatedSecondsSaved: Int,
    val createdAt: Long,
    val reason: String = "Repeated workflow observed",
    val generatedBy: String = "deterministic",
    val actions: List<WorkflowAction> = emptyList(),
)

@Serializable
data class WorkflowAction(
    val type: String,
    val params: Map<String,String> = emptyMap(),
    val delayMs: Long = 0,
    val onError: String = "stop",
)

@Serializable
data class NativeWorkflow(
    val id: String,
    val fromPackage: String,
    val toPackage: String,
    val title: String,
    val enabled: Boolean = true,
    val runCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val evidenceCount: Int,
    val estimatedSecondsSaved: Int,
    val approvedAt: Long,
    val actions: List<WorkflowAction> = emptyList(),
    val cooldownMs: Long = 30_000,
    val lastRunAt: Long = 0,
)
