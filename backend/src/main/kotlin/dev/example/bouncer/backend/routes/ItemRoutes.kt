package dev.example.bouncer.backend.routes

import dev.example.bouncer.backend.security.PolicyEnforcementPoint
import dev.example.bouncer.shared.domain.ResourceNotFoundException
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.model.Item
import dev.example.bouncer.shared.domain.policy.Action
import dev.example.bouncer.shared.domain.repository.ItemRepository
import dev.example.bouncer.shared.dto.ItemWriteRequest
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.itemRoutes(itemRepository: ItemRepository, tokenGateway: TokenGateway) {
    get("/items", {
        description = "List the item catalog. Available to every authenticated role."
    }) {
        call.respond(itemRepository.findAll().map { it.toDto() })
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.VIEW_ITEMS
    }

    post("/items", {
        description = "Create an item. ADMIN only."
    }) {
        val request = call.receive<ItemWriteRequest>()
        val item = Item(id = newId("item"), name = request.name, price = request.price, stock = request.stock)
        call.respond(HttpStatusCode.Created, itemRepository.save(item).toDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.MANAGE_ITEMS
    }

    put("/items/{id}", {
        description = "Replace an item. ADMIN only."
    }) {
        val id = call.parameters["id"]!!
        itemRepository.findById(id) ?: throw ResourceNotFoundException()
        val request = call.receive<ItemWriteRequest>()
        val updated = Item(id = id, name = request.name, price = request.price, stock = request.stock)
        call.respond(itemRepository.save(updated).toDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.MANAGE_ITEMS
    }

    delete("/items/{id}", {
        description = "Delete an item. ADMIN only."
    }) {
        val id = call.parameters["id"]!!
        if (!itemRepository.delete(id)) throw ResourceNotFoundException()
        call.respond(HttpStatusCode.NoContent)
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.MANAGE_ITEMS
    }
}
