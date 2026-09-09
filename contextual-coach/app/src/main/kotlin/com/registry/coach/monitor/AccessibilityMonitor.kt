package com.registry.coach.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.registry.coach.ai.OnDeviceWorkflowGenerator
import com.registry.coach.data.WorkflowStore
import com.registry.coach.engine.PatternEngine
import com.registry.coach.filter.ContextGuard
import com.registry.coach.execution.WorkflowExecutor
import com.registry.coach.sync.FirebaseWorkflowSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AccessibilityMonitor : AccessibilityService() {
    private lateinit var store:WorkflowStore
    private lateinit var guard:ContextGuard
    private lateinit var sync:FirebaseWorkflowSync
    private lateinit var executor:WorkflowExecutor
    private val engine=PatternEngine()
    private val generator=OnDeviceWorkflowGenerator()
    private val json=Json
    private val events=ArrayDeque<PatternEngine.Event>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val processed=mutableSetOf<String>()
    private var ephemeralSelection=""

    override fun onServiceConnected() {
        store=WorkflowStore(applicationContext);guard=ContextGuard(applicationContext);sync=FirebaseWorkflowSync(applicationContext);executor=WorkflowExecutor(applicationContext)
        val saved=getSharedPreferences("secondguess_observer",MODE_PRIVATE).getString("events","[]") ?: "[]"
        try { events.addAll(json.decodeFromString<List<PatternEngine.Event>>(saved)) } catch (_:Exception) { }
    }

    override fun onAccessibilityEvent(event:AccessibilityEvent) {
        val packageName=event.packageName?.toString() ?: return
        // Mandatory pre-filter: no accessibility tree access is permitted before this returns false.
        if(packageName==applicationContext.packageName || guard.isBlocked(packageName)) return
        if(event.eventType==AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            if(!store.screenContextEnabled()) return
            // In-memory hint only. It is consumed on the next allowed transition and never persisted or synced.
            ephemeralSelection=event.text.joinToString(" ").take(240)
            return
        }
        if(event.eventType!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || events.lastOrNull()?.packageName==packageName) return
        val now=System.currentTimeMillis()
        store.workflows().filter { it.enabled && it.fromPackage==packageName && now-it.lastRunAt>=it.cooldownMs }.forEach { workflow ->
            scope.launch { val success=executor.execute(workflow);store.recordRun(workflow.id,success);val updated=store.workflows().firstOrNull { it.id==workflow.id } ?: workflow;sync.workflow(updated);sync.execution(updated,success) }
        }
        events.addLast(PatternEngine.Event(packageName,now))
        while(events.size>200) events.removeFirst()
        val snapshot=events.toList()
        scope.launch(Dispatchers.IO) {
            getSharedPreferences("secondguess_observer",MODE_PRIVATE).edit().putString("events",json.encodeToString(snapshot)).apply()
        }
        engine.detect(events.toList()).forEach { pattern ->
            val base=engine.suggestion(pattern)
            if(base.id in processed || store.rejectedIds().contains(base.id) || store.workflows().any { it.id==base.id }) return@forEach
            processed.add(base.id)
            // Screen context is extracted only after filtering, capped, passed to AICore, then discarded.
            val ephemeral=if(store.screenContextEnabled()) (ephemeralSelection+" "+extractVisibleContext(rootInActiveWindow)).trim().take(1500) else ""
            ephemeralSelection=""
            scope.launch {
                val suggestion=generator.enrich(base,pattern,ephemeral)
                store.upsertSuggestion(suggestion)
                sync.suggestion(suggestion)
            }
        }
    }

    private fun extractVisibleContext(root:AccessibilityNodeInfo?):String {
        if(root==null) return ""
        val output=StringBuilder()
        fun visit(node:AccessibilityNodeInfo) {
            if(output.length>=1500) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { output.append(it.take(160)).append(' ') }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { output.append(it.take(160)).append(' ') }
            for(index in 0 until node.childCount) node.getChild(index)?.let { child -> visit(child); child.recycle() }
        }
        return try { visit(root);output.toString().take(1500) } finally { root.recycle() }
    }
    override fun onInterrupt()=Unit
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
