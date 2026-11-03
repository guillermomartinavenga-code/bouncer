package dev.example.bouncer.web.di

import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.client.ClientLogger
import dev.example.bouncer.shared.client.InMemoryTokenStorage
import dev.example.bouncer.shared.client.TokenStorage
import dev.example.bouncer.shared.presentation.AdminCatalogViewModel
import dev.example.bouncer.shared.presentation.AdminOrdersViewModel
import dev.example.bouncer.shared.presentation.CatalogViewModel
import dev.example.bouncer.shared.presentation.CustomerOrdersViewModel
import dev.example.bouncer.shared.presentation.CustomersViewModel
import dev.example.bouncer.shared.presentation.OrderLookupViewModel
import dev.example.bouncer.shared.presentation.SessionViewModel
import io.ktor.client.engine.js.Js
import org.koin.dsl.module

const val BACKEND_BASE_URL = "http://localhost:8080"

/** What this app declares to POST /auth/login; the server enforces which roles may use it. */
private const val CLIENT_TYPE = "web"

val webModule = module {
    // Tokens live in memory only: reloading the page logs the user out (nothing is persisted in the browser).
    single<TokenStorage> { InMemoryTokenStorage() }
    // The web app only runs from the dev server, so traffic and token logs go to the browser console.
    single { BouncerApiClient(BACKEND_BASE_URL, get<TokenStorage>(), Js.create(), ClientLogger { println("[Bouncer] $it") }) }

    factory { SessionViewModel(get(), CLIENT_TYPE) }
    factory { CatalogViewModel(get()) }
    factory { CustomerOrdersViewModel(get()) }
    factory { OrderLookupViewModel(get()) }
    factory { AdminCatalogViewModel(get()) }
    factory { AdminOrdersViewModel(get()) }
    factory { CustomersViewModel(get()) }
}
