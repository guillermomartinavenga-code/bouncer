package dev.example.bouncer.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.example.bouncer.web.di.webModule
import dev.example.bouncer.web.ui.BouncerWebApp
import kotlinx.browser.document
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(webModule) }
    ComposeViewport(document.body!!) { BouncerWebApp() }
}
