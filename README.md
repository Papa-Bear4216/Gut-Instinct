# Gut-Instinct

On-device behavioral-pattern discovery, task gap detection, and workflow automation engine powered by on-device Gemini Nano and Accessibility monitoring.

## Architecture

- **Contextual Coach (`contextual-coach/`)**: Native Kotlin Android application (`com.secondguess.app`) providing:
  - Background accessibility observation of foreground transitions.
  - Strict privacy sandboxing: denylist for sensitive apps (banking, password managers, healthcare).
  - Ephemeral on-device **Gemini Nano** analysis of task friction without cloud AI or API keys.
  - User approval checkpoints before any automation or shortcut is executed.
  - Sanitized workflow metadata synchronization with Firestore.

## Repository Structure

- `contextual-coach/`: Android Studio project with `AccessibilityService`, `GeminiNanoGapEvaluator`, and deterministic action runners.
- `.github/`: CI workflows and automation configs.
