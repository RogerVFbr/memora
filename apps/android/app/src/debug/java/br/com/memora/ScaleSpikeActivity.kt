package br.com.memora

import android.annotation.SuppressLint
import android.app.Activity
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
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated metadata-only G5 bench. Never opens a media URL or the G3/G4 store. */
@SuppressLint("SetTextI18n")
class ScaleSpikeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var qr: ImageView
    private lateinit var probe: ScaleProbe
    private val buttons = mutableListOf<Button>()
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var task: Future<*>? = null
    private var cancellation: AtomicBoolean? = null
    private var transaction: OAuthTransaction? = null
    private var slot = "A"
    private var busy = false
    private var foreground = false
    @Volatile private var generation = 0
    @Volatile private var creating = false
    @Volatile private var uncertainCreation = false
    private var polling: String? = null
    private val windows = mutableMapOf<String, PickerPollingWindow>()
    private val pollTask = Runnable { poll() }
    private val authTimeout = Runnable { stop("Autorização expirou. Nenhuma seleção foi iniciada.") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        probe = ScaleProbe(applicationContext)
        status = TextView(this).apply { textSize = 20f }
        qr = ImageView(this).apply { visibility = View.GONE; contentDescription = "QR privado da seleção G5" }
        setContentView(ScrollView(this).apply {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24)
                addView(status); addView(qr, LinearLayout.LayoutParams(360, 360))
                fun action(label: String, block: () -> Unit) { addView(Button(context).apply {
                    text = label; setOnClickListener { block() }; buttons.add(this)
                }) }
                action("G5: criar ou retomar seleção A") { begin("A") }
                action("G5: criar ou retomar seleção B") { begin("B") }
                action("G5: listar metadados A") { list("A") }
                action("G5: listar metadados B") { list("B") }
                action("G5: comparar seleções offline") { runAction { probe.compare() } }
                action("G5: informações locais") { info() }
                action("G5: remover sessões remotas") { runAction {
                    val a = probe.cleanup("A"); val b = probe.cleanup("B")
                    if (a && b) "Sessões G5 removidas. Evidência local preservada; apagamento separado disponível."
                    else "Limpeza remota G5 incompleta. Evidência preservada para diagnóstico."
                } }
                action("G5: apagar evidência local") { runAction {
                    if (probe.clearAll()) "Evidência G5 apagada. Ensaio G4 preservado."
                    else "Há sessões G5 sem remoção confirmada. Evidência mantida."
                } }
                addView(Button(context).apply { text = "G5: cancelar tentativa"; setOnClickListener {
                    stop("Interrupção solicitada. Seleções existentes preservadas; consulte informações locais.")
                } })
                addView(Button(context).apply { text = "Encerrar G5"; setOnClickListener { finish() } })
            })
        })
        info()
        intent.data = null // No verifier is restored; opening never authorizes or accesses the network.
    }

    private fun controls(enabled: Boolean) { buttons.forEach { it.isEnabled = enabled } }
    private fun readSlot(name: String) = ScaleStore(applicationContext).read()?.optJSONObject("slots")?.optJSONObject(name)
    private fun begin(name: String) {
        if (busy || creating || transaction != null) return
        if (uncertainCreation) { status.text = "Criação anterior de resultado remoto incerto. Preserve o diagnóstico antes de repetir."; return }
        try {
            val existing = readSlot(name)
            if (existing != null && existing.optString("state") != "REMOTE_DELETED") {
                stop(""); showSelection(name, existing.getJSONObject("session")); return
            }
        } catch (_: Exception) { status.text = "Evidência G5 indisponível; nada foi sobrescrito."; return }
        val client = getString(R.string.oauth_spike_client_id)
        if (client.isBlank()) { status.text = "Cliente OAuth local não configurado."; return }
        stop(""); slot = name
        transaction = OAuthTransaction(client, SystemClock.elapsedRealtime(), "/oauth2redirect/scale")
        controls(false)
        status.text = "G5 $name: autorize no navegador da TV. A seleção privada recuperará apenas metadados, até 2000 itens; nenhum byte de foto ou vídeo."
        handler.postDelayed(authTimeout, OAuthTransaction.LIFETIME_MS)
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(transaction!!.authorizationUrl())).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); setPackage("com.amazon.cloud9")
        }) } catch (_: Exception) { stop("Navegador indisponível. Nenhuma sessão criada.") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val callback = intent.data?.toString(); intent.data = null; setIntent(intent)
        if (callback == null) return
        val auth = transaction; transaction = null; handler.removeCallbacks(authTimeout)
        val result = auth?.consume(callback, SystemClock.elapsedRealtime())
        if (result !is OAuthTransaction.Result.Code || auth == null) {
            stop("Autorização recusada, expirada ou retorno inválido."); return
        }
        val current = generation; val selectedSlot = slot
        busy = true; creating = true
        status.text = "Autorização validada. Criando seleção privada G5…"
        task = worker.submit {
            var posted = false
            try {
                val response = exchange(auth, result.code)
                if (current != generation) return@submit
                posted = true
                val session = probe.create(response.getString("access_token"), response.getLong("expires_in"), selectedSlot)
                if (current != generation) {
                    val cleaned = probe.cleanup(selectedSlot)
                    handler.post { if (!isDestroyed) status.text = if (cleaned)
                        "Criação cancelada; remoção remota G5 confirmada. Evidência local preservada."
                        else "Criação cancelada; remoção remota não confirmada. Evidência preservada." }
                } else handler.post { if (current == generation && !isDestroyed) {
                    busy = false; showSelection(selectedSlot, session)
                } }
            } catch (_: Exception) {
                if (posted) uncertainCreation = true
                handler.post { if (current == generation && !isDestroyed) {
                    busy = false; controls(true)
                    status.text = if (posted) "Criação sem resultado confirmado. Não há confirmação de limpeza remota."
                        else "Autorização interrompida ou recusada. Nenhuma criação foi solicitada."
                } }
            } finally { creating = false }
        }
    }

    private fun showSelection(name: String, session: JSONObject) {
        try { showSelectionChecked(name, session) } catch (_: Exception) {
            polling = null; qr.visibility = View.GONE; controls(true)
            status.text = "Seleção preservada; prazo encerrado ou instruções indisponíveis. Nenhuma listagem foi iniciada."
        }
    }
    private fun showSelectionChecked(name: String, session: JSONObject) {
        controls(true)
        if (session.optBoolean("mediaItemsSet")) {
            polling = null; qr.visibility = View.GONE
            status.text = "G5 $name: seleção concluída. Use Listar metadados $name explicitamente."; return
        }
        val uri = Uri.parse(session.getString("pickerUri"))
        require(uri.scheme == "https" && uri.host == "photos.google.com" && uri.userInfo == null && uri.port == -1 && uri.fragment == null)
        val matrix = QRCodeWriter().encode(uri.toString(), BarcodeFormat.QR_CODE, 360, 360)
        qr.setImageBitmap(Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 360) for (x in 0 until 360) setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        })
        qr.visibility = View.VISIBLE; polling = name
        val config = session.getJSONObject("pollingConfig")
        val window = windows.getOrPut(name) { PickerPollingWindow() }
        updateWindow(name, window, config)
        status.text = "G5 $name: escaneie o QR e selecione sua amostra privada. Apenas metadados, até 2000 itens; nenhum download de mídia. Depois conclua no Picker."
        schedule()
    }

    private fun updateWindow(name: String, window: PickerPollingWindow, config: JSONObject) {
        val now = Instant.now()
        val proposed = now.plusNanos(java.math.BigDecimal(config.getString("timeoutIn").removeSuffix("s"))
            .multiply(java.math.BigDecimal(1_000_000_000)).longValueExact())
        var deadline = proposed
        ScaleStore(applicationContext).update { saved ->
            val item = saved.getJSONObject("slots").getJSONObject(name)
            val old = item.optString("pollingDeadlineAt")
            if (old.isNotBlank()) deadline = minOf(deadline, Instant.parse(old))
            item.put("pollingDeadlineAt", deadline.toString())
        }
        val remaining = java.time.Duration.between(now, deadline).toMillis()
        require(remaining > 0)
        window.update(SystemClock.elapsedRealtime(), config.getString("pollInterval"), "${java.math.BigDecimal(remaining).divide(java.math.BigDecimal(1000))}s")
    }

    private fun schedule() {
        handler.removeCallbacks(pollTask)
        if (!foreground || busy) return
        val name = polling ?: return
        val delay = windows[name]?.delay(SystemClock.elapsedRealtime())
        if (delay == null) { polling = null; status.text = "Prazo de polling encerrado. Evidência G5 preservada."; return }
        handler.postDelayed(pollTask, delay)
    }
    private fun poll() {
        val name = polling ?: return
        if (!foreground || busy) return
        val window = windows[name] ?: return
        if (window.delay(SystemClock.elapsedRealtime()) == null) { schedule(); return }
        window.markRequest(SystemClock.elapsedRealtime())
        runAction(keepPolling = true) {
            val current = generation
            val session = probe.poll(name)
            handler.post { if (current == generation && foreground && polling == name) {
                if (session.optBoolean("mediaItemsSet")) {
                    polling = null; qr.visibility = View.GONE
                    status.text = "G5 $name: seleção concluída. Use Listar metadados explicitamente."
                } else {
                    try {
                        val config = session.getJSONObject("pollingConfig")
                        updateWindow(name, window, config)
                    } catch (_: Exception) {
                        polling = null; qr.visibility = View.GONE
                        status.text = "Prazo de polling encerrado ou instruções inválidas. Evidência preservada."
                    }
                }
            } }
            ""
        }
    }
    private fun list(name: String) {
        if (busy || creating || transaction != null) return
        status.text = "G5 $name: consultando conclusão e listando metadados…"
        runAction { cancelled ->
            check(!cancelled.get())
            val session = probe.poll(name) // Explicit single GET; never renew the original polling window.
            check(!cancelled.get())
            if (!session.optBoolean("mediaItemsSet")) {
                "G5 $name: seleção ainda não concluída. Evidência preservada; conclua no Picker e solicite Listar novamente."
            } else probe.list(name, isCancelled = { cancelled.get() }) { message ->
                handler.post { if (!cancelled.get() && foreground) status.text = message }
            }
        }
    }
    private fun runAction(keepPolling: Boolean = false, action: (AtomicBoolean) -> String) {
        if (busy || creating || transaction != null) return
        if (!keepPolling) { polling = null; handler.removeCallbacks(pollTask); qr.visibility = View.GONE }
        val cancelled = AtomicBoolean(false); cancellation = cancelled
        val current = generation
        busy = true; controls(false)
        task = worker.submit {
            val message = try {
                check(!cancelled.get() && current == generation)
                action(cancelled)
            } catch (_: Exception) { "Operação G5 interrompida ou recusada. Evidência preservada." }
            handler.post { if (current == generation && !isDestroyed) {
                busy = false; cancellation = null; controls(true)
                if (message.isNotBlank()) status.text = message
                if (keepPolling) schedule()
            } }
        }
    }
    private fun info() {
        if (busy) return
        try {
            val saved = ScaleStore(applicationContext).read()
            status.text = "Ensaio G5 isolado: apenas metadados; limite 2000 itens por seleção.\n" +
                listOf("A", "B").joinToString("\n") { name ->
                    val item = saved?.optJSONObject("slots")?.optJSONObject(name)
                    if (item == null) "$name: ausente" else {
                        val catalog = item.optJSONArray("catalog")
                        val types = mutableMapOf("PHOTO" to 0, "VIDEO" to 0, "UNKNOWN" to 0)
                        for (index in 0 until (catalog?.length() ?: 0)) {
                            val type = catalog?.optJSONObject(index)?.optString("type")
                            val label = if (type == "PHOTO" || type == "VIDEO") type else "UNKNOWN"
                            types[label] = types.getValue(label) + 1
                        }
                        val paths = item.optJSONArray("fieldPaths")
                        val fields = (0 until (paths?.length() ?: 0)).mapNotNull { index ->
                            paths?.optString(index)?.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_.]{0,255}")) }
                        }.sorted().joinToString(", ").ifBlank { "não registrado" }
                        "$name: itens ${catalog?.length() ?: 0}; páginas ${item.optJSONArray("pages")?.length() ?: 0}; seleção concluída ${item.optJSONObject("session")?.optBoolean("mediaItemsSet") == true}; removida ${item.optString("state") == "REMOTE_DELETED"}." +
                            "\nTipos: PHOTO=${types.getValue("PHOTO")}, VIDEO=${types.getValue("VIDEO")}, UNKNOWN=${types.getValue("UNKNOWN")}." +
                            "\n${item.optString("summary").ifBlank { "Sem listagem registrada." }}" +
                            "\nCampos presentes (somente nomes): $fields"
                    }
                } + "\n" + (saved?.optString("summary") ?: "Nenhuma comparação registrada.") +
                "\nLeitura local, sem requisições. Evidência G4 permanece separada."
        } catch (_: Exception) { status.text = "Evidência G5 indisponível; nada foi sobrescrito." }
    }
    private fun stop(message: String) {
        cancellation?.set(true); if (::probe.isInitialized) probe.cancel(); cancellation = null
        generation++; transaction?.cancel(); transaction = null
        handler.removeCallbacks(authTimeout); handler.removeCallbacks(pollTask); polling = null
        if (!creating) task?.cancel(true) // POST must yield its ID so the worker can remove the cancelled creation.
        task = null; busy = false; qr.visibility = View.GONE; qr.setImageDrawable(null); controls(true)
        if (message.isNotBlank()) status.text = message
    }
    private fun exchange(auth: OAuthTransaction, code: String): JSONObject {
        val connection = URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 15000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.useCaches = false; connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(OAuthTransaction.form(mapOf("client_id" to auth.clientId,
                "code" to code, "code_verifier" to auth.verifier, "redirect_uri" to auth.redirect,
                "grant_type" to "authorization_code")).toByteArray()) }
            require(connection.responseCode in 200..299)
            val bytes = connection.inputStream.use { readScaleBytes(it, 64 * 1024) }
            return try { JSONObject(String(bytes, Charsets.UTF_8)) } finally { bytes.fill(0) }
        } finally { connection.disconnect() }
    }
    override fun onResume() { super.onResume(); foreground = true; schedule() }
    override fun onPause() {
        foreground = false
        // External OAuth retains its in-memory transaction; all metadata activity requires explicit resumption.
        if (transaction == null) stop("Interrupção solicitada ao sair da tela. Evidência G5 preservada.")
        super.onPause()
    }
    override fun onDestroy() { stop(""); worker.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
