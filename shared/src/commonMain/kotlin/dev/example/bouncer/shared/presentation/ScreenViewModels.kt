package dev.example.bouncer.shared.presentation

import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.dto.CreateOrderRequest
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.dto.OrderLineDto
import dev.example.bouncer.shared.dto.OrderResponse

class CatalogViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<ItemResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getItems())
    }
}

/** A CUSTOMER's own orders: list, place a one-line order from the catalog, cancel a pending one. */
class CustomerOrdersViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<OrderResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getOrders())
    }

    fun createOrder(itemId: String) = launch {
        setState(UiState.Loading)
        val request = CreateOrderRequest(lines = listOf(OrderLineDto(itemId, quantity = 1)))
        when (val result = api.createOrder(request)) {
            is UiState.Error -> setState(result)
            else -> setState(api.getOrders())
        }
    }

    fun cancel(orderId: String) = launch {
        setState(UiState.Loading)
        when (val result = api.cancelOrder(orderId)) {
            is UiState.Error -> setState(result)
            else -> setState(api.getOrders())
        }
    }
}

/** Looks an order up by id — lets the demo show the FORBIDDEN error for someone else's order. */
class OrderLookupViewModel(private val api: BouncerApiClient) : BouncerViewModel<OrderResponse?>() {

    init {
        setState(UiState.Content(null))
    }

    fun lookup(orderId: String) = launch {
        val id = orderId.trim()
        if (id.isEmpty()) {
            setState(UiState.Error(ErrorType.UNKNOWN, "Enter an order id to look up."))
            return@launch
        }
        setState(UiState.Loading)
        setState(api.getOrder(id))
    }
}

/** The DISPATCHER's view: every order, with the (DISPATCHER-only) dispatch action. */
class DispatcherOrdersViewModel(private val api: BouncerApiClient) : BouncerViewModel<List<OrderResponse>>() {

    init {
        load()
    }

    fun load() = launch {
        setState(UiState.Loading)
        setState(api.getOrders())
    }

    fun dispatch(orderId: String) = launch {
        setState(UiState.Loading)
        when (val result = api.dispatchOrder(orderId)) {
            is UiState.Error -> setState(result)
            else -> setState(api.getOrders())
        }
    }
}
