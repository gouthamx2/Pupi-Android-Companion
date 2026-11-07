package com.pupi.pet

import android.app.Application
import com.google.android.filament.Filament

class PupiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Filament native C++ graphics engine once at app launch
        Filament.init()
    }
}
