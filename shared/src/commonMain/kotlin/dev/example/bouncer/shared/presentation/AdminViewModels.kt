package dev.example.bouncer.shared.presentation

import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.dto.CreateOrderRequest
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.dto.ItemWriteRequest
import dev.example.bouncer.shared.dto.OrderLineDto
import dev.example.bouncer.shared.dto.OrderResponse
import dev.example.bouncer.shared.dto.UserResponse

/** The ADMIN's item management: the catalog plus the create/edit/delete actions only ADMIN may call. */
class AdminCatalogViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<ItemResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getItems())
    }

    /** Creates the item when [id] is null, otherwise replaces the existing one. */
    fun save(id: String?, name: String, price: Double, stock: Int) = launch {
        setState(UiState.Loading)
        val request = ItemWriteRequest(name, price, stock)
        val result = if (id == null) api.createItem(request) else api.updateItem(id, request)
        if (result is UiState.Error) setState(result) else setState(api.getItems())
    }

    fun delete(id: String) = launch {
        setState(UiState.Loading)
        val result = api.deleteItem(id)
        if (result is UiState.Error) setState(result) else setState(api.getItems())
    }
}

/** The ADMIN's view of every order: cancel any pending one, or place one on behalf of a customer. */
class AdminOrdersViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<OrderResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getOrders())
    }

    fun cancel(orderId: String) = launch {
        setState(UiState.Loading)
        val result = api.cancelOrder(orderId)
        if (result is UiState.Error) setState(result) else setState(api.getOrders())
    }

    fun createFor(customerId: String, itemId: String, quantity: Int) = launch {
        setState(UiState.Loading)
        val request = CreateOrderRequest(customerId, listOf(OrderLineDto(itemId, quantity)))
        val result = api.createOrder(request)
        if (result is UiState.Error) setState(result) else setState(api.getOrders())
    }
}

class CustomersViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<UserResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getCustomers())
    }
}
