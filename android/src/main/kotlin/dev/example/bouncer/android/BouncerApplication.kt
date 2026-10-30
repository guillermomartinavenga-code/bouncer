package dev.example.bouncer.android

import android.app.Application
import dev.example.bouncer.android.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BouncerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BouncerApplication)
            modules(androidModule)
        }
    }
}
