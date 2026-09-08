package com.registry.coach.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.registry.coach.data.NativeWorkflow
import com.registry.coach.data.WorkflowSuggestion
import kotlinx.coroutines.tasks.await

/** Sync boundary: accepts sanitized models only; never accepts screen or accessibility content. */
class FirebaseWorkflowSync(private val context:Context) {
    private fun available()=FirebaseApp.getApps(context).isNotEmpty()
    private suspend fun uid():String? {
        if(!available()) return null
        val auth=FirebaseAuth.getInstance()
        return auth.currentUser?.uid ?: try { auth.signInAnonymously().await().user?.uid } catch (_:Exception) { null }
    }
    suspend fun suggestion(item:WorkflowSuggestion) { val uid=uid()?:return; FirebaseFirestore.getInstance().collection("workflowSuggestions").document(item.id).set(mapOf("createdBy" to uid,"fromPackage" to item.fromPackage,"toPackage" to item.toPackage,"title" to item.title,"evidenceCount" to item.evidenceCount,"confidence" to item.confidence,"estimatedSecondsSaved" to item.estimatedSecondsSaved,"reason" to item.reason,"generatedBy" to item.generatedBy,"createdAt" to item.createdAt,"status" to "suggested")).await() }
    suspend fun workflow(item:NativeWorkflow) { val uid=uid()?:return; FirebaseFirestore.getInstance().collection("workflows").document(item.id).set(mapOf("createdBy" to uid,"fromPackage" to item.fromPackage,"toPackage" to item.toPackage,"title" to item.title,"enabled" to item.enabled,"runCount" to item.runCount,"successCount" to item.successCount,"failureCount" to item.failureCount,"evidenceCount" to item.evidenceCount,"estimatedSecondsSaved" to item.estimatedSecondsSaved,"approvedAt" to item.approvedAt)).await() }
    suspend fun execution(workflow:NativeWorkflow,success:Boolean) { val uid=uid()?:return; FirebaseFirestore.getInstance().collection("workflowRuns").add(mapOf("createdBy" to uid,"workflowId" to workflow.id,"success" to success,"executedAt" to System.currentTimeMillis(),"actionCount" to 1)).await() }
}
