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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.dto.UserResponse
import dev.example.bouncer.shared.presentation.AdminCatalogViewModel
import dev.example.bouncer.shared.presentation.AdminOrdersViewModel
import dev.example.bouncer.shared.presentation.CatalogViewModel
import dev.example.bouncer.shared.presentation.CustomersViewModel
import dev.example.bouncer.shared.presentation.UiState

@Composable
fun AdminHome(user: UserResponse, onLogout: () -> Unit, onSessionExpired: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    // Set by "View orders" on a customer: narrows the orders tab to that customer's orders.
    var customerFilter by remember { mutableStateOf<UserResponse?>(null) }

    HomeScaffold(user, listOf("Items", "Orders", "Customers"), tab, { tab = it }, onLogout) {
        when (tab) {
            0 -> AdminItemsScreen(user.id, onSessionExpired)
            1 -> AdminOrdersScreen(user.id, customerFilter, { customerFilter = null }, onSessionExpired)
            else -> CustomersScreen(user.id, onSessionExpired) {
                customerFilter = it
                tab = 1
            }
        }
    }
}

@Composable
private fun AdminItemsScreen(key: String, onSessionExpired: () -> Unit) {
    val viewModel = rememberViewModel<AdminCatalogViewModel>(key)
    val state by viewModel.uiState.collectAsState()
    // null = dialog closed; an item with a blank id = creating a new one.
    var editing by remember { mutableStateOf<ItemResponse?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp)) {
            Button(onClick = { editing = ItemResponse(id = "", name = "", price = 0.0, stock = 0) }) {
                Text("Add item")
            }
        }
        UiStateView(state, onRetry = viewModel::load, onSessionExpired = onSessionExpired) { items ->
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.name} (${item.id})", style = MaterialTheme.typography.titleSmall)
                                Text("$${item.price} · stock ${item.stock}")
                            }
                            OutlinedButton(onClick = { editing = item }) { Text("Edit") }
                            OutlinedButton(onClick = { viewModel.delete(item.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        ItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { name, price, stock ->
                viewModel.save(item.id.ifBlank { null }, name, price, stock)
                editing = null
            },
        )
    }
}

@Composable
private fun ItemDialog(item: ItemResponse, onDismiss: () -> Unit, onSave: (String, Double, Int) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var price by remember { mutableStateOf(if (item.id.isBlank()) "" else item.price.toString()) }
    var stock by remember { mutableStateOf(if (item.id.isBlank()) "" else item.stock.toString()) }
    val parsedPrice = price.toDoubleOrNull()
    val parsedStock = stock.toIntOrNull()
    val valid = name.isNotBlank() && parsedPrice != null && parsedPrice >= 0 && parsedStock != null && parsedStock >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id.isBlank()) "Add item" else "Edit ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Price") }, singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Stock") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), parsedPrice!!, parsedStock!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AdminOrdersScreen(
    key: String,
    customerFilter: UserResponse?,
    onClearFilter: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val viewModel = rememberViewModel<AdminOrdersViewModel>(key)
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        NewOrderForm(key, onCreate = viewModel::createFor)
        if (customerFilter != null) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Showing orders of ${customerFilter.name}", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClearFilter) { Text("Show all") }
            }
        }
        UiStateView(state, onRetry = viewModel::load, onSessionExpired = onSessionExpired) { orders ->
            val visible = orders.filter { customerFilter == null || it.customerId == customerFilter.id }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { order ->
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

/** Place an order on behalf of any customer — the ADMIN-only variant of POST /orders. */
@Composable
private fun NewOrderForm(key: String, onCreate: (customerId: String, itemId: String, quantity: Int) -> Unit) {
    val customersViewModel = rememberViewModel<CustomersViewModel>(key)
    val itemsViewModel = rememberViewModel<CatalogViewModel>(key)
    val customers = (customersViewModel.uiState.collectAsState().value as? UiState.Content)?.data.orEmpty()
    val items = (itemsViewModel.uiState.collectAsState().value as? UiState.Content)?.data.orEmpty()

    var customer by remember { mutableStateOf<UserResponse?>(null) }
    var item by remember { mutableStateOf<ItemResponse?>(null) }
    var quantity by remember { mutableStateOf("1") }
    val parsedQuantity = quantity.toIntOrNull()

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("New order on behalf of a customer", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chooser("Customer", customers, customer, { it.name }) { customer = it }
            Chooser("Item", items, item, { it.name }) { item = it }
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Qty") },
                singleLine = true,
                modifier = Modifier.padding(0.dp),
            )
            Button(
                enabled = customer != null && item != null && parsedQuantity != null && parsedQuantity > 0,
                onClick = { onCreate(customer!!.id, item!!.id, parsedQuantity!!) },
            ) { Text("Create order") }
        }
    }
}

@Composable
private fun CustomersScreen(key: String, onSessionExpired: () -> Unit, onViewOrders: (UserResponse) -> Unit) {
    val viewModel = rememberViewModel<CustomersViewModel>(key)
    val state by viewModel.uiState.collectAsState()

    UiStateView(state, onRetry = viewModel::load, onSessionExpired = onSessionExpired) { customers ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(customers, key = { it.id }) { customer ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${customer.name} (${customer.id})", style = MaterialTheme.typography.titleSmall)
                            Text(customer.email)
                        }
                        OutlinedButton(onClick = { onViewOrders(customer) }) { Text("View orders") }
                    }
                }
            }
        }
    }
}
