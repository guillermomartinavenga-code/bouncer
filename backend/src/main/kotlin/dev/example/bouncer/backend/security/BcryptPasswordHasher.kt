package dev.example.bouncer.backend.security

import at.favre.lib.crypto.bcrypt.BCrypt
import dev.example.bouncer.shared.domain.auth.PasswordHasher

class BcryptPasswordHasher(private val cost: Int = 12) : PasswordHasher {
    private val verifier = BCrypt.verifyer()

    override fun hash(plainPassword: String): String =
        BCrypt.withDefaults().hashToString(cost, plainPassword.toCharArray())

    override fun verify(plainPassword: String, hash: String): Boolean =
        verifier.verify(plainPassword.toCharArray(), hash).verified
}
