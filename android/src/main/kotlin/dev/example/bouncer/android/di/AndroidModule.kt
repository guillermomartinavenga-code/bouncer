package dev.example.bouncer.android.di

import dev.example.bouncer.android.security.KeystoreTokenStorage
import dev.example.bouncer.shared.client.BouncerApiClient
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

/** 10.0.2.2 is the emulator's alias for the host machine, where the backend runs on :8080. */
const val BACKEND_BASE_URL = "http://10.0.2.2:8080"

/** What this app declares to POST /auth/login; the server enforces which roles may use it. */
private const val CLIENT_TYPE = "android"

val androidModule = module {
    single { KeystoreTokenStorage(androidContext()) } bind TokenStorage::class
    single { BouncerApiClient(BACKEND_BASE_URL, get<TokenStorage>(), OkHttp.create()) }

    factory { SessionViewModel(get(), CLIENT_TYPE) }
    factory { CatalogViewModel(get()) }
    factory { CustomerOrdersViewModel(get()) }
    factory { OrderLookupViewModel(get()) }
    factory { DispatcherOrdersViewModel(get()) }
}
