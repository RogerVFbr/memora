package br.com.memora

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Ephemeral PKCE transaction. No token, verifier or callback is logged or persisted. */
internal class OAuthTransaction(val clientId: String, private val startedAt: Long,
    private val callbackPath: String = "/oauth2redirect") {
    init { require(callbackPath in setOf("/oauth2redirect", "/oauth2redirect/scale")) }
    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
        const val LIFETIME_MS = 300_000L
        private fun random() = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        fun form(values: Map<String, String>): String = values.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
    }
    val state = random()
    val verifier = random()
    val redirect = "com.googleusercontent.apps.${clientId.removeSuffix(".apps.googleusercontent.com")}:$callbackPath"
    private var consumed = false
    val challenge: String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizationUrl() = "https://accounts.google.com/o/oauth2/v2/auth?" + form(mapOf(
        "client_id" to clientId, "redirect_uri" to redirect, "response_type" to "code",
        "scope" to SCOPE, "state" to state, "code_challenge" to challenge,
        "code_challenge_method" to "S256", "prompt" to "consent"))

    sealed class Result {
        data class Code(val code: String) : Result()
        object Denied : Result()
        object Rejected : Result()
    }
    fun cancel() { consumed = true }
    fun consume(callback: String, now: Long): Result {
        if (consumed) return Result.Rejected
        consumed = true // Even malformed callbacks terminate this attempt.
        if (now < startedAt || now - startedAt >= LIFETIME_MS) return Result.Rejected
        return try {
            val uri = URI(callback)
            if (uri.isOpaque || uri.rawFragment != null || uri.rawAuthority != null ||
                uri.scheme != URI(redirect).scheme || uri.rawPath != callbackPath) return Result.Rejected
            val params = linkedMapOf<String, String>()
            for (pair in (uri.rawQuery ?: "").split('&')) {
                val parts = pair.split('=', limit = 2)
                if (parts.size != 2) return Result.Rejected
                val key = URLDecoder.decode(parts[0], "UTF-8")
                if (params.containsKey(key)) return Result.Rejected
                params[key] = URLDecoder.decode(parts[1], "UTF-8")
            }
            if (!MessageDigest.isEqual(state.toByteArray(), params["state"].orEmpty().toByteArray()))
                return Result.Rejected
            if (params.containsKey("code") && params.containsKey("error")) return Result.Rejected
            when {
                params["error"] == "access_denied" -> Result.Denied
                !params["code"].isNullOrBlank() && !params.containsKey("error") -> Result.Code(params.getValue("code"))
                else -> Result.Rejected
            }
        } catch (_: Exception) { Result.Rejected }
    }
}
