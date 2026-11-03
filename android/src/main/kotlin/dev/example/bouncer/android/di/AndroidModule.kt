package dev.example.bouncer.android.di

import android.util.Log
import dev.example.bouncer.android.BuildConfig
import dev.example.bouncer.android.security.KeystoreTokenStorage
import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.client.ClientLogger
import dev.example.bouncer.shared.client.TokenStorage
import dev.example.bouncer.shared.presentation.CatalogViewModel
import dev.example.bouncer.shared.presentation.CustomerOrdersViewModel
import dev.example.bouncer.shared.presentation.DispatcherOrdersViewModel
import dev.example.bouncer.shared.presentation.OrderLookupViewModel
import dev.example.bouncer.shared.presentation.SessionViewModel
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** What this app declares to POST /auth/login; the server enforces which roles may use it. */
private const val CLIENT_TYPE = "android"

val androidModule = module {
    single { KeystoreTokenStorage(androidContext()) } bind TokenStorage::class
    // Traffic and token logs print whole tokens, so they only exist in debug builds (tag "Bouncer" in Logcat).
    single {
        val logger = if (BuildConfig.DEBUG) ClientLogger { Log.d("Bouncer", it) } else ClientLogger.None
        BouncerApiClient(BuildConfig.BACKEND_BASE_URL, get<TokenStorage>(), OkHttp.create(), logger)
    }

    factory { SessionViewModel(get(), CLIENT_TYPE) }
    factory { CatalogViewModel(get()) }
    factory { CustomerOrdersViewModel(get()) }
    factory { OrderLookupViewModel(get()) }
    factory { DispatcherOrdersViewModel(get()) }
}
