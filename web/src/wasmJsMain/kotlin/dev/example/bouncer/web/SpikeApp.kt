package dev.example.bouncer.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.presentation.UiState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Throwaway spike: proves Compose Multiplatform + Koin + the shared Ktor client all work together
 * in the browser, end to end against the real backend (login, then an authenticated request).
 */
@Composable
fun SpikeApp() {
    MaterialTheme {
        val api = koinInject<BouncerApiClient>()
        val scope = rememberCoroutineScope()
        var log by remember { mutableStateOf("Press the button.") }

        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bouncer web spike", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = {
                scope.launch {
                    log = "Logging in as caro@example.com (client_type=web)…"
                    when (val login = api.login("caro@example.com", "password123", "web")) {
                        is UiState.Error -> log = "Login failed: ${login.type} — ${login.message}"
                        is UiState.Loading -> Unit
                        is UiState.Content -> {
                            val customers = api.getCustomers()
                            log = "Login OK (token ${login.data.accessToken.take(12)}…). " +
                                "GET /customers -> " + when (customers) {
                                    is UiState.Content -> "${customers.data.size} customers: " +
                                        customers.data.joinToString { it.name }
                                    is UiState.Error -> "${customers.type} — ${customers.message}"
                                    UiState.Loading -> "loading"
                                }
                        }
                    }
                }
            }) { Text("Login as admin + load customers") }
            Text(log)
        }
    }
}
