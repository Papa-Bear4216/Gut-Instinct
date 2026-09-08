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
