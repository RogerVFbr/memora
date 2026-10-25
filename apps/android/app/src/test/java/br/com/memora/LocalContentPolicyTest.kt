package br.com.memora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalContentPolicyTest {
    @Test fun permitsOnlyPackagedHttpsAssets() {
        listOf(LocalContentPolicy.ENTRY,
            "https://appassets.androidplatform.net/assets/baseline.css",
            "https://appassets.androidplatform.net/assets/index.html#section")
            .forEach { assertTrue(it, LocalContentPolicy.allows(it)) }
    }

    @Test fun rejectsExternalOriginsSchemesAndEscapes() {
        listOf("https://example.com/assets/index.html",
            "http://appassets.androidplatform.net/assets/index.html",
            "https://appassets.androidplatform.net.evil.com/assets/index.html",
            "https://user@appassets.androidplatform.net/assets/index.html",
            "https://appassets.androidplatform.net:443/assets/index.html",
            "https://appassets.androidplatform.net/resources/index.html",
            "https://appassets.androidplatform.net/assets/../secret",
            "https://appassets.androidplatform.net/assets/%2e%2e/secret",
            "file:///android_asset/index.html", "content://media/photo", "javascript:alert(1)", "bad url")
            .forEach { assertFalse(it, LocalContentPolicy.allows(it)) }
    }
}
