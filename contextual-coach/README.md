# SecondGuess Native Android

One native Kotlin Android application that observes active workflows, recognizes repeated patterns, uses built-in Gemini Nano to describe suggestions, applies deterministic safety filters, requires approval, executes approved actions, records outcomes, and synchronizes sanitized workflow metadata with Firebase.

No cloud AI provider or AI API key is used. Raw screen context never enters Firebase.

## Windows setup

1. Install Android Studio and Android SDK Platform 35.
2. Create or select a Firebase Android app with package `com.secondguess.app`.
3. Enable Anonymous Authentication and create a Firestore database.
4. Download `google-services.json` and place it in `app/google-services.json`.
5. Deploy the included `firestore.rules` from Firebase Console or Firebase CLI.
6. Open this `contextual-coach` folder in Android Studio and allow Gradle Sync to finish.
7. Enable Developer options and USB debugging on the Android phone.
8. Connect the phone, approve its debugging prompt, select it, and click **Run**.
9. In SecondGuess, read the disclosure, tap **Enable observation**, and enable its Accessibility service.

The app still runs locally when Firebase has not been configured, but metadata sync is disabled.

## Verified product loop

1. Accessibility observes app changes.
2. Sensitive apps are blocked before any accessibility-tree read.
3. A candidate requires the same transition at least three times within 90 seconds.
4. Allowed-screen text is capped, processed ephemerally by Gemini Nano, and immediately discarded.
5. Gemini can label and explain a verified pattern; executable package targets remain deterministic.
6. The user approves or rejects the suggestion.
7. Approved workflows can run automatically from their trigger or manually with **Run now**.
8. A 30-second cooldown prevents immediate trigger loops.
9. Execution success/failure is stored locally and synchronized without raw context.

## Supported native actions

- Launch an Android app
- Open a URL
- Copy predetermined text
- Show a notification

Each action has a delay and stop/continue error behavior. The complete workflow has a 30-second execution timeout.

## Privacy rules

- Raw screen text, messages, passwords, notification bodies, and accessibility nodes are never persisted or uploaded.
- Bank, wallet, password-manager, authenticator, medical, health, and Settings packages are blocked before content access.
- Only the latest 200 package/timestamp observations remain locally.
- Firebase receives sanitized suggestions, approved workflow metadata, counters, and execution results.
- Every generated workflow requires explicit approval.

Run unit tests from Windows PowerShell with `./gradlew.bat testDebugUnitTest`.
