# Gut Instinct / SecondGuess implementation rules

These rules control future development.

1. This is one native Android application written in Kotlin and shipped as an APK/AAB.
2. Subscription tracking is removed. Do not reintroduce costs, billing cycles, dormancy spending alerts, or subscription dashboards.
3. The product loop is observe → identify repeated pattern → filter → generate suggestion → user approval → execute → log → refine.
4. A directly observed pattern requires at least three occurrences. Do not claim a pattern exists below that threshold.
5. Built-in Gemini Nano through Android AICore is the only generative AI. Do not add OpenAI, Gemini cloud, or any other AI API key.
6. Sensitive-package filtering must run before any accessibility-tree or selected-text access.
7. Raw screen text, selected text, node trees, passwords, message bodies, and notification contents must never be logged, persisted, or uploaded.
8. Firebase may store authentication identity, sanitized suggestions, approved workflows, settings, counters, and execution results. It must not receive raw screen context.
9. Gemini may label, explain, refine, or imagine a workflow only from facts supplied to it. Executable app package names and action parameters must pass deterministic validation. Never execute a model-invented package or unsupported action.
10. No generated workflow may run before explicit user approval. Users must be able to pause, resume, run manually, and reject suggestions.
11. Automatic triggers require cooldowns and a 30-second execution timeout to prevent loops.
12. If device support, model status, an Android API, or repository state is unknown, report it as unknown and add a safe fallback. Never fabricate capability, test results, or successful execution.
13. Imaginative suggestions are unlocked only after an observed base exists. They pass the same filtering, validation, and approval gates as observed suggestions.
