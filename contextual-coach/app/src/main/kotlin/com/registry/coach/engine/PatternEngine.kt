package com.registry.coach.engine

import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.data.WorkflowAction
import kotlin.math.min
import kotlinx.serialization.Serializable

class PatternEngine {
    @Serializable data class Event(val packageName:String,val occurredAt:Long)
    data class Pattern(val fromPackage:String,val toPackage:String,val count:Int,val medianGapMs:Long)

    fun detect(events:List<Event>):List<Pattern> {
        val gaps=mutableMapOf<Pair<String,String>,MutableList<Long>>()
        events.sortedBy { it.occurredAt }.zipWithNext().forEach { (from,to) ->
            val gap=to.occurredAt-from.occurredAt
            if(from.packageName!=to.packageName && gap in 0..MAX_GAP_MS && !isSensitive(from.packageName) && !isSensitive(to.packageName))
                gaps.getOrPut(from.packageName to to.packageName){ mutableListOf() }.add(gap)
        }
        return gaps.mapNotNull { (pair,values) ->
            if(values.size<MIN_EVIDENCE) null else Pattern(pair.first,pair.second,values.size,values.sorted()[values.size/2])
        }.sortedByDescending { it.count }
    }

    fun suggestion(pattern:Pattern,now:Long=System.currentTimeMillis()):WorkflowSuggestion = WorkflowSuggestion(
        id="${pattern.fromPackage}_${pattern.toPackage}".replace(Regex("[^A-Za-z0-9_]"),"_"),
        fromPackage=pattern.fromPackage,toPackage=pattern.toPackage,
        title="${label(pattern.fromPackage)} to ${label(pattern.toPackage)}",
        evidenceCount=pattern.count,confidence=min(.98,.55+pattern.count*.07),
        estimatedSecondsSaved=maxOf(5,(pattern.medianGapMs/1000).toInt()),createdAt=now,
        actions=listOf(WorkflowAction(type="launch_app",params=mapOf("package" to pattern.toPackage))),
    )

    fun isSensitive(packageName:String)=SENSITIVE.any { it.containsMatchIn(packageName) }
    private fun label(packageName:String)=packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    companion object {
        const val MIN_EVIDENCE=3
        const val MAX_GAP_MS=90_000L
        private val SENSITIVE=listOf(Regex("bank",RegexOption.IGNORE_CASE),Regex("wallet",RegexOption.IGNORE_CASE),Regex("password",RegexOption.IGNORE_CASE),Regex("authenticator",RegexOption.IGNORE_CASE),Regex("medical",RegexOption.IGNORE_CASE),Regex("health",RegexOption.IGNORE_CASE),Regex("com\\.android\\.settings"))
    }
}
