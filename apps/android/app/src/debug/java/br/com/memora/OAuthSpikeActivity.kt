package br.com.memora

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Native debug probes. G2 never reads media; G3 exports only the user's explicit test selection. */
@SuppressLint("SetTextI18n") // Portuguese-only debug diagnostics; not a product UI.
class OAuthSpikeActivity : Activity() {
    private enum class Mode { AUTH_ONLY, SELECT_MEDIA }
    private class CreationAttempt {
        @Volatile var requested = false
        @Volatile var resolved = false
        @Volatile var uncertain = false
        @Volatile var cleaned = false
    }
    private data class Selection(val token: String, val sessionId: String,
        val window: PickerPollingWindow = PickerPollingWindow(), var finished: Boolean = false)
    private lateinit var status: TextView
    private lateinit var login: Button
    private lateinit var selectMedia: Button
    private lateinit var qr: ImageView
    private lateinit var probe: PickerProbe
    private var mode = Mode.AUTH_ONLY
    private var transaction: OAuthTransaction? = null
    private var selection: Selection? = null
    private var creationAttempt: CreationAttempt? = null
    private var resumed = false
    private var busy = false
    private var currentNetwork: Future<*>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    private val timeout = Runnable { cancel("Tempo esgotado. Inicie outra tentativa.") }
    private val pollTask = Runnable { pollSelection() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        probe = PickerProbe(applicationContext)
        status = TextView(this).apply {
            textSize = 20f
            text = "Ensaios Google Photos — desenvolvimento\nSPK-01: sessão vazia. SPK-02: seleção privada de 10 fotos e 2 vídeos, sem galeria."
        }
        qr = ImageView(this).apply {
            visibility = View.GONE
            contentDescription = "QR privado da seleção Google Photos"
        }
        login = Button(this).apply {
            text = "Iniciar autorização Google"
            setOnClickListener { begin(Mode.AUTH_ONLY) }
        }
        selectMedia = Button(this).apply {
            text = "SPK-02: selecionar 10 fotos e 2 vídeos"
            setOnClickListener { begin(Mode.SELECT_MEDIA) }
        }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 24)
                addView(status)
                addView(qr, LinearLayout.LayoutParams(360, 360))
                addView(login); addView(selectMedia)
                addView(Button(context).apply {
                    text = "Cancelar tentativa"; setOnClickListener { cancel("Tentativa cancelada.") }
                })
                addView(Button(context).apply {
                    text = "Limpar sessão e evidência privada"; setOnClickListener { clearSavedProbe() }
                })
                addView(Button(context).apply { text = "Encerrar ensaio"; setOnClickListener { finish() } })
            })
        })
        // No OAuth verifier is restored. G3 evidence remains encrypted for the separate G4 experiment.
        if (intent.data != null) { intent.data = null; status.text = "Retorno sem tentativa ativa. Inicie novamente." }
    }

    private fun controls(enabled: Boolean) { login.isEnabled = enabled; selectMedia.isEnabled = enabled }

    private fun begin(requestedMode: Mode) {
        if (requestedMode == Mode.SELECT_MEDIA) {
            if (creationAttempt?.uncertain == true && creationAttempt?.cleaned != true) {
                status.text = "A criação anterior tem resultado remoto incerto. Não há confirmação de limpeza; preserve o diagnóstico."
                return
            }
            val existing = try { ProbeStore(applicationContext).read() } catch (_: Exception) {
                status.text = "Não foi possível ler a evidência privada. Não será sobrescrita."; return
            }
            if (existing != null) {
                status.text = "Há uma sessão privada preservada para os ensaios. Use Limpar apenas quando puder descartá-la."
                return
            }
        }
        cancel("", clearSelection = selection?.finished != true)
        val client = getString(R.string.oauth_spike_client_id)
        if (client.isBlank()) { status.text = "Cliente Android local não configurado no build."; return }
        mode = requestedMode
        transaction = OAuthTransaction(client, SystemClock.elapsedRealtime())
        controls(false)
        status.text = "Aguardando autorização no navegador desta TV. Volte aqui para cancelar."
        handler.postDelayed(timeout, OAuthTransaction.LIFETIME_MS)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(transaction!!.authorizationUrl())).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage("com.amazon.cloud9")
            })
        } catch (_: ActivityNotFoundException) { cancel("Navegador externo indisponível.") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val callback = intent.data?.toString()
        intent.data = null; setIntent(intent)
        if (callback == null) return
        val attempt = transaction
        transaction = null
        handler.removeCallbacks(timeout)
        when (val result = attempt?.consume(callback, SystemClock.elapsedRealtime())) {
            is OAuthTransaction.Result.Code -> {
                val requestedMode = mode
                val current = generation
                val creation = if (requestedMode == Mode.SELECT_MEDIA) CreationAttempt().also { creationAttempt = it } else null
                status.text = "Retorno validado. Preparando ensaio…"
                currentNetwork = worker.submit {
                    try {
                        val tokenResponse = exchange(attempt, result.code)
                        val tokenReceivedAt = Instant.now()
                        if (generation != current) return@submit
                        val token = tokenResponse.getString("access_token")
                        if (requestedMode == Mode.AUTH_ONLY) {
                            val message = verifyScope(token, current)
                            handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                                status.text = message; controls(true)
                            } }
                        } else {
                            val expiresIn = tokenResponse.optLong("expires_in", 0L)
                            creation!!.requested = true
                            val session = probe.create(token, expiresIn)
                            creation.resolved = true
                            ProbeStore(applicationContext).update { snapshot ->
                                snapshot.put("tokenReceivedAt", tokenReceivedAt.toString())
                                snapshot.put("tokenIssuedAt", tokenReceivedAt.toString())
                                snapshot.put("tokenExpiresAt", tokenReceivedAt.plusSeconds(expiresIn).toString())
                                snapshot.put("tokenTypePresent", tokenResponse.has("token_type"))
                                if (tokenResponse.has("token_type")) snapshot.put("tokenType", tokenResponse.getString("token_type"))
                                snapshot.put("grantedScopesPresent", tokenResponse.has("scope"))
                                if (tokenResponse.has("scope")) snapshot.put("grantedScopes", tokenResponse.getString("scope"))
                                val refresh = tokenResponse.optString("refresh_token").takeIf { it.isNotBlank() }
                                snapshot.put("refreshTokenPresent", refresh != null)
                                if (refresh != null) snapshot.put("refreshToken", refresh)
                            }
                            if (generation != current) {
                                creation.cleaned = probe.cleanup(token, session.getString("id")); return@submit
                            }
                            handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                                showSelection(token, session)
                            } }
                        }
                    } catch (error: Exception) {
                        if (creation?.requested == true && !creation.resolved) {
                            if (error is ProbeHttpException && error.status in setOf(400, 401, 403, 404, 429)) {
                                creation.resolved = true; creation.cleaned = true // Explicit rejection, no session created.
                            } else creation.uncertain = true
                        }
                        val http = if (error is ProbeHttpException) " (HTTP ${error.status})" else ""
                        handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                            status.text = if (creation?.uncertain == true)
                                "Criação com resultado remoto incerto$http. Não há confirmação de limpeza; preserve o diagnóstico."
                            else "Falha no ensaio$http: rede, autorização ou resposta inválida. Nenhum detalhe privado foi exibido."
                            controls(true)
                        } }
                    }
                }
            }
            OAuthTransaction.Result.Denied -> cancel("Consentimento negado. Nenhuma sessão criada.")
            else -> cancel("Retorno rejeitado: sem tentativa válida, expirado ou não associado.")
        }
    }

    private fun showSelection(token: String, session: JSONObject) {
        try {
            val currentSelection = Selection(token, session.getString("id"))
            selection = currentSelection
            val polling = session.getJSONObject("pollingConfig")
            currentSelection.window.update(SystemClock.elapsedRealtime(), polling.getString("pollInterval"), polling.getString("timeoutIn"))
            val pickerUri = Uri.parse(session.getString("pickerUri"))
            require(pickerUri.scheme == "https" && pickerUri.host == "photos.google.com" && pickerUri.userInfo == null && pickerUri.port == -1 && pickerUri.fragment == null)
            val matrix = QRCodeWriter().encode(pickerUri.toString(), BarcodeFormat.QR_CODE, 360, 360)
            val pixels = IntArray(360 * 360) { index ->
                if (matrix[index % 360, index / 360]) Color.BLACK else Color.WHITE
            }
            qr.setImageBitmap(Bitmap.createBitmap(pixels, 360, 360, Bitmap.Config.ARGB_8888))
            qr.visibility = View.VISIBLE
            status.text = "No celular, leia o QR e selecione exatamente 10 fotos e 2 vídeos da conta autorizada.\nTeste privado de bytes/metadados; evidência criptografada para expiração em G4. Nenhuma galeria será exibida."
            schedulePoll()
        } catch (_: Exception) { cancel("Sessão com instruções inválidas; limpando tentativa.") }
    }

    private fun schedulePoll() {
        handler.removeCallbacks(pollTask)
        val selected = selection ?: return
        if (!resumed || busy || selected.finished) return
        val delay = selected.window.delay(SystemClock.elapsedRealtime())
        if (delay == null) { cancel("Prazo de seleção encerrado. Tentativa limpa."); return }
        handler.postDelayed(pollTask, delay)
    }

    private fun pollSelection() {
        val selected = selection ?: return
        if (!resumed || busy || selected.finished) return
        if (selected.window.delay(SystemClock.elapsedRealtime()) == null) {
            cancel("Prazo de seleção encerrado. Tentativa limpa."); return
        }
        selected.window.markRequest(SystemClock.elapsedRealtime())
        busy = true
        val current = generation
        currentNetwork = worker.submit {
            try {
                val response = probe.poll(selected.token, selected.sessionId)
                // Preserve the server's newest interval even when backgrounding invalidates UI delivery.
                if (!response.optBoolean("mediaItemsSet", false)) {
                    try {
                        val config = response.getJSONObject("pollingConfig")
                        selected.window.update(SystemClock.elapsedRealtime(), config.getString("pollInterval"), config.getString("timeoutIn"))
                    } catch (_: Exception) {
                        handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                            cancel("Seleção incompleta: prazo encerrado ou instruções inválidas.")
                        } }
                        return@submit
                    }
                }
                if (generation != current || Thread.currentThread().isInterrupted) return@submit
                if (response.optBoolean("mediaItemsSet", false)) {
                    val summary = probe.inspect(selected.token, selected.sessionId) { message ->
                        handler.post { if (generation == current && resumed) status.text = message }
                    }
                    handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                        selected.finished = true; busy = false
                        qr.visibility = View.GONE; qr.setImageDrawable(null)
                        status.text = summary + "\nEvidência privada preservada para G4; não limpe ainda."
                        controls(true)
                    } }
                } else handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                    busy = false
                    schedulePoll()
                } }
            } catch (error: Exception) {
                val http = if (error is ProbeHttpException) " (HTTP ${error.status})" else ""
                handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                    busy = false; selected.finished = true; qr.visibility = View.GONE
                    status.text = "Inspeção interrompida ou recusada$http. Evidência privada mantida para diagnóstico; use Limpar para cancelar."
                    controls(true)
                } }
            }
        }
    }

    private fun cancel(message: String, clearSelection: Boolean = true) {
        generation++
        val current = generation
        val creation = creationAttempt
        val hadPendingCreation = mode == Mode.SELECT_MEDIA && transaction == null &&
            currentNetwork != null && currentNetwork?.isDone == false
        transaction?.cancel(); transaction = null
        handler.removeCallbacks(timeout); handler.removeCallbacks(pollTask)
        // Once POST creation may have started, let its bounded response finish and yield an ID for cleanup.
        // Cancelling that read could orphan an already-created session before the snapshot exists.
        if (!hadPendingCreation || creation?.resolved == true) currentNetwork?.cancel(true)
        currentNetwork = null; busy = false
        val selected = selection
        selection = null
        if (::qr.isInitialized) { qr.visibility = View.GONE; qr.setImageDrawable(null) }
        val needsCleanup = clearSelection && (selected != null || hadPendingCreation || creation?.uncertain == true)
        if (needsCleanup && ::probe.isInitialized && !worker.isShutdown) {
            // Queued after the interrupted operation: also catches a session saved while creation was cancelled.
            worker.submit {
                val cleaned = try {
                    if (selected != null) probe.cleanup(selected.token, selected.sessionId)
                    else {
                        val saved = ProbeStore(applicationContext).read()
                        if (saved == null) creation?.requested != true || creation?.cleaned == true
                        else probe.cleanup(saved.getString("token"), saved.getString("sessionId"))
                    }
                } catch (_: Exception) { false }
                if (cleaned && creation != null) creation.cleaned = true
                handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                    status.text = if (cleaned) "Tentativa cancelada; sessão e evidência privada removidas."
                        else "Tentativa cancelada; limpeza remota não confirmada. Pode haver sessão sem identificador local; preserve o diagnóstico."
                } }
            }
        }
        if (::login.isInitialized) controls(true)
        if (::status.isInitialized && message.isNotBlank()) status.text =
            if (needsCleanup) "Tentativa cancelada; verificando limpeza privada…" else message
    }

    private fun clearSavedProbe() {
        cancel("Preparando limpeza privada…")
        val current = generation
        controls(false)
        currentNetwork = worker.submit {
            val cleaned = try {
                val saved = ProbeStore(applicationContext).read()
                if (saved == null) creationAttempt?.requested != true || creationAttempt?.cleaned == true
                else probe.cleanup(saved.getString("token"), saved.getString("sessionId"))
            } catch (_: Exception) { false }
            if (cleaned) creationAttempt?.cleaned = true
            handler.post { if (generation == current && !isFinishing && !isDestroyed) {
                status.text = if (cleaned) "Sessão e evidência privada removidas." else
                    "Limpeza remota não confirmada. Pode haver sessão sem identificador local; preserve o diagnóstico."
                controls(true)
            } }
        }
    }

    private fun request(url: String, method: String, body: String? = null, bearer: String? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 15_000; connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-store")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", if (bearer == null) "application/x-www-form-urlencoded" else "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val response = if (code in 200..299) connection.inputStream.bufferedReader().use {
                val buffer = CharArray(65_537); var size = 0
                while (size < buffer.size) { val read = it.read(buffer, size, buffer.size - size); if (read == -1) break; size += read }
                require(size <= 65_536); String(buffer, 0, size)
            } else ""
            return code to response
        } finally { connection.disconnect() }
    }

    private fun exchange(attempt: OAuthTransaction, code: String): JSONObject {
        val (http, body) = request("https://oauth2.googleapis.com/token", "POST", OAuthTransaction.form(mapOf(
            "client_id" to attempt.clientId, "code" to code, "code_verifier" to attempt.verifier,
            "redirect_uri" to attempt.redirect, "grant_type" to "authorization_code")))
        if (http !in 200..299) throw ProbeHttpException(http)
        return JSONObject(body)
    }

    private fun verifyScope(token: String, current: Int): String {
        if (generation != current) return "Cancelado."
        val (createStatus, body) = request("https://photospicker.googleapis.com/v1/sessions", "POST", "{}", token)
        if (createStatus !in 200..299) return "OAuth respondeu, mas Picker recusou (HTTP $createStatus)."
        val id = JSONObject(body).getString("id"); require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        val (deleteStatus, _) = request("https://photospicker.googleapis.com/v1/sessions/$id", "DELETE", bearer = token)
        return if (deleteStatus in 200..299) "AUTORIZAÇÃO PICKER COMPROVADA\nSessão vazia criada e removida. Nenhuma foto selecionada. Tokens não persistidos."
            else "Picker criou sessão vazia, mas limpeza falhou (HTTP $deleteStatus). Registrar pendência de limpeza."
    }

    override fun onResume() { super.onResume(); resumed = true; schedulePoll() }
    override fun onPause() {
        resumed = false
        handler.removeCallbacks(pollTask)
        if (selection != null) { generation++; currentNetwork?.cancel(true); busy = false }
        super.onPause()
    }
    override fun onDestroy() {
        cancel("", clearSelection = selection?.finished != true)
        worker.shutdown() // Permit pending remote cleanup; each request is bounded.
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
