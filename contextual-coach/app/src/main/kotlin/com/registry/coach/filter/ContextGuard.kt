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
