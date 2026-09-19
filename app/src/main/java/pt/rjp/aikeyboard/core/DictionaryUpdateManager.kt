package pt.rjp.aikeyboard.core

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject
import pt.rjp.aikeyboard.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object DictionaryUpdateManager {
    const val ACTION_DICTIONARIES_UPDATED = "pt.rjp.aikeyboard.DICTIONARIES_UPDATED"
    private const val TAG = "RjpDictUpdate"
    private const val JOB_ID = 0x524A50
    private const val PREFS = "rjp_dictionary_updates"
    private const val UPDATE_DIR = "dictionary_updates"
    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val STARTUP_MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val MAX_DICTIONARY_BYTES = 16L * 1024L * 1024L
    private val running = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor()

    data class UpdateResult(val updated: Int, val checked: Int, val message: String)

    fun dictionaryFile(context: Context, language: Language): File =
        File(File(context.filesDir, UPDATE_DIR), language.dictionaryAsset)

    fun isAutoUpdateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_update", true)

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_update", enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    fun lastSuccess(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("last_success", 0L)

    fun lastError(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_error", "") ?: ""

    fun configuredManifestUrl(): String = BuildConfig.DICTIONARY_MANIFEST_URL.trim()

    fun schedule(context: Context) {
        if (!isAutoUpdateEnabled(context) || configuredManifestUrl().isBlank()) return
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, DictionaryUpdateJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(CHECK_INTERVAL_MS)
            .build()
        scheduler.schedule(info)
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.cancel(JOB_ID)
    }

    fun maybeUpdateNow(context: Context) {
        if (!isAutoUpdateEnabled(context) || configuredManifestUrl().isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - lastCheck >= STARTUP_MIN_INTERVAL_MS) {
            updateNowAsync(context, null)
        }
    }

    fun updateNowAsync(context: Context, callback: ((UpdateResult) -> Unit)?) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) {
            callback?.invoke(UpdateResult(0, 0, "Já existe uma atualização em curso."))
            return
        }
        io.execute {
            val result = try {
                updateBlocking(app)
            } catch (t: Throwable) {
                Log.w(TAG, "Dictionary update failed", t)
                val msg = t.message ?: t.javaClass.simpleName
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("last_error", msg)
                    .apply()
                UpdateResult(0, 0, "Falha na atualização: $msg")
            } finally {
                running.set(false)
            }
            callback?.invoke(result)
        }
    }

    fun updateBlocking(context: Context): UpdateResult {
        val manifestUrl = configuredManifestUrl()
        require(manifestUrl.startsWith("https://")) { "O manifesto dos dicionários tem de usar HTTPS." }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()

        val json = httpGetText(manifestUrl, 2L * 1024L * 1024L)
        val root = JSONObject(json)
        require(root.optInt("schema", 0) == 1) { "Versão de manifesto não suportada." }
        val dictionaries = root.getJSONObject("dictionaries")
        val baseUrl = manifestUrl.substringBeforeLast('/') + "/"
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }

        var checked = 0
        var updated = 0
        for (language in Language.entries) {
            val code = language.dictionaryAsset.substringBeforeLast('.')
            if (!dictionaries.has(code)) continue
            checked++
            val item = dictionaries.getJSONObject(code)
            val version = item.getString("version")
            val expectedHash = item.getString("sha256").lowercase()
            val relativeFile = item.optString("file", language.dictionaryAsset)
            require(relativeFile.matches(Regex("[A-Za-z0-9_.-]+"))) { "Nome de ficheiro inválido no manifesto." }
            val currentVersion = prefs.getString("version_$code", "") ?: ""
            val target = File(dir, language.dictionaryAsset)

            if (currentVersion == version && target.isFile && sha256(target) == expectedHash) continue

            val tmp = File(dir, ".${language.dictionaryAsset}.download")
            downloadFile(URL(baseUrl + relativeFile), tmp, MAX_DICTIONARY_BYTES)
            validateDictionary(tmp)
            require(sha256(tmp) == expectedHash) { "SHA-256 inválido para $code." }

            val replacement = File(dir, ".${language.dictionaryAsset}.new")
            if (replacement.exists()) replacement.delete()
            check(tmp.renameTo(replacement)) { "Não foi possível preparar $code." }
            try {
                Files.move(
                    replacement.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: Exception) {
                Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            prefs.edit().putString("version_$code", version).apply()
            updated++
        }

        prefs.edit()
            .putLong("last_success", System.currentTimeMillis())
            .putString("last_error", "")
            .apply()

        if (updated > 0) {
            context.sendBroadcast(Intent(ACTION_DICTIONARIES_UPDATED).setPackage(context.packageName))
        }
        return UpdateResult(updated, checked, if (updated > 0) "$updated dicionário(s) atualizado(s)." else "Os dicionários já estão atualizados.")
    }

    private fun httpGetText(url: String, maxBytes: Long): String {
        val tmp = File.createTempFile("rjp_manifest", ".json")
        return try {
            downloadFile(URL(url), tmp, maxBytes)
            tmp.readText(Charsets.UTF_8)
        } finally {
            tmp.delete()
        }
    }

    private fun downloadFile(url: URL, target: File, maxBytes: Long) {
        require(url.protocol == "https") { "Só são permitidos downloads HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "RJP-AI-Keyboard/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code ao descarregar ${url.host}." }
            val declared = connection.contentLengthLong
            require(declared < 0 || declared <= maxBytes) { "Ficheiro demasiado grande." }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(target, false).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= maxBytes) { "Ficheiro excede o limite permitido." }
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateDictionary(file: File) {
        var validLines = 0
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val p = line.split('\t')
                require(p.isNotEmpty() && p[0].length >= 2) { "Dicionário com formato inválido." }
                if (p.size > 1) require(p[1].toIntOrNull() != null) { "Frequência inválida no dicionário." }
                validLines++
                if (validLines >= 100) return@useLines
            }
        }
        require(validLines >= 100) { "Dicionário demasiado pequeno ou corrompido." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
