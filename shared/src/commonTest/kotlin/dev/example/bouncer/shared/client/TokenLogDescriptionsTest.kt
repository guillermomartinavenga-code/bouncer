package dev.example.bouncer.shared.client

import dev.example.bouncer.shared.dto.TokenPairResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TokenLogDescriptionsTest {

    private val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiAiY3VzdG9tZXItMSIsICJyb2xlIjogIkNVU1RPTUVSIiwgImlzcyI6ICJib3VuY2VyIiwgImlhdCI6IDE3MDAwMDAwMDAsICJleHAiOiAxNzAwMDAwMzAwfQ.sig"

    @Test
    fun `token pair description prints the full tokens and the decoded claims`() {
        val text = TokenLogDescriptions.describeTokenPair(
            "login",
            TokenPairResponse(jwt, accessTokenExpiresAt = 0, refreshToken = "opaque-refresh", refreshTokenExpiresAt = 0),
        )

        assertContains(text, jwt)
        assertContains(text, "opaque-refresh")
        assertContains(text, "sub=customer-1 role=CUSTOMER iss=bouncer exp=2023-11-14T22:18:20Z")
    }

    @Test
    fun `an access token long past its exp is reported as expired`() {
        assertContains(TokenLogDescriptions.describeAccessToken(jwt), "EXPIRED")
    }

    @Test
    fun `a token that is not a JWT is described by its tail only`() {
        assertEquals("…ue-token", TokenLogDescriptions.describeAccessToken("opaque-value-token"))
    }
}
