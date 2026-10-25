package br.com.memora

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant

/** Pure merge for a refresh of the CURRENT grant; never used to merge another browser login. */
internal object RefreshProbePolicy {
    const val MAX_CHECKS = 16
    const val MAX_BODY = 64 * 1024
    fun merge(previous: Map<String, String>, response: Map<String, String>, receivedAt: Instant): Map<String, String> {
        require(!previous["refresh_token"].isNullOrBlank()) { "No current refresh credential" }
        require(!response["access_token"].isNullOrBlank()) { "Missing refreshed access credential" }
        val seconds = response["expires_in"]?.toLongOrNull()
        require(seconds != null && seconds in 1..31_536_000) { "Invalid refreshed lifetime" }
        require(response["token_type"].equals("Bearer", ignoreCase = true)) { "Unexpected refreshed credential type" }
        val merged = linkedMapOf<String, String>()
        listOf("access_token", "expires_in", "token_type").forEach { merged[it] = response.getValue(it) }
        val scope = response["scope"] ?: previous["scope"]
        if (scope != null) {
            require(scope.split(' ').contains("https://www.googleapis.com/auth/photospicker.mediaitems.readonly")) { "Picker scope missing" }
            merged["scope"] = scope
        }
        val refresh = response["refresh_token"] ?: previous.getValue("refresh_token")
        require(refresh.isNotBlank()) { "Invalid rotated refresh credential" }
        merged["refresh_token"] = refresh
        // Omitting lifetime, including on rotation, never restarts an existing grant's clock.
        val refreshSeconds = response["refresh_token_expires_in"]
        if (refreshSeconds != null) {
            val lifetime = refreshSeconds.toLongOrNull()
            require(lifetime != null && lifetime > 0) { "Invalid refresh lifetime" }
            merged["refreshExpiresAt"] = receivedAt.plusSeconds(lifetime).toString()
        } else previous["refreshExpiresAt"]?.let { merged["refreshExpiresAt"] = it }
        return merged
    }
    fun safeError(value: String): String = if (value in setOf("invalid_grant", "invalid_client", "invalid_request",
        "unauthorized_client", "unsupported_grant_type", "invalid_scope", "temporarily_unavailable", "server_error")) value else "OTHER"
}

internal fun readRefreshBody(input: InputStream, active: () -> Unit): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var total = 0
    try {
        while (true) {
            active()
            val count = input.read(buffer, 0, minOf(buffer.size, RefreshProbePolicy.MAX_BODY - total + 1))
            active()
            if (count == -1) return output.toByteArray()
            total += count
            require(total <= RefreshProbePolicy.MAX_BODY) { "Refresh response exceeds bound" }
            output.write(buffer, 0, count)
        }
    } finally { buffer.fill(0) }
}
