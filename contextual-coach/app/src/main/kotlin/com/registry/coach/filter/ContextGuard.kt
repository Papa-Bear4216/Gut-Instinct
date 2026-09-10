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
    fun isBlocked(packageName:String):Boolean = packageName in exact || isSensitivePackage(packageName)
    companion object {
        val PATTERNS = listOf(
            Regex("bank", RegexOption.IGNORE_CASE),
            Regex("wallet", RegexOption.IGNORE_CASE),
            Regex("password", RegexOption.IGNORE_CASE),
            Regex("authenticator", RegexOption.IGNORE_CASE),
            Regex("medical", RegexOption.IGNORE_CASE),
            Regex("health", RegexOption.IGNORE_CASE),
            Regex("doctor", RegexOption.IGNORE_CASE),
            Regex("invest", RegexOption.IGNORE_CASE),
            Regex("credit", RegexOption.IGNORE_CASE),
            Regex("com\\.android\\.settings")
        )

        val KNOWN_SENSITIVE_PACKAGES = setOf(
            "com.chase.sig.android",
            "com.bankofamerica.digitalwallet",
            "com.wellsfargo.mobile.android",
            "com.wf.wellsfargomobile",
            "com.citi.citimobile",
            "com.usbank.mobilebanking",
            "com.capitalone.mobile",
            "com.infonow.chase",
            "com.konylabs.capitalone",
            "com.americanexpress.android.acctsvcs.us",
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.squareup.cash",
            "com.coinbase.android",
            "org.toshi",
            "com.sofi.mobile",
            "com.fidelity.android",
            "com.schwab.mobile",
            "com.robinhood.android",
            "com.vanguard",
            "epic.mychart.android",
            "com.teladoc.members",
            "com.cvs.rx",
            "com.goodrx",
            "com.onepassword.android",
            "com.lastpass.lpandroid",
            "com.bitwarden.authenticator",
            "com.google.android.apps.healthdata",
            "com.google.android.apps.authenticator2",
            "com.microsoft.msa.authenticator",
            "com.authy.authy",
            "com.duosecurity.duomobile",
            "com.android.managedprovisioning",
            "com.android.vending.billing.InAppBillingService",
            "com.google.android.apps.walletnfcrel",
            "com.samsung.android.spay"
        )

        fun isSensitivePackage(packageName: String): Boolean =
            packageName in KNOWN_SENSITIVE_PACKAGES || PATTERNS.any { it.containsMatchIn(packageName) }
    }
}
