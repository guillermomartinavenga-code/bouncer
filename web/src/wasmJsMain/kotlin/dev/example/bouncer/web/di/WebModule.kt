package dev.example.bouncer.web.di

import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.client.InMemoryTokenStorage
import dev.example.bouncer.shared.client.TokenStorage
import io.ktor.client.engine.js.Js
import org.koin.dsl.module

const val BACKEND_BASE_URL = "http://localhost:8080"

val webModule = module {
    single<TokenStorage> { InMemoryTokenStorage() }
    single { BouncerApiClient(BACKEND_BASE_URL, get<TokenStorage>(), Js.create()) }
}
