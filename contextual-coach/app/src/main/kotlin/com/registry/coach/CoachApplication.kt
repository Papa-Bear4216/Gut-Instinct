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
