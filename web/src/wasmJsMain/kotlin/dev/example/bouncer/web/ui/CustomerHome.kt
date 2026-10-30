package dev.example.bouncer.web.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.example.bouncer.shared.domain.model.OrderStatus
import dev.example.bouncer.shared.dto.UserResponse
import dev.example.bouncer.shared.presentation.CatalogViewModel
import dev.example.bouncer.shared.presentation.CustomerOrdersViewModel
import dev.example.bouncer.shared.presentation.OrderLookupViewModel
import dev.example.bouncer.shared.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    user: UserResponse,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLogout: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${user.name} (${user.role})") },
                actions = { TextButton(onClick = onLogout) { Text("Log out") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { onTabSelected(index) }, text = { Text(title) })
                }
            }
            content()
        }
    }
}

@Composable
fun CustomerHome(user: UserResponse, onLogout: () -> Unit, onSessionExpired: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    // Shared by both tabs: ordering from the catalog must show up in "My orders".
    val ordersViewModel = rememberViewModel<CustomerOrdersViewModel>(user.id)

    HomeScaffold(user, listOf("Catalog", "My orders"), tab, { tab = it }, onLogout) {
        when (tab) {
            0 -> CustomerCatalogScreen(
                key = user.id,
                onSessionExpired = onSessionExpired,
                onOrder = { itemId ->
                    ordersViewModel.createOrder(itemId)
                    tab = 1
                },
            )
            else -> CustomerOrdersScreen(user, ordersViewModel, onSessionExpired)
        }
    }
}

@Composable
private fun CustomerCatalogScreen(key: String, onSessionExpired: () -> Unit, onOrder: (String) -> Unit) {
    val viewModel = rememberViewModel<CatalogViewModel>(key)
    val state by viewModel.uiState.collectAsState()

    UiStateView(state, onRetry = viewModel::load, onSessionExpired = onSessionExpired) { items ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text("$${item.price} · stock ${item.stock}")
                        }
                        Button(onClick = { onOrder(item.id) }) { Text("Order 1") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerOrdersScreen(
    user: UserResponse,
    viewModel: CustomerOrdersViewModel,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val lookupViewModel = rememberViewModel<OrderLookupViewModel>(user.id)
    val lookupState by lookupViewModel.uiState.collectAsState()
    var lookupId by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Look up an order by id", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lookupId,
                    onValueChange = { lookupId = it },
                    label = { Text("Order id (e.g. order-4)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { lookupViewModel.lookup(lookupId) }) { Text("Look up") }
            }
            when (val result = lookupState) {
                is UiState.Error -> ErrorMessage(result)
                is UiState.Content -> result.data?.let { OrderCard(it) }
                UiState.Loading -> Text("Looking up…")
            }
        }

        UiStateView(state, onRetry = viewModel::load, onSessionExpired = onSessionExpired) { orders ->
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderCard(order) {
                        if (order.status == OrderStatus.PENDING) {
                            OutlinedButton(onClick = { viewModel.cancel(order.id) }) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}
