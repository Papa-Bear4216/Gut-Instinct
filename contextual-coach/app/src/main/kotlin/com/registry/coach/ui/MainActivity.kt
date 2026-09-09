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
    private var isPiecesSyncEnabled by mutableStateOf(true)
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
                    isPiecesSyncEnabled = isPiecesSyncEnabled,
                    suggestions = suggestions,
                    workflows = workflows,
                    onToggleAccessibility = {
                        AccessibilitySettingsHelper.openAccessibilitySettingsForThisService(this@MainActivity)
                    },
                    onToggleScreenContext = { enabled ->
                        store.setScreenContextEnabled(enabled)
                        refreshState()
                    },
                    onTogglePiecesSync = { enabled ->
                        store.setPiecesSyncEnabled(enabled)
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
        isPiecesSyncEnabled = store.piecesSyncEnabled()
        suggestions = store.suggestions()
        workflows = store.workflows()
    }
}
