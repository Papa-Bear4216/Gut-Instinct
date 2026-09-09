package com.registry.coach.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkflowStore(context: Context) {
    private val preferences = context.getSharedPreferences("secondguess_workflows", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun suggestions(): List<WorkflowSuggestion> = decode("suggestions")
    @Synchronized fun workflows(): List<NativeWorkflow> = decode("workflows")
    @Synchronized fun rejectedIds(): Set<String> = preferences.getStringSet("rejected", emptySet()) ?: emptySet()
    fun screenContextEnabled():Boolean=preferences.getBoolean("screen_context_enabled",true)
    fun setScreenContextEnabled(enabled:Boolean)=preferences.edit().putBoolean("screen_context_enabled",enabled).apply()
    fun piecesSyncEnabled():Boolean=preferences.getBoolean("pieces_sync_enabled",true)
    fun setPiecesSyncEnabled(enabled:Boolean)=preferences.edit().putBoolean("pieces_sync_enabled",enabled).apply()
    fun piecesProxyUrl():String=preferences.getString("pieces_proxy_url","http://127.0.0.1:8787") ?: "http://127.0.0.1:8787"
    fun setPiecesProxyUrl(url:String)=preferences.edit().putString("pieces_proxy_url",url).apply()
    fun piecesProxyToken():String=preferences.getString("pieces_proxy_token","") ?: ""
    fun setPiecesProxyToken(token:String)=preferences.edit().putString("pieces_proxy_token",token).apply()

    @Synchronized fun upsertSuggestion(suggestion: WorkflowSuggestion) {
        if (rejectedIds().contains(suggestion.id) || workflows().any { it.id == suggestion.id }) return
        val next = suggestions().filterNot { it.id == suggestion.id } + suggestion
        preferences.edit().putString("suggestions", json.encodeToString(next)).apply()
    }

    @Synchronized fun approve(id: String): NativeWorkflow? {
        val suggestion = suggestions().firstOrNull { it.id == id } ?: return null
        val workflow = NativeWorkflow(
            id=suggestion.id, fromPackage=suggestion.fromPackage, toPackage=suggestion.toPackage,
            title=suggestion.title, evidenceCount=suggestion.evidenceCount,
            estimatedSecondsSaved=suggestion.estimatedSecondsSaved, approvedAt=System.currentTimeMillis(),
            actions=suggestion.actions,
            patternType=suggestion.patternType,
            chainPackages=suggestion.chainPackages,
        )
        preferences.edit()
            .putString("suggestions", json.encodeToString(suggestions().filterNot { it.id == id }))
            .putString("workflows", json.encodeToString(workflows().filterNot { it.id == id } + workflow)).apply()
        return workflow
    }

    @Synchronized fun reject(id: String) {
        preferences.edit().putString("suggestions", json.encodeToString(suggestions().filterNot { it.id == id }))
            .putStringSet("rejected", rejectedIds() + id).apply()
    }

    @Synchronized fun setEnabled(id: String, enabled: Boolean) = updateWorkflow(id) { it.copy(enabled=enabled) }
    @Synchronized fun recordRun(id: String, success: Boolean) = updateWorkflow(id) {
        it.copy(runCount=it.runCount+1, successCount=it.successCount+if(success) 1 else 0, failureCount=it.failureCount+if(success) 0 else 1,lastRunAt=System.currentTimeMillis())
    }

    private fun updateWorkflow(id: String, transform: (NativeWorkflow)->NativeWorkflow) {
        preferences.edit().putString("workflows", json.encodeToString(workflows().map { if(it.id==id) transform(it) else it })).apply()
    }

    private inline fun <reified T> decode(key: String): List<T> = try {
        json.decodeFromString(preferences.getString(key, "[]") ?: "[]")
    } catch (_: Exception) { emptyList() }
}
