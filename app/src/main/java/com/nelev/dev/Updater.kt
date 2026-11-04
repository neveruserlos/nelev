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
    val minSdk: Int
)

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
                minSdk = o.optInt("minSdk", 26)
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
