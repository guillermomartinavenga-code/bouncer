package dev.example.bouncer.backend.di

import dev.example.bouncer.backend.data.InMemoryItemRepository
import dev.example.bouncer.backend.data.InMemoryOrderRepository
import dev.example.bouncer.backend.data.InMemoryUserRepository
import dev.example.bouncer.backend.data.SeedData
import dev.example.bouncer.backend.data.buildSeedData
import dev.example.bouncer.backend.security.BcryptPasswordHasher
import dev.example.bouncer.backend.security.JwtTokenGateway
import dev.example.bouncer.shared.domain.auth.PasswordHasher
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.repository.ItemRepository
import dev.example.bouncer.shared.domain.repository.OrderRepository
import dev.example.bouncer.shared.domain.repository.UserRepository
import dev.example.bouncer.shared.domain.usecase.AuthenticateUserUseCase
import dev.example.bouncer.shared.domain.usecase.LogoutUseCase
import dev.example.bouncer.shared.domain.usecase.RefreshSessionUseCase
import org.koin.dsl.bind
import org.koin.dsl.module

val backendModule = module {
    single<PasswordHasher> { BcryptPasswordHasher() }
    single { JwtTokenGateway() } bind TokenGateway::class

    single { buildSeedData(get()) }
    single<UserRepository> { InMemoryUserRepository(get<SeedData>().users) }
    single<ItemRepository> { InMemoryItemRepository(get<SeedData>().items) }
    single<OrderRepository> { InMemoryOrderRepository(get<SeedData>().orders) }

    single { AuthenticateUserUseCase(get(), get(), get()) }
    single { RefreshSessionUseCase(get()) }
    single { LogoutUseCase(get()) }
}
