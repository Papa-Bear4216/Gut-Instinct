package com.registry.coach.ai

import com.google.mlkit.genai.prompt.Generation
import com.registry.coach.filter.ContextGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * On-device workstream analyst powered by Gemini Nano.
 * Distills active screen context into "WHAT WAS DONE" (actions, topics, entities, state)
 * and dispatches clean semantic context to the Pieces Workstream.
 */
class GeminiNanoWorkstreamReporter(
    private val contextGuard: ContextGuard
) {
    data class WorkstreamAction(
        val action: String,
        val topic: String,
        val entities: String,
        val state: String,
        val packageName: String,
        val appLabel: String
    )

    /**
     * Evaluates visible screen context with Gemini Nano to extract task-level action data.
     * Raw screen text is discarded immediately after inference and never transmitted.
     */
    suspend fun analyzeAndReport(
        packageName: String,
        appLabel: String,
        ephemeralText: String,
        proxyUrl: String?,
        proxyToken: String? = null
    ): WorkstreamAction? = withContext(Dispatchers.IO) {
        if (contextGuard.isBlocked(packageName)) return@withContext null
        if (ephemeralText.isBlank() || ephemeralText.length < 20) return@withContext null

        try {
            val prompt = """
                You are an on-device personal workstream analyst powered by Gemini Nano.
                Analyze the visible screen content from app '$appLabel' ($packageName).
                Describe WHAT WAS DONE (specific task, content, decision, code, or user action), NOT just what app was opened.

                Visible screen content:
                ${ephemeralText.take(1200)}

                Return exactly 4 plain-text lines:
                ACTION: <concise verb-driven summary of the task done, e.g. "Reviewing PR diff for auth validation", "Debugging SQL syntax error in BigQuery", "Drafting reply to email about project deadline">
                TOPIC: <core subject, problem, query, or technical context>
                ENTITIES: <specific key data: filenames, code symbols, ticket IDs, URLs, errors, or names>
                STATE: <in_progress, completed, reading, researching, or drafting>
            """.trimIndent()

            val response = Generation.getClient().generateContent(prompt)
            val output = response.candidates.firstOrNull()?.text.orEmpty()

            val action = output.lineSequence().firstOrNull { it.startsWith("ACTION:") }?.substringAfter("ACTION:")?.trim().orEmpty()
            val topic = output.lineSequence().firstOrNull { it.startsWith("TOPIC:") }?.substringAfter("TOPIC:")?.trim().orEmpty()
            val entities = output.lineSequence().firstOrNull { it.startsWith("ENTITIES:") }?.substringAfter("ENTITIES:")?.trim().orEmpty()
            val state = output.lineSequence().firstOrNull { it.startsWith("STATE:") }?.substringAfter("STATE:")?.trim().orEmpty()

            if (action.isBlank()) return@withContext null

            val result = WorkstreamAction(
                action = action.take(120),
                topic = topic.take(120),
                entities = entities.take(200),
                state = state.take(40),
                packageName = packageName,
                appLabel = appLabel
            )

            if (!proxyUrl.isNullOrBlank()) {
                dispatchToProxy(result, proxyUrl, proxyToken)
            }

            result
        } catch (_: Exception) {
            null
        }
    }

    private fun dispatchToProxy(action: WorkstreamAction, proxyUrl: String, proxyToken: String? = null) {
        try {
            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            df.timeZone = TimeZone.getTimeZone("UTC")
            val isoDate = df.format(Date())

            val telemetryBody = "ACTION: ${action.action}\nTOPIC: ${action.topic}\nENTITIES: ${action.entities}\nSTATE: ${action.state}"

            val eventObj = JSONObject().apply {
                put("type", "system_telemetry")
                put("screen", "activity_done")
                put("package", action.packageName)
                put("app_label", action.appLabel)
                put("telemetry", telemetryBody)
                put("timestamp", isoDate)
            }

            val payload = JSONObject().apply {
                put("events", JSONArray().put(eventObj))
            }

            val target = if (proxyUrl.endsWith("/")) "${proxyUrl}mobile/usage-report" else "$proxyUrl/mobile/usage-report"
            val url = URL(target)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            if (!proxyToken.isNullOrBlank()) {
                // Only attach the bearer token over HTTPS or to a loopback host —
                // never send a credential in cleartext to a remote proxy.
                val host = url.host
                val isLoopback = host == "127.0.0.1" || host == "localhost" || host == "::1"
                if (url.protocol.equals("https", ignoreCase = true) || isLoopback) {
                    conn.setRequestProperty("Authorization", "Bearer $proxyToken")
                } else {
                    return
                }
            }
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // Fail silently if proxy is unreachable; queue or retry on next tick
        }
    }
}
