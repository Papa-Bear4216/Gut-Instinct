# Gut-Instinct / SecondGuess (contextual-coach)

Native Kotlin Android app (`com.registry.coach` / `com.secondguess.app`) providing on-device behavioral-pattern discovery, task gap detection, and workflow automation powered by on-device Gemini Nano (Android AICore) and Accessibility monitoring.

---

## 1. The 13 Core Rules (Strict Invariants)

1. **Native Android only:** Shipped as an APK/AAB in Kotlin/Jetpack Compose.
2. **No subscription tracking:** Do NOT reintroduce costs, billing cycles, dormancy spending alerts, or subscription dashboards.
3. **Core product loop:** `observe` → `identify repeated pattern` → `filter` → `generate suggestion` → `user approval` → `execute` → `log` → `refine`.
4. **Pattern threshold:** Requires at least **3 occurrences** within the sliding window (90s). Never claim a pattern below 3 transitions.
5. **No Cloud AI keys:** Built-in **Gemini Nano** through Android **AICore** is the only generative AI. Do not add OpenAI, Gemini cloud API keys, or external AI endpoints.
6. **Pre-filter safety:** Sensitive-package filtering (`ContextGuard`) MUST run **before** any accessibility-tree or selected-text inspection.
7. **Zero raw screen leaks:** Raw screen text, selected text, node trees, passwords, message bodies, and notification contents must NEVER be logged, persisted, or uploaded.
8. **Firestore scope:** Firebase stores authentication identity, sanitized suggestions, approved workflows, settings, counters, and execution results. NEVER raw screen context.
9. **Deterministic validation:** Gemini may label, explain, refine, or imagine a workflow only from supplied facts. Executable package names and action parameters MUST pass deterministic validation. Never execute a model-invented package.
10. **Explicit user approval:** No generated workflow may run before explicit user approval. Users can pause, resume, trigger manually, and reject suggestions.
11. **Loop protection:** Automatic triggers require cooldowns and a 30-second execution timeout.
12. **No fabricated capability:** If device support, AICore status, or an Android API is unknown, report it as unknown and use a safe fallback. Never fabricate capability.
13. **Imaginative suggestions:** Unlocked only after an observed base pattern exists.

---

## 2. Architecture & File Map

```
contextual-coach/app/src/main/
├── AndroidManifest.xml                        # Declares AccessibilityService, permissions, launcher
├── kotlin/com/registry/coach/
│   ├── CoachApplication.kt                   # App entry, Firebase init
│   ├── ai/
│   │   ├── GeminiNanoIntentEngine.kt         # Intent inference (NAVIGATION, COPY_PASTE, etc.)
│   │   └── OnDeviceWorkflowGenerator.kt      # Titles and reasons enrichment via on-device GenAI
│   ├── data/
│   │   ├── WorkflowModels.kt                 # WorkflowSuggestion, WorkflowAction, AutomationSchema
│   │   └── WorkflowStore.kt                  # Local persistence for suggestions, approvals, history
│   ├── engine/
│   │   └── PatternEngine.kt                  # Detects >=3 transitions, gap windows, sensitive checks
│   ├── execution/
│   │   └── WorkflowExecutor.kt               # Deterministic action runners (launch_app, open_url, etc.)
│   ├── filter/
│   │   └── ContextGuard.kt                   # Pre-filter denylist (banking, health, passwords, auth)
│   ├── monitor/
│   │   └── AccessibilityMonitor.kt           # AccessibilityService tracking foreground app transitions
│   ├── sync/
│   │   └── FirebaseWorkflowSync.kt           # Syncs sanitized workflow suggestions to Firestore
│   └── ui/
│       ├── MainActivity.kt                   # Compose root container & approval dialogs
│       ├── MainScreen.kt                     # Active patterns, pending approvals, status badges
│       ├── AccessibilitySettingsHelper.kt   # System settings intent launcher
│       └── components/                       # WorkflowCard, SuggestionCard, PrivacyCard, etc.
└── res/
    ├── raw/sensitive_apps.json               # Seed list of sensitive package substrings
    └── xml/accessibility_service_config.xml   # AccessibilityService flags & event types
```

---

## 3. Known Issues & Priority Remediations

1. **`Generation.getClient()` Fix:** `GeminiNanoIntentEngine.kt` and `OnDeviceWorkflowGenerator.kt` call a non-existent `Generation.getClient()` static. Real on-device Gemini on Android uses AICore / `GenerativeModel` / `AiSession`.
2. **Intent to Action Wiring:** `PatternEngine.suggestion()` / `WorkflowExecutor.execute()` needs `IntentResult.suggestedAction` and `executionPayload` wired into `WorkflowAction`.
3. **Denylist Harmonization:** Align `PatternEngine.isSensitive()` with `ContextGuard.kt` (ensure `health`/`medical` are covered in both).
4. **Main Thread Performance:** Offload `AccessibilityMonitor.kt` event-history serialization from the main thread into background coroutine.

---

## 4. Single-File Codebase Digest

If you need to inspect all Kotlin source files at once without issuing multiple `Read` tool calls, read:
👉 **[`CODEBASE_DIGEST.md`](./CODEBASE_DIGEST.md)**
