package br.com.memora

import java.net.URI

internal object LocalContentPolicy {
    const val ENTRY = "https://appassets.androidplatform.net/assets/index.html"

    fun allows(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
            uri.rawAuthority == "appassets.androidplatform.net" &&
            uri.rawPath.startsWith("/assets/") &&
            uri.normalize().rawPath == uri.rawPath &&
            !uri.rawPath.contains('%') && !uri.rawPath.contains('\\')
    } catch (_: Exception) {
        false
    }
}
