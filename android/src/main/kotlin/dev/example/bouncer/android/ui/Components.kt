package dev.example.bouncer.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.dto.OrderResponse
import dev.example.bouncer.shared.presentation.BouncerViewModel
import dev.example.bouncer.shared.presentation.UiState
import org.koin.compose.getKoin

/**
 * ViewModels are plain objects (no androidx.lifecycle), so Compose owns them: created through Koin
 * when the screen enters composition, cancelled via `onCleared()` when it leaves. Pass keys (e.g. the
 * user id) to get a fresh instance — and fresh data — whenever they change.
 */
@Composable
inline fun <reified VM : BouncerViewModel<*>> rememberViewModel(vararg keys: Any?): VM {
    val koin = getKoin()
    val viewModel = remember(*keys) { koin.get<VM>() }
    DisposableEffect(viewModel) { onDispose { viewModel.onCleared() } }
    return viewModel
}

/** Renders the three LCE states; a persistent 401 (refresh failed too) hands control back to login. */
@Composable
fun <T> UiStateView(
    state: UiState<T>,
    onRetry: () -> Unit,
    onSessionExpired: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> {
            if (state.type == ErrorType.UNAUTHORIZED) {
                LaunchedEffect(state) { onSessionExpired() }
            }
            ErrorMessage(state, onRetry)
        }
        is UiState.Content -> content(state.data)
    }
}

@Composable
fun ErrorMessage(error: UiState.Error, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Error: ${error.type}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(error.message, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun OrderCard(order: OrderResponse, actions: @Composable () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.id, style = MaterialTheme.typography.titleSmall)
            Text("Customer: ${order.customerId}")
            Text("Status: ${order.status}")
            order.lines.forEach { Text("• ${it.quantity} × ${it.itemId}") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}
