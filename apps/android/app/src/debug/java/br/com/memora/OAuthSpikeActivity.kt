package br.com.memora

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Debug-only SPK-01 surface. It never opens the Picker or reads personal media. */
@SuppressLint("SetTextI18n") // Portuguese-only debug diagnostics; not a product UI.
class OAuthSpikeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var login: Button
    private var transaction: OAuthTransaction? = null
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    private val timeout = Runnable { cancel("Tempo esgotado. Inicie outra tentativa.") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        status = TextView(this).apply {
            textSize = 22f
            text = "Ensaio OAuth Picker — desenvolvimento\nLogin no navegador desta TV. Não seleciona fotos."
        }
        login = Button(this).apply { text = "Iniciar autorização Google"; setOnClickListener { begin() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            addView(status); addView(login)
            addView(Button(context).apply { text = "Cancelar tentativa"; setOnClickListener { cancel("Tentativa cancelada.") } })
            addView(Button(context).apply { text = "Voltar à demonstração"; setOnClickListener { finish() } })
        })
        // A callback after process death must not restore an absent verifier.
        if (intent.data != null) { intent.data = null; status.text = "Retorno sem tentativa ativa. Inicie novamente." }
    }

    private fun begin() {
        cancel("")
        val client = getString(R.string.oauth_spike_client_id)
        if (client.isBlank()) { status.text = "Cliente Android local não configurado no build."; return }
        val attempt = OAuthTransaction(client, SystemClock.elapsedRealtime())
        transaction = attempt
        login.isEnabled = false
        status.text = "Aguardando autorização no navegador. Volte aqui para cancelar."
        handler.postDelayed(timeout, OAuthTransaction.LIFETIME_MS)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attempt.authorizationUrl())).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                // Target the browser verified on this Fire TV, avoiding media-player handlers.
                setPackage("com.amazon.cloud9")
            })
        } catch (_: ActivityNotFoundException) { cancel("Navegador externo indisponível.") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val callback = intent.data?.toString()
        intent.data = null
        setIntent(intent)
        if (callback == null) return
        val attempt = transaction
        transaction = null
        handler.removeCallbacks(timeout)
        val result = attempt?.consume(callback, SystemClock.elapsedRealtime())
        when (result) {
            is OAuthTransaction.Result.Code -> {
                status.text = "Retorno validado. Verificando escopo com sessão vazia…"
                val current = generation
                worker.execute {
                    val message = try { verifyScope(attempt, result.code, current) }
                        catch (_: Exception) { "Falha de rede ou resposta inválida. Reinicie o ensaio." }
                    handler.post {
                        if (generation == current && !isFinishing) { status.text = message; login.isEnabled = true }
                    }
                }
            }
            OAuthTransaction.Result.Denied -> cancel("Consentimento negado. Nenhuma sessão criada.")
            else -> cancel("Retorno rejeitado: sem tentativa válida, expirado ou não associado.")
        }
    }

    private fun cancel(message: String) {
        generation++
        transaction?.cancel(); transaction = null
        handler.removeCallbacks(timeout)
        if (::login.isInitialized) login.isEnabled = true
        if (::status.isInitialized && message.isNotBlank()) status.text = message
    }

    private fun request(url: String, method: String, body: String? = null, bearer: String? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", if (bearer == null)
                    "application/x-www-form-urlencoded" else "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            // Error bodies may contain codes or account details; do not read or display them.
            val response = if (code in 200..299) connection.inputStream.bufferedReader().use {
                val buffer = CharArray(65_537)
                var size = 0
                while (size < buffer.size) {
                    val read = it.read(buffer, size, buffer.size - size)
                    if (read == -1) break
                    size += read
                }
                require(size <= 65_536)
                String(buffer, 0, size)
            } else ""
            return code to response
        } finally { connection.disconnect() }
    }

    private fun verifyScope(attempt: OAuthTransaction, code: String, current: Int): String {
        if (generation != current) return "Cancelado."
        val (tokenStatus, tokenBody) = request("https://oauth2.googleapis.com/token", "POST",
            OAuthTransaction.form(mapOf("client_id" to attempt.clientId, "code" to code,
                "code_verifier" to attempt.verifier, "redirect_uri" to attempt.redirect,
                "grant_type" to "authorization_code")))
        if (tokenStatus !in 200..299) return "Troca OAuth recusada (HTTP $tokenStatus). Sem sessão Picker."
        val token = JSONObject(tokenBody).getString("access_token")
        if (generation != current) return "Cancelado após autorização. Token descartado da tentativa."
        val (createStatus, sessionBody) = request("https://photospicker.googleapis.com/v1/sessions", "POST", "{}", token)
        if (createStatus !in 200..299) return "OAuth respondeu, mas Picker recusou (HTTP $createStatus)."
        val sessionId = JSONObject(sessionBody).getString("id")
        require(sessionId.matches(Regex("[a-zA-Z0-9_-]+")))
        val (deleteStatus, _) = request("https://photospicker.googleapis.com/v1/sessions/$sessionId", "DELETE", bearer = token)
        return if (deleteStatus in 200..299)
            "AUTORIZAÇÃO PICKER COMPROVADA\nSessão vazia criada e removida. Nenhuma foto selecionada. Tokens não persistidos."
        else "Picker criou sessão vazia, mas limpeza falhou (HTTP $deleteStatus). Registrar pendência de limpeza."
    }

    override fun onDestroy() {
        cancel("")
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
