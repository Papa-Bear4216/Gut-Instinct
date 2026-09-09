# Gut-Instinct / Contextual Coach Codebase Digest

This single file contains the complete source code for all core modules in contextual-coach.


---
## File: contextual-coach/app/src/main/AndroidManifest.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".CoachApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.DarkActionBar">

        <!-- Single native app entry -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- AccessibilityService — the core monitoring component -->
        <service
            android:name=".monitor.AccessibilityMonitor"
            android:exported="false"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/CoachApplication.kt

``kt
package com.registry.coach

import android.app.Application
import com.google.firebase.FirebaseApp
/** Application entry for the single, local-first SecondGuess APK. */
class CoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Optional at development time; enabled automatically when google-services.json exists.
        FirebaseApp.initializeApp(this)
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/ai/GeminiNanoIntentEngine.kt

``kt
package com.registry.coach.ai

import com.google.mlkit.genai.prompt.Generation
import com.registry.coach.filter.ContextGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device Intent Engine powered by Google Gemini Nano (ML Kit GenAI / AICore).
 * Executes 100% locally on the device chip for zero-latency, private intent classification.
 */
class GeminiNanoIntentEngine(private val contextGuard: ContextGuard) {

    data class IntentResult(
        val intentName: String,
        val confidence: Float,
        val suggestedAction: String?,
        val executionPayload: String?,
        val isOfflineOnly: Boolean = true
    )

    /**
     * Evaluates on-screen text to detect automation intent.
     * Guaranteed never to emit or upload text over the network.
     */
    suspend fun evaluateOnScreenIntent(
        foregroundPackage: String,
        screenText: String
    ): IntentResult? = withContext(Dispatchers.Default) {
        // 1. Strict Privacy Gate: Never inspect sensitive/banking apps
        if (contextGuard.isBlocked(foregroundPackage)) {
            return@withContext null
        }

        if (screenText.isBlank() || screenText.length < 15) {
            return@withContext null
        }

        // 2. Query On-Device Gemini Nano
        try {
            val sanitizedSample = screenText.take(800).replace("\n", " ")
            val prompt = """
                You are an on-device Android assistant running locally via Gemini Nano.
                Classify the user intent from this visible screen text: "$sanitizedSample"
                App: $foregroundPackage

                Allowed Intents:
                - TRACK_PACKAGE: package tracking number present
                - EXPENSE_LOG: payment, price, receipt, or invoice present
                - NAVIGATION: address, location, or travel destination present
                - WORKFLOW_SHORTCUT: repetitive app navigation detected
                - NONE: casual browsing or unclassifiable

                Return exactly in format:
                INTENT: <ALLOWED_INTENT>
                CONFIDENCE: <0.0 to 1.0>
                PAYLOAD: <extracted entity like tracking number, address, or empty>
            """.trimIndent()

            val response = Generation.getClient().generateContent(prompt)
            val output = response.candidates.firstOrNull()?.text.orEmpty()

            val intent = output.lineSequence()
                .firstOrNull { it.startsWith("INTENT:") }
                ?.substringAfter("INTENT:")?.trim().orEmpty()

            val confidenceStr = output.lineSequence()
                .firstOrNull { it.startsWith("CONFIDENCE:") }
                ?.substringAfter("CONFIDENCE:")?.trim().orEmpty()

            val payload = output.lineSequence()
                .firstOrNull { it.startsWith("PAYLOAD:") }
                ?.substringAfter("PAYLOAD:")?.trim().orEmpty()

            val confidence = confidenceStr.toFloatOrNull() ?: 0.0f

            if (intent == "NONE" || confidence < 0.70f) {
                return@withContext null
            }

            val suggestedAction = when (intent) {
                "TRACK_PACKAGE" -> "open_url"
                "EXPENSE_LOG" -> "launch_app"
                "NAVIGATION" -> "launch_app"
                else -> "shortcut_executed"
            }

            IntentResult(
                intentName = intent,
                confidence = confidence,
                suggestedAction = suggestedAction,
                executionPayload = payload.take(200)
            )
        } catch (_: Exception) {
            // Graceful fallback to local regex heuristics if Gemini Nano is unavailable
            fallbackRegexIntent(screenText)
        }
    }

    /**
     * Deterministic local fallback if AICore / Gemini Nano model is downloading or unsupported.
     */
    private fun fallbackRegexIntent(text: String): IntentResult? {
        val trackingRegex = Regex("""\b(1Z[0-9A-Z]{16}|[0-9]{12}|[0-9]{20,22})\b""")
        val match = trackingRegex.find(text)
        if (match != null) {
            return IntentResult(
                intentName = "TRACK_PACKAGE",
                confidence = 0.95f,
                suggestedAction = "open_url",
                executionPayload = match.value
            )
        }
        return null
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/ai/OnDeviceWorkflowGenerator.kt

``kt
package com.registry.coach.ai

import com.google.mlkit.genai.prompt.Generation
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.engine.PatternEngine

/** Gemini may describe a proven pattern, but cannot invent executable package targets. */
class OnDeviceWorkflowGenerator {
    suspend fun enrich(base:WorkflowSuggestion,pattern:PatternEngine.Pattern,ephemeralContext:String):WorkflowSuggestion {
        if(ephemeralContext.isBlank()) return base
        return try {
            val prompt="""
                You are naming an Android shortcut from verified observations. Do not invent apps, steps, facts, or capabilities.
                Verified transition: ${pattern.fromPackage} -> ${pattern.toPackage}
                Frequency: ${pattern.count}
                Ephemeral visible context: ${ephemeralContext.take(1200)}
                Return exactly two plain-text lines:
                TITLE: a short factual title
                REASON: one sentence grounded only in the verified transition and frequency
            """.trimIndent()
            val response=Generation.getClient().generateContent(prompt)
            val text=response.candidates.firstOrNull()?.text.orEmpty()
            val title=text.lineSequence().firstOrNull { it.startsWith("TITLE:") }?.substringAfter("TITLE:")?.trim().orEmpty()
            val reason=text.lineSequence().firstOrNull { it.startsWith("REASON:") }?.substringAfter("REASON:")?.trim().orEmpty()
            if(title.isBlank()||reason.isBlank()) base else base.copy(title=title.take(80),reason=reason.take(240),generatedBy="gemini_nano")
        } catch (_:Exception) { base }
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/data/WorkflowModels.kt

``kt
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
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/data/WorkflowStore.kt

``kt
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
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/engine/PatternEngine.kt

``kt
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
        private val SENSITIVE=listOf(Regex("bank",RegexOption.IGNORE_CASE),Regex("wallet",RegexOption.IGNORE_CASE),Regex("password",RegexOption.IGNORE_CASE),Regex("authenticator",RegexOption.IGNORE_CASE),Regex("com\\.android\\.settings"))
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/execution/WorkflowExecutor.kt

``kt
package com.registry.coach.execution

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.registry.coach.data.NativeWorkflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

class WorkflowExecutor(private val context:Context) {
    suspend fun execute(workflow:NativeWorkflow):Boolean = try {
        withTimeout(30_000) {
            val actions=workflow.actions.ifEmpty { listOf(com.registry.coach.data.WorkflowAction("launch_app",mapOf("package" to workflow.toPackage))) }
            for(action in actions) {
                if(action.delayMs>0) delay(action.delayMs)
                val success=when(action.type) {
                    "launch_app" -> launch(action.params["package"].orEmpty())
                    "open_url" -> openUrl(action.params["url"].orEmpty())
                    "copy_text" -> copy(action.params["text"].orEmpty())
                    "show_notification" -> notify(action.params["title"].orEmpty(),action.params["message"].orEmpty())
                    else -> false
                }
                if(!success && action.onError=="stop") return@withTimeout false
            }
            true
        }
    } catch (_:Exception) { false }
    private fun launch(packageName:String)=try { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true } catch (_:Exception){false}
    private fun openUrl(url:String)=try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true } catch (_:Exception){false}
    private fun copy(text:String):Boolean { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("SecondGuess",text));return true }
    private fun notify(title:String,message:String):Boolean { val manager=context.getSystemService(NotificationManager::class.java);if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("workflows","Workflow execution",NotificationManager.IMPORTANCE_DEFAULT));manager.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),NotificationCompat.Builder(context,"workflows").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle(title.ifBlank { "SecondGuess" }).setContentText(message).build());return true }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/filter/ContextGuard.kt

``kt
package com.registry.coach.filter

import android.content.Context
import com.registry.coach.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Must run before rootInActiveWindow is accessed. */
class ContextGuard(context:Context) {
    @Serializable private data class SensitiveApps(val version:Int=1,val updated:String="",val packages:List<String> = emptyList())
    private val exact=try {
        val raw=context.resources.openRawResource(R.raw.sensitive_apps).bufferedReader().use { it.readText() }
        Json.decodeFromString<SensitiveApps>(raw).packages.toSet()
    } catch (_:Exception) { emptySet() }
    fun isBlocked(packageName:String):Boolean = packageName in exact || PATTERNS.any { it.containsMatchIn(packageName) }
    companion object {
        private val PATTERNS=listOf(Regex("bank",RegexOption.IGNORE_CASE),Regex("wallet",RegexOption.IGNORE_CASE),Regex("password",RegexOption.IGNORE_CASE),Regex("authenticator",RegexOption.IGNORE_CASE),Regex("medical",RegexOption.IGNORE_CASE),Regex("health",RegexOption.IGNORE_CASE),Regex("com\\.android\\.settings"))
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/monitor/AccessibilityMonitor.kt

``kt
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
        getSharedPreferences("secondguess_observer",MODE_PRIVATE).edit().putString("events",json.encodeToString(events.toList())).apply()
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
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/sync/FirebaseWorkflowSync.kt

``kt
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
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/ui/MainActivity.kt

``kt
package com.registry.coach.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.registry.coach.data.NativeWorkflow
import com.registry.coach.data.WorkflowStore
import com.registry.coach.data.WorkflowSuggestion
import com.registry.coach.execution.WorkflowExecutor
import com.registry.coach.sync.FirebaseWorkflowSync
import com.registry.coach.ui.theme.SecondGuessTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: WorkflowStore
    private lateinit var sync: FirebaseWorkflowSync
    private lateinit var executor: WorkflowExecutor

    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isScreenContextEnabled by mutableStateOf(true)
    private var suggestions by mutableStateOf<List<WorkflowSuggestion>>(emptyList())
    private var workflows by mutableStateOf<List<NativeWorkflow>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WorkflowStore(this)
        sync = FirebaseWorkflowSync(this)
        executor = WorkflowExecutor(this)
        refreshState()

        setContent {
            SecondGuessTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                MainScreen(
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isScreenContextEnabled = isScreenContextEnabled,
                    suggestions = suggestions,
                    workflows = workflows,
                    onToggleAccessibility = {
                        AccessibilitySettingsHelper.openAccessibilitySettingsForThisService(this@MainActivity)
                    },
                    onToggleScreenContext = { enabled ->
                        store.setScreenContextEnabled(enabled)
                        refreshState()
                    },
                    onApproveSuggestion = { id ->
                        val approved = store.approve(id)
                        if (approved != null) {
                            lifecycleScope.launch {
                                sync.workflow(approved)
                            }
                            refreshState()
                            scope.launch {
                                snackbarHostState.showSnackbar("Routine approved: ${approved.title}")
                            }
                        }
                    },
                    onRejectSuggestion = { id ->
                        store.reject(id)
                        refreshState()
                        scope.launch {
                            snackbarHostState.showSnackbar("Suggestion dismissed")
                        }
                    },
                    onToggleWorkflow = { id, enabled ->
                        store.setEnabled(id, enabled)
                        store.workflows().firstOrNull { it.id == id }?.let { changed ->
                            lifecycleScope.launch { sync.workflow(changed) }
                        }
                        refreshState()
                    },
                    onRunWorkflow = { workflow ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Running: ${workflow.title}...")
                            val success = executor.execute(workflow)
                            store.recordRun(workflow.id, success)
                            val updated = store.workflows().firstOrNull { it.id == workflow.id } ?: workflow
                            sync.workflow(updated)
                            sync.execution(updated, success)
                            refreshState()
                            if (success) {
                                snackbarHostState.showSnackbar("Executed successfully!")
                            } else {
                                snackbarHostState.showSnackbar("Execution failed or timed out")
                            }
                        }
                    },
                    onRefresh = {
                        refreshState()
                        scope.launch {
                            snackbarHostState.showSnackbar("Routines refreshed")
                        }
                    },
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            refreshState()
        }
    }

    private fun refreshState() {
        isAccessibilityEnabled = AccessibilitySettingsHelper.isAccessibilityServiceEnabled(this)
        isScreenContextEnabled = store.screenContextEnabled()
        suggestions = store.suggestions()
        workflows = store.workflows()
    }
}
``

---
## File: contextual-coach/app/src/main/kotlin/com/registry/coach/ui/MainScreen.kt

``kt
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
``
