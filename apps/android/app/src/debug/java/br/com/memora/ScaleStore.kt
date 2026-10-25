package br.com.memora

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private, authenticated snapshot for the G5 scale bench; never stores media bytes. */
class ScaleStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "picker-scale.enc"))

    companion object {
        private val lock = Any()
        private const val ALIAS = "memora.picker.scale.v1"
        private const val MAX_BYTES = 8 * 1024 * 1024
        private val aad = "memora-picker-scale-v1".toByteArray(Charsets.US_ASCII)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    fun read(): JSONObject? = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        val envelope = file.openRead().use { readScaleBytes(it, MAX_BYTES + 64) }
        require(envelope.size > 30 && envelope[0].toInt() == 1) { "Invalid private snapshot" }
        val ivLength = envelope[1].toInt() and 0xff
        require(ivLength == 12 && envelope.size > 2 + ivLength + 16) { "Invalid private snapshot" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, envelope.copyOfRange(2, 2 + ivLength)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(envelope, 2 + ivLength, envelope.size - 2 - ivLength)
        try { JSONObject(String(plain, Charsets.UTF_8)).also { require(it.optInt("version") == 1) } }
        finally { plain.fill(0) }
    }

    fun save(snapshot: JSONObject) = synchronized(lock) {
        require(snapshot.optInt("version") == 1) { "Invalid private snapshot" }
        val plain = snapshot.toString().toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_BYTES) { "Private snapshot exceeds diagnostic limit" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(aad)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val iv = cipher.iv
        require(iv.size == 12)
        val output = file.startWrite()
        try {
            output.write(byteArrayOf(1, iv.size.toByte()))
            output.write(iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    /** Keep read/modify/write atomic across store instances used by the native UI. */
    fun update(change: (JSONObject) -> Unit): JSONObject = synchronized(lock) {
        val snapshot = read() ?: error("No private probe snapshot")
        change(snapshot)
        save(snapshot)
        snapshot
    }

    fun clear() = synchronized(lock) {
        file.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        check(!file.baseFile.exists()) { "Private snapshot cleanup incomplete" }
    }
}
