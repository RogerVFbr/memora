package br.com.memora

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class OAuthTransactionTest {
    private fun attempt() = OAuthTransaction("123-example.apps.googleusercontent.com", 100)
    @Test fun pkceIsRandomAndUsesS256WithoutVerifierInUrl() {
        val a = attempt(); val b = attempt()
        assertNotEquals(a.state, b.state); assertNotEquals(a.verifier, b.verifier)
        assertEquals(43, a.verifier.length)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(a.verifier.toByteArray())), a.challenge)
        assertTrue(a.authorizationUrl().contains("code_challenge_method=S256"))
        assertFalse(a.authorizationUrl().contains(a.verifier))
    }
    @Test fun acceptsAssociatedCodeOnlyOnce() {
        val a = attempt(); val cb = "${a.redirect}?state=${a.state}&code=test-code"
        assertEquals(OAuthTransaction.Result.Code("test-code"), a.consume(cb, 101))
        assertEquals(OAuthTransaction.Result.Rejected, a.consume(cb, 102))
    }
    @Test fun rejectsWrongOriginStateDuplicatesAndFragments() {
        val cases: List<(OAuthTransaction) -> String> = listOf(
            { "evil:/oauth2redirect?state=${it.state}&code=x" },
            { "${it.redirect}?state=wrong&code=x" },
            { "${it.redirect}?state=${it.state}&code=x&code=y" },
            { "${it.redirect}?state=${it.state}&code=x#ignored" },
            { "${it.redirect}?state=${it.state}&code=x&error=access_denied" },
            { it.redirect.replace(":/", "://evil/") + "?state=${it.state}&code=x" })
        cases.forEach { build -> val a = attempt(); assertEquals(OAuthTransaction.Result.Rejected, a.consume(build(a), 101)) }
    }
    @Test fun deniesWithoutCodeExchange() {
        val a = attempt()
        assertEquals(OAuthTransaction.Result.Denied, a.consume("${a.redirect}?state=${a.state}&error=access_denied", 101))
    }
    @Test fun rejectsExpiredAndCancelledTransactions() {
        val a = attempt(); assertEquals(OAuthTransaction.Result.Rejected,
            a.consume("${a.redirect}?state=${a.state}&code=x", 100 + OAuthTransaction.LIFETIME_MS))
        val b = attempt(); b.cancel(); assertEquals(OAuthTransaction.Result.Rejected,
            b.consume("${b.redirect}?state=${b.state}&code=x", 101))
    }
}
