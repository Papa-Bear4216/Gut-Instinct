package com.registry.coach.engine

import com.registry.coach.data.WorkflowAction
import com.registry.coach.data.WorkflowSuggestion
import kotlin.math.min
import kotlinx.serialization.Serializable

class PatternEngine {
    @Serializable
    data class Event(val packageName: String, val occurredAt: Long)

    @Serializable
    data class TransitionRecord(
        val fromPackage: String,
        val toPackage: String,
        val timestamp: Long,
        val gapMs: Long
    )

    enum class PatternKind {
        SEQUENTIAL,
        THRASHING,
        CHAIN,
        HABIT
    }

    data class Pattern(
        val fromPackage: String,
        val toPackage: String,
        val count: Int,
        val medianGapMs: Long,
        val kind: PatternKind = PatternKind.SEQUENTIAL,
        val chainPackages: List<String> = emptyList()
    )

    fun isSensitive(packageName: String): Boolean =
        SENSITIVE.any { it.containsMatchIn(packageName) }

    fun isTrampolineOrNoise(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        if (TRAMPOLINE_PACKAGES.contains(packageName)) return true
        return TRAMPOLINE_PATTERNS.any { it.containsMatchIn(packageName) }
    }

    fun sanitizeEvents(events: List<Event>): List<Event> {
        val nonNoise = events
            .sortedBy { it.occurredAt }
            .filterNot { isTrampolineOrNoise(it.packageName) || isSensitive(it.packageName) }
        val collapsed = mutableListOf<Event>()
        for (event in nonNoise) {
            if (collapsed.isEmpty() || collapsed.last().packageName != event.packageName) {
                collapsed.add(event)
            } else {
                collapsed[collapsed.lastIndex] = event
            }
        }
        return collapsed
    }

    fun extractTransitions(events: List<Event>): List<TransitionRecord> {
        val clean = sanitizeEvents(events)
        return clean.zipWithNext().mapNotNull { (from, to) ->
            val gap = to.occurredAt - from.occurredAt
            if (from.packageName != to.packageName && gap in 0..MAX_GAP_MS &&
                !isSensitive(from.packageName) && !isSensitive(to.packageName)
            ) {
                TransitionRecord(from.packageName, to.packageName, to.occurredAt, gap)
            } else null
        }
    }

    private fun detectChains(clean: List<Event>): List<Pattern> {
        if (clean.size < 3) return emptyList()
        val chainGaps = mutableMapOf<Triple<String, String, String>, MutableList<Long>>()

        for (i in 0..clean.size - 3) {
            val e0 = clean[i]
            val e1 = clean[i + 1]
            val e2 = clean[i + 2]
            if (e0.packageName == e1.packageName || e1.packageName == e2.packageName || e0.packageName == e2.packageName) continue
            val gap1 = e1.occurredAt - e0.occurredAt
            val gap2 = e2.occurredAt - e1.occurredAt
            val totalDuration = e2.occurredAt - e0.occurredAt
            if (gap1 in 0..MAX_GAP_MS && gap2 in 0..MAX_GAP_MS && totalDuration in 0..MAX_CHAIN_DURATION_MS) {
                val key = Triple(e0.packageName, e1.packageName, e2.packageName)
                chainGaps.getOrPut(key) { mutableListOf() }.add(totalDuration)
            }
        }

        return chainGaps.mapNotNull { (triple, durations) ->
            if (durations.size < MIN_EVIDENCE) null
            else Pattern(
                fromPackage = triple.first,
                toPackage = triple.second,
                count = durations.size,
                medianGapMs = durations.sorted()[durations.size / 2],
                kind = PatternKind.CHAIN,
                chainPackages = listOf(triple.first, triple.second, triple.third)
            )
        }
    }

    private fun detectThrashing(clean: List<Event>): List<Pattern> {
        if (clean.size < 6) return emptyList()
        val thrashingPatterns = mutableListOf<Pattern>()
        val transitions = clean.zipWithNext().mapNotNull { (from, to) ->
            val gap = to.occurredAt - from.occurredAt
            if (from.packageName != to.packageName && gap in 0..MAX_GAP_MS) {
                from.packageName to to.packageName
            } else null
        }

        val pairTransitions = mutableMapOf<Set<String>, MutableList<Pair<String, String>>>()
        for (t in transitions) {
            val pair = setOf(t.first, t.second)
            if (pair.size == 2) {
                pairTransitions.getOrPut(pair) { mutableListOf() }.add(t)
            }
        }

        for ((pair, list) in pairTransitions) {
            val pkgList = pair.toList()
            val p1 = pkgList[0]
            val p2 = pkgList[1]
            val count1To2 = list.count { it.first == p1 && it.second == p2 }
            val count2To1 = list.count { it.first == p2 && it.second == p1 }

            if (count1To2 >= MIN_EVIDENCE && count2To1 >= MIN_EVIDENCE) {
                val totalCount = count1To2 + count2To1
                thrashingPatterns.add(
                    Pattern(
                        fromPackage = p1,
                        toPackage = p2,
                        count = totalCount,
                        medianGapMs = 5_000L,
                        kind = PatternKind.THRASHING
                    )
                )
            }
        }
        return thrashingPatterns
    }

    fun detect(
        events: List<Event>,
        historicTransitions: List<TransitionRecord> = emptyList()
    ): List<Pattern> {
        val clean = sanitizeEvents(events)
        val results = mutableListOf<Pattern>()

        // 1. Chains (A -> B -> C)
        val chains = detectChains(clean)
        results.addAll(chains)

        // 2. Thrashing Loops (A <-> B)
        val thrashing = detectThrashing(clean)
        results.addAll(thrashing)

        val thrashingPairs = thrashing.map { setOf(it.fromPackage, it.toPackage) }.toSet()

        // 3. Sequential Transitions & Macro-Habits across sessions
        val recentTransitions = extractTransitions(clean)
        val allTransitions = (historicTransitions + recentTransitions)
            .distinctBy { "${it.fromPackage}->${it.toPackage}@${it.timestamp}" }

        val transitionsByPair = allTransitions
            .filterNot { setOf(it.fromPackage, it.toPackage) in thrashingPairs }
            .groupBy { it.fromPackage to it.toPackage }

        for ((pair, records) in transitionsByPair) {
            if (records.size < MIN_EVIDENCE) continue
            val sortedGaps = records.map { it.gapMs }.sorted()
            val medianGap = sortedGaps[sortedGaps.size / 2]
            val timeSpan = records.maxOf { it.timestamp } - records.minOf { it.timestamp }
            val kind = if (timeSpan > SESSION_THRESHOLD_MS) PatternKind.HABIT else PatternKind.SEQUENTIAL

            results.add(
                Pattern(
                    fromPackage = pair.first,
                    toPackage = pair.second,
                    count = records.size,
                    medianGapMs = medianGap,
                    kind = kind
                )
            )
        }

        return results.sortedByDescending { it.count }
    }

    fun suggestion(pattern: Pattern, now: Long = System.currentTimeMillis()): WorkflowSuggestion {
        val id = when (pattern.kind) {
            PatternKind.CHAIN -> "chain_${pattern.chainPackages.joinToString("_")}".replace(Regex("[^A-Za-z0-9_]"), "_")
            PatternKind.THRASHING -> "pair_${minOf(pattern.fromPackage, pattern.toPackage)}_${maxOf(pattern.fromPackage, pattern.toPackage)}".replace(Regex("[^A-Za-z0-9_]"), "_")
            PatternKind.HABIT -> "habit_${pattern.fromPackage}_${pattern.toPackage}".replace(Regex("[^A-Za-z0-9_]"), "_")
            PatternKind.SEQUENTIAL -> "${pattern.fromPackage}_${pattern.toPackage}".replace(Regex("[^A-Za-z0-9_]"), "_")
        }

        val title = when (pattern.kind) {
            PatternKind.CHAIN -> pattern.chainPackages.joinToString(" → ") { label(it) }
            PatternKind.THRASHING -> "Split-Screen: ${label(pattern.fromPackage)} & ${label(pattern.toPackage)}"
            PatternKind.HABIT -> "${label(pattern.fromPackage)} to ${label(pattern.toPackage)} Routine"
            PatternKind.SEQUENTIAL -> "${label(pattern.fromPackage)} to ${label(pattern.toPackage)}"
        }

        val reason = when (pattern.kind) {
            PatternKind.CHAIN -> "3-step routine observed ${pattern.count} times (${pattern.chainPackages.joinToString(" → ") { label(it) }})."
            PatternKind.THRASHING -> "Frequent multitasking observed (${pattern.count} alternating switches). Launch side-by-side in split-screen."
            PatternKind.HABIT -> "Habitual workflow observed across ${pattern.count} sessions (median handoff: ${pattern.medianGapMs / 1000}s)."
            PatternKind.SEQUENTIAL -> "Repeated workflow observed ${pattern.count} times (median handoff: ${pattern.medianGapMs / 1000}s)."
        }

        val actions = when (pattern.kind) {
            PatternKind.CHAIN -> pattern.chainPackages.drop(1).mapIndexed { idx, pkg ->
                WorkflowAction(
                    type = "launch_app",
                    params = mapOf("package" to pkg),
                    delayMs = if (idx > 0) 1500L else 0L
                )
            }
            PatternKind.THRASHING -> listOf(
                WorkflowAction(type = "launch_app", params = mapOf("package" to pattern.fromPackage)),
                WorkflowAction(type = "launch_app", params = mapOf("package" to pattern.toPackage, "mode" to "adjacent"), delayMs = 500L)
            )
            PatternKind.HABIT, PatternKind.SEQUENTIAL -> listOf(
                WorkflowAction(type = "launch_app", params = mapOf("package" to pattern.toPackage))
            )
        }

        val estimatedSecondsSaved = when (pattern.kind) {
            PatternKind.CHAIN -> maxOf(15, (pattern.medianGapMs / 1000).toInt() * 2)
            PatternKind.THRASHING -> pattern.count * 4
            PatternKind.HABIT, PatternKind.SEQUENTIAL -> maxOf(5, (pattern.medianGapMs / 1000).toInt())
        }

        val confidence = when (pattern.kind) {
            PatternKind.CHAIN -> minOf(0.99, 0.70 + pattern.count * 0.08)
            PatternKind.THRASHING -> minOf(0.99, 0.75 + pattern.count * 0.05)
            PatternKind.HABIT -> minOf(0.98, 0.65 + pattern.count * 0.06)
            PatternKind.SEQUENTIAL -> minOf(0.98, 0.55 + pattern.count * 0.07)
        }

        return WorkflowSuggestion(
            id = id,
            fromPackage = pattern.fromPackage,
            toPackage = pattern.toPackage,
            title = title,
            evidenceCount = pattern.count,
            confidence = confidence,
            estimatedSecondsSaved = estimatedSecondsSaved,
            createdAt = now,
            reason = reason,
            generatedBy = "deterministic",
            actions = actions,
            patternType = pattern.kind.name.lowercase(),
            chainPackages = pattern.chainPackages
        )
    }

    private fun label(packageName: String) =
        packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    companion object {
        const val MIN_EVIDENCE = 3
        const val MAX_GAP_MS = 90_000L
        const val MAX_CHAIN_DURATION_MS = 180_000L
        const val SESSION_THRESHOLD_MS = 300_000L
        const val THRASHING_WINDOW_MS = 90_000L

        val TRAMPOLINE_PACKAGES = setOf(
            "com.android.systemui",
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "ginlemon.flowerfree",
            "bitpit.launcher",
            "app.lawnchair",
            "ch.deletescape.lawnchair",
            "net.oneplus.launcher",
            "com.mi.android.globallauncher",
            "com.miui.home",
            "com.motorola.launcher3",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey",
            "com.android.intentresolver",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.secondguess.app",
            "com.registry.coach"
        )

        val TRAMPOLINE_PATTERNS = listOf(
            Regex("""(^|\.)(?:launcher\d?|nexuslauncher|systemui|honeyboard|swiftkey)($|\.)""", RegexOption.IGNORE_CASE),
            Regex("""(^|\.)inputmethod(\.|\$)""", RegexOption.IGNORE_CASE)
        )

        private val SENSITIVE = listOf(
            Regex("bank", RegexOption.IGNORE_CASE),
            Regex("wallet", RegexOption.IGNORE_CASE),
            Regex("password", RegexOption.IGNORE_CASE),
            Regex("authenticator", RegexOption.IGNORE_CASE),
            Regex("medical", RegexOption.IGNORE_CASE),
            Regex("health", RegexOption.IGNORE_CASE),
            Regex("com\\.android\\.settings")
        )
    }
}
