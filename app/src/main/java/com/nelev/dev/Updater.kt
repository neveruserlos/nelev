package com.nelev.dev

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * OTA-обновления через GitHub Releases.
 *
 * Манифест всегда лежит в ассете version.json последнего релиза:
 *   https://github.com/neveruserlos/nelev/releases/latest/download/version.json
 *
 * Сравнение идёт по versionCode (число). versionName — только для показа.
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val changelog: String,
    val minSdk: Int,
    /** Локализованные списки изменений прямо из манифеста: lang -> text. */
    val changelogs: Map<String, String> = emptyMap()
)

data class LaunchUpdateInfo(
    val previousCode: Int,
    val currentCode: Int,
    val updated: Boolean
)

/**
 * Хранит версию прошлого запуска и списки изменений.
 * При каждом запуске пишет текущую версию в filesDir/last_run_version.txt.
 */
object VersionHistory {

    private const val LAST_VERSION_FILE = "last_run_version.txt"
    private const val CHANGELOG_PREFIX = "changelog_"
    private const val MAX_CHANGELOG_CHARS = 16_000

    fun recordLaunch(ctx: Context): LaunchUpdateInfo {
        val file = File(ctx.filesDir, LAST_VERSION_FILE)
        val previous = try {
            if (file.exists()) file.readText().trim().toIntOrNull() ?: 0 else 0
        } catch (_: Exception) {
            0
        }
        try {
            file.writeText(BuildConfig.VERSION_CODE.toString())
        } catch (_: Exception) {}
        return LaunchUpdateInfo(
            previousCode = previous,
            currentCode = BuildConfig.VERSION_CODE,
            updated = if (previous > 0) {
                previous != BuildConfig.VERSION_CODE
            } else {
                // Миграция с версий без VersionHistory: файла ещё нет, но
                // lastUpdateTime > firstInstallTime означает, что это обновление.
                wasUpdated(ctx)
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun wasUpdated(ctx: Context): Boolean {
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            info.lastUpdateTime - info.firstInstallTime > 60_000L
        } catch (_: Exception) {
            false
        }
    }

    fun saveChangelog(ctx: Context, versionCode: Int, changelog: String, lang: String = "ru") {
        if (changelog.isBlank()) return
        try {
            val file = File(ctx.filesDir, "${CHANGELOG_PREFIX}${versionCode}_${lang}.txt")
            file.writeText(changelog.take(MAX_CHANGELOG_CHARS))
            pruneChangelogs(ctx)
        } catch (_: Exception) {}
    }

    fun changelog(ctx: Context, versionCode: Int, lang: String = "ru"): String? {
        // Сначала берём локализованный текст из встроенного version_history.json —
        // он всегда на нужном языке. Файл из манифеста (обычно английский)
        // используем только как запасной вариант.
        loadChangelogFromAssets(ctx, versionCode, lang)?.let { return it }
        return try {
            val file = File(ctx.filesDir, "${CHANGELOG_PREFIX}${versionCode}_${lang}.txt")
            if (file.exists()) {
                file.readText().trim().ifBlank { null }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    
    private fun loadChangelogFromAssets(ctx: Context, versionCode: Int, lang: String): String? {
        return try {
            val json = ctx.assets.open("version_history.json").bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(json)
            val versions = root.getJSONArray("versions")
            for (i in 0 until versions.length()) {
                val v = versions.getJSONObject(i)
                if (v.getInt("versionCode") == versionCode) {
                    val changelog = v.getJSONObject("changelog")
                    return if (changelog.has(lang)) {
                        changelog.getString(lang)
                    } else {
                        changelog.optString("en", null)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun pruneChangelogs(ctx: Context) {
        try {
            val files = ctx.filesDir.listFiles { f -> f.name.startsWith(CHANGELOG_PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            files.drop(10).forEach { it.delete() }
        } catch (_: Exception) {}
    }
}

object VersionHistoryManager {
    
    data class VersionInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseDate: String,
        val changelog: Map<String, String>
    )
    
    private const val INSTALLED_VERSIONS_FILE = "installed_versions.json"
    
    // Сохранить информацию об установленной версии
    fun recordInstalledVersion(ctx: Context, versionCode: Int, versionName: String, changelog: Map<String, String>) {
        try {
            val installed = loadInstalledVersions(ctx).toMutableList()
            installed.removeAll { it.versionCode == versionCode }
            installed.add(VersionInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date()),
                changelog = changelog
            ))
            saveInstalledVersions(ctx, installed)
        } catch (_: Exception) {}
    }
    
    // Загрузить список установленных версий
    fun loadInstalledVersions(ctx: Context): List<VersionInfo> {
        return try {
            val file = File(ctx.filesDir, INSTALLED_VERSIONS_FILE)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            parseVersionsJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    // Загрузить все доступные версии из встроенного JSON
    fun loadAllVersions(ctx: Context): List<VersionInfo> {
        return try {
            val json = ctx.assets.open("version_history.json").bufferedReader().use { it.readText() }
            parseVersionsJson(json)
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    // Загрузить changelog конкретной версии с GitHub.
    // Основной путь — файл в assets (он всегда актуален и лежит в репозитории),
    // запасной — копия в корне репозитория для старых клиентов.
    suspend fun fetchChangelogFromGitHub(versionName: String, lang: String): String? {
        val urls = listOf(
            "https://raw.githubusercontent.com/neveruserlos/nelev/main/app/src/main/assets/version_history.json",
            "https://raw.githubusercontent.com/neveruserlos/nelev/main/version_history.json"
        )
        for (url in urls) {
            val text = try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                if (connection.responseCode == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else null
            } catch (_: Exception) {
                null
            } ?: continue
            val found = try {
                parseVersionsJson(text).find { it.versionName == versionName }?.changelog?.get(lang)
            } catch (_: Exception) {
                null
            }
            if (!found.isNullOrBlank()) return found
        }
        return null
    }
    
    private fun parseVersionsJson(json: String): List<VersionInfo> {
        val versions = mutableListOf<VersionInfo>()
        try {
            val root = org.json.JSONObject(json)
            val array = root.getJSONArray("versions")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val changelogObj = obj.getJSONObject("changelog")
                val changelog = mutableMapOf<String, String>()
                changelogObj.keys().forEach { key ->
                    changelog[key] = changelogObj.getString(key)
                }
                versions.add(VersionInfo(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.getString("versionName"),
                    releaseDate = obj.getString("releaseDate"),
                    changelog = changelog
                ))
            }
        } catch (_: Exception) {}
        return versions.sortedByDescending { it.versionCode }
    }
    
    private fun saveInstalledVersions(ctx: Context, versions: List<VersionInfo>) {
        try {
            val json = buildVersionsJson(versions)
            File(ctx.filesDir, INSTALLED_VERSIONS_FILE).writeText(json)
        } catch (_: Exception) {}
    }
    
    private fun buildVersionsJson(versions: List<VersionInfo>): String {
        val obj = org.json.JSONObject()
        val array = org.json.JSONArray()
        versions.forEach { v ->
            val vObj = org.json.JSONObject()
            vObj.put("versionCode", v.versionCode)
            vObj.put("versionName", v.versionName)
            vObj.put("releaseDate", v.releaseDate)
            val changelogObj = org.json.JSONObject()
            v.changelog.forEach { (lang, text) ->
                changelogObj.put(lang, text)
            }
            vObj.put("changelog", changelogObj)
            array.put(vObj)
        }
        obj.put("versions", array)
        return obj.toString()
    }
}

object Updater {

    private const val MANIFEST_URL =
        "https://github.com/neveruserlos/nelev/releases/latest/download/version.json"

    private const val PREFS = "nelev_updater"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_SKIPPED = "skipped_code"
    private const val CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastCheckMs(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_CHECK, 0L)
    fun markChecked(ctx: Context) = prefs(ctx).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    fun skippedCode(ctx: Context): Int = prefs(ctx).getInt(KEY_SKIPPED, 0)
    fun markSkipped(ctx: Context, code: Int) = prefs(ctx).edit().putInt(KEY_SKIPPED, code).apply()

    fun shouldAutoCheck(ctx: Context): Boolean =
        System.currentTimeMillis() - lastCheckMs(ctx) > CHECK_INTERVAL_MS

    /**
     * Возвращает UpdateInfo, если на GitHub есть версия новее установленной.
     * null — обновлений нет (или версия пропущена пользователем при respectSkip).
     */
    suspend fun check(ctx: Context, respectSkip: Boolean): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val body = fetchText(MANIFEST_URL)
            val o = JSONObject(body)
            val info = UpdateInfo(
                versionCode = o.optInt("versionCode", 0),
                versionName = o.optString("versionName", ""),
                apkUrl = o.optString("apkUrl", ""),
                sha256 = o.optString("sha256", "").trim().lowercase(),
                sizeBytes = o.optLong("sizeBytes", 0L),
                changelog = o.optString("changelog", "").trim(),
                minSdk = o.optInt("minSdk", 26),
                changelogs = parseChangelogs(o.optJSONObject("changelogs"))
            )
            when {
                info.versionCode <= BuildConfig.VERSION_CODE -> Result.success(null)
                info.minSdk > Build.VERSION.SDK_INT -> Result.success(null)
                info.apkUrl.isBlank() -> Result.failure(IOException("apkUrl is empty"))
                respectSkip && skippedCode(ctx) == info.versionCode -> Result.success(null)
                else -> Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseChangelogs(obj: org.json.JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, String>()
        obj.keys().forEach { key ->
            val value = obj.optString(key, "").trim()
            if (value.isNotEmpty()) result[key] = value
        }
        return result
    }

    suspend fun changelogFor(ctx: Context, versionCode: Int): String? = withContext(Dispatchers.IO) {        try {
            val o = JSONObject(fetchText(MANIFEST_URL))
            if (o.optInt("versionCode", 0) == versionCode) {
                o.optString("changelog", "").trim().ifBlank { null }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        onProgress: suspend (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "nelev-${info.versionCode}.apk")

        // Уже скачан и совпадает по размеру/хешу — не качаем заново.
        if (file.exists() && info.sha256.isNotBlank() && sha256(file) == info.sha256) {
            return@withContext file
        }
        file.delete()

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "nelev-updater")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var read = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (info.sizeBytes > 0 && file.length() != info.sizeBytes) {
            file.delete()
            throw IOException("size mismatch")
        }
        if (info.sha256.isNotBlank() && sha256(file) != info.sha256) {
            file.delete()
            throw IOException("checksum mismatch")
        }
        // Сохраняем список изменений: после установки покажем его на первом запуске.
        // Манифест на английском, поэтому кладём как "en", чтобы не перекрывать
        // локализованные переводы из version_history.json.
        if (info.changelog.isNotBlank()) {
            VersionHistory.saveChangelog(ctx, info.versionCode, info.changelog, "en")
        }
        onProgress(100)
        file
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "nelev-updater")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
                // Некоторые прокси/CDN отдают UTF-8 с BOM — org.json на нём падает.
                .trimStart('\uFEFF', '\u200B')
                .trim()
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
