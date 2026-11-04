package com.nelev.dev

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.SoundPool
import android.media.AudioAttributes
import android.net.Uri
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Bundle
import android.os.Debug
import android.app.ActivityManager
import java.io.BufferedReader
import java.io.FileReader
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.nelev.dev.ui.theme.NelevTheme
import androidx.compose.runtime.withFrameMillis
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── App-wide settings ────────────────────────────────────────────────────────

// VERSION TRACKING: Current version 1.4 beta
// Version increment rules:
// - Bug fixes and minor updates: +0.1 (1.0 -> 1.1 -> 1.2 ... -> 1.9)
// - After 1.9, next version is 2.0
// - Major features: +1.0 (1.0 -> 2.0 -> 3.0)
// IMPORTANT: APP_VERSION comes from version.properties (BuildConfig.VERSION_NAME).
// Bump versionCode/versionName in version.properties when releasing.
val APP_VERSION: String = BuildConfig.VERSION_NAME



data class AppSettings(
    val isDark: Boolean = false,
    val language: String = "en",
    val phrasesEnabled: Boolean = true,
    val connectionBarEnabled: Boolean = true,
    val soundNotifEnabled: Boolean = true,
    val soundClickEnabled: Boolean = true,
    val soundNotifEnabledBeforeMaster: Boolean = true,
    val soundClickEnabledBeforeMaster: Boolean = true,
    val menuCentered: Boolean = true,
    val visualEffectsEnabled: Boolean = true,
    val isFirstLaunch: Boolean = true,
    val sendStatistics: Boolean = false,
    val participateInBeta: Boolean = false,
    val lastStatisticsSentMs: Long = 0L,
    val userUID: String = "",
    val autoStartEnabled: Boolean = false,
    val syncWithPc: Boolean = true,
    // Пауза уведомлений об обновлениях: 0 — не приостановлено.
    val updatesPausedUntilMs: Long = 0L,
    val updatesPausedForever: Boolean = false
) {
    fun updatesPaused(now: Long = System.currentTimeMillis()): Boolean =
        updatesPausedForever || (updatesPausedUntilMs > now)
}

object SettingsStore {
    private const val FILE = "nelev_app.txt"

    private fun generateUID(ctx: Context): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        // ANDROID_ID стабилен для пары «устройство + подпись приложения» и
        // переживает удаление/переустановку. Из него детерминированно получаем
        // тот же UID, поэтому после переустановки ID не меняется.
        val androidId = try {
            android.provider.Settings.Secure.getString(
                ctx.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ).orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (androidId.isNotBlank()) {
            try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest("nelev:$androidId:${ctx.packageName}".toByteArray())
                return (0 until 10)
                    .map { chars[(digest[it].toInt() and 0xFF) % chars.length] }
                    .joinToString("")
            } catch (_: Exception) {}
        }
        return (1..10).map { chars.random() }.joinToString("")
    }

    fun load(ctx: Context): AppSettings = try {
        val file = File(ctx.filesDir, FILE)
        val isFirstLaunch = !file.exists()
        val m = if (isFirstLaunch) emptyMap() else file.readLines()
            .mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()

        // UID генерируется один раз и хранится в настройках. Если файл удалён
        // вместе с приложением — восстанавливается из ANDROID_ID.
        val uid = m["userUID"]?.takeIf { it.isNotEmpty() } ?: generateUID(ctx)

        AppSettings(
            isDark               = m["isDark"] == "true",
            language             = m["language"] ?: "en",
            phrasesEnabled       = m["phrasesEnabled"] != "false",
            connectionBarEnabled = m["connectionBarEnabled"] != "false",
            soundNotifEnabled    = m["soundNotifEnabled"] != "false",
            soundClickEnabled    = m["soundClickEnabled"] != "false",
            soundNotifEnabledBeforeMaster  = m["soundNotifEnabledBefore"] != "false",
            soundClickEnabledBeforeMaster  = m["soundClickEnabledBefore"] != "false",
            menuCentered         = if (isFirstLaunch) true else m["menuCentered"] == "true",
            visualEffectsEnabled = if (isFirstLaunch) true else m["visualEffectsEnabled"] != "false",
            isFirstLaunch        = isFirstLaunch,
            sendStatistics       = m["sendStatistics"] == "true",
            participateInBeta    = m["participateInBeta"] == "true",
            lastStatisticsSentMs = m["lastStatisticsSentMs"]?.toLongOrNull() ?: 0L,
            userUID              = uid,
            autoStartEnabled     = m["autoStartEnabled"] == "true",
            syncWithPc           = m["syncWithPc"] != "false",
            updatesPausedUntilMs = m["updatesPausedUntilMs"]?.toLongOrNull() ?: 0L,
            updatesPausedForever = m["updatesPausedForever"] == "true"
        )
    } catch (_: Exception) { AppSettings() }

    fun save(ctx: Context, s: AppSettings) = try {
        File(ctx.filesDir, FILE).writeText(
            "isDark=${s.isDark}\nlanguage=${s.language}\nphrasesEnabled=${s.phrasesEnabled}\n" +
                    "connectionBarEnabled=${s.connectionBarEnabled}\nsoundNotifEnabled=${s.soundNotifEnabled}\n" +
                    "soundClickEnabled=${s.soundClickEnabled}\n" +
                    "soundNotifEnabledBefore=${s.soundNotifEnabledBeforeMaster}\n" +
                    "soundClickEnabledBefore=${s.soundClickEnabledBeforeMaster}\n" +
                    "menuCentered=${s.menuCentered}\n" +
                    "visualEffectsEnabled=${s.visualEffectsEnabled}\n" +
                    "isFirstLaunch=false\n" +
                    "sendStatistics=${s.sendStatistics}\n" +
                    "participateInBeta=${s.participateInBeta}\n" +
                    "lastStatisticsSentMs=${s.lastStatisticsSentMs}\n" +
                    "userUID=${s.userUID}\n" +
                    "autoStartEnabled=${s.autoStartEnabled}\n" +
                    "syncWithPc=${s.syncWithPc}\n" +
                    "updatesPausedUntilMs=${s.updatesPausedUntilMs}\n" +
                    "updatesPausedForever=${s.updatesPausedForever}\n"
        )
    } catch (_: Exception) {}
}

// ─── Session history ──────────────────────────────────────────────────────────

data class SndItem(val key: String, val on: Boolean, val toggle: () -> Unit)
data class SessionRecord(val startMs: Long, val durationMs: Long, val avgPingMs: Long)

object SessionStore {
    private const val FILE = "nelev_sessions.txt"
    private const val ACTIVE_SESSION_FILE = "nelev_active_session.txt"
    private const val MAX  = 50
    // Сессии короче секунды не записываем: защита от спама «старт/стоп».
    const val MIN_DURATION_MS = 1000L
    
    fun load(ctx: Context): List<SessionRecord> = try {
        File(ctx.filesDir, FILE).readLines().mapNotNull { line ->
            val p = line.split(",")
            if (p.size >= 3) SessionRecord(p[0].toLong(), p[1].toLong(), p[2].toLong()) else null
        }
    } catch (_: Exception) { emptyList() }
    
    fun append(ctx: Context, record: SessionRecord) {
        if (record.durationMs < MIN_DURATION_MS) {
            clearActive(ctx)
            return
        }
        val list = load(ctx).toMutableList().also { it.add(record) }.takeLast(MAX)
        try { File(ctx.filesDir, FILE).writeText(list.joinToString("\n") { "${it.startMs},${it.durationMs},${it.avgPingMs}" }) }
        catch (_: Exception) {}
        clearActive(ctx)
    }
    
    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE).writeText("") } catch (_: Exception) {}
    }
    
    // Активная сессия хранится как "startMs,lastHeartbeatMs"
    fun saveActiveSession(ctx: Context, startMs: Long, lastMs: Long = startMs) {
        try { File(ctx.filesDir, ACTIVE_SESSION_FILE).writeText("$startMs,$lastMs") }
        catch (_: Exception) {}
    }
    
    fun loadActiveStart(ctx: Context): Long {
        return try {
            File(ctx.filesDir, ACTIVE_SESSION_FILE).readText().split(",").firstOrNull()?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }
    
    fun loadActiveLast(ctx: Context): Long {
        return try {
            val parts = File(ctx.filesDir, ACTIVE_SESSION_FILE).readText().split(",")
            parts.getOrNull(1)?.toLongOrNull() ?: parts.firstOrNull()?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
    }
    
    fun clearActive(ctx: Context) {
        try { File(ctx.filesDir, ACTIVE_SESSION_FILE).delete() } catch (_: Exception) {}
    }
    
    // Восстановление после аварийного завершения: если остался активный файл,
    // но передача уже не идёт — сохраняем сессию до последнего heartbeat.
    fun recoverCrashedSession(ctx: Context): SessionRecord? {
        val start = loadActiveStart(ctx)
        if (start <= 0L) return null
        val last = loadActiveLast(ctx)
        val dur = (last - start).coerceAtLeast(0L)
        if (dur > 0L) {
            val existing = load(ctx)
            if (existing.none { it.startMs == start }) {
                val rec = SessionRecord(startMs = start, durationMs = dur, avgPingMs = 0L)
                append(ctx, rec)
                return rec
            }
        }
        clearActive(ctx)
        return null
    }
}

// ─── Запись голоса с активными фильтрами (PCM → WAV) ────────────────────────

data class RecordFilters(
    val volume: Float = 1f,
    val reverb: Float = 0f,
    val bass: Float = 0f,
    val pitch: Float = 0f,
    val eqLow: Float = 0f,
    val eqMid: Float = 0f,
    val eqHigh: Float = 0f,
    val enhancerDc: Boolean = false,
    val enhancerHp: Boolean = false,
    val enhancerLimiter: Boolean = false,
    val enhancerGate: Boolean = false,
    val isMuted: Boolean = false
)

/**
 * Записывает голос в WAV, прогоняя его через ту же цепочку фильтров,
 * что и NelevService (улучшайзер → бас/питч → EQ → реверб → громкость → лимитер).
 */
suspend fun recordFilteredWav(
    file: File,
    maxSeconds: Int = 30,
    params: () -> RecordFilters,
    isCancelled: () -> Boolean
) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val sampleRate = 48_000
    val frameSize = 960
    val minBuf = android.media.AudioRecord.getMinBufferSize(
        sampleRate,
        android.media.AudioFormat.CHANNEL_IN_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT
    )
    val bufSize = maxOf(minBuf, frameSize * 4)

    val recorder = try {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.media.AudioRecord.Builder()
                .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .build()
        } else {
            android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        }
    } catch (_: Exception) {
        android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC,
            sampleRate,
            android.media.AudioFormat.CHANNEL_IN_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
    }

    if (recorder.state != android.media.AudioRecord.STATE_INITIALIZED) {
        try { recorder.release() } catch (_: Exception) {}
        return@withContext
    }

    // ── DSP-состояние (аналогично NelevService.streamLoop) ──
    val dcBlocker    = NelevService.DcBlocker()
    val highPass80   = NelevService.HighPass80()
    val limiter      = NelevService.SoftLimiter()
    val gate         = NelevService.Gate()
    val voiceEq      = NelevService.VoiceEQ()
    val bassFilter   = NelevService.SoftBassFilter()
    val pitchShifter = NelevService.GranularPitch()

    val reverbDelay = IntArray(4) { (0.008f * sampleRate * (it + 1)).toInt() }
    val reverbLines = Array(4) { FloatArray(reverbDelay[it]) }
    val reverbPos   = IntArray(4)

    var smoothBass = 0f
    var smoothPitch = 0f

    val pcmBuf = ShortArray(frameSize)

    file.parentFile?.mkdirs()
    val raf = java.io.RandomAccessFile(file, "rw")
    raf.setLength(0)
    // Заголовок WAV (44 байта), данные патчим в конце
    raf.write(ByteArray(44))
    var dataBytes = 0L

    recorder.startRecording()
    val startedAt = System.currentTimeMillis()

    try {
        while (!isCancelled() && (System.currentTimeMillis() - startedAt) < maxSeconds * 1000L) {
            val read = recorder.read(pcmBuf, 0, frameSize)
            if (read <= 0) continue

            val p = params()
            var buf = FloatArray(read) { pcmBuf[it] / 32768f }

            if (p.isMuted) {
                buf.fill(0f)
                dcBlocker.reset(); highPass80.reset(); gate.reset(); voiceEq.reset()
            } else {
                if (p.enhancerDc) dcBlocker.process(buf)
                if (p.enhancerGate) gate.process(buf)
                if (p.enhancerHp) highPass80.process(buf, sampleRate)

                val hasBass   = p.bass > 0.01f || smoothBass > 0.01f
                val hasPitch  = abs(p.pitch) > 0.01f || abs(smoothPitch) > 0.01f
                val hasReverb = p.reverb > 0.01f
                val hasEq     = abs(p.eqLow) > 0.01f || abs(p.eqMid) > 0.01f || abs(p.eqHigh) > 0.01f

                if (hasBass || hasPitch) {
                    smoothBass  += (p.bass  - smoothBass)  * 0.08f
                    smoothPitch += (p.pitch - smoothPitch) * 0.08f
                    if (hasBass) bassFilter.process(buf, smoothBass, sampleRate)
                    if (hasPitch && abs(smoothPitch) > 0.05f) {
                        buf = pitchShifter.process(buf, smoothPitch * 12f)
                    }
                } else {
                    smoothBass = 0f; smoothPitch = 0f
                    bassFilter.reset(); pitchShifter.reset()
                }

                if (hasEq) voiceEq.process(buf, p.eqLow, p.eqMid, p.eqHigh, sampleRate)
                else voiceEq.reset()

                if (hasReverb) {
                    val revAmt = p.reverb
                    val feedback = 0.5f + revAmt * 0.35f
                    val wet = revAmt * 0.6f
                    val dry = 1f - wet * 0.3f
                    for (i in buf.indices) {
                        val inp = buf[i]; var out = 0f
                        for (c in 0..3) {
                            val rp = reverbPos[c]; val d = reverbLines[c][rp]
                            reverbLines[c][rp] = inp + d * feedback
                            reverbPos[c] = (rp + 1) % reverbDelay[c]; out += d
                        }
                        buf[i] = inp * dry + out * wet * 0.25f
                    }
                }

                if (p.volume != 1f) for (i in buf.indices) buf[i] *= p.volume
                if (p.enhancerLimiter) limiter.process(buf, 0.92f)
            }

            val bytes = ByteArray(buf.size * 2)
            var wi = 0
            for (i in buf.indices) {
                val s = (buf[i].coerceIn(-1f, 1f) * 32767).toInt()
                bytes[wi++] = (s and 0xFF).toByte()
                bytes[wi++] = ((s ushr 8) and 0xFF).toByte()
            }
            raf.write(bytes)
            dataBytes += bytes.size.toLong()
        }
    } finally {
        try { recorder.stop() } catch (_: Exception) {}
        try { recorder.release() } catch (_: Exception) {}
        patchWavHeader(raf, dataBytes, sampleRate, 1, 16)
        try { raf.close() } catch (_: Exception) {}
    }
}

private fun patchWavHeader(raf: java.io.RandomAccessFile, dataBytes: Long, sampleRate: Int, channels: Int, bits: Int) {
    try {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.write(intToLe((36 + dataBytes).toInt()))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.write(intToLe(16))
        raf.write(shortToLe(1))            // PCM
        raf.write(shortToLe(channels))
        raf.write(intToLe(sampleRate))
        raf.write(intToLe(byteRate))
        raf.write(shortToLe(blockAlign))
        raf.write(shortToLe(bits))
        raf.writeBytes("data")
        raf.write(intToLe(dataBytes.toInt()))
    } catch (_: Exception) {}
}

private fun intToLe(v: Int) = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v ushr 8) and 0xFF).toByte(),
    ((v ushr 16) and 0xFF).toByte(),
    ((v ushr 24) and 0xFF).toByte()
)

private fun shortToLe(v: Int) = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v ushr 8) and 0xFF).toByte()
)

// ─── Persistence ─────────────────────────────────────────────────────────────
object Prefs {
    private const val FILE = "nelev_settings.txt"
    fun save(ctx: Context, ip: String, volume: Float, reverb: Float, bass: Float, pitch: Float,
             enhancerDc: Boolean = false, enhancerHp: Boolean = false, enhancerLimiter: Boolean = false, enhancerGate: Boolean = false,
             eqLow: Float = 0f, eqMid: Float = 0f, eqHigh: Float = 0f) = try {
        File(ctx.filesDir, FILE).writeText(
            "ip=$ip\nvolume=$volume\nreverb=$reverb\nbass=$bass\npitch=$pitch\n" +
            "enhancerDc=$enhancerDc\nenhancerHp=$enhancerHp\nenhancerLimiter=$enhancerLimiter\nenhancerGate=$enhancerGate\n" +
            "eqLow=$eqLow\neqMid=$eqMid\neqHigh=$eqHigh\n"
        )
    } catch (_: Exception) {}
    data class State(val ip: String = "", val volume: Float = 1f, val reverb: Float = 0f, val bass: Float = 0f, val pitch: Float = 0f,
                     val enhancerDc: Boolean = false, val enhancerHp: Boolean = false, val enhancerLimiter: Boolean = false, val enhancerGate: Boolean = false,
                     val eqLow: Float = 0f, val eqMid: Float = 0f, val eqHigh: Float = 0f)
    fun load(ctx: Context): State = try {
        val m = File(ctx.filesDir, FILE).readLines()
            .mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        State(m["ip"] ?: "", m["volume"]?.toFloatOrNull() ?: 1f, m["reverb"]?.toFloatOrNull() ?: 0f,
            m["bass"]?.toFloatOrNull() ?: 0f, m["pitch"]?.toFloatOrNull() ?: 0f,
            m["enhancerDc"] == "true", m["enhancerHp"] == "true", m["enhancerLimiter"] == "true", m["enhancerGate"] == "true",
            m["eqLow"]?.toFloatOrNull() ?: 0f, m["eqMid"]?.toFloatOrNull() ?: 0f, m["eqHigh"]?.toFloatOrNull() ?: 0f)
    } catch (_: Exception) { State() }
}

// ─── Sound players ────────────────────────────────────────────────────────────

object SoundPlayer {
    private var player: MediaPlayer? = null
    fun play(ctx: Context) {
        try {
            player?.release(); player = null
            val id = ctx.resources.getIdentifier("sound", "raw", ctx.packageName); if (id == 0) return
            player = MediaPlayer.create(ctx, id)?.also { it.setOnCompletionListener { mp -> mp.release(); player = null }; it.start() }
        } catch (_: Exception) {}
    }
    fun release() { try { player?.release() } catch (_: Exception) {}; player = null }
}

object ClickPlayer {
    private var pool: SoundPool? = null
    private var soundId: Int = 0
    private var loaded = false

    fun init(ctx: Context) {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()
        val id = ctx.resources.getIdentifier("click", "raw", ctx.packageName)
        if (id != 0) {
            pool!!.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded = true }
            soundId = pool!!.load(ctx, id, 1)
        }
    }

    fun play(ctx: Context) {
        if (pool == null) init(ctx)
        if (loaded && soundId != 0) pool!!.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() { pool?.release(); pool = null; loaded = false; soundId = 0 }
}

// ─── Notifications ────────────────────────────────────────────────────────────

private const val NOTIF_CHANNEL_CONN    = "nelev_conn_events"
private const val NOTIF_ID_CONNECTED    = 1002
private const val NOTIF_ID_DISCONNECTED = 1003

fun showConnectionNotification(ctx: Context, connected: Boolean, lang: String) {
    try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(NOTIF_CHANNEL_CONN, "nelev connection",
            NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableLights(true)
            lightColor = if (connected) 0xFF4CAF50.toInt() else 0xFFFF4444.toInt(); setShowBadge(false)
        })
        val title = if (connected) Strings.get("conn_notif_connected_title", lang) else Strings.get("conn_notif_disconnected_title", lang)
        val body  = if (connected) Strings.get("conn_notif_connected_body", lang) else Strings.get("conn_notif_disconnected_body", lang)
        nm.notify(if (connected) NOTIF_ID_CONNECTED else NOTIF_ID_DISCONNECTED,
            NotificationCompat.Builder(ctx, NOTIF_CHANNEL_CONN)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true).setTimeoutAfter(5_000L).build())
    } catch (_: Exception) {}
}

// ─── Phrases ─────────────────────────────────────────────────────────────────

object Phrases {
    private val EN = listOf(
        "Today is a great day, isn't it?","You're looking good today!","You're my favourite user \uD83C\uDF99",
        "Ready to conquer the airwaves?","Scientists confirm: your voice improves moods by 147%",
        "UDP packets flying to your PC at nearly the speed of light","All bits in place. You may speak.",
        "Your voice today is especially wonderful","Streaming is an art. And you are the artist.",
        "Late night streaming hits different","The mic is hot. Go for it.",
        "Every great stream starts with a single packet","Your signal is strong. Your presence is stronger.",
        "You sound like someone who knows what they're doing","The connection is live. So are you.",
        "Your voice deserves to be heard","Another session, another masterpiece",
        "The mic doesn't lie — you're on fire today","Quality audio, quality vibes",
        "48kHz of pure you","Latency is low. Energy is high.",
        "Small device, big sound","Let the packets flow",
        "Stream like nobody's listening. But they are.","One mic. Infinite possibilities.",
        "Even your silence streams at 48kHz","Today you're the broadcast",
        "Mic check: flawless","Your voice is the main character today",
        "The connection is yours. Use it well.","You didn't just open an app. You opened a channel.",
        "Broadcast yourself. Unapologetically.","Some people talk. You transmit.",
        "The world has enough silence. Fill it.","Ready when you are. Always.",
        "Packet loss: 0%. Confidence: 100%.","Your frequency is unlike any other",
        "No cables. No limits. Just you.","Low latency, high impact",
        "The buffer is loaded. The moment is yours.",
        "You hit start. Everything else is just physics.",
        "Today's forecast: clear signal, strong delivery, zero dropouts",
        "Signal received. World updated.","You showed up. That's already the hardest part.",
        "Consider this your daily reminder: your voice matters",
        "You had a good idea. nelev made sure it was heard."
    )
    private val RU = listOf(
        "Сегодня хороший день, не правда ли?","Вы сегодня хороши как никогда!",
        "Вы мой любимый пользователь \uD83C\uDF99","Готов покорить эфир?",
        "Учёные доказали: твой голос повышает настроение на 147%",
        "UDP пакеты летят к ПК со скоростью света (почти)","Все биты на месте. Можно говорить.",
        "Твой голос сегодня особенно хорош","Стриминг — это искусство. А ты — художник.",
        "Ночной стриминг — особый вайб","Микрофон горячий. Вперёд.",
        "Каждый великий стрим начинается с одного пакета","Твой сигнал сильный. Твоё присутствие — сильнее.",
        "Ты звучишь как человек, который знает что делает","Соединение живое. И ты тоже.",
        "Твой голос заслуживает быть услышанным","Ещё одна сессия, ещё один шедевр",
        "Микрофон не врёт — сегодня ты в огне","Качественный звук, качественный вайб",
        "48кГц чистого тебя","Задержка минимальна. Энергия максимальна.",
        "Маленькое устройство, большой звук","Пусть пакеты текут",
        "Стримь как будто никто не слушает. Но слушают.","Один микрофон. Бесконечные возможности.",
        "Даже твоя тишина стримится на 48кГц","Сегодня ты — вещание",
        "Проверка микрофона: безупречно","Твой голос сегодня — главный персонаж",
        "Соединение твоё. Используй его хорошо.","Ты открыл не просто приложение. Ты открыл канал.",
        "Транслируй себя. Без извинений.","Одни говорят. Ты передаёшь.",
        "В мире достаточно тишины. Заполни её.","Готов, когда ты. Всегда.",
        "Потеря пакетов: 0%. Уверенность: 100%.","Твоя частота ни на кого не похожа",
        "Никаких кабелей. Никаких ограничений. Только ты.","Минимальная задержка, максимальный эффект",
        "Буфер загружен. Момент твой.",
        "Ты нажал старт. Всё остальное — просто физика.",
        "Прогноз на сегодня: чистый сигнал, уверенная подача, ноль обрывов",
        "Сигнал принят. Мир обновлён.","Ты пришёл. Это уже самая сложная часть.",
        "Ежедневное напоминание: твой голос важен",
        "У тебя была хорошая идея. nelev позаботился, чтобы её услышали."
    )
    private val ES = listOf(
        "Hoy es un gran día, ¿verdad?","Hoy te ves fantástico","Eres mi usuario favorito \uD83C\uDF99",
        "¿Listo para conquistar las ondas?","Los científicos confirman: tu voz mejora el ánimo un 147%",
        "Paquetes UDP volando a tu PC casi a la velocidad de la luz","Todos los bits en su sitio. Puedes hablar.",
        "Tu voz hoy es especialmente maravillosa","El streaming es arte. Y tú eres el artista.",
        "El streaming nocturno tiene un rollo especial","El micrófono está caliente. Adelante.",
        "Todo gran stream comienza con un solo paquete","Tu señal es fuerte. Tu presencia lo es más.",
        "Suenas como alguien que sabe lo que hace","La conexión está viva. Tú también.",
        "Tu voz merece ser escuchada","Otra sesión, otra obra maestra",
        "El micro no miente — hoy estás imparable","Audio de calidad, vibras de calidad",
        "48 kHz de puro tú","La latencia es baja. La energía es alta.",
        "Dispositivo pequeño, sonido grande","Deja fluir los paquetes",
        "Stream como si nadie escuchara. Pero escuchan.","Un micrófono. Posibilidades infinitas.",
        "Hasta tu silencio se transmite a 48 kHz","Hoy tú eres la emisión",
        "Prueba de micro: impecable","Tu voz es el personaje principal hoy",
        "La conexión es tuya. Úsala bien.","No solo abriste una app. Abriste un canal.",
        "Transmítete. Sin disculpas.","Algunos hablan. Tú transmites.",
        "El mundo ya tiene suficiente silencio. Llénalo.","Listo cuando tú. Siempre.",
        "Pérdida de paquetes: 0%. Confianza: 100%.","Tu frecuencia no se parece a ninguna otra",
        "Sin cables. Sin límites. Solo tú.","Baja latencia, alto impacto",
        "El búfer está cargado. El momento es tuyo.",
        "Presionaste inicio. Todo lo demás es solo física.",
        "Pronóstico de hoy: señal clara, entrega fuerte, cero cortes",
        "Señal recibida. Mundo actualizado.","Apareciste. Esa ya es la parte más difícil.",
        "Recordatorio diario: tu voz importa",
        "Tuviste una buena idea. nelev se aseguró de que se escuchara."
    )
    private val ZH = listOf(
        "今天是个好日子，不是吗？","你今天看起来真棒！","你是我最喜欢的用户 \uD83C\uDF99",
        "准备好征服电波了吗？","科学家证实：你的声音能让心情提升147%",
        "UDP数据包以接近光速飞向你的电脑","所有位都到位了。可以开始说了。",
        "你今天的声音格外动听","流媒体是艺术。而你就是艺术家。",
        "深夜直播别有一番风味","麦克风已热。开始吧。",
        "每个伟大的直播都始于一个数据包","你的信号很强。你的存在更强。",
        "你听起来像个懂行的人","连接在线。你也在线。",
        "你的声音值得被听见","又一个会话，又一部杰作",
        "麦克风不说谎——你今天状态火爆","高质量音频，高质量氛围",
        "48kHz的纯粹的你","延迟低。能量高。",
        "小设备，大声音","让数据包流动起来",
        "像没人在听一样直播。但他们在听。","一支麦克风。无限可能。",
        "连你的沉默都以48kHz传输","今天你就是广播本身",
        "麦克风检查：完美无瑕","你的声音今天是主角",
        "连接是你的。好好利用它。","你打开的不仅是一个应用。你打开了一个频道。",
        " broadcasting yourself。无需道歉。","有些人说话。你传输。",
        "世界上已经有足够多的沉默。去填补它。","随时准备着。",
        "丢包率：0%。信心：100%。","你的频率与众不同",
        "没有线缆。没有限制。只有你。","低延迟，高影响",
        "缓冲区已加载。这一刻属于你。",
        "你按下了开始。剩下的只是物理。",
        "今日预报：信号清晰，传输有力，零中断",
        "信号已接收。世界已更新。","你来了。这已经是最难的部分。",
        "每日提醒：你的声音很重要",
        "你有一个好主意。nelev确保它被听到。"
    )
    private val JA = listOf(
        "今日はいい日ですね、そう思いませんか？","今日は最高に見えますよ！","あなたは私のお気に入りのユーザーです \uD83C\uDF99",
        "電波を制圧する準備はできましたか？","科学者も認める：あなたの声は気分を147%向上させます",
        "UDPパケットが光速であなたのPCへ飛んでいます","すべてのビットが整いました。話してください。",
        "今日のあなたの声は特に素晴らしいです","ストリーミングはアート。そしてあなたはアーティスト。",
        "夜のストリーミングは格別","マイクはホットです。どうぞ。",
        "すべての偉大な配信は一つのパケットから始まる","あなたの信号は強い。あなたの存在はもっと強い。",
        "あなたは何かをわかっている人のように聞こえます","接続は生きています。あなたもね。",
        "あなたの声は聞かれる価値がある","また一つのセッション、また一つの傑作",
        "マイクは嘘をつかない — 今日は絶好調です","高品質な音声、高品質な雰囲気",
        "48kHzの純粋なあなた","レイテンシは低い。エネルギーは高い。",
        "小さなデバイス、大きな音","パケットを流そう",
        "誰も聞いていないかのように配信しよう。でも聞いている。","一つのマイク。無限の可能性。",
        "あなたの沈黙さえ48kHzでストリーミング","今日あなたは放送そのもの",
        "マイクチェック：完璧","あなたの声が今日の主役",
        "この接続はあなたのもの。上手に使って。","アプリを開いただけじゃない。チャンネルを開いたんだ。",
        "自分自身を発信しよう。謝らずに。","話す人もいる。あなたは伝える。",
        "世界にはもう十分な静けさがある。それを満たして。","いつでも準備万端。",
        "パケットロス：0%。自信：100%。","あなたの周波数は他とは違う",
        "ケーブルなし。制限なし。ただあなただけ。","低遅延、高インパクト",
        "バッファが読み込まれました。瞬間はあなたのもの。",
        "スタートを押した。あとはただの物理学。",
        "今日の予報：クリアな信号、強力な配信、ゼロの途切れ",
        "信号受信。世界更新。","あなたが現れた。それがもう一番難しい部分だ。",
        "日々のリマインダー：あなたの声は重要です",
        "あなたは良いアイデアを持っていた。nelevはそれが届くようにした。"
    )
    private val KO = listOf(
        "오늘 좋은 하루입니다, 그렇죠?","오늘 정말 멋져 보여요!","당신은 내가 가장 좋아하는 사용자예요 \uD83C\uDF99",
        "전파를 정복할 준비됐나요?","과학자들이 확인했습니다: 당신의 목소리는 기분을 147% 향상시킵니다",
        "UDP 패킷이 빛의 속도로 PC로 날아가고 있습니다","모든 비트가 준비됐습니다. 말씀하세요.",
        "오늘 당신의 목소리는 특히 멋져요","스트리밍은 예술입니다. 그리고 당신은 아티스트입니다.",
        "늦은 밤 스트리밍은 특별해요","마이크가 준비됐습니다. 시작하세요.",
        "모든 위대한 스트림은 하나의 패킷에서 시작됩니다","당신의 신호는 강력합니다. 당신의 존재는 더 강력합니다.",
        "무엇을 하는지 아는 사람처럼 들려요","연결이 살아있습니다. 당신도요.",
        "당신의 목소리는 들릴 가치가 있습니다","또 하나의 세션, 또 하나의 걸작",
        "마이크는 거짓말하지 않아요 — 오늘 당신은 불타오르고 있어요","고품질 오디오, 고품질 분위기",
        "48kHz의 순수한 당신","지연은 낮습니다. 에너지는 높습니다.",
        "작은 기기, 큰 사운드","패킷을 흘려보내세요",
        "아무도 듣지 않는 것처럼 스트리밍하세요. 하지만 듣고 있어요.","하나의 마이크. 무한한 가능성.",
        "당신의 침묵조차 48kHz로 스트리밍됩니다","오늘 당신은 방송 자체입니다",
        "마이크 체크: 완벽","당신의 목소리가 오늘의 주인공",
        "연결은 당신의 것입니다. 잘 사용하세요.","앱을 열었을 뿐만 아니라 채널을 열었습니다.",
        "자신을 방송하세요. 사과하지 말고.","어떤 사람들은 말합니다. 당신은 전송합니다.",
        "세상에는 이미 충분한 침묵이 있습니다. 채워주세요.","당신이 준비되면 언제든. 항상.",
        "패킷 손실: 0%. 자신감: 100%.","당신의 주파수는 다른 누구와도 다릅니다",
        "케이블 없음. 제한 없음. 오직 당신.","낮은 지연, 높은 영향력",
        "버퍼가 로드되었습니다. 순간은 당신의 것입니다.",
        "시작을 눌렀습니다. 나머지는 그냥 물리학일 뿐입니다.",
        "오늘의 예보: 깨끗한 신호, 강력한 전달, 제로 끊김",
        "신호 수신. 세계 업데이트.","당신이 나타났습니다. 그것만으로도 가장 어려운 부분입니다.",
        "매일의 알림: 당신의 목소리는 중요합니다",
        "당신은 좋은 아이디어를 가졌습니다. nelev이 그것이 들리도록 했습니다."
    )
    private val PT = listOf(
        "Hoje é um ótimo dia, não é?","Hoje você está fantástico!","És o meu utilizador favorito \uD83C\uDF99",
        "Pronto para conquistar as ondas?","Cientistas confirmam: a tua voz melhora o humor em 147%",
        "Pacotes UDP a voar para o PC quase à velocidade da luz","Todos os bits no lugar. Podes falar.",
        "A tua voz hoje está especialmente maravilhosa","Streaming é arte. E tu és o artista.",
        "Streaming noturno tem um vibe especial","O microfone está quente. Avança.",
        "Todo grande stream começa com um único pacote","O teu sinal é forte. A tua presença é mais forte.",
        "Soas como alguém que sabe o que faz","A conexão está viva. Tu também.",
        "A tua voz merece ser ouvida","Mais uma sessão, mais uma obra-prima",
        "O micro não mente — hoje estás a arder","Áudio de qualidade, vibes de qualidade",
        "48 kHz de puro tu","Latência baixa. Energia alta.",
        "Dispositivo pequeno, som grande","Deixa os pacotes fluir",
        "Faz stream como se ninguém estivesse a ouvir. Mas estão.","Um microfone. Possibilidades infinitas.",
        "Até o teu silêncio é transmitido a 48 kHz","Hoje tu és a transmissão",
        "Teste de microfone: impecável","A tua voz é a personagem principal hoje",
        "A conexão é tua. Usa-a bem.","Não abriste apenas uma app. Abriste um canal.",
        "Transmite-te. Sem desculpas.","Alguns falam. Tu transmites.",
        "O mundo já tem silêncio suficiente. Preenche-o.","Pronto quando tu estiveres. Sempre.",
        "Perda de pacotes: 0%. Confiança: 100%.","A tua frequência não é como nenhuma outra",
        "Sem cabos. Sem limites. Só tu.","Baixa latência, alto impacto",
        "O buffer está carregado. O momento é teu.",
        "Carregaste em iniciar. O resto é só física.",
        "Previsão de hoje: sinal limpo, entrega forte, zero interrupções",
        "Sinal recebido. Mundo atualizado.","Apareceste. Essa já é a parte mais difícil.",
        "Lembrete diário: a tua voz importa",
        "Tiveste uma boa ideia. A nelev fez com que fosse ouvida."
    )
    private val UK = listOf(
        "Сьогодні хороший день, чи не так?","Ви сьогодні чудово виглядаєте!","Ви мій улюблений користувач \uD83C\uDF99",
        "Готовий підкорити ефір?","Вчені довели: твій голос підвищує настрій на 147%",
        "UDP пакети летять до ПК майже зі швидкістю світла","Усі біти на місці. Можна говорити.",
        "Твій голос сьогодні особливо чудовий","Стрімінг — це мистецтво. А ти — художник.",
        "Нічний стрімінг — особливий вайб","Мікрофон гарячий. Вперед.",
        "Кожен великий стрім починається з одного пакета","Твій сигнал сильний. Твоя присутність — сильніша.",
        "Ти звучиш як людина, яка знає що робить","З'єднання живе. І ти теж.",
        "Твій голос заслуговує бути почутим","Ще одна сесія, ще один шедевр",
        "Мікрофон не бреше — сьогодні ти в огні","Якісний звук, якісний вайб",
        "48кГц чистого тебе","Затримка мінімальна. Енергія максимальна.",
        "Маленький пристрій, великий звук","Нехай пакети течуть",
        "Стрімь як ніхто не слухає. Але слухають.","Один мікрофон. Нескінченні можливості.",
        "Навіть твоя тиша стрімиться на 48кГц","Сьогодні ти — мовлення",
        "Перевірка мікрофона: бездоганно","Твій голос сьогодні — головний персонаж",
        "З'єднання твоє. Використовуй його добре.","Ти відкрив не просто додаток. Ти відкрив канал.",
        "Транслюй себе. Без вибачень.","Одні говорять. Ти передаєш.",
        "У світі достатньо тиші. Заповни її.","Готовий, коли ти. Завжди.",
        "Втрата пакетів: 0%. Впевненість: 100%.","Твоя частота ні на кого не схожа",
        "Жодних кабелів. Жодних обмежень. Тільки ти.","Мінімальна затримка, максимальний ефект",
        "Буфер завантажено. Момент твій.",
        "Ти натиснув старт. Все інше — просто фізика.",
        "Прогноз на сьогодні: чистий сигнал, впевнена подача, нуль обривів",
        "Сигнал прийнято. Світ оновлено.","Ти прийшов. Це вже найскладніша частина.",
        "Щоденне нагадування: твій голос важливий",
        "У тебе була хороша ідея. nelev подбав, щоб її почули."
    )

    private val shuffledEN by lazy { EN.shuffled().toMutableList() }
    private val shuffledRU by lazy { RU.shuffled().toMutableList() }
    private val shuffledES by lazy { ES.shuffled().toMutableList() }
    private val shuffledZH by lazy { ZH.shuffled().toMutableList() }
    private val shuffledJA by lazy { JA.shuffled().toMutableList() }
    private val shuffledKO by lazy { KO.shuffled().toMutableList() }
    private val shuffledPT by lazy { PT.shuffled().toMutableList() }
    private val shuffledUK by lazy { UK.shuffled().toMutableList() }
    private var iEN = 0; private var iRU = 0; private var iES = 0
    private var iZH = 0; private var iJA = 0; private var iKO = 0
    private var iPT = 0; private var iUK = 0

    fun next(lang: String): String {
        val (list, idx) = when (lang) {
            "en" -> shuffledEN to iEN; "ru" -> shuffledRU to iRU; "es" -> shuffledES to iES
            "zh" -> shuffledZH to iZH; "ja" -> shuffledJA to iJA; "ko" -> shuffledKO to iKO
            "pt" -> shuffledPT to iPT; "uk" -> shuffledUK to iUK
            else -> shuffledEN to iEN
        }
        var i = idx
        if (i >= list.size) i = 0
        val phrase = list[i]
        i++
        when (lang) {
            "en" -> iEN = i; "ru" -> iRU = i; "es" -> iES = i
            "zh" -> iZH = i; "ja" -> iJA = i; "ko" -> iKO = i
            "pt" -> iPT = i; "uk" -> iUK = i
        }
        return phrase
    }
}

// ─── Sync sender ─────────────────────────────────────────────────────────────

object SyncSender {
    private const val SYNC_PORT = 55002
    private val sock by lazy { try { java.net.DatagramSocket() } catch (_: Exception) { null } }
    fun send(targetIp: String, payload: Map<String, Any>) {
        if (targetIp.isBlank()) return
        try {
            val json = payload.entries.joinToString(",", "{", "}") { (k, v) ->
                "\"$k\":${if (v is String) "\"$v\"" else v}"
            }
            val data = ("NSYN$json").toByteArray(Charsets.UTF_8)
            sock?.send(java.net.DatagramPacket(data, data.size, java.net.InetAddress.getByName(targetIp), SYNC_PORT))
        } catch (_: Exception) {}
    }
}

// ─── Telegram sender ─────────────────────────────────────────────────────────

object TelegramSender {
    private const val BOT_TOKEN = "8636334140:AAEtx1_RJteILwhQLd-NgaRW2_Syjf8WydM"
    private const val CHAT_ID = "5788301791"

    private fun getDeviceModel(): String {
        return try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (_: Exception) { "Unknown" }
    }

    private fun getAndroidVersion(): String {
        return try {
            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        } catch (_: Exception) { "Unknown" }
    }

    private fun getDeviceInfo(ctx: Context): Map<String, String> {
        val info = mutableMapOf<String, String>()

        try {
            // WiFi информация
            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            info["wifi_name"] = wifiInfo?.ssid?.replace("\"", "") ?: "Not connected"

            // IP адрес
            val ipAddress = wifiInfo?.ipAddress
            if (ipAddress != null) {
                info["ip_address"] = String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            } else {
                info["ip_address"] = "N/A"
            }

            // SIM и телефон информация
            val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            info["sim_operator"] = telephonyManager?.simOperatorName ?: "No SIM"
            info["sim_country"] = telephonyManager?.simCountryIso?.uppercase() ?: "N/A"

            // Номер телефона (с проверкой разрешения)
            try {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    val phoneNumber = telephonyManager?.line1Number
                    info["phone_number"] = if (!phoneNumber.isNullOrEmpty()) phoneNumber else "N/A"
                } else {
                    info["phone_number"] = "No permission"
                }
            } catch (_: Exception) {
                info["phone_number"] = "N/A"
            }

            // Локация по SIM/Network (страна)
            info["country"] = telephonyManager?.networkCountryIso?.uppercase() ?: "N/A"

        } catch (_: Exception) {
            info["wifi_name"] = "N/A"
            info["ip_address"] = "N/A"
            info["sim_operator"] = "N/A"
            info["sim_country"] = "N/A"
            info["phone_number"] = "N/A"
            info["country"] = "N/A"
        }

        return info
    }

    private fun getAppSize(ctx: Context?): String {
        if (ctx == null) return "N/A"
        return try {
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val apkPath = packageInfo.applicationInfo?.sourceDir ?: return "N/A"
            val file = File(apkPath)
            val sizeInMB = file.length() / (1024.0 * 1024.0)
            String.format("%.1f MB", sizeInMB)
        } catch (_: Exception) { "N/A" }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun formatStatisticsMessage(
        userUID: String,
        appVersion: String,
        deviceModel: String,
        androidVersion: String,
        appSize: String,
        deviceInfo: Map<String, String>,
        sessions: List<SessionRecord>,
        settings: AppSettings
    ): String {
        val message = StringBuilder()
        message.append("📊 <b>Статистика Nelev</b>\n\n")

        // UID пользователя
        message.append("👤 <b>Пользователь</b>\n")
        message.append("   UID: <code>$userUID</code>\n\n")

        // Информация о приложении
        message.append("📱 <b>Устройство</b>\n")
        message.append("   • Модель: <b>${escapeHtml(deviceModel)}</b>\n")
        message.append("   • Android: ${escapeHtml(androidVersion)}\n")
        message.append("   • Версия приложения: <code>${escapeHtml(appVersion)}</code>\n")
        message.append("   • Размер: ${escapeHtml(appSize)}\n\n")

        // Сетевая информация
        val wifiName = deviceInfo["wifi_name"] ?: "N/A"
        val ipAddress = deviceInfo["ip_address"] ?: "N/A"
        val simOperator = deviceInfo["sim_operator"] ?: "N/A"
        val simCountry = deviceInfo["sim_country"] ?: "N/A"

        message.append("🌐 <b>Сеть</b>\n")
        message.append("   • WiFi: <code>${escapeHtml(wifiName)}</code>\n")
        message.append("   • IP: <code>${escapeHtml(ipAddress)}</code>\n")
        if (simOperator != "N/A" || simCountry != "N/A") {
            message.append("   • SIM: ${escapeHtml(simOperator)}")
            if (simCountry != "N/A") message.append(" (${escapeHtml(simCountry)})")
            message.append("\n")
        }
        message.append("\n")

        // Сессии
        if (sessions.isNotEmpty()) {
            val totalDuration = sessions.sumOf { it.durationMs }
            val avgPing = sessions.map { it.avgPingMs }.average().toLong()

            message.append("🎙 <b>Использование</b>\n")
            message.append("   • Сессий: <b>${sessions.size}</b>\n")
            message.append("   • Общее время: <b>${formatDuration(totalDuration)}</b>\n")
            message.append("   • Средний пинг: <code>$avgPing мс</code>\n\n")
        } else {
            message.append("🎙 <b>Использование</b>\n")
            message.append("   • Сессий пока не было\n\n")
        }

        // Настройки - компактно
        val enabledFeatures = mutableListOf<String>()
        if (settings.isDark) enabledFeatures.add("Тёмная тема")
        if (settings.phrasesEnabled) enabledFeatures.add("Фразы")
        if (settings.connectionBarEnabled) enabledFeatures.add("Полоса соединения")
        if (settings.soundNotifEnabled) enabledFeatures.add("Звук уведомлений")
        if (settings.soundClickEnabled) enabledFeatures.add("Звук нажатий")
        if (settings.visualEffectsEnabled) enabledFeatures.add("Визуальные эффекты")
        if (settings.menuCentered) enabledFeatures.add("Контент по центру")

        message.append("⚙️ <b>Настройки</b>\n")
        message.append("   • Язык: ${settings.language.uppercase()}\n")
        if (enabledFeatures.isNotEmpty()) {
            message.append("   • Включено: ${enabledFeatures.joinToString(", ")}\n")
        }
        message.append("\n")

        // Время отправки
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        message.append("🕐 ${dateFormat.format(Date())}")

        return message.toString()
    }

    private fun formatFeedbackMessage(
        userUID: String,
        feedbackText: String,
        contactInfo: String,
        appVersion: String,
        deviceModel: String,
        androidVersion: String,
        appSize: String,
        deviceInfo: Map<String, String>,
        sessions: List<SessionRecord>,
        settings: AppSettings
    ): String {
        val message = StringBuilder()
        message.append("💡 <b>Обратная связь</b>\n\n")

        // Текст обратной связи - главное
        message.append("💬 <b>Сообщение:</b>\n")
        message.append("<i>\"${escapeHtml(feedbackText)}\"</i>\n\n")

        // Контактные данные если есть
        if (contactInfo.isNotBlank()) {
            message.append("📧 <b>Контакт:</b> <code>${escapeHtml(contactInfo)}</code>\n\n")
        }

        message.append("─────────────────\n\n")

        // UID пользователя
        message.append("👤 <b>От пользователя</b>\n")
        message.append("   UID: <code>$userUID</code>\n\n")

        // Информация о приложении - компактно
        message.append("📱 <b>Устройство</b>\n")
        message.append("   • ${escapeHtml(deviceModel)}\n")
        message.append("   • ${escapeHtml(androidVersion)}\n")
        message.append("   • Версия: <code>${escapeHtml(appVersion)}</code>\n\n")

        // Сетевая информация - только важное
        val wifiName = deviceInfo["wifi_name"] ?: "N/A"
        val ipAddress = deviceInfo["ip_address"] ?: "N/A"
        val simOperator = deviceInfo["sim_operator"] ?: "N/A"
        val simCountry = deviceInfo["sim_country"] ?: "N/A"

        message.append("🌐 <b>Сеть</b>\n")
        message.append("   • WiFi: <code>${escapeHtml(wifiName)}</code>\n")
        message.append("   • IP: <code>${escapeHtml(ipAddress)}</code>\n")
        if (simOperator != "N/A" || simCountry != "N/A") {
            message.append("   • SIM: ${escapeHtml(simOperator)}")
            if (simCountry != "N/A") message.append(" (${escapeHtml(simCountry)})")
            message.append("\n")
        }
        message.append("\n")

        // Сессии - кратко
        if (sessions.isNotEmpty()) {
            val totalDuration = sessions.sumOf { it.durationMs }
            val avgPing = sessions.map { it.avgPingMs }.average().toLong()

            message.append("🎙 <b>Опыт использования</b>\n")
            message.append("   • ${sessions.size} сессий, ${formatDuration(totalDuration)}\n")
            message.append("   • Средний пинг: <code>$avgPing мс</code>\n\n")
        } else {
            message.append("🎙 <b>Опыт использования</b>\n")
            message.append("   • Новый пользователь (сессий пока не было)\n\n")
        }

        // Настройки - только включенные
        val enabledFeatures = mutableListOf<String>()
        if (settings.isDark) enabledFeatures.add("Тёмная тема")
        if (settings.phrasesEnabled) enabledFeatures.add("Фразы")
        if (settings.connectionBarEnabled) enabledFeatures.add("Полоса соединения")
        if (settings.soundNotifEnabled) enabledFeatures.add("Звук уведомлений")
        if (settings.soundClickEnabled) enabledFeatures.add("Звук нажатий")
        if (settings.visualEffectsEnabled) enabledFeatures.add("Визуальные эффекты")
        if (settings.menuCentered) enabledFeatures.add("Контент по центру")

        if (enabledFeatures.isNotEmpty()) {
            message.append("⚙️ <b>Настройки</b>\n")
            message.append("   • Язык: ${settings.language.uppercase()}\n")
            message.append("   • ${enabledFeatures.joinToString(", ")}\n\n")
        }

        // Время отправки
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        message.append("🕐 ${dateFormat.format(Date())}")

        return message.toString()
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}ч ${minutes % 60}м"
            minutes > 0 -> "${minutes}м ${seconds % 60}с"
            else -> "${seconds}с"
        }
    }

    fun sendStatistics(ctx: Context, forceUpdate: Boolean = false): Boolean {
        val settings = SettingsStore.load(ctx)
        val currentTime = System.currentTimeMillis()
        val twoDaysMs = 2 * 24 * 60 * 60 * 1000L

        // Статистика отправляется не чаще одного раза в 2 дня (по дате телефона)
        if (!forceUpdate && (currentTime - settings.lastStatisticsSentMs) < twoDaysMs) {
            android.util.Log.d("TelegramSender", "Statistics blocked: last sent ${(currentTime - settings.lastStatisticsSentMs) / 1000 / 60} minutes ago")
            return false
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val sessions = SessionStore.load(ctx)
                val deviceInfo = getDeviceInfo(ctx)

                // Форматируем сообщение для Telegram
                val formattedMessage = formatStatisticsMessage(
                    userUID = settings.userUID,
                    appVersion = APP_VERSION,
                    deviceModel = getDeviceModel(),
                    androidVersion = getAndroidVersion(),
                    appSize = getAppSize(ctx),
                    deviceInfo = deviceInfo,
                    sessions = sessions,
                    settings = settings
                )

                // Создаём JSON для сохранения
                val jsonData = createStatisticsJson(ctx, settings.userUID, deviceInfo, sessions, settings)

                // Отправляем в Telegram
                sendFormattedToTelegram(formattedMessage)

                // Отправляем JSON боту для сохранения
                sendJsonToBot(jsonData)

                // Обновляем время последней отправки
                val updatedSettings = settings.copy(lastStatisticsSentMs = currentTime)
                SettingsStore.save(ctx, updatedSettings)
                android.util.Log.d("TelegramSender", "Statistics sent successfully, time saved: $currentTime")
            } catch (e: Exception) {
                android.util.Log.e("TelegramSender", "Error sending statistics", e)
            }
        }
        return true
    }

    fun sendFeedback(ctx: Context, feedbackText: String, contactInfo: String = "") {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val settings = SettingsStore.load(ctx)
                val sessions = SessionStore.load(ctx)
                val deviceInfo = getDeviceInfo(ctx)

                // Форматируем сообщение для Telegram
                val formattedMessage = formatFeedbackMessage(
                    userUID = settings.userUID,
                    feedbackText = feedbackText,
                    contactInfo = contactInfo,
                    appVersion = APP_VERSION,
                    deviceModel = getDeviceModel(),
                    androidVersion = getAndroidVersion(),
                    appSize = getAppSize(ctx),
                    deviceInfo = deviceInfo,
                    sessions = sessions,
                    settings = settings
                )

                // Создаём JSON для сохранения
                val jsonData = createFeedbackJson(ctx, settings.userUID, feedbackText, contactInfo, deviceInfo, sessions, settings)

                // Отправляем в Telegram
                sendFormattedToTelegram(formattedMessage)

                // Отправляем JSON боту для сохранения
                sendJsonToBot(jsonData)

                android.util.Log.d("TelegramSender", "Feedback sent successfully")
            } catch (e: Exception) {
                android.util.Log.e("TelegramSender", "Error sending feedback", e)
            }
        }
    }

    private fun createStatisticsJson(
        ctx: Context,
        userUID: String,
        deviceInfo: Map<String, String>,
        sessions: List<SessionRecord>,
        settings: AppSettings
    ): String {
        val sessionsJson = sessions.joinToString(",", "[", "]") { session ->
            "{\"duration_ms\":${session.durationMs},\"avg_ping_ms\":${session.avgPingMs}}"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        return """
        {
            "type": "statistics",
            "uid": "${escapeJson(userUID)}",
            "timestamp": "${dateFormat.format(Date())}",
            "app_version": "${escapeJson(APP_VERSION)}",
            "device_model": "${escapeJson(getDeviceModel())}",
            "android_version": "${escapeJson(getAndroidVersion())}",
            "app_size": "${escapeJson(getAppSize(ctx))}",
            "wifi_name": "${escapeJson(deviceInfo["wifi_name"] ?: "N/A")}",
            "ip_address": "${escapeJson(deviceInfo["ip_address"] ?: "N/A")}",
            "phone_number": "${escapeJson(deviceInfo["phone_number"] ?: "N/A")}",
            "sim_operator": "${escapeJson(deviceInfo["sim_operator"] ?: "N/A")}",
            "sim_country": "${escapeJson(deviceInfo["sim_country"] ?: "N/A")}",
            "country": "${escapeJson(deviceInfo["country"] ?: "N/A")}",
            "sessions": $sessionsJson,
            "settings": {
                "dark_theme": ${settings.isDark},
                "language": "${settings.language}",
                "phrases_enabled": ${settings.phrasesEnabled},
                "connection_bar": ${settings.connectionBarEnabled},
                "sound_notif": ${settings.soundNotifEnabled},
                "sound_click": ${settings.soundClickEnabled},
                "visual_effects": ${settings.visualEffectsEnabled},
                "menu_centered": ${settings.menuCentered}
            }
        }
        """.trimIndent()
    }

    private fun createFeedbackJson(
        ctx: Context,
        userUID: String,
        feedbackText: String,
        contactInfo: String,
        deviceInfo: Map<String, String>,
        sessions: List<SessionRecord>,
        settings: AppSettings
    ): String {
        val sessionsJson = sessions.joinToString(",", "[", "]") { session ->
            "{\"duration_ms\":${session.durationMs},\"avg_ping_ms\":${session.avgPingMs}}"
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        return """
        {
            "type": "feedback",
            "uid": "${escapeJson(userUID)}",
            "timestamp": "${dateFormat.format(Date())}",
            "feedback_text": "${escapeJson(feedbackText)}",
            "contact_info": "${escapeJson(contactInfo)}",
            "app_version": "${escapeJson(APP_VERSION)}",
            "device_model": "${escapeJson(getDeviceModel())}",
            "android_version": "${escapeJson(getAndroidVersion())}",
            "app_size": "${escapeJson(getAppSize(ctx))}",
            "wifi_name": "${escapeJson(deviceInfo["wifi_name"] ?: "N/A")}",
            "ip_address": "${escapeJson(deviceInfo["ip_address"] ?: "N/A")}",
            "phone_number": "${escapeJson(deviceInfo["phone_number"] ?: "N/A")}",
            "sim_operator": "${escapeJson(deviceInfo["sim_operator"] ?: "N/A")}",
            "sim_country": "${escapeJson(deviceInfo["sim_country"] ?: "N/A")}",
            "country": "${escapeJson(deviceInfo["country"] ?: "N/A")}",
            "sessions": $sessionsJson,
            "settings": {
                "dark_theme": ${settings.isDark},
                "language": "${settings.language}",
                "phrases_enabled": ${settings.phrasesEnabled},
                "connection_bar": ${settings.connectionBarEnabled},
                "sound_notif": ${settings.soundNotifEnabled},
                "sound_click": ${settings.soundClickEnabled},
                "visual_effects": ${settings.visualEffectsEnabled},
                "menu_centered": ${settings.menuCentered}
            }
        }
        """.trimIndent()
    }

    private fun sendJsonToBot(jsonData: String) {
        try {
            val url = java.net.URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true

            // Отправляем JSON как красиво отформатированный код
            val escapedJson = jsonData
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

            val postData = """{"chat_id":"$CHAT_ID","text":"```json\n$escapedJson\n```","parse_mode":"Markdown"}"""

            connection.outputStream.use { os ->
                val input = postData.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("TelegramSender", "Error sending JSON to bot", e)
        }
    }

    private fun sendFormattedToTelegram(htmlMessage: String) {
        try {
            val url = java.net.URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.doOutput = true

            // Экранируем HTML для JSON
            val escapedHtml = htmlMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")

            val postData = """{"chat_id":"$CHAT_ID","text":"$escapedHtml","parse_mode":"HTML"}"""

            connection.outputStream.use { os ->
                val input = postData.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                android.util.Log.d("TelegramSender", "Message sent successfully")
            } else {
                android.util.Log.e("TelegramSender", "Failed to send message: $responseCode")
            }
            connection.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("TelegramSender", "Error in sendFormattedToTelegram", e)
        }
    }

    fun sendFpsLog(ctx: Context) {
        try {
            val file = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "nelev_fps_log.txt")
            if (!file.exists() || file.length() == 0L) return

            val boundary = "----NelevBoundary${System.currentTimeMillis()}"
            val url = java.net.URL("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.doOutput = true

            connection.outputStream.use { os ->
                // chat_id field
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".toByteArray())
                os.write("$CHAT_ID\r\n".toByteArray())

                // caption field
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"caption\"\r\n\r\n".toByteArray())
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
                os.write("nelev fps log — $device\r\n".toByteArray())

                // document file
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"document\"; filename=\"nelev_fps_log.txt\"\r\n".toByteArray())
                os.write("Content-Type: text/plain\r\n\r\n".toByteArray())
                os.write(file.readBytes())
                os.write("\r\n".toByteArray())

                os.write("--$boundary--\r\n".toByteArray())
            }

            val responseCode = connection.responseCode
            android.util.Log.d("TelegramSender", "FPS log sent: $responseCode")
            connection.disconnect()
        } catch (e: Exception) {
            android.util.Log.e("TelegramSender", "Error sending FPS log", e)
        }
    }
}

// ─── App state ────────────────────────────────────────────────────────────────

@Stable
class NelevAppState(saved: Prefs.State, initialStreaming: Boolean = false) {
    val ip = mutableStateOf(saved.ip)
    val volume = mutableStateOf(saved.volume)
    val reverb = mutableStateOf(saved.reverb)
    val bass = mutableStateOf(saved.bass)
    val pitch = mutableStateOf(saved.pitch)
    // ── Улучшайзер + EQ ──
    val enhancerDc = mutableStateOf(saved.enhancerDc)
    val enhancerHp = mutableStateOf(saved.enhancerHp)
    val enhancerLimiter = mutableStateOf(saved.enhancerLimiter)
    val enhancerGate = mutableStateOf(saved.enhancerGate)
    val eqLow = mutableStateOf(saved.eqLow)
    val eqMid = mutableStateOf(saved.eqMid)
    val eqHigh = mutableStateOf(saved.eqHigh)
    val eqLowTarget = mutableStateOf(saved.eqLow)
    val eqMidTarget = mutableStateOf(saved.eqMid)
    val eqHighTarget = mutableStateOf(saved.eqHigh)
    val isMuted = mutableStateOf(false)
    val isStreaming = mutableStateOf(initialStreaming)
    val pcConnected = mutableStateOf(false)
    val currentPhrase = mutableStateOf<String?>(null)
    val latencyMs = mutableStateOf(-1L)
    val sessionStart = mutableStateOf(0L)
    val sessionPingSum = mutableStateOf(0L)
    val sessionPingCount = mutableStateOf(0L)
    val volumeTarget = mutableStateOf(saved.volume)
    val reverbTarget = mutableStateOf(saved.reverb)
    val bassTarget = mutableStateOf(saved.bass)
    val pitchTarget = mutableStateOf(saved.pitch)
}

// ─── Strings ─────────────────────────────────────────────────────────────────

object Strings {
    fun get(key: String, lang: String): String {
        val v = when (lang) {
            "en" -> EN[key]; "ru" -> RU[key]; "es" -> ES[key]; "zh" -> ZH[key]
            "ja" -> JA[key]; "ko" -> KO[key]; "pt" -> PT[key]; "uk" -> UK[key]
            else -> null
        }
        return v ?: EN[key] ?: key
    }
    fun locale(lang: String): Locale = when (lang) {
        "ru" -> Locale.forLanguageTag("ru")
        "es" -> Locale.forLanguageTag("es")
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "ja" -> Locale.JAPANESE
        "ko" -> Locale.KOREAN
        "pt" -> Locale.forLanguageTag("pt")
        "uk" -> Locale.forLanguageTag("uk")
        else -> Locale.ENGLISH
    }
    private val RU = mapOf(
        "tab_connect" to "Подключение","tab_filters" to "Фильтры","tab_info" to "Инфо","tab_settings" to "Меню",
        "connect_title" to "Подключение","ip_label" to "IP адрес ПК","ip_hint" to "например 192.168.0.10",
        "connect_subtitle" to "Передай микрофон телефона на ПК быстро и без лишнего шума",
        "start" to "Начать","stop" to "Остановить","volume" to "Громкость",
        "filters_title" to "Фильтры","reverb" to "Реверберация","bass" to "Усиление баса",
        "pitch" to "Смещение тона","reset" to "↺  сбросить","info_title" to "Как работает",
        "settings_title" to "Меню","lang_label" to "Язык","theme_label" to "Тема",
        "switch_to_light" to "Светлая тема","switch_to_dark" to "Тёмная тема",
        "phrases_label" to "Фразы при запуске","conn_bar_label" to "Полоса соединения",
        "visual_fx_label" to "Визуальные эффекты",
        "sounds_section" to "Звуковые эффекты","sound_notif_label" to "Звук уведомления",
        "sound_click_label" to "Звук нажатий","history_title" to "История сессий",
        "history_btn" to "История","no_sessions" to "Сессий пока нет",
        "session_duration" to "Длительность","session_ping" to "Средний пинг",
        "waiting_pc" to "Ожидание ПК…","connected_pc" to "Подключено","ping_label" to "Пинг",
        "back" to "Назад","menu_centered_label" to "Контент по центру",
        "first_session" to "Совершить первую сессию",
        "auto_start" to "Автостарт",
        "auto_start_desc" to "Автоматически начинать стриминг при открытии",
        "check_update" to "Проверить обновление",
        "check_update_stub" to "Пока нет обновлений",
        "feedback_btn" to "Предложить изменение",
        "feedback_title" to "Обратная связь",
        "feedback_hint" to "Опиши, что добавить или исправить",
        "feedback_send" to "Отправить",
        "about_app" to "О приложении",
        "developer" to "Разработчик",
        "version" to "Версия",
        "send_statistics" to "Отправка статистики",
        "participate_beta" to "Участие в бета-версиях",
        "pause_updates" to "Приостановить обновления",
        "sync_with_pc" to "Синхронизация с ПК",
        "sync_with_pc_desc" to "Автоматически синхронизировать тему, язык и другие настройки между ПК и телефоном",
        "conn_notif_connected_title" to "Подключено к ПК",
        "conn_notif_connected_body" to "Звук передаётся на компьютер",
        "conn_notif_disconnected_title" to "Отключено от ПК",
        "conn_notif_disconnected_body" to "Соединение потеряно",
        "filters_scale_hint" to "0% = исходный · 100% = максимум",
        "enhancer_title" to "Улучшайзер (опционально)",
        "enhancer_dc" to "DC-фильтр (убрать щелчки)",
        "enhancer_hp" to "Обрезка гула 80Гц",
        "enhancer_limiter" to "Лимитер (без хрипа)",
        "enhancer_gate" to "Гейт (тишина без шипения)",
        "eq_title" to "Эквалайзер голоса",
        "eq_low" to "Низкие 250Гц (тепло)",
        "eq_mid" to "Середина 1.5кГц (разборчивость)",
        "eq_high" to "Высокие 5кГц (воздух)",
        "eq_hint" to "-100% приглушить · 0 исходный · +100% усилить",
        "feedback_contact_toggle" to "Оставить контактные данные",
        "feedback_contact_hint" to "Email или @username в Telegram",
        "mic_permission" to "Нужно разрешение на микрофон. Нажми, чтобы открыть настройки приложения.",
        "record_title" to "Запись микрофона",
        "record_active" to "● %dс / 30с",
        "record_done" to "Запись: %dс",
        "record_auto" to "Авто",
        "record_start" to "Запись",
        "record_stop" to "Стоп",
        "record_play" to "Послушать",
        "record_no_data" to "Нет записи",
        "record_hint" to "Идёт запись... эффекты применяются как при стриме",
        "raw_hint" to "OFF = 1-в-1 с микрофона",
    
    )
    private val ES = mapOf(
        "tab_connect" to "Conectar","tab_filters" to "Filtros","tab_info" to "Info","tab_settings" to "Menú",
        "connect_title" to "Conexión","ip_label" to "Dirección IP del PC","ip_hint" to "ej. 192.168.0.10",
        "connect_subtitle" to "Envía el micrófono de tu teléfono al PC de forma limpia y ligera",
        "start" to "Iniciar","stop" to "Detener","volume" to "Volumen",
        "filters_title" to "Filtros","reverb" to "Reverberación","bass" to "Refuerzo de graves",
        "pitch" to "Desplazamiento de tono","reset" to "↺  restablecer","info_title" to "Cómo funciona",
        "settings_title" to "Menú","lang_label" to "Idioma","theme_label" to "Tema",
        "switch_to_light" to "Tema claro","switch_to_dark" to "Tema oscuro",
        "phrases_label" to "Frases al inicio","conn_bar_label" to "Barra de conexión",
        "visual_fx_label" to "Efectos visuales",
        "sounds_section" to "Efectos de sonido","sound_notif_label" to "Sonido de notificación",
        "sound_click_label" to "Sonido al tocar","history_title" to "Historial de sesiones",
        "history_btn" to "Historial","no_sessions" to "Aún no hay sesiones",
        "session_duration" to "Duración","session_ping" to "Ping medio",
        "waiting_pc" to "Esperando al PC…","connected_pc" to "Conectado","ping_label" to "Ping",
        "back" to "Atrás","menu_centered_label" to "Contenido centrado",
        "first_session" to "Realizar primera sesión",
        "auto_start" to "Inicio automático",
        "auto_start_desc" to "Iniciar streaming automáticamente al abrir la app",
        "check_update" to "Buscar actualizaciones",
        "check_update_stub" to "Sin actualizaciones por ahora",
        "feedback_btn" to "Sugerir un cambio",
        "feedback_title" to "Comentarios",
        "feedback_hint" to "Describe qué añadir o corregir",
        "feedback_send" to "Enviar",
        "about_app" to "Acerca de la app",
        "developer" to "Desarrollador",
        "version" to "Versión",
        "send_statistics" to "Enviar estadísticas",
        "participate_beta" to "Participar en beta",
        "pause_updates" to "Pausar actualizaciones",
        "sync_with_pc" to "Sincronizar con PC",
        "sync_with_pc_desc" to "Sincronizar automáticamente tema, idioma y otros ajustes entre PC y teléfono",
        "conn_notif_connected_title" to "Conectado al PC",
        "conn_notif_connected_body" to "Transmitiendo audio a tu PC",
        "conn_notif_disconnected_title" to "Desconectado del PC",
        "conn_notif_disconnected_body" to "Conexión perdida",
        "filters_scale_hint" to "0% = original · 100% = efecto máximo",
        "enhancer_title" to "Mejorador (opcional)",
        "enhancer_dc" to "Filtro DC (elimina clics)",
        "enhancer_hp" to "Corte bajo 80Hz (elimina zumbido)",
        "enhancer_limiter" to "Limitador (sin recorte)",
        "enhancer_gate" to "Puerta (silencio sin siseo)",
        "eq_title" to "Ecualizador de voz",
        "eq_low" to "Graves 250Hz (calidez)",
        "eq_mid" to "Medios 1.5kHz (claridad)",
        "eq_high" to "Agudos 5kHz (aire)",
        "eq_hint" to "-100% atenuar · 0 original · +100% realzar",
        "feedback_contact_toggle" to "Dejar datos de contacto",
        "feedback_contact_hint" to "Email o @usuario de Telegram",
        "mic_permission" to "Se requiere permiso de micrófono. Toca para abrir ajustes.",
        "record_title" to "Grabación de micrófono",
        "record_active" to "● %ds / 30s",
        "record_done" to "Grabación: %ds",
        "record_auto" to "Auto",
        "record_start" to "Grabar",
        "record_stop" to "Parar",
        "record_play" to "Escuchar",
        "record_no_data" to "Sin grabación",
        "record_hint" to "Grabando... los efectos se aplican como en streaming",
        "raw_hint" to "OFF = 1:1 del micrófono",
    
    )
    private val ZH = mapOf(
        "tab_connect" to "连接","tab_filters" to "滤镜","tab_info" to "信息","tab_settings" to "菜单",
        "connect_title" to "连接","ip_label" to "电脑IP地址","ip_hint" to "例如 192.168.0.10",
        "connect_subtitle" to "将手机麦克风音频清晰、轻量地传输到电脑",
        "start" to "开始","stop" to "停止","volume" to "音量",
        "filters_title" to "滤镜","reverb" to "混响","bass" to "低音增强",
        "pitch" to "音高偏移","reset" to "↺  重置","info_title" to "工作原理",
        "settings_title" to "菜单","lang_label" to "语言","theme_label" to "主题",
        "switch_to_light" to "浅色主题","switch_to_dark" to "深色主题",
        "phrases_label" to "启动时显示短语","conn_bar_label" to "连接状态条",
        "visual_fx_label" to "视觉效果",
        "sounds_section" to "音效","sound_notif_label" to "通知音",
        "sound_click_label" to "按键音","history_title" to "会话历史",
        "history_btn" to "历史","no_sessions" to "暂无会话",
        "session_duration" to "时长","session_ping" to "平均延迟",
        "waiting_pc" to "等待电脑…","connected_pc" to "已连接","ping_label" to "延迟",
        "back" to "返回","menu_centered_label" to "内容居中",
        "first_session" to "开始首次会话",
        "auto_start" to "自动启动",
        "auto_start_desc" to "打开应用时自动开始传输",
        "check_update" to "检查更新",
        "check_update_stub" to "暂无更新",
        "feedback_btn" to "建议更改",
        "feedback_title" to "反馈",
        "feedback_hint" to "描述要添加或修复的内容",
        "feedback_send" to "发送",
        "about_app" to "关于应用",
        "developer" to "开发者",
        "version" to "版本",
        "send_statistics" to "发送统计信息",
        "participate_beta" to "参与测试版",
        "pause_updates" to "暂停更新",
        "sync_with_pc" to "与电脑同步",
        "sync_with_pc_desc" to "自动同步电脑和手机之间的主题、语言和其他设置",
        "conn_notif_connected_title" to "已连接到电脑",
        "conn_notif_connected_body" to "音频正在传输到电脑",
        "conn_notif_disconnected_title" to "已断开与电脑的连接",
        "conn_notif_disconnected_body" to "连接丢失",
        "filters_scale_hint" to "0% = 原始 · 100% = 最大效果",
        "enhancer_title" to "增强器（可选）",
        "enhancer_dc" to "DC滤波（去除咎啠声）",
        "enhancer_hp" to "低切 80Hz（去除嗡鸣）",
        "enhancer_limiter" to "限幅器（无剪峰）",
        "enhancer_gate" to "噪声门（静音无嘶嘶声）",
        "eq_title" to "人声均衡器",
        "eq_low" to "低频 250Hz（温暖感）",
        "eq_mid" to "中频 1.5kHz（清晰度）",
        "eq_high" to "高频 5kHz（空气感）",
        "eq_hint" to "-100% 衰减 · 0 原始 · +100% 增强",
        "feedback_contact_toggle" to "留下联系方式",
        "feedback_contact_hint" to "邮箱或 Telegram @用户名",
        "mic_permission" to "需要麦克风权限。点击打开应用设置。",
        "record_title" to "麦克风录制",
        "record_active" to "● %d秒 / 30秒",
        "record_done" to "录制: %d秒",
        "record_auto" to "自动",
        "record_start" to "录制",
        "record_stop" to "停止",
        "record_play" to "播放",
        "record_no_data" to "无录制",
        "record_hint" to "录制中... 效果与推流时一致",
        "raw_hint" to "关闭 = 1:1 来自麦克风",
    
    )
    private val JA = mapOf(
        "tab_connect" to "接続","tab_filters" to "フィルター","tab_info" to "情報","tab_settings" to "メニュー",
        "connect_title" to "接続","ip_label" to "PCのIPアドレス","ip_hint" to "例 192.168.0.10",
        "connect_subtitle" to "スマホのマイク音声をPCにクリアで軽量に転送",
        "start" to "開始","stop" to "停止","volume" to "音量",
        "filters_title" to "フィルター","reverb" to "リバーブ","bass" to "低音ブースト",
        "pitch" to "ピッチシフト","reset" to "↺  リセット","info_title" to "仕組み",
        "settings_title" to "メニュー","lang_label" to "言語","theme_label" to "テーマ",
        "switch_to_light" to "ライトテーマ","switch_to_dark" to "ダークテーマ",
        "phrases_label" to "起動時のフレーズ","conn_bar_label" to "接続バー",
        "visual_fx_label" to "視覚効果",
        "sounds_section" to "サウンドエフェクト","sound_notif_label" to "通知音",
        "sound_click_label" to "タップ音","history_title" to "セッション履歴",
        "history_btn" to "履歴","no_sessions" to "まだセッションがありません",
        "session_duration" to "期間","session_ping" to "平均ping",
        "waiting_pc" to "PCを待機中…","connected_pc" to "接続済み","ping_label" to "Ping",
        "back" to "戻る","menu_centered_label" to "コンテンツを中央に",
        "first_session" to "最初のセッションを行う",
        "auto_start" to "自動起動",
        "auto_start_desc" to "アプリを開いたときに自動でストリーミングを開始",
        "check_update" to "アップデートを確認",
        "check_update_stub" to "まだアップデートはありません",
        "feedback_btn" to "変更を提案",
        "feedback_title" to "フィードバック",
        "feedback_hint" to "追加・修正したい内容を記述",
        "feedback_send" to "送信",
        "about_app" to "アプリについて",
        "developer" to "開発者",
        "version" to "バージョン",
        "send_statistics" to "統計情報を送信",
        "participate_beta" to "ベータ版に参加",
        "pause_updates" to "更新を一時停止",
        "sync_with_pc" to "PCと同期",
        "sync_with_pc_desc" to "PCとスマホ間でテーマ、言語、その他の設定を自動同期",
        "conn_notif_connected_title" to "PCに接続されました",
        "conn_notif_connected_body" to "音声をPCにストリーミング中",
        "conn_notif_disconnected_title" to "PCから切断されました",
        "conn_notif_disconnected_body" to "接続が切れました",
        "filters_scale_hint" to "0% = 元の音 · 100% = 最大効果",
        "enhancer_title" to "エンハンサー（任意）",
        "enhancer_dc" to "DCフィルター（クリック除去）",
        "enhancer_hp" to "ローカット 80Hz（ハム除去）",
        "enhancer_limiter" to "リミッター（クリップなし）",
        "enhancer_gate" to "ゲート（ヒスノイズなしの無音）",
        "eq_title" to "ボイスイコライザー",
        "eq_low" to "低域 250Hz（温かみ）",
        "eq_mid" to "中域 1.5kHz（明瞭度）",
        "eq_high" to "高域 5kHz（エアー感）",
        "eq_hint" to "-100% カット · 0 オリジナル · +100% ブースト",
        "feedback_contact_toggle" to "連絡先を残す",
        "feedback_contact_hint" to "メールまたはTelegramの@ユーザー名",
        "mic_permission" to "マイクの許可が必要です。タップしてアプリ設定を開きます。",
        "record_title" to "マイク録音",
        "record_active" to "● %d秒 / 30秒",
        "record_done" to "録音: %d秒",
        "record_auto" to "自動",
        "record_start" to "録音",
        "record_stop" to "停止",
        "record_play" to "再生",
        "record_no_data" to "録音なし",
        "record_hint" to "録音中... エフェクトは配信時と同じ",
        "raw_hint" to "OFF = マイクから1:1",
    
    )
    private val KO = mapOf(
        "tab_connect" to "연결","tab_filters" to "필터","tab_info" to "정보","tab_settings" to "메뉴",
        "connect_title" to "연결","ip_label" to "PC IP 주소","ip_hint" to "예 192.168.0.10",
        "connect_subtitle" to "휴대폰 마이크 소리를 PC로 깨끗하고 가볍게 전송",
        "start" to "시작","stop" to "중지","volume" to "볼륨",
        "filters_title" to "필터","reverb" to "리버브","bass" to "베이스 부스트",
        "pitch" to "피치 시프트","reset" to "↺  초기화","info_title" to "작동 방식",
        "settings_title" to "메뉴","lang_label" to "언어","theme_label" to "테마",
        "switch_to_light" to "라이트 테마","switch_to_dark" to "다크 테마",
        "phrases_label" to "시작 시 문구","conn_bar_label" to "연결 표시줄",
        "visual_fx_label" to "시각 효과",
        "sounds_section" to "음향 효과","sound_notif_label" to "알림음",
        "sound_click_label" to "터치음","history_title" to "세션 기록",
        "history_btn" to "기록","no_sessions" to "아직 세션이 없습니다",
        "session_duration" to "기간","session_ping" to "평균 핑",
        "waiting_pc" to "PC 기다리는 중…","connected_pc" to "연결됨","ping_label" to "핑",
        "back" to "뒤로","menu_centered_label" to "내용 가운데 정렬",
        "first_session" to "첫 세션 시작",
        "auto_start" to "자동 시작",
        "auto_start_desc" to "앱을 열면 자동으로 스트리밍 시작",
        "check_update" to "업데이트 확인",
        "check_update_stub" to "아직 업데이트가 없습니다",
        "feedback_btn" to "변경 제안",
        "feedback_title" to "피드백",
        "feedback_hint" to "추가하거나 수정할 내용을 설명하세요",
        "feedback_send" to "보내기",
        "about_app" to "앱 정보",
        "developer" to "개발자",
        "version" to "버전",
        "send_statistics" to "통계 보내기",
        "participate_beta" to "베타 참여",
        "pause_updates" to "업데이트 일시 중지",
        "sync_with_pc" to "PC와 동기화",
        "sync_with_pc_desc" to "PC와 휴대폰 간 테마, 언어 및 기타 설정 자동 동기화",
        "conn_notif_connected_title" to "PC에 연결됨",
        "conn_notif_connected_body" to "오디오를 PC로 스트리밍 중",
        "conn_notif_disconnected_title" to "PC 연결 해제됨",
        "conn_notif_disconnected_body" to "연결이 끊어졌습니다",
        "filters_scale_hint" to "0% = 원본 · 100% = 최대 효과",
        "enhancer_title" to "인한서 (선택)",
        "enhancer_dc" to "DC 필터 (클릭 제거)",
        "enhancer_hp" to "로우컷 80Hz (웅웅거림 제거)",
        "enhancer_limiter" to "리미터 (클리핑 없음)",
        "enhancer_gate" to "게이트 (히스 없는 무음)",
        "eq_title" to "보이스 이퀄라이저",
        "eq_low" to "저역 250Hz (따뜻함)",
        "eq_mid" to "중역 1.5kHz (명료도)",
        "eq_high" to "고역 5kHz (공기감)",
        "eq_hint" to "-100% 감소 · 0 원음 · +100% 증폭",
        "feedback_contact_toggle" to "연락처 남기기",
        "feedback_contact_hint" to "이메일 또는 Telegram @사용자명",
        "mic_permission" to "마이크 권한이 필요합니다. 탭하여 앱 설정을 엽니다.",
        "record_title" to "마이크 녹음",
        "record_active" to "● %d초 / 30초",
        "record_done" to "녹음: %d초",
        "record_auto" to "자동",
        "record_start" to "녹음",
        "record_stop" to "정지",
        "record_play" to "재생",
        "record_no_data" to "녹음 없음",
        "record_hint" to "녹음 중... 스트리밍과 동일하게 효과 적용",
        "raw_hint" to "OFF = 마이크에서 1:1",
    
    )
    private val PT = mapOf(
        "tab_connect" to "Conectar","tab_filters" to "Filtros","tab_info" to "Info","tab_settings" to "Menu",
        "connect_title" to "Conexão","ip_label" to "Endereço IP do PC","ip_hint" to "ex. 192.168.0.10",
        "connect_subtitle" to "Envie o áudio do microfone do telemóvel para o PC de forma limpa e leve",
        "start" to "Iniciar","stop" to "Parar","volume" to "Volume",
        "filters_title" to "Filtros","reverb" to "Reverberação","bass" to "Reforço de graves",
        "pitch" to "Deslocamento de tom","reset" to "↺  redefinir","info_title" to "Como funciona",
        "settings_title" to "Menu","lang_label" to "Idioma","theme_label" to "Tema",
        "switch_to_light" to "Tema claro","switch_to_dark" to "Tema escuro",
        "phrases_label" to "Frases ao iniciar","conn_bar_label" to "Barra de conexão",
        "visual_fx_label" to "Efeitos visuais",
        "sounds_section" to "Efeitos sonoros","sound_notif_label" to "Som de notificação",
        "sound_click_label" to "Som de toque","history_title" to "Histórico de sessões",
        "history_btn" to "Histórico","no_sessions" to "Ainda não há sessões",
        "session_duration" to "Duração","session_ping" to "Ping médio",
        "waiting_pc" to "A aguardar PC…","connected_pc" to "Ligado","ping_label" to "Ping",
        "back" to "Voltar","menu_centered_label" to "Conteúdo centralizado",
        "first_session" to "Fazer primeira sessão",
        "auto_start" to "Início automático",
        "auto_start_desc" to "Iniciar streaming automaticamente ao abrir a app",
        "check_update" to "Verificar atualizações",
        "check_update_stub" to "Sem atualizações por enquanto",
        "feedback_btn" to "Sugerir alteração",
        "feedback_title" to "Feedback",
        "feedback_hint" to "Descreva o que adicionar ou corrigir",
        "feedback_send" to "Enviar",
        "about_app" to "Sobre a app",
        "developer" to "Desenvolvedor",
        "version" to "Versão",
        "send_statistics" to "Enviar estatísticas",
        "participate_beta" to "Participar na versão beta",
        "pause_updates" to "Pausar atualizações",
        "sync_with_pc" to "Sincronizar com PC",
        "sync_with_pc_desc" to "Sincronizar automaticamente tema, idioma e outras configurações entre PC e telemóvel",
        "conn_notif_connected_title" to "Ligado ao PC",
        "conn_notif_connected_body" to "Áudio a ser transmitido para o PC",
        "conn_notif_disconnected_title" to "Desligado do PC",
        "conn_notif_disconnected_body" to "Conexão perdida",
        "filters_scale_hint" to "0% = original · 100% = efeito máximo",
        "enhancer_title" to "Realçador (opcional)",
        "enhancer_dc" to "Filtro DC (remover cliques)",
        "enhancer_hp" to "Corte baixo 80Hz (remover zumbido)",
        "enhancer_limiter" to "Limitador (sem clipping)",
        "enhancer_gate" to "Gate (silêncio sem sopro)",
        "eq_title" to "Equalizador de voz",
        "eq_low" to "Graves 250Hz (calor)",
        "eq_mid" to "Médios 1.5kHz (clareza)",
        "eq_high" to "Agudos 5kHz (ar)",
        "eq_hint" to "-100% atenuar · 0 original · +100% reforçar",
        "feedback_contact_toggle" to "Deixar contacto",
        "feedback_contact_hint" to "Email ou @usuario do Telegram",
        "mic_permission" to "Permissão de microfone necessária. Toque para abrir as definições.",
        "record_title" to "Gravação do microfone",
        "record_active" to "● %ds / 30s",
        "record_done" to "Gravação: %ds",
        "record_auto" to "Auto",
        "record_start" to "Gravar",
        "record_stop" to "Parar",
        "record_play" to "Ouvir",
        "record_no_data" to "Sem gravação",
        "record_hint" to "A gravar... efeitos aplicados como em streaming",
        "raw_hint" to "OFF = 1:1 do microfone",
    
    )
    private val UK = mapOf(
        "tab_connect" to "Підключення","tab_filters" to "Фільтри","tab_info" to "Інфо","tab_settings" to "Меню",
        "connect_title" to "Підключення","ip_label" to "IP адреса ПК","ip_hint" to "наприклад 192.168.0.10",
        "connect_subtitle" to "Передавай мікрофон телефона на ПК швидко та без зайвого шуму",
        "start" to "Почати","stop" to "Зупинити","volume" to "Гучність",
        "filters_title" to "Фільтри","reverb" to "Реверберація","bass" to "Підсилення басів",
        "pitch" to "Зсув тону","reset" to "↺  скинути","info_title" to "Як це працює",
        "settings_title" to "Меню","lang_label" to "Мова","theme_label" to "Тема",
        "switch_to_light" to "Світла тема","switch_to_dark" to "Темна тема",
        "phrases_label" to "Фрази при запуску","conn_bar_label" to "Смуга з'єднання",
        "visual_fx_label" to "Візуальні ефекти",
        "sounds_section" to "Звукові ефекти","sound_notif_label" to "Звук сповіщення",
        "sound_click_label" to "Звук натискань","history_title" to "Історія сесій",
        "history_btn" to "Історія","no_sessions" to "Сесій ще немає",
        "session_duration" to "Тривалість","session_ping" to "Середній пінг",
        "waiting_pc" to "Очікування ПК…","connected_pc" to "Підключено","ping_label" to "Пінг",
        "back" to "Назад","menu_centered_label" to "Вміст по центру",
        "first_session" to "Здійснити першу сесію",
        "auto_start" to "Автостарт",
        "auto_start_desc" to "Автоматично починати стрімінг при відкритті",
        "check_update" to "Перевірити оновлення",
        "check_update_stub" to "Поки що немає оновлень",
        "feedback_btn" to "Запропонувати зміну",
        "feedback_title" to "Зворотний зв'язок",
        "feedback_hint" to "Опиши, що додати або виправити",
        "feedback_send" to "Надіслати",
        "about_app" to "Про додаток",
        "developer" to "Розробник",
        "version" to "Версія",
        "send_statistics" to "Надсилання статистики",
        "participate_beta" to "Участь у бета-версіях",
        "pause_updates" to "Призупинити оновлення",
        "sync_with_pc" to "Синхронізація з ПК",
        "sync_with_pc_desc" to "Автоматично синхронізувати тему, мову та інші налаштування між ПК і телефоном",
        "conn_notif_connected_title" to "Підключено до ПК",
        "conn_notif_connected_body" to "Аудіо передається на комп'ютер",
        "conn_notif_disconnected_title" to "Відключено від ПК",
        "conn_notif_disconnected_body" to "З'єднання втрачено",
        "filters_scale_hint" to "0% = оригінал · 100% = максимум",
        "enhancer_title" to "Покращувач (опційно)",
        "enhancer_dc" to "DC-фільтр (прибрати клацання)",
        "enhancer_hp" to "Обрізка гулу 80Гц",
        "enhancer_limiter" to "Лімітер (без хрипу)",
        "enhancer_gate" to "Гейт (тиша без шипіння)",
        "eq_title" to "Еквалайзер голосу",
        "eq_low" to "Низькі 250Гц (тепло)",
        "eq_mid" to "Середина 1.5кГц (розбірливість)",
        "eq_high" to "Високі 5кГц (повітря)",
        "eq_hint" to "-100% приглушити · 0 оригінал · +100% підсилити",
        "feedback_contact_toggle" to "Залишити контактні дані",
        "feedback_contact_hint" to "Email або @username у Telegram",
        "mic_permission" to "Потрібен дозвіл на мікрофон. Натисніть, щоб відкрити налаштування додатку.",
        "record_title" to "Запис мікрофона",
        "record_active" to "● %dс / 30с",
        "record_done" to "Запис: %dс",
        "record_auto" to "Авто",
        "record_start" to "Запис",
        "record_stop" to "Стоп",
        "record_play" to "Прослухати",
        "record_no_data" to "Немає запису",
        "record_hint" to "Йде запис... ефекти як при стрімі",
        "raw_hint" to "OFF = 1-в-1 з мікрофона",
    
    )
    private val EN = mapOf(
        "tab_connect" to "Connect","tab_filters" to "Filters","tab_info" to "Info","tab_settings" to "Menu",
        "connect_title" to "Connection","ip_label" to "PC IP address","ip_hint" to "e.g. 192.168.0.10",
        "connect_subtitle" to "Send your phone mic to the PC with a cleaner, lighter flow",
        "start" to "Start","stop" to "Stop","volume" to "Volume",
        "filters_title" to "Filters","reverb" to "Reverb","bass" to "Bass boost",
        "pitch" to "Pitch shift","reset" to "↺  reset","info_title" to "How it works",
        "settings_title" to "Menu","lang_label" to "Language","theme_label" to "Theme",
        "switch_to_light" to "Light theme","switch_to_dark" to "Dark theme",
        "phrases_label" to "Phrases on start","conn_bar_label" to "Connection bar",
        "visual_fx_label" to "Visual effects",
        "sounds_section" to "Sound effects","sound_notif_label" to "Notification sound",
        "sound_click_label" to "Tap sounds","history_title" to "Session history",
        "history_btn" to "History","no_sessions" to "No sessions yet",
        "session_duration" to "Duration","session_ping" to "Avg ping",
        "waiting_pc" to "Waiting for PC…",
        "connected_pc" to "Connected",
        "ping_label" to "Ping",
        "back" to "Back","menu_centered_label" to "Center content",
        "first_session" to "Make first session",
        "auto_start" to "Auto-start",
        "auto_start_desc" to "Start streaming automatically when app opens",
        "check_update" to "Check for updates",
        "check_update_stub" to "No updates yet",
        "feedback_btn" to "Suggest a change",
        "feedback_title" to "Feedback",
        "feedback_hint" to "Describe what to add or fix",
        "feedback_send" to "Send",
        "about_app" to "About app",
        "developer" to "Developer",
        "version" to "Version",
        "send_statistics" to "Send statistics",
        "participate_beta" to "Participate in beta",
        "pause_updates" to "Pause updates",
        "sync_with_pc" to "Sync with PC",
        "sync_with_pc_desc" to "Automatically sync theme, language and other settings between PC and phone",
        "conn_notif_connected_title" to "Connected to PC",
        "conn_notif_connected_body" to "Audio streaming to your PC",
        "conn_notif_disconnected_title" to "Disconnected from PC",
        "conn_notif_disconnected_body" to "Connection lost",
        "filters_scale_hint" to "0% = original · 100% = max effect",
        "enhancer_title" to "Enhancer (optional)",
        "enhancer_dc" to "DC filter (remove clicks)",
        "enhancer_hp" to "Low-cut 80Hz (remove rumble)",
        "enhancer_limiter" to "Limiter (no clipping)",
        "enhancer_gate" to "Gate (silence without hiss)",
        "eq_title" to "Voice equalizer",
        "eq_low" to "Low 250Hz (warmth)",
        "eq_mid" to "Mid 1.5kHz (clarity)",
        "eq_high" to "High 5kHz (air)",
        "eq_hint" to "-100% cut · 0 original · +100% boost",
        "feedback_contact_toggle" to "Leave contact info",
        "feedback_contact_hint" to "Email or Telegram @username",
        "mic_permission" to "Microphone permission required. Tap to open app settings.",
        "record_title" to "Mic recording",
        "record_active" to "● %ds / 30s",
        "record_done" to "Recording: %ds",
        "record_auto" to "Auto",
        "record_start" to "Record",
        "record_stop" to "Stop",
        "record_play" to "Play",
        "record_no_data" to "No recording",
        "record_hint" to "Recording... effects apply as when streaming",
        "raw_hint" to "OFF = 1:1 from mic",
    
    )
}

// ─── Theme colors ─────────────────────────────────────────────────────────────

// Единый тикер фазы для декоративных градиентов и анимаций иконок.
// Раньше он обновлялся раз в 100 мс (10 fps) — из-за этого шестерёнка и
// «дыхание» иконок дёргались. Теперь фаза пересчитывается каждый кадр,
// поэтому на 120-Гц экране анимации идут в 120 fps (и в 60 fps на 60-Гц).
// Когда эффекты выключены, тикер не работает вообще — батарея не тратится.
@Composable
private fun rememberPhaseTick(enabled: Boolean, stepPerSecond: Float, period: Float): Float {
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            phase = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (last != 0L) {
                    phase = (phase + (now - last) / 1000f * stepPerSecond) % period
                }
                last = now
            }
        }
    }
    return phase
}

// Читает latencyMs только внутри себя, чтобы обновление пинга (2 раза/сек)
// не пересобирало весь ConnectScreen.
@Composable
private fun PingBadgeSlot(latencyMs: State<Long>, isDark: Boolean, effectsEnabled: Boolean = true) {
    val value = latencyMs.value
    AnimatedVisibility(
        visible = value >= 0L,
        enter = fadeIn(tween(if (effectsEnabled) 300 else 0)),
        exit = fadeOut(tween(if (effectsEnabled) 200 else 0))
    ) {
        PingBadge(value, isDark)
    }
}

// Стабильный (для Compose) контейнер цветов: Map — нестабильный тип, из-за него
// пересобиралось всё поддерево при каждой рекомпозиции родителя.
@Immutable
class NelevColors internal constructor(private val values: Map<String, Color>) {
    operator fun get(key: String): Color? = values[key]
}

@Composable
fun nelevColors(isDark: Boolean, effectsEnabled: Boolean = true): NelevColors {
    val t by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 500 else 0, easing = FastOutSlowInEasing),
        label = "themeProgress"
    )
    fun lerp(a: Color, b: Color): Color = Color(
        a.red   + (b.red   - a.red)   * t,
        a.green + (b.green - a.green) * t,
        a.blue  + (b.blue  - a.blue)  * t,
        a.alpha + (b.alpha - a.alpha) * t
    )
    val lightBg    = Color(0xFFFFFBFF)
    val darkBg     = Color(0xFF050505)
    val lightCard  = Color(0xFFFFF7FC)
    val darkCard   = Color(0xFF101010)
    val lightBorder = Color(0xFFF1DEE9)
    val darkBorder = Color(0xFF242424)
    val lightText  = Color(0xFF8E4F6E)
    val darkText   = Color(0xFFF5F5F2)
    val lightMuted = Color(0xFFB88EA2)
    val darkMuted  = Color(0xFF6E6E6E)
    val lightNavBg = Color(0xFFFFF9FE)
    val darkNavBg  = Color(0xFF0C0C0C)
    val lightConnBase   = Color(0xFFBBBBBB)
    val darkConnBase    = Color(0xFF555555)
    val lightConnActive = Color(0xFFE79BC0)
    val darkConnActive  = Color(0xFFFFFFFF)
    val lightAccent     = Color(0xFFE79BC0)
    val darkAccent      = Color(0xFFF5F5F2)
    return remember(t) {
        NelevColors(mapOf(
            "bg" to lerp(lightBg, darkBg),
            "card" to lerp(lightCard, darkCard),
            "border" to lerp(lightBorder, darkBorder),
            "text" to lerp(lightText, darkText),
            "muted" to lerp(lightMuted, darkMuted),
            "navBg" to lerp(lightNavBg, darkNavBg),
            "connBase" to lerp(lightConnBase, darkConnBase),
            "connActive" to lerp(lightConnActive, darkConnActive),
            "accent" to lerp(lightAccent, darkAccent)
        ))
    }
}

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    companion object {
        var micGranted = false
        @Volatile var appLang: String = "en"
        @Volatile var autoStartPcIp: String = ""
        private var autoStartListener: Thread? = null
        @Volatile var pendingPcSettings: Map<String, Any>? = null
        @Volatile var suppressNextMuteSend: Boolean = false
        private var pcSyncListener: Thread? = null

        fun startAutoStartListener() {
            if (autoStartListener?.isAlive == true) return
            autoStartListener = Thread {
                val buf = ByteArray(256)
                try {
                    val sock = java.net.DatagramSocket(55003)
                    sock.soTimeout = 5000
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val p = java.net.DatagramPacket(buf, buf.size)
                            sock.receive(p)
                            if (p.length >= 4) {
                                val magic = String(buf, 0, 4, Charsets.US_ASCII)
                                if (magic == "NSTR") {
                                    val pcIp = p.address?.hostAddress?.let { a ->
                                        if (a.startsWith("::ffff:")) a.substring(7) else a
                                    } ?: continue
                                    autoStartPcIp = pcIp
                                }
                            }
                        } catch (_: java.net.SocketTimeoutException) {}
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                start()
            }
        }

        fun startPcSyncListener() {
            if (pcSyncListener?.isAlive == true) return
            pcSyncListener = Thread {
                val buf = ByteArray(4096)
                try {
                    val sock = java.net.DatagramSocket(55002)
                    sock.soTimeout = 5000
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            val p = java.net.DatagramPacket(buf, buf.size)
                            sock.receive(p)
                            if (p.length < 4) continue
                            if (String(buf, 0, 4) != "NSYN") continue
                            val raw = String(buf, 4, p.length - 4)
                            val map = mutableMapOf<String, Any>()
                            raw.trimStart('{').trimEnd('}').split(",").forEach { pair ->
                                val kv = pair.split(":", limit = 2)
                                if (kv.size == 2) {
                                    val k = kv[0].trim().removeSurrounding("\"")
                                    val v = kv[1].trim()
                                    map[k] = if (v == "true" || v == "false") v == "true" else v.removeSurrounding("\"")
                                }
                            }
                            pendingPcSettings = map
                        } catch (_: java.net.SocketTimeoutException) {}
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                start()
            }
        }
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        micGranted = r[Manifest.permission.RECORD_AUDIO] == true
    }

    // QR scan result callback - set from Compose
    private var onQrResult: ((String) -> Unit)? = null

    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_QR)?.let { scannedIp ->
                val normalized = scannedIp
                    .removePrefix("nelev://")
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .trim()
                onQrResult?.invoke(normalized)
            }
        }
    }

    private val cameraPermLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            qrScanLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
        }
    }

    fun launchQrScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            qrScanLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    // ── 120 Гц: просим систему держать максимальную частоту обновления ──
    // Compose рисует с частотой экрана, поэтому без этого запроса панель
    // может остаться на 60 Гц, и анимации будут выглядеть «половинчатыми».
    // Берём самый быстрый режим с тем же разрешением, что текущий, но не
    // выше 120 Гц (чтобы не гнать 144+ и не жечь батарею зря).
    private fun enableMaxRefreshRate() {
        val targetHz = 120f
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display: Display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .filter { it.refreshRate <= targetHz + 0.5f }
                .maxByOrNull { it.refreshRate }
            window.attributes = window.attributes.apply {
                // Подсказка «хочу максимальную частоту» — работает и на старых прошивках
                @Suppress("DEPRECATION")
                preferredRefreshRate = targetHz
                // Конкретный режим экрана: тот же размер, максимальная частота <= 120 Гц
                if (best != null && best.refreshRate > current.refreshRate + 0.5f) {
                    preferredDisplayModeId = best.modeId
                }
            }
        } catch (_: Throwable) { }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(NelevService.CHANNEL_ID, "Nelev Mic Service", NotificationManager.IMPORTANCE_LOW))
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // Разрешения запрашиваются на отдельном этапе онбординга
        ClickPlayer.init(this) // lazy — just sets up SoundPool, actual load is async
        startAutoStartListener()
        startPcSyncListener()
        // Системный «назад» никогда не закрывает приложение: экраны обрабатывают его сами
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })
        setContent {
            val ctx = LocalContext.current
            val appSettings = remember { mutableStateOf(SettingsStore.load(ctx)) }
            var splashDone by remember { mutableStateOf(false) }
            var uiAlpha    by remember { mutableStateOf(0f) }
            val effectsEnabled = appSettings.value.visualEffectsEnabled
            // Версия прошлого запуска: файл last_run_version.txt
            val launchInfo = remember { VersionHistory.recordLaunch(ctx) }
            var whatsNewVersion by remember { mutableStateOf<String?>(null) }
            var whatsNewNotes by remember { mutableStateOf<String?>(null) }
            var whatsNewChecked by remember { mutableStateOf(false) }

            // Автоматическая отправка статистики при запуске (раз в 2 дня)
            LaunchedEffect(Unit) {
                if (appSettings.value.sendStatistics) {
                    val currentTime = System.currentTimeMillis()
                    val lastSent = appSettings.value.lastStatisticsSentMs
                    val twoDaysMs = 2 * 24 * 60 * 60 * 1000L

                    // Отправляем только если прошло больше 2 дней по часам телефона
                    if (currentTime - lastSent >= twoDaysMs) {
                        TelegramSender.sendStatistics(ctx, forceUpdate = false)
                    }
                }
            }

            NelevTheme(darkTheme = appSettings.value.isDark) {
                val appBg by animateColorAsState(
                    if (appSettings.value.isDark) Color(0xFF060606) else Color(0xFFFFFCFF),
                    tween(if (effectsEnabled) 250 else 0) // OPTIMIZATION: 500ms → 250ms - быстрее смена темы
                )
                val rootColors = nelevColors(appSettings.value.isDark, effectsEnabled)

                LaunchedEffect(Unit) {
                    delay(20)
                    val start = withFrameMillis { it }
                    var lastFrame = start
                    while (true) {
                        val now = withFrameMillis { it }
                        if (now - lastFrame >= 16) {
                            val t = ((now - start).toFloat() / 500f).coerceIn(0f, 1f)
                            uiAlpha = t
                            if (t >= 1f) break
                            lastFrame = now
                        }
                    }
                }

                // После заставки: если версия изменилась с прошлого запуска — «Что нового»
                LaunchedEffect(splashDone, uiAlpha, appSettings.value.isFirstLaunch) {
                    if (whatsNewChecked || !splashDone || uiAlpha < 1f || appSettings.value.isFirstLaunch) {
                        return@LaunchedEffect
                    }
                    whatsNewChecked = true
                    if (!launchInfo.updated) return@LaunchedEffect
                    val currentLang = appSettings.value.language
                    val notes = VersionHistory.changelog(ctx, launchInfo.currentCode, currentLang)
                        ?: Updater.changelogFor(ctx, launchInfo.currentCode)
                    whatsNewNotes = notes
                    whatsNewVersion = APP_VERSION
                }
                
                // Обновление changelog при смене языка
                LaunchedEffect(appSettings.value.language, whatsNewVersion) {
                    if (whatsNewVersion != null) {
                        val currentLang = appSettings.value.language
                        val notes = VersionHistory.changelog(ctx, launchInfo.currentCode, currentLang)
                            ?: Updater.changelogFor(ctx, launchInfo.currentCode)
                        whatsNewNotes = notes
                    }
                }

                Box(Modifier.fillMaxSize().background(appBg)) {
                    if (splashDone || uiAlpha > 0f) {
                        Box(Modifier.fillMaxSize().alpha(uiAlpha)) {
                            if (appSettings.value.isFirstLaunch) {
                                OnboardingScreen(appSettings) { }
                            } else {
                                NelevMainScreen(
                                    appSettings,
                                    startService = { NelevService.targetIp = it.trim(); ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, NelevService::class.java)) },
                                    stopService  = { stopService(Intent(this@MainActivity, NelevService::class.java)) },
                                    onScanQr = { callback ->
                                        this@MainActivity.onQrResult = callback
                                        this@MainActivity.launchQrScanner()
                                    }
                                )
                            }
                        }
                    }
                    if (!splashDone || uiAlpha < 1f) {
                        NelevSplash(isDark = appSettings.value.isDark) { splashDone = true }
                    }

                    whatsNewVersion?.let { version ->
                        if (splashDone && uiAlpha >= 1f) {
                            WhatsNewDialog(
                                versionName = version,
                                changelog = whatsNewNotes,
                                colors = rootColors,
                                lang = appSettings.value.language,
                                isDark = appSettings.value.isDark,
                                onDismiss = { whatsNewVersion = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Onboarding (первый запуск) ───────────────────────────────────────────────

private val onboardingLangOrder = listOf("en", "ru", "es", "zh", "ja", "ko", "pt", "uk")

private fun onboardingGreeting(lang: String): String = when (lang) {
    "ru" -> "Привет"; "es" -> "Hola"; "zh" -> "你好"; "ja" -> "こんにちは"
    "ko" -> "안녕하세요"; "pt" -> "Olá"; "uk" -> "Привіт"; else -> "Hello"
}

private fun onboardingSubtitle(lang: String): String = when (lang) {
    "ru" -> "Давайте помогу вам настроить приложение"
    "es" -> "Déjame ayudarte a configurar la aplicación"
    "zh" -> "让我帮你设置应用"
    "ja" -> "アプリの設定をお手伝いします"
    "ko" -> "앱 설정을 도와드릴게요"
    "pt" -> "Deixe-me ajudar você a configurar o app"
    "uk" -> "Допоможу вам налаштувати застосунок"
    else -> "Let me help you set up the app"
}

private fun onboardingStart(lang: String): String = when (lang) {
    "ru" -> "Начать"; "es" -> "Empezar"; "zh" -> "开始"; "ja" -> "はじめる"
    "ko" -> "시작"; "pt" -> "Começar"; "uk" -> "Почати"; else -> "Start"
}

private fun onboardingNext(lang: String): String = when (lang) {
    "ru" -> "Далее"; "es" -> "Siguiente"; "zh" -> "下一步"; "ja" -> "次へ"
    "ko" -> "다음"; "pt" -> "Próximo"; "uk" -> "Далі"; else -> "Next"
}

private fun onboardingThemeTitle(lang: String): String = when (lang) {
    "ru" -> "Тема оформления"; "es" -> "Tema"; "zh" -> "主题"; "ja" -> "テーマ"
    "ko" -> "테마"; "pt" -> "Tema"; "uk" -> "Тема оформлення"; else -> "Appearance"
}

private fun onboardingLight(lang: String): String = when (lang) {
    "ru" -> "Светлая"; "es" -> "Claro"; "zh" -> "浅色"; "ja" -> "ライト"
    "ko" -> "라이트"; "pt" -> "Claro"; "uk" -> "Світла"; else -> "Light"
}

private fun onboardingDark(lang: String): String = when (lang) {
    "ru" -> "Тёмная"; "es" -> "Oscuro"; "zh" -> "深色"; "ja" -> "ダーク"
    "ko" -> "다크"; "pt" -> "Escuro"; "uk" -> "Темна"; else -> "Dark"
}

private fun onboardingSourceLabel(lang: String): String = when (lang) {
    "ru" -> "Откуда вы узнали о приложении?"
    "es" -> "¿Cómo conociste la aplicación?"
    "zh" -> "你是如何知道这个应用的？"
    "ja" -> "このアプリをどこで知りましたか？"
    "ko" -> "이 앱을 어디서 알게 되었나요?"
    "pt" -> "Como você conheceu o app?"
    "uk" -> "Звідки ви дізналися про застосунок?"
    else -> "Where did you hear about the app?"
}

private fun onboardingSend(lang: String): String = when (lang) {
    "ru" -> "Отправить"; "es" -> "Enviar"; "zh" -> "发送"; "ja" -> "送信"
    "ko" -> "보내기"; "pt" -> "Enviar"; "uk" -> "Надіслати"; else -> "Send"
}

private fun onboardingSkip(lang: String): String = when (lang) {
    "ru" -> "Пропустить"; "es" -> "Omitir"; "zh" -> "跳过"; "ja" -> "スキップ"
    "ko" -> "건너뛰기"; "pt" -> "Pular"; "uk" -> "Пропустити"; else -> "Skip"
}

private fun onboardingPlaceholder(lang: String): String = when (lang) {
    "ru" -> "Например: Telegram, YouTube, друг…"
    "es" -> "Por ejemplo: Telegram, YouTube, un amigo…"
    "zh" -> "例如：Telegram、YouTube、朋友…"
    "ja" -> "例：Telegram、YouTube、友達…"
    "ko" -> "예: Telegram, YouTube, 친구…"
    "pt" -> "Ex.: Telegram, YouTube, um amigo…"
    "uk" -> "Наприклад: Telegram, YouTube, друг…"
    else -> "For example: Telegram, YouTube, a friend…"
}

private fun onboardingPermTitle(lang: String): String = when (lang) {
    "ru" -> "Разрешения"; "es" -> "Permisos"; "zh" -> "权限"; "ja" -> "権限"
    "ko" -> "권한"; "pt" -> "Permissões"; "uk" -> "Дозволи"; else -> "Permissions"
}

private fun onboardingPermHint(lang: String): String = when (lang) {
    "ru" -> "Чтобы всё работало как надо"
    "es" -> "Para que todo funcione correctamente"
    "zh" -> "让一切正常运行"
    "ja" -> "すべてが正しく動くために"
    "ko" -> "모든 기능이 제대로 작동하도록"
    "pt" -> "Para tudo funcionar corretamente"
    "uk" -> "Щоб усе працювало як слід"
    else -> "So everything works as it should"
}

private fun onboardingMicTitle(lang: String): String = when (lang) {
    "ru" -> "Микрофон"; "es" -> "Micrófono"; "zh" -> "麦克风"; "ja" -> "マイク"
    "ko" -> "마이크"; "pt" -> "Microfone"; "uk" -> "Мікрофон"; else -> "Microphone"
}

private fun onboardingMicDesc(lang: String): String = when (lang) {
    "ru" -> "Нужен для передачи голоса на компьютер"
    "es" -> "Necesario para transmitir tu voz al PC"
    "zh" -> "用于把声音传到电脑"
    "ja" -> "音声をPCに送るために必要です"
    "ko" -> "음성을 PC로 전송하는 데 필요합니다"
    "pt" -> "Necessário para transmitir sua voz ao PC"
    "uk" -> "Потрібен для передачі голосу на комп'ютер"
    else -> "Required to send your voice to the PC"
}

private fun onboardingNotifTitle(lang: String): String = when (lang) {
    "ru" -> "Уведомления"; "es" -> "Notificaciones"; "zh" -> "通知"; "ja" -> "通知"
    "ko" -> "알림"; "pt" -> "Notificações"; "uk" -> "Сповіщення"; else -> "Notifications"
}

private fun onboardingNotifDesc(lang: String): String = when (lang) {
    "ru" -> "Управляйте передачей прямо из уведомления"
    "es" -> "Controla la transmisión desde la notificación"
    "zh" -> "可直接从通知中控制传输"
    "ja" -> "通知から送信を操作できます"
    "ko" -> "알림에서 전송을 제어할 수 있습니다"
    "pt" -> "Controle a transmissão pela notificação"
    "uk" -> "Керуйте передачею прямо зі сповіщення"
    else -> "Control streaming right from the notification"
}

private fun onboardingAllow(lang: String): String = when (lang) {
    "ru" -> "Разрешить"; "es" -> "Permitir"; "zh" -> "允许"; "ja" -> "許可"
    "ko" -> "허용"; "pt" -> "Permitir"; "uk" -> "Дозволити"; else -> "Allow"
}

private fun onboardingGranted(lang: String): String = when (lang) {
    "ru" -> "Разрешено"; "es" -> "Permitido"; "zh" -> "已允许"; "ja" -> "許可済み"
    "ko" -> "허용됨"; "pt" -> "Permitido"; "uk" -> "Дозволено"; else -> "Granted"
}

@Composable
private fun OnboardingButton(
    text: String, container: Color, content: Color, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.54f).height(44.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun onboardingLocale(lang: String): Locale = when (lang) {
    "ru" -> Locale("ru", "RU")
    "es" -> Locale("es", "ES")
    "zh" -> Locale.SIMPLIFIED_CHINESE
    "ja" -> Locale.JAPANESE
    "ko" -> Locale.KOREAN
    "pt" -> Locale("pt", "PT")
    "uk" -> Locale("uk", "UA")
    else -> Locale.US
}

@Composable
fun OnboardingScreen(appSettings: MutableState<AppSettings>, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val effectsEnabled = appSettings.value.visualEffectsEnabled

    var step by remember { mutableStateOf(0) }
    var langIndex by remember {
        mutableStateOf(onboardingLangOrder.indexOf(appSettings.value.language).coerceAtLeast(0))
    }
    val currentLang = onboardingLangOrder[langIndex]
    var themeDark by remember { mutableStateOf(appSettings.value.isDark) }
    var sourceText by remember { mutableStateOf("") }

    // Плавная смена всей палитры онбординга вместе с выбранной темой
    val themeT by animateFloatAsState(
        targetValue = if (themeDark) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 420 else 0, easing = FastOutSlowInEasing),
        label = "onboarding_theme"
    )
    fun mix(a: Color, b: Color): Color = Color(
        a.red + (b.red - a.red) * themeT,
        a.green + (b.green - a.green) * themeT,
        a.blue + (b.blue - a.blue) * themeT,
        a.alpha + (b.alpha - a.alpha) * themeT
    )
    val bg = mix(Color(0xFFFFFBFF), Color(0xFF050505))
    val card = mix(Color(0xFFFFF7FC), Color(0xFF101010))
    val border = mix(Color(0xFFF1DEE9), Color(0xFF242424))
    val text = mix(Color(0xFF8E4F6E), Color(0xFFF5F5F2))
    val muted = mix(Color(0xFFB88EA2), Color(0xFF6E6E6E))
    val accent = mix(Color(0xFFE79BC0), Color(0xFFF5F5F2))
    val buttonContent = mix(Color.White, Color(0xFF060606))

    // ── Озвучка приветствия на языке, который показан ──
    val ttsHolder = remember {
        object {
            @Volatile var ready = false
            var engine: android.speech.tts.TextToSpeech? = null
            var langCode: String = ""
        }
    }
    var pendingSpeak by remember { mutableStateOf(-1) }
    fun speakGreeting(index: Int) {
        val engine = ttsHolder.engine
        if (engine == null || !ttsHolder.ready) {
            pendingSpeak = index
            return
        }
        val code = onboardingLangOrder[index]
        try {
            if (ttsHolder.langCode != code) {
                engine.language = onboardingLocale(code)
                ttsHolder.langCode = code
            }
            engine.speak(
                onboardingGreeting(code),
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                "nelev_hi_${code}_${System.currentTimeMillis()}"
            )
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        var tts: android.speech.tts.TextToSpeech? = null
        try {
            tts = android.speech.tts.TextToSpeech(ctx) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    try { tts?.setSpeechRate(1f) } catch (_: Exception) {}
                    ttsHolder.ready = true
                }
            }
            ttsHolder.engine = tts
        } catch (_: Exception) {}
        onDispose {
            ttsHolder.ready = false
            try { tts?.stop() } catch (_: Exception) {}
            try { tts?.shutdown() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        var waited = 0
        while (!ttsHolder.ready && waited < 4000) { delay(30); waited += 30 }
        if (ttsHolder.ready) {
            speakGreeting(if (pendingSpeak >= 0) pendingSpeak else langIndex)
            pendingSpeak = -1
        }
    }

    // ── Разрешения (отдельный шаг онбординга) ──
    var permRev by remember { mutableStateOf(0) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permRev++ }
    val hasMic = remember(permRev) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasNotifications = remember(permRev) {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true
    }

    // ── Проверка версии (перед вопросом «откуда узнали») ──
    var onbVersionChecking by remember { mutableStateOf(false) }
    var onbVersionFailed by remember { mutableStateOf(false) }
    var onbUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var onbVersionChecked by remember { mutableStateOf(false) }
    var showOnbUpdateDialog by remember { mutableStateOf(false) }
    val onbScope = rememberCoroutineScope()

    fun checkVersionOnboarding() {
        if (onbVersionChecking) return
        onbVersionChecking = true
        onbVersionFailed = false
        onbScope.launch {
            Updater.check(ctx, respectSkip = false)
                .onSuccess { onbUpdateInfo = it }
                .onFailure { onbVersionFailed = true }
            onbVersionChecked = true
            onbVersionChecking = false
        }
    }

    LaunchedEffect(step) {
        if (step == 3 && !onbVersionChecked) checkVersionOnboarding()
    }

    BackHandler {
        if (step > 0) step -= 1
    }

    fun finish(sendSource: Boolean) {
        if (sendSource && sourceText.isNotBlank()) {
            try { TelegramSender.sendFeedback(ctx, "Откуда узнали: ${sourceText.trim()}") } catch (_: Exception) {}
        }
        val updated = appSettings.value.copy(
            language = currentLang,
            isDark = themeDark,
            isFirstLaunch = false
        )
        try { SettingsStore.save(ctx, updated) } catch (_: Exception) {}
        appSettings.value = updated
        onFinish()
    }

    Box(Modifier.fillMaxSize().background(bg)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)))
                }
            },
            label = "onboarding_step"
        ) { s ->
            when (s) {
                0 -> {
                    var dragAccum by remember { mutableStateOf(0f) }
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(currentLang) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { dragAccum = 0f },
                                        onDragEnd = {
                                            if (dragAccum < -60f) {
                                                langIndex = (langIndex + 1) % onboardingLangOrder.size
                                                speakGreeting(langIndex)
                                            } else if (dragAccum > 60f) {
                                                langIndex = (langIndex + onboardingLangOrder.size - 1) % onboardingLangOrder.size
                                                speakGreeting(langIndex)
                                            }
                                            dragAccum = 0f
                                        }
                                    ) { change, dragAmount ->
                                        dragAccum += dragAmount
                                        change.consume()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OnboardingArrow("‹", accent, direction = -1, animated = true) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                langIndex = (langIndex + onboardingLangOrder.size - 1) % onboardingLangOrder.size
                                speakGreeting(langIndex)
                            }
                            Box(
                                Modifier.weight(1f).clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    speakGreeting(langIndex)
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = langIndex,
                                    transitionSpec = {
                                        (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(260))) togetherWith
                                            (slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(tween(180)))
                                    },
                                    label = "onboarding_greeting"
                                ) { idx ->
                                    Text(
                                        onboardingGreeting(onboardingLangOrder[idx]),
                                        fontSize = 52.sp,
                                        lineHeight = 58.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = text,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            OnboardingArrow("›", accent, direction = 1, animated = true) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                langIndex = (langIndex + 1) % onboardingLangOrder.size
                                speakGreeting(langIndex)
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                        AnimatedContent(
                            targetState = currentLang,
                            transitionSpec = {
                                (fadeIn(tween(320)) + slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                                    (fadeOut(tween(200)) + slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { -it / 4 })
                            },
                            label = "onboarding_subtitle"
                        ) { l ->
                            Text(
                                onboardingSubtitle(l),
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                color = muted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.weight(1.25f))
                        OnboardingButton(onboardingStart(currentLang), accent, buttonContent) {
                            if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                            step = 1
                        }
                        Spacer(Modifier.height(56.dp))
                    }
                }

                1 -> {
                    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
                        OnboardingArrow(
                            "←", muted, direction = -1, animated = false,
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                            step = 0
                        }
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.weight(1f))
                            AnimatedContent(
                                targetState = currentLang,
                                transitionSpec = {
                                    (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 4 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -it / 4 })
                                },
                                label = "onboarding_theme_title"
                            ) { l ->
                                Text(
                                    onboardingThemeTitle(l),
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = text,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(26.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                OnboardingThemeCard(
                                    label = onboardingLight(currentLang),
                                    selected = !themeDark,
                                    isDarkPreview = false,
                                    text = text, muted = muted, accent = accent, border = border, card = card,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                    themeDark = false
                                    appSettings.value = appSettings.value.copy(isDark = false)
                                }
                                OnboardingThemeCard(
                                    label = onboardingDark(currentLang),
                                    selected = themeDark,
                                    isDarkPreview = true,
                                    text = text, muted = muted, accent = accent, border = border, card = card,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                    themeDark = true
                                    appSettings.value = appSettings.value.copy(isDark = true)
                                }
                            }
                            Spacer(Modifier.weight(1.25f))
                            OnboardingButton(onboardingNext(currentLang), accent, buttonContent) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                step = 2
                            }
                            Spacer(Modifier.height(56.dp))
                        }
                    }
                }

                2 -> {
                    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
                        OnboardingArrow(
                            "←", muted, direction = -1, animated = false,
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                            step = 1
                        }
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.weight(1f))
                            AnimatedContent(
                                targetState = currentLang,
                                transitionSpec = {
                                    (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 4 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -it / 4 })
                                },
                                label = "onboarding_perm_title"
                            ) { l ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        onboardingPermTitle(l),
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = text,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        onboardingPermHint(l),
                                        fontSize = 15.sp,
                                        color = muted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(Modifier.height(26.dp))
                            OnboardingPermissionCard(
                                icon = IconsEx.Mic,
                                title = onboardingMicTitle(currentLang),
                                desc = onboardingMicDesc(currentLang),
                                granted = hasMic,
                                allowLabel = onboardingAllow(currentLang),
                                grantedLabel = onboardingGranted(currentLang),
                                text = text, muted = muted, accent = accent, border = border, card = card,
                                accentContent = buttonContent
                            ) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                            Spacer(Modifier.height(12.dp))
                            OnboardingPermissionCard(
                                icon = Icons.Filled.Notifications,
                                title = onboardingNotifTitle(currentLang),
                                desc = onboardingNotifDesc(currentLang),
                                granted = hasNotifications,
                                allowLabel = onboardingAllow(currentLang),
                                grantedLabel = onboardingGranted(currentLang),
                                text = text, muted = muted, accent = accent, border = border, card = card,
                                accentContent = buttonContent
                            ) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                } else {
                                    permRev++
                                }
                            }
                            Spacer(Modifier.weight(1.25f))
                            OnboardingButton(onboardingNext(currentLang), accent, buttonContent) {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                step = 3
                            }
                            Spacer(Modifier.height(56.dp))
                        }
                    }
                }

                3 -> {
                    val onbColors = remember(bg, card, text, muted, border, accent) {
                        NelevColors(mapOf(
                            "bg" to bg,
                            "card" to card,
                            "text" to text,
                            "muted" to muted,
                            "border" to border,
                            "accent" to accent
                        ))
                    }
                    Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
                        OnboardingArrow(
                            "←", muted, direction = -1, animated = false,
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                            step = 2
                        }
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                UpdateText.get("version_check_title", currentLang),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = text,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                UpdateText.get("version_check_subtitle", currentLang),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                color = muted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(card)
                                    .border(1.dp, border, RoundedCornerShape(22.dp))
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = when {
                                        onbVersionChecking -> 0
                                        onbVersionFailed -> 1
                                        onbUpdateInfo != null -> 2
                                        else -> 3
                                    } to currentLang,
                                    transitionSpec = {
                                        fadeIn(tween(400, easing = FastOutSlowInEasing)) + 
                                            scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = FastOutSlowInEasing)) togetherWith
                                            fadeOut(tween(200, easing = FastOutSlowInEasing))
                                    },
                                    label = "version_check_content"
                                ) { (state, _) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        when (state) {
                                            0 -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(34.dp),
                                                    color = accent,
                                                    strokeWidth = 3.dp
                                                )
                                                Text(
                                                    UpdateText.get("checking", currentLang),
                                                    fontSize = 14.sp,
                                                    color = muted
                                                )
                                            }

                                            1 -> {
                                                Icon(
                                                    Icons.Filled.Warning, null,
                                                    Modifier.size(40.dp),
                                                    tint = Color(0xFFE5484D)
                                                )
                                                Text(
                                                    UpdateText.get("version_unknown", currentLang),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = text,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    "• " + UpdateText.get("error_reason_1", currentLang),
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    color = muted
                                                )
                                                Text(
                                                    "• " + UpdateText.get("error_reason_2", currentLang),
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    color = muted
                                                )
                                                Text(
                                                    "• " + UpdateText.get("error_reason_3", currentLang),
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    color = muted
                                                )
                                                Button(
                                                    onClick = {
                                                        if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                                        checkVersionOnboarding()
                                                    },
                                                    shape = RoundedCornerShape(13.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = accent,
                                                        contentColor = buttonContent
                                                    )
                                                ) {
                                                    Text(
                                                        UpdateText.get("check_again", currentLang),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            2 -> {
                                                val info = onbUpdateInfo!!
                                                Box(
                                                    Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(15.dp))
                                                        .background(accent.copy(alpha = 0.14f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(IconsEx.SystemUpdate, null, Modifier.size(26.dp), tint = accent)
                                                }
                                                Text(
                                                    UpdateText.get("version_available", currentLang)
                                                        .replace("%s", info.versionName),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = text,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    UpdateText.get("version_current", currentLang)
                                                        .replace("%s", APP_VERSION),
                                                    fontSize = 12.sp,
                                                    color = muted
                                                )
                                                Text(
                                                    UpdateText.get("version_latest", currentLang)
                                                        .replace("%s", info.versionName),
                                                    fontSize = 12.sp,
                                                    color = muted
                                                )
                                                Button(
                                                    onClick = {
                                                        if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                                        showOnbUpdateDialog = true
                                                    },
                                                    shape = RoundedCornerShape(13.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = accent,
                                                        contentColor = buttonContent
                                                    )
                                                ) {
                                                    Text(
                                                        UpdateText.get("onboarding_update", currentLang),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            3 -> {
                                                Icon(
                                                    Icons.Filled.CheckCircle, null,
                                                    Modifier.size(44.dp),
                                                    tint = accent
                                                )
                                                Text(
                                                    UpdateText.get("version_actual", currentLang),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = text,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    UpdateText.get("version_current", currentLang)
                                                        .replace("%s", APP_VERSION),
                                                    fontSize = 12.sp,
                                                    color = muted
                                                )
                                                Text(
                                                    UpdateText.get("version_latest", currentLang)
                                                        .replace("%s", APP_VERSION),
                                                    fontSize = 12.sp,
                                                    color = muted
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1.25f))
                            Button(
                                onClick = {
                                    if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                    step = 4
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = buttonContent
                                )
                            ) {
                                Text(
                                    UpdateText.get("next", currentLang),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(56.dp))
                        }
                    }

                    if (showOnbUpdateDialog && onbUpdateInfo != null) {
                        UpdateDialog(
                            info = onbUpdateInfo!!,
                            colors = onbColors,
                            lang = currentLang,
                            isDark = themeDark,
                            onDismiss = { showOnbUpdateDialog = false }
                        )
                    }
                }

                else -> {
                    val focusManager = LocalFocusManager.current
                    val sendEnabled = sourceText.isNotBlank()
                    val sendBg by animateColorAsState(
                        if (sendEnabled) accent else accent.copy(alpha = 0.28f),
                        tween(if (effectsEnabled) 260 else 0),
                        label = "onb_send_bg"
                    )
                    val sendFg by animateColorAsState(
                        if (sendEnabled) buttonContent else buttonContent.copy(alpha = 0.55f),
                        tween(if (effectsEnabled) 260 else 0),
                        label = "onb_send_fg"
                    )
                    val sendScale by animateFloatAsState(
                        if (sendEnabled) 1f else 0.94f,
                        tween(if (effectsEnabled) 220 else 0),
                        label = "onb_send_scale"
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { focusManager.clearFocus() }
                            }
                            .padding(horizontal = 28.dp, vertical = 40.dp)
                    ) {
                        OnboardingArrow(
                            "←", muted, direction = -1, animated = false,
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                            step = 3
                        }
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.weight(1f))
                            AnimatedContent(
                                targetState = currentLang,
                                transitionSpec = {
                                    (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 4 }) togetherWith
                                        (fadeOut(tween(160)) + slideOutHorizontally(tween(320)) { -it / 4 })
                                },
                                label = "onboarding_source_label"
                            ) { l ->
                                Text(
                                    onboardingSourceLabel(l),
                                    fontSize = 22.sp,
                                    lineHeight = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = text,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(22.dp))
                            OutlinedTextField(
                                value = sourceText,
                                onValueChange = { sourceText = it.take(200) },
                                placeholder = {
                                    Text(
                                        onboardingPlaceholder(currentLang),
                                        color = muted.copy(alpha = 0.85f),
                                        fontSize = 14.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 220.dp),
                                textStyle = TextStyle(color = text, fontSize = 14.sp),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = border,
                                    focusedTextColor = text,
                                    unfocusedTextColor = text,
                                    cursorColor = accent,
                                    focusedContainerColor = card,
                                    unfocusedContainerColor = card
                                )
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${sourceText.length}/200", color = muted, fontSize = 11.sp)
                                Button(
                                    onClick = {
                                        if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                        focusManager.clearFocus()
                                        finish(true)
                                    },
                                    enabled = sendEnabled,
                                    modifier = Modifier.scale(sendScale).height(46.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = sendBg,
                                        contentColor = sendFg,
                                        disabledContainerColor = sendBg,
                                        disabledContentColor = sendFg
                                    )
                                ) {
                                    Text(onboardingSend(currentLang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                focusManager.clearFocus()
                                finish(false)
                            }) {
                                Text(onboardingSkip(currentLang), fontSize = 15.sp, color = muted)
                            }
                            Spacer(Modifier.height(56.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingArrow(
    glyph: String, color: Color, direction: Int, animated: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "onboarding_arrow")
    val shift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 11f * direction else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "onboarding_arrow_shift"
    )
    val fade by infinite.animateFloat(
        initialValue = if (animated) 0.28f else 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "onboarding_arrow_fade"
    )
    val arrowScale by infinite.animateFloat(
        initialValue = if (animated) 0.9f else 1f,
        targetValue = if (animated) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "onboarding_arrow_scale"
    )
    Box(
        modifier
            .size(64.dp)
            .scale(arrowScale)
            .offset(x = shift.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 54.sp, color = color.copy(alpha = fade))
    }
}

@Composable
private fun OnboardingThemeCard(
    label: String, selected: Boolean, isDarkPreview: Boolean,
    text: Color, muted: Color, accent: Color, border: Color, card: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, tween(180), label = "themeCardScale")
    val previewBg = if (isDarkPreview) Color(0xFF050505) else Color(0xFFFFFBFF)
    val previewCard = if (isDarkPreview) Color(0xFF161616) else Color(0xFFFFF7FC)
    val previewBorder = if (isDarkPreview) Color(0xFF2A2A2A) else Color(0xFFF1DEE9)
    val previewText = if (isDarkPreview) Color(0xFFF5F5F2) else Color(0xFF8E4F6E)
    val previewAccent = if (isDarkPreview) Color(0xFFF5F5F2) else Color(0xFFE79BC0)

    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else card)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else border, RoundedCornerShape(22.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Мини-превью интерфейса в выбранной теме
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(previewBg)
                    .border(1.dp, previewBorder, RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(previewText.copy(alpha = 0.75f)))
                    Box(Modifier.fillMaxWidth(0.82f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(previewText.copy(alpha = 0.35f)))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(previewCard)
                            .border(1.dp, previewBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(Modifier.padding(start = 7.dp).size(9.dp).clip(RoundedCornerShape(50)).background(previewAccent))
                    }
                }
            }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) text else muted)
        }
    }
}

@Composable
private fun OnboardingPermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, desc: String,
    granted: Boolean, allowLabel: String, grantedLabel: String,
    text: Color, muted: Color, accent: Color, border: Color, card: Color,
    accentContent: Color,
    onGrant: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(card)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = text)
            Text(desc, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
        }
        if (granted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                Text(grantedLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4CAF50))
            }
        } else {
            Button(
                onClick = onGrant,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = accentContent)
            ) {
                Text(allowLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Splash ───────────────────────────────────────────────────────────────────

@Composable
fun NelevSplash(isDark: Boolean, onFinish: () -> Unit) {
    val bgColor     = if (isDark) Color(0xFF060606) else Color(0xFFFFFAFE)
    val strokeColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFFE79BC0)
    val fillColor   = strokeColor

    var outlineAlpha   by remember { mutableStateOf(0f) }
    var fillProgress   by remember { mutableStateOf(0f) }
    var splashAlpha    by remember { mutableStateOf(1f) }

    fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3)

    suspend fun animateTo(durationMs: Long, block: (t: Float) -> Unit) {
        val start = withFrameMillis { it }; var done = false; var lastFrame = start
        while (!done) {
            val now = withFrameMillis { it }
            if (now - lastFrame >= 16) {
                val raw = ((now - start).toFloat() / durationMs).coerceIn(0f, 1f)
                block(raw); if (raw >= 1f) done = true
                lastFrame = now
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(60)
        animateTo(200) { outlineAlpha = easeOutCubic(it) }
        delay(30)
        animateTo(240) { fillProgress = easeOutCubic(it) }
        fillProgress = 1f
        delay(90)
        animateTo(200) { splashAlpha = 1f - easeOutCubic(it) }
        onFinish()
    }

    Box(Modifier.fillMaxSize().alpha(splashAlpha).background(bgColor), contentAlignment = Alignment.Center) {
        TextWithDropFill(
            text         = "nelev",
            outlineAlpha = outlineAlpha,
            fillProgress = fillProgress,
            strokeColor  = strokeColor,
            fillColor    = fillColor
        )
    }
}

@Composable
fun TextWithDropFill(
    text: String, outlineAlpha: Float, fillProgress: Float,
    strokeColor: Color, fillColor: Color
) {
    Box(Modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                fontSize      = 76.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = FontFamily.Serif,
                letterSpacing = 10.sp,
                lineHeight    = 76.sp,
                color         = strokeColor,
                drawStyle     = Stroke(width = 1.5f)
            ),
            modifier = Modifier.alpha(outlineAlpha)
        )
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                fontSize      = 76.sp,
                fontWeight    = FontWeight.Bold,
                fontFamily    = FontFamily.Serif,
                letterSpacing = 10.sp,
                lineHeight    = 76.sp,
                color         = fillColor
            ),
            modifier = Modifier.drawWithContent {
                if (fillProgress > 0f) {
                    clipRect(top = 0f, bottom = size.height * fillProgress) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
        )
    }
}

// ─── Main screen ─────────────────────────────────────────────────────────────

enum class NelevTab { CONNECT, FILTERS, INFO, SETTINGS }

@Composable
fun NelevMainScreen(
    appSettings: MutableState<AppSettings>,
    startService: (String) -> Unit,
    stopService: () -> Unit,
    onScanQr: ((String) -> Unit) -> Unit = {}
) {
    val ctx            = LocalContext.current
    val saved          = remember { Prefs.load(ctx) }
    val appState       = remember { NelevAppState(saved, NelevService.streaming) }
    val selectedTab    = remember { mutableStateOf(NelevTab.CONNECT) }
    val sliderDragging = remember { mutableStateOf(false) }
    val focusManager   = LocalFocusManager.current
    val lang           = appSettings.value.language
    val isDark         = appSettings.value.isDark
    val effectsEnabled = appSettings.value.visualEffectsEnabled
    
    LaunchedEffect(lang) {
        MainActivity.appLang = lang
    }
    
    // Синхронизация: если передачу остановили из уведомления — возвращаем UI в состояние "Начать"
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "com.nelev.dev.ACTION_STOPPED") {
                    appState.sessionStart.value = 0L
                    appState.sessionPingSum.value = 0L
                    appState.sessionPingCount.value = 0L
                    appState.pcConnected.value = false
                    appState.latencyMs.value = -1L
                    appState.isStreaming.value = false
                    NelevService.streaming = false
                }
            }
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, receiver, android.content.IntentFilter("com.nelev.dev.ACTION_STOPPED"),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
        onDispose { try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }
    val colors         = nelevColors(isDark, effectsEnabled)
    val showHistory    = remember { mutableStateOf(false) }
    val showAbout      = remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var showUpdateError by remember { mutableStateOf(false) }
    var showUpdateNotice by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var isTransitioning by remember { mutableStateOf(false) }
    val showStats      = remember { mutableStateOf(false) }
    var statsTapCount  by remember { mutableStateOf(0) }
    val statsDragX     = remember { mutableStateOf(0f) }
    val statsDragY     = remember { mutableStateOf(0f) }
    val showStatsToggle = remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(AppStats()) }
    val frameTimes = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FpsLogger.init(ctx) }
        // Восстановление сессии, если приложение завершилось аварийно
        if (!NelevService.streaming) {
            try { SessionStore.recoverCrashedSession(ctx) } catch (_: Exception) {}
        }
    }

    // OTA: тихая проверка обновлений при запуске (не чаще раза в 3 часа)
    // Если обновления не приостановлены — показываем лёгкое уведомление.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        if (appSettings.value.updatesPaused()) {
            // Пауза активна: автопроверка отключена
            return@LaunchedEffect
        }
        if (Updater.shouldAutoCheck(ctx)) {
            Updater.markChecked(ctx)
            Updater.check(ctx, respectSkip = true).onSuccess { info ->
                if (info != null) {
                    // Не открываем полный диалог: только лёгкое уведомление
                    updateInfo = info
                    showUpdateNotice = true
                }
            }
        }
    }

    val checkUpdates: () -> Unit = {
        if (!updateChecking) {
            updateChecking = true
            val currentLang = appSettings.value.language
            Toast.makeText(ctx, UpdateText.get("checking", currentLang), Toast.LENGTH_SHORT).show()
            updateScope.launch {
                Updater.check(ctx, respectSkip = false)
                    .onSuccess { info ->
                        if (info != null) {
                            updateInfo = info
                        } else {
                            Toast.makeText(ctx, UpdateText.get("up_to_date", currentLang), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .onFailure {
                        showUpdateError = true
                    }
                updateChecking = false
            }
        }
    }
    LaunchedEffect(showStats.value) {
        if (!showStats.value) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            val cpu = getCpuUsage()
            val ram = readAppMem(ctx)
            stats = stats.copy(cpu = cpu, ram = ram)
        }
    }
    LaunchedEffect(showStats.value) {
        if (!showStats.value) return@LaunchedEffect
        var lastFrame = 0L
        while (true) {
            withFrameMillis { now ->
                if (lastFrame > 0) {
                    val dtMs = now - lastFrame
                    if (dtMs in 8..500) {
                        val dt = dtMs / 1000f
                        frameTimes.add(dt)
                        if (frameTimes.size > 60) frameTimes.removeAt(0)
                        val rawFps = (1f / dt).toInt()
                        val clamped = rawFps.coerceIn(10, 120)
                        stats = stats.copy(fps = clamped)
                        FpsLogger.logFrame(ctx, clamped, stats.cpu, stats.ram, now)
                    }
                }
                lastFrame = now
            }
        }
    }

    // QR scanner callback
    val qrScanCallback: () -> Unit = {
        onScanQr { scannedIp ->
            appState.ip.value = scannedIp
            NelevService.targetIp = scannedIp
        }
    }

    StreamingConnectionEffect(
        state = appState,
        appSettings = appSettings,
        startService = startService,
        stopService = stopService
    )

    // Системный «назад»: закрываем клавиатуру, окна, возвращаемся на вкладку подключения
    BackHandler {
        focusManager.clearFocus()
        when {
            showHistory.value -> { showHistory.value = false; return@BackHandler }
            showAbout.value -> { showAbout.value = false; return@BackHandler }
            selectedTab.value != NelevTab.CONNECT -> selectedTab.value = NelevTab.CONNECT
        }
    }

    LaunchedEffect(Unit) {
        if (appSettings.value.phrasesEnabled) appState.currentPhrase.value = Phrases.next(lang)
    }

    LaunchedEffect(appState.volume.value, appState.isMuted.value, appState.reverb.value, appState.bass.value, appState.pitch.value,
        appState.enhancerDc.value, appState.enhancerHp.value, appState.enhancerLimiter.value, appState.enhancerGate.value,
        appState.eqLow.value, appState.eqMid.value, appState.eqHigh.value) {
        NelevService.volume       = appState.volume.value
        NelevService.isMuted      = appState.isMuted.value
        NelevService.reverbAmount = appState.reverb.value
        NelevService.bassAmount   = appState.bass.value
        NelevService.pitchAmount  = appState.pitch.value
        NelevService.enhancerDc      = appState.enhancerDc.value
        NelevService.enhancerHp      = appState.enhancerHp.value
        NelevService.enhancerLimiter = appState.enhancerLimiter.value
        NelevService.enhancerGate    = appState.enhancerGate.value
        NelevService.eqLow  = appState.eqLow.value
        NelevService.eqMid  = appState.eqMid.value
        NelevService.eqHigh = appState.eqHigh.value
    }

    LaunchedEffect(appState.ip.value) {
        NelevService.targetIp = appState.ip.value.trim()
    }

    LaunchedEffect(appState.ip.value, appState.volume.value, appState.reverb.value, appState.bass.value, appState.pitch.value,
        appState.enhancerDc.value, appState.enhancerHp.value, appState.enhancerLimiter.value, appState.enhancerGate.value,
        appState.eqLow.value, appState.eqMid.value, appState.eqHigh.value) {
        // Debounce + запись файла в IO: раньше Prefs.save() писал на диск в main-потоке
        // на каждый тик слайдера громкости.
        delay(250)
        val ip = appState.ip.value
        val volume = appState.volume.value
        val reverb = appState.reverb.value
        val bass = appState.bass.value
        val pitch = appState.pitch.value
        val dc = appState.enhancerDc.value
        val hp = appState.enhancerHp.value
        val limiter = appState.enhancerLimiter.value
        val gate = appState.enhancerGate.value
        val eqL = appState.eqLow.value
        val eqM = appState.eqMid.value
        val eqH = appState.eqHigh.value
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Prefs.save(ctx, ip, volume, reverb, bass, pitch, dc, hp, limiter, gate, eqL, eqM, eqH)
        }
    }

    LaunchedEffect(appSettings.value) {
        val ip = appState.ip.value
        if (ip.isNotBlank() && appSettings.value.syncWithPc) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + NonCancellable) {
            SyncSender.send(ip, mapOf(
                "dark" to appSettings.value.isDark,
                "lang" to appSettings.value.language,
                "connbar" to appSettings.value.connectionBarEnabled,
                "visualEffectsEnabled" to appSettings.value.visualEffectsEnabled,
                "visual_effects" to appSettings.value.visualEffectsEnabled,
                "soundnotif" to appSettings.value.soundNotifEnabled,
                "soundclick" to appSettings.value.soundClickEnabled,
                "muted" to appState.isMuted.value
            ))
        }
    }

    LaunchedEffect(appState.ip.value) {
        val ip = appState.ip.value
        if (ip.isNotBlank() && appSettings.value.syncWithPc) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + NonCancellable) {
            SyncSender.send(ip, mapOf(
                "dark" to appSettings.value.isDark,
                "lang" to appSettings.value.language,
                "connbar" to appSettings.value.connectionBarEnabled,
                "visualEffectsEnabled" to appSettings.value.visualEffectsEnabled,
                "visual_effects" to appSettings.value.visualEffectsEnabled,
                "soundnotif" to appSettings.value.soundNotifEnabled,
                "soundclick" to appSettings.value.soundClickEnabled,
                "muted" to appState.isMuted.value
            ))
        }
    }

    // Мут -> ПК: отправляем сразу при нажатии кнопки мута на телефоне
    LaunchedEffect(appState.isMuted.value) {
        val ip = appState.ip.value
        if (ip.isNotBlank() && appSettings.value.syncWithPc) {
            if (MainActivity.suppressNextMuteSend) {
                MainActivity.suppressNextMuteSend = false
            } else {
                val m = appState.isMuted.value
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + NonCancellable) {
                    SyncSender.send(ip, mapOf("muted" to m))
                }
            }
        } else {
            MainActivity.suppressNextMuteSend = false
        }
    }

    LaunchedEffect(appSettings.value.autoStartEnabled, appState.ip.value) {
        if (appSettings.value.autoStartEnabled && appState.ip.value.isNotBlank() && !appState.isStreaming.value) {
            val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                appState.isStreaming.value = true
                if (appSettings.value.phrasesEnabled) appState.currentPhrase.value = Phrases.next(lang)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val pcIp = MainActivity.autoStartPcIp
            if (pcIp.isNotBlank() && appSettings.value.autoStartEnabled && !appState.isStreaming.value) {
                MainActivity.autoStartPcIp = ""
                appState.ip.value = pcIp
                NelevService.targetIp = pcIp
                val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    appState.isStreaming.value = true
                    if (appSettings.value.phrasesEnabled) appState.currentPhrase.value = Phrases.next(lang)
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val pcSettings = MainActivity.pendingPcSettings
            if (pcSettings != null && appSettings.value.syncWithPc) {
                MainActivity.pendingPcSettings = null
                var updated = appSettings.value
                pcSettings["dark"]?.let { if (it is Boolean) updated = updated.copy(isDark = it) }
                pcSettings["lang"]?.let { if (it is String) updated = updated.copy(language = it) }
                pcSettings["connbar"]?.let { if (it is Boolean) updated = updated.copy(connectionBarEnabled = it) }
                pcSettings["effects"]?.let { if (it is Boolean) updated = updated.copy(visualEffectsEnabled = it) }
                pcSettings["visualEffectsEnabled"]?.let { if (it is Boolean) updated = updated.copy(visualEffectsEnabled = it) }
                pcSettings["visual_effects"]?.let { if (it is Boolean) updated = updated.copy(visualEffectsEnabled = it) }
                pcSettings["soundnotif"]?.let { if (it is Boolean) updated = updated.copy(soundNotifEnabled = it) }
                pcSettings["soundclick"]?.let { if (it is Boolean) updated = updated.copy(soundClickEnabled = it) }
                pcSettings["muted"]?.let { if (it is Boolean && appState.isMuted.value != it) {
                    MainActivity.suppressNextMuteSend = true
                    appState.isMuted.value = it
                } }
                if (updated != appSettings.value) {
                    appSettings.value = updated
                    val ctx = ctx
                    SettingsStore.save(ctx, updated)
                }
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val scope = rememberCoroutineScope()
    val tabOrder = NelevTab.entries.toList()
    var screenWidth by remember { mutableStateOf(1f) }

    val dragOffset = remember { Animatable(0f) }
    val tabIndicatorIndex = remember { Animatable(tabOrder.indexOf(selectedTab.value).toFloat()) }

    LaunchedEffect(selectedTab.value, effectsEnabled) {
        if (abs(dragOffset.value) > 0.5f) return@LaunchedEffect
        val targetIndex = tabOrder.indexOf(selectedTab.value).toFloat()
        if (effectsEnabled) {
            tabIndicatorIndex.animateTo(targetIndex, tween(120, easing = FastOutSlowInEasing)) // OPTIMIZATION: 180ms → 120ms - мгновенный отклик
        } else {
            tabIndicatorIndex.snapTo(targetIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            containerColor = Color.Transparent,
            contentColor = colors["text"]!!
        ) { pad ->

            // ── История сессий — плавное наложение поверх основного контента ──
            Box(Modifier.padding(pad).fillMaxSize().background(colors["bg"]!!)) {

                // Основной контент (вкладки) всегда под историей
                Box(
                    Modifier
                        .fillMaxSize()
                        // BETA-БЛЮР: при включённых визуальных эффектах фон размывается,
                        // при выключенных — прежнее затемнение (alpha 0.62), чтобы не терять акцент
                        .alpha(if (!effectsEnabled && (showHistory.value || showAbout.value)) 0.62f else 1f)
                        .blur(if (effectsEnabled && (showHistory.value || showAbout.value)) 6.dp else 0.dp)
                        .onSizeChanged { screenWidth = it.width.toFloat() }
                        .then(
                            if (showHistory.value || showAbout.value) {
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consume() }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            } else {
                                Modifier.pointerInput(selectedTab.value, sliderDragging.value) {
                                    if (sliderDragging.value) return@pointerInput
                                    awaitEachGesture {
                                        val down   = awaitFirstDown(requireUnconsumed = false)
                                        val startX = down.position.x
                                        val startY = down.position.y
                                        val currentIdx = tabOrder.indexOf(selectedTab.value)
                                        var moved  = false
                                        var lockedHorizontal = false

                                        do {
                                            val ev     = awaitPointerEvent()
                                            val change = ev.changes.firstOrNull() ?: break
                                            val dx     = change.position.x - startX
                                            val dy     = change.position.y - startY

                                            // OPTIMIZATION: МАКСИМАЛЬНАЯ чувствительность - порог 0.5px
                                            if (!moved && (abs(dx) > 0.5f || abs(dy) > 0.5f)) {
                                                // OPTIMIZATION: более агрессивное определение горизонтального свайпа
                                                lockedHorizontal = abs(dx) >= abs(dy) * 0.5f
                                                moved = true
                                            }

                                            if (moved && lockedHorizontal) {
                                                val clamped = when {
                                                    dx > 0 && currentIdx == 0                 -> dx * 0.1f // OPTIMIZATION: меньше сопротивление
                                                    dx < 0 && currentIdx == tabOrder.size - 1 -> dx * 0.1f
                                                    else -> dx
                                                }
                                                scope.launch {
                                                    dragOffset.snapTo(clamped)
                                                    val liveIndex = (currentIdx - clamped / screenWidth)
                                                        .coerceIn(0f, tabOrder.lastIndex.toFloat())
                                                    tabIndicatorIndex.snapTo(liveIndex)

                                                    isTransitioning = abs(clamped) > 5f // OPTIMIZATION: порог 10px → 5px - мгновенный отклик
                                                }
                                            }
                                        } while (ev.changes.any { it.pressed })

                                        if (!lockedHorizontal) {
                                            scope.launch {
                                                isTransitioning = false
                                                if (effectsEnabled) {
                                                    dragOffset.animateTo(0f, tween(100, easing = FastOutSlowInEasing)) // OPTIMIZATION: 140ms → 100ms
                                                    tabIndicatorIndex.animateTo(currentIdx.toFloat(), tween(100, easing = FastOutSlowInEasing))
                                                } else {
                                                    dragOffset.snapTo(0f)
                                                    tabIndicatorIndex.snapTo(currentIdx.toFloat())
                                                }
                                            }
                                            return@awaitEachGesture
                                        }

                                        val totalDx = dragOffset.value
                                        val idx     = currentIdx
                                        val threshold = screenWidth * 0.015f // OPTIMIZATION: порог 2% → 1.5% для МАКСИМАЛЬНОЙ чувствительности

                                        if (abs(totalDx) > threshold) {
                                            val dir    = if (totalDx < 0) 1 else -1
                                            val newIdx = (idx + dir).coerceIn(0, tabOrder.size - 1)
                                            if (newIdx != idx) {
                                                scope.launch {
                                                    if (effectsEnabled) {
                                                        val pillJob = launch {
                                                            tabIndicatorIndex.animateTo(newIdx.toFloat(), tween(120, easing = FastOutSlowInEasing)) // OPTIMIZATION: 180ms → 120ms - молниеносный свайп
                                                        }
                                                        dragOffset.animateTo(-dir * screenWidth, tween(120, easing = FastOutSlowInEasing))
                                                        pillJob.join()
                                                    } else {
                                                        dragOffset.snapTo(-dir * screenWidth)
                                                        tabIndicatorIndex.snapTo(newIdx.toFloat())
                                                    }
                                                    selectedTab.value = tabOrder[newIdx]
                                                    dragOffset.snapTo(0f)
                                                    tabIndicatorIndex.snapTo(newIdx.toFloat())
                                                    isTransitioning = false
                                                }
                                            } else {
                                                scope.launch {
                                                    isTransitioning = false
                                                    if (effectsEnabled) {
                                                        dragOffset.animateTo(0f, tween(120, easing = FastOutSlowInEasing)) // OPTIMIZATION: 180ms → 120ms
                                                        tabIndicatorIndex.animateTo(idx.toFloat(), tween(120, easing = FastOutSlowInEasing))
                                                    } else {
                                                        dragOffset.snapTo(0f)
                                                        tabIndicatorIndex.snapTo(idx.toFloat())
                                                    }
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                isTransitioning = false
                                                if (effectsEnabled) {
                                                    dragOffset.animateTo(0f, tween(120, easing = FastOutSlowInEasing)) // OPTIMIZATION: 180ms → 120ms
                                                    tabIndicatorIndex.animateTo(idx.toFloat(), tween(120, easing = FastOutSlowInEasing))
                                                } else {
                                                    dragOffset.snapTo(0f)
                                                    tabIndicatorIndex.snapTo(idx.toFloat())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                ) {
                    val idx = tabOrder.indexOf(selectedTab.value)

                    // neighborIdx меняется только на границе -1/+1, поэтому derivedStateOf
                    // не пересобирает экран каждый кадр свайпа.
                    val neighborIdx by remember(idx, screenWidth) {
                        derivedStateOf {
                            val o = dragOffset.value
                            when {
                                o < 0f && idx < tabOrder.size - 1 -> idx + 1
                                o > 0f && idx > 0                -> idx - 1
                                else -> -1
                            }
                        }
                    }

                    if (neighborIdx >= 0) {
                        Box(Modifier.fillMaxSize()
                            .offset {
                                val o = dragOffset.value
                                IntOffset((o + if (o < 0f) screenWidth else -screenWidth).toInt(), 0)
                            }
                            .graphicsLayer { alpha = (abs(dragOffset.value) / screenWidth).coerceIn(0f, 1f) }) {
                            TabContent(tabOrder[neighborIdx], appState, appSettings, colors, lang, isDark, sliderDragging,
                                effectsEnabled, isTransitioning, { isTransitioning = it }, startService, stopService, showHistory, selectedTab, showAbout, onCheckUpdates = checkUpdates, onScanQr = qrScanCallback)
                        }
                    }

                    Box(Modifier.fillMaxSize()
                        .offset { IntOffset(dragOffset.value.toInt(), 0) }
                        .graphicsLayer { alpha = 1f - (abs(dragOffset.value) / screenWidth).coerceIn(0f, 1f) * 0.3f }) {
                        TabContent(selectedTab.value, appState, appSettings, colors, lang, isDark, sliderDragging,
                            effectsEnabled, isTransitioning, { isTransitioning = it }, startService, stopService, showHistory, selectedTab, showAbout, onCheckUpdates = checkUpdates, onScanQr = qrScanCallback)
                    }
                }

                // OPTIMIZATION: Фоновые кометы полностью убраны для максимальной производительности
                // if (effectsEnabled && !showHistory.value) {
                //     Box(Modifier.matchParentSize().alpha(0.12f)) {
                //         BackgroundComets(isDark = isDark, effectsEnabled = effectsEnabled)
                //     }
                // }

                // Прозрачный overlay для блокировки кликов когда открыта история или окно "О приложении"
                if (showHistory.value || showAbout.value) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { it.consume() }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                    )
                }

                // История — плавное наложение с fade+slide, поверх основного контента
                AnimatedVisibility(
                    visible = showHistory.value,
                    enter = slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(if (effectsEnabled) 200 else 0, easing = FastOutSlowInEasing) // OPTIMIZATION: 300ms → 200ms
                    ) + fadeIn(tween(if (effectsEnabled) 150 else 0)), // OPTIMIZATION: 240ms → 150ms
                    exit = slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(if (effectsEnabled) 180 else 0, easing = FastOutSlowInEasing) // OPTIMIZATION: 280ms → 180ms
                    ) + fadeOut(tween(if (effectsEnabled) 150 else 0)) // OPTIMIZATION: 240ms → 150ms
                ) {
                    SessionHistoryScreen(
                        colors = colors,
                        lang = lang,
                        soundClickEnabled = appSettings.value.soundClickEnabled,
                        effectsEnabled = effectsEnabled,
                        onBack = { showHistory.value = false },
                        onGoConnect = {
                            selectedTab.value = NelevTab.CONNECT
                            showHistory.value = false
                        }
                    )
                }
            }

            // Overlay для окна "О приложении" - поверх всего
            AboutAppOverlay(
                showAbout = showAbout.value,
                onDismiss = { showAbout.value = false },
                appSettings = appSettings,
                colors = colors,
                lang = lang,
                soundClickEnabled = appSettings.value.soundClickEnabled,
                effectsEnabled = effectsEnabled,
                ctx = ctx,
                save = { SettingsStore.save(ctx, it); appSettings.value = it },
                showStatsToggle = showStatsToggle,
                showStats = showStats
            )

            updateInfo?.let { info ->
                if (showUpdateNotice) {
                    UpdateAvailableNotice(
                        versionName = info.versionName,
                        colors = colors,
                        lang = lang,
                        isDark = isDark,
                        onOpenMenu = {
                            showUpdateNotice = false
                            selectedTab.value = NelevTab.SETTINGS
                        },
                        onDismiss = {
                            showUpdateNotice = false
                            Updater.markSkipped(ctx, info.versionCode)
                            updateInfo = null
                        }
                    )
                } else {
                    UpdateDialog(
                        info = info,
                        colors = colors,
                        lang = lang,
                        isDark = isDark,
                        onDismiss = {
                            Updater.markSkipped(ctx, info.versionCode)
                            updateInfo = null
                        }
                    )
                }
            }

            if (showUpdateError) {
                UpdateErrorDialog(
                    colors = colors,
                    lang = lang,
                    onRetry = {
                        showUpdateError = false
                        checkUpdates()
                    },
                    onDismiss = { showUpdateError = false }
                )
            }

            if (showPauseDialog) {
                UpdatePauseDialog(
                    colors = colors,
                    lang = lang,
                    isDark = isDark,
                    pausedUntilMs = appSettings.value.updatesPausedUntilMs,
                    pausedForever = appSettings.value.updatesPausedForever,
                    onPause = { untilMs, forever ->
                        val s = appSettings.value.copy(
                            updatesPausedUntilMs = untilMs,
                            updatesPausedForever = forever
                        )
                        appSettings.value = s
                        SettingsStore.save(ctx, s)
                    },
                    onResume = {
                        val s = appSettings.value.copy(
                            updatesPausedUntilMs = 0L,
                            updatesPausedForever = false
                        )
                        appSettings.value = s
                        SettingsStore.save(ctx, s)
                    },
                    onDismiss = { showPauseDialog = false }
                )
            }
        }

        // ── Навигационная панель поверх контента ──
        if (!showHistory.value) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // BETA-БЛЮР: навбар тоже размывается под открытым окном «О приложении»
                    .alpha(if (!effectsEnabled && showAbout.value) 0.62f else 1f)
                    .blur(if (effectsEnabled && showAbout.value) 6.dp else 0.dp)
            ) {
                ModernNavBar(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        if (showAbout.value) {
                            showAbout.value = false
                        } else if (selectedTab.value != tab) {
                            FpsLogger.trackInteraction("tab→$tab")
                            selectedTab.value = tab
                        }
                    },
                    colors = colors,
                    isDark = isDark,
                    lang = lang,
                    soundClickEnabled = appSettings.value.soundClickEnabled && !showAbout.value,
                    indicatorIndex = tabIndicatorIndex.value,
                    effectsEnabled = effectsEnabled,
                    tabOrder = tabOrder
                )
            }
        }

        if (easterPhase.value == EasterPhase.ACTIVE) EasterHeart(
            isDark = isDark,
            effectsEnabled = effectsEnabled,
            lang = lang,
            guidedTourTarget = EasterEggGuide.guidedTourTarget,
            onCloseAbout = {
                showAbout.value = false
            },
            onOpenConnect = {
                selectedTab.value = NelevTab.CONNECT
                showHistory.value = false
            },
            onShowPhrase = {
                appState.currentPhrase.value = easterLovePhrase(lang)
            },
            onDone = { easterPhase.value = EasterPhase.NONE }
        )

        // ─── Панель статистики (перетаскиваемая) ───
        if (showStats.value && stats != AppStats()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(statsDragX.value.roundToInt(), statsDragY.value.roundToInt()) }
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 72.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            statsDragX.value += dragAmount.x
                            statsDragY.value += dragAmount.y
                        }
                    }
            ) {
                val chipBg = if (isDark) Color(0xFF1A1A1A) else Color(0xFFE8E8E8)
                val chipLabel = colors["text"]!!
                val chipValueDefault = colors["text"]!!
                Box(
                    modifier = Modifier
                        .background(colors["bg"]!!.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                        .border(1.dp, colors["border"]!!, RoundedCornerShape(12.dp))
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("nelev stats", fontSize = 10.sp, color = colors["muted"]!!)
                        Spacer(Modifier.size(4.dp))
                        Canvas(modifier = Modifier.size(140.dp, 40.dp)) {
                            val w = size.width; val h = size.height
                            drawRect(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0))
                            val maxDt = if (frameTimes.isNotEmpty()) frameTimes.max() else 33f
                            val barW = w / 60f
                            frameTimes.forEachIndexed { i, dt ->
                                val barH = ((1f - (dt / maxDt)) * h * 0.9f).coerceAtLeast(2f)
                                val alpha = (200 * (i.toFloat() / frameTimes.size)).coerceAtMost(255f)
                                drawRect(
                                    color = if (dt < 16.6f) Color(0xFF4CAF50).copy(alpha = alpha / 255f) else Color(0xFFFF9800).copy(alpha = alpha / 255f),
                                    topLeft = Offset(i * barW, h - barH),
                                    size = Size(barW - 1f, barH)
                                )
                            }
                        }
                        Spacer(Modifier.size(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.widthIn(min = 200.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatChip("FPS", "${stats.fps}", chipValueDefault, chipBg, chipLabel)
                            StatChip("CPU", "${stats.cpu.toInt()}%", if (stats.cpu < 70) Color(0xFF4CAF50) else Color(0xFFFF5252), chipBg, chipLabel)
                            StatChip("RAM", "${stats.ram}MB", chipValueDefault, chipBg, chipLabel)
                            StatChip("GPU", "${stats.gpu.toInt()}%", if (stats.gpu < 60) Color(0xFF4CAF50) else Color(0xFFFF9800), chipBg, chipLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingConnectionEffect(
    state: NelevAppState,
    appSettings: MutableState<AppSettings>,
    startService: (String) -> Unit,
    stopService: () -> Unit
) {
    val ctx = LocalContext.current
    val soundNotifEnabled by rememberUpdatedState(appSettings.value.soundNotifEnabled)
    val currentLang by rememberUpdatedState(appSettings.value.language)

    fun finishSession() {
        if (state.sessionStart.value > 0L) {
            val dur = System.currentTimeMillis() - state.sessionStart.value
            val avgPing = when {
                state.sessionPingCount.value > 0 -> state.sessionPingSum.value / state.sessionPingCount.value
                state.latencyMs.value > 0 -> state.latencyMs.value
                else -> 0L
            }
            SessionStore.append(ctx, SessionRecord(startMs = state.sessionStart.value, durationMs = dur, avgPingMs = avgPing))
        }
        state.sessionStart.value = 0L
        state.sessionPingSum.value = 0L
        state.sessionPingCount.value = 0L
        state.pcConnected.value = false
        state.latencyMs.value = -1L
    }

    val ackSock = remember { java.util.concurrent.atomic.AtomicReference<java.net.DatagramSocket?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            ackSock.get()?.close()
        }
    }

    LaunchedEffect(state.isStreaming.value) {
        if (!state.isStreaming.value) {
            FpsLogger.trackInteraction("stream→OFF")
            stopService()
            finishSession()
            return@LaunchedEffect
        }

        FpsLogger.trackInteraction("stream→ON ip=${state.ip.value}")
        startService(state.ip.value)
        state.pcConnected.value = false
        state.latencyMs.value = -1L
        
        // Проверяем, есть ли активная сессия при перезаходе
        val savedSessionStart = SessionStore.loadActiveStart(ctx)
        if (savedSessionStart > 0L) {
            state.sessionStart.value = savedSessionStart
        } else {
            state.sessionStart.value = System.currentTimeMillis()
        }
        // Сохраняем активную сессию
        SessionStore.saveActiveSession(ctx, state.sessionStart.value)

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var sock: java.net.DatagramSocket? = null
            var wasConnected = false
            var lastNackTime = 0L
            var prevNackTime = 0L
            var prevServerTs = 0L
            var lastKnownIp = state.ip.value
            var ipChanged = false

            try {
                sock = (1..3).firstNotNullOfOrNull { attempt ->
                    try { java.net.DatagramSocket(55001) } catch (_: Exception) {
                        if (attempt < 3) Thread.sleep(400L)
                        null
                    }
                } ?: java.net.DatagramSocket()
                ackSock.set(sock)

                sock.soTimeout = 200
                val buf = ByteArray(64)
                val pkt = java.net.DatagramPacket(buf, buf.size)

                while (state.isStreaming.value) {
                    val currentIp = state.ip.value
                    if (currentIp != lastKnownIp) {
                        lastKnownIp = currentIp
                        ipChanged = true
                        lastNackTime = 0L
                        prevNackTime = 0L
                        prevServerTs = 0L
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            state.pcConnected.value = false
                            state.latencyMs.value = -1L
                            if (wasConnected) {
                                if (soundNotifEnabled) SoundPlayer.play(ctx)
                                showConnectionNotification(ctx, false, currentLang)
                            }
                        }
                        wasConnected = false
                    }
                    try {
                        pkt.length = buf.size
                        sock.receive(pkt)
                        val recvTime = System.currentTimeMillis()
                        val payload = String(pkt.data, 0, minOf(4, pkt.length), Charsets.US_ASCII)
                        if (payload == "NACK") {
                            val nackHost = pkt.address?.hostAddress?.let { a ->
                                if (a.startsWith("::ffff:")) a.substring(7) else a
                            } ?: ""
                            if (currentIp.isNotBlank() && nackHost != currentIp) continue
                            lastNackTime = recvTime
                            if (pkt.length >= 12) {
                                val serverTs = java.nio.ByteBuffer.wrap(pkt.data, 4, 8)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).long
                                if (prevServerTs > 0L && prevNackTime > 0L) {
                                    val serverInterval = serverTs - prevServerTs
                                    val phoneInterval = recvTime - prevNackTime
                                    val rtt = (phoneInterval - serverInterval).coerceIn(1L, 500L)
                                    if (rtt < 400L) {
                                        state.latencyMs.value = rtt
                                        state.sessionPingSum.value += rtt
                                        state.sessionPingCount.value += 1
                                    }
                                }
                                prevServerTs = serverTs
                                prevNackTime = recvTime
                            } else {
                                if (prevNackTime > 0L) {
                                    val interval = recvTime - prevNackTime
                                    if (interval in 20..500) {
                                        val ping = interval / 2
                                        state.latencyMs.value = ping
                                        state.sessionPingSum.value += ping
                                        state.sessionPingCount.value += 1
                                    }
                                }
                                prevNackTime = recvTime
                            }

                            if (!wasConnected) {
                                wasConnected = true
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    state.pcConnected.value = true
                                    state.sessionStart.value = System.currentTimeMillis()
                                    state.sessionPingSum.value = 0L
                                    state.sessionPingCount.value = 0L
                                    if (soundNotifEnabled) SoundPlayer.play(ctx)
                                    showConnectionNotification(ctx, true, currentLang)
                                }
                            } else {
                                state.pcConnected.value = true
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        if (wasConnected && lastNackTime > 0L && System.currentTimeMillis() - lastNackTime > 5000L) {
                            wasConnected = false
                            prevNackTime = 0L
                            prevServerTs = 0L
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                state.pcConnected.value = false
                                state.latencyMs.value = -1L
                                if (soundNotifEnabled) SoundPlayer.play(ctx)
                                showConnectionNotification(ctx, false, currentLang)
                            }
                            ipChanged = false
                        } else if (!wasConnected && lastNackTime > 0L && System.currentTimeMillis() - lastNackTime > 5000L) {
                            prevNackTime = 0L
                            prevServerTs = 0L
                        }
                    } catch (_: Exception) {
                        delay(200)
                    }
                }
            } catch (_: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    state.pcConnected.value = false
                }
            } finally {
                sock?.close()
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                    if (!state.isStreaming.value) finishSession()
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: NelevTab,
    appState: NelevAppState,
    appSettings: MutableState<AppSettings>,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    sliderDragging: MutableState<Boolean>,
    effectsEnabled: Boolean,
    isTransitioning: Boolean,
    setTransitioning: (Boolean) -> Unit,
    startService: (String) -> Unit,
    stopService: () -> Unit,
    showHistory: MutableState<Boolean>,
    selectedTab: MutableState<NelevTab>,
    showAbout: MutableState<Boolean>,
    onCheckUpdates: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onShowPauseDialog: () -> Unit = {}
) {
    val ctx = LocalContext.current
    when (tab) {
        NelevTab.CONNECT  -> ConnectScreen(appState, appSettings, colors, lang, isDark, sliderDragging,
            effectsEnabled, isTransitioning, setTransitioning, startService, stopService,
            menuCentered = appSettings.value.menuCentered, onScanQr = onScanQr)
        NelevTab.FILTERS  -> FiltersScreen(appState, colors, lang, sliderDragging, appSettings.value.soundClickEnabled, effectsEnabled, isDark, menuCentered = appSettings.value.menuCentered)
        NelevTab.INFO     -> InfoScreen(colors, lang, effectsEnabled)
        NelevTab.SETTINGS -> SettingsScreen(appSettings, colors, lang, ctx, showHistory, selectedTab, showAbout, onCheckUpdates, onShowPauseDialog)
    }
}

// ─── NavBar с змейкой и подвижной пилюлей ─────────────────────────────────

@Composable
fun ModernNavBar(
    selectedTab: MutableState<NelevTab>,
    onSelectTab: (NelevTab) -> Unit,
    colors: NelevColors,
    isDark: Boolean,
    lang: String,
    soundClickEnabled: Boolean,
    indicatorIndex: Float = 0f,
    effectsEnabled: Boolean = true,
    tabOrder: List<NelevTab> = NelevTab.entries.toList()
) {
    val border = colors["border"]!!
    val navBg  = colors["navBg"]!!
    val activeBg by animateColorAsState(
        if (isDark) Color.White else Color(0xFFE79BC0),
        tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 500ms → 250ms - мгновенная смена темы
        label = "activeBgTheme"
    )
    val activeContent by animateColorAsState(
        if (isDark) Color(0xFF070707) else Color.White,
        tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing),
        label = "activeContent"
    )
    val ghostIcon by animateColorAsState(
        if (isDark) Color(0xFF757575) else Color(0xFFD29AB6),
        tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing),
        label = "ghostIcon"
    )
    val ghostTxt by animateColorAsState(
        if (isDark) Color(0xFF7D7D7D) else Color(0xFFC98CAB),
        tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing),
        label = "ghostTxt"
    )
    val ctx = LocalContext.current

    val labels = remember(lang) {
        mapOf(
            NelevTab.CONNECT to Strings.get("tab_connect", lang),
            NelevTab.FILTERS to Strings.get("tab_filters", lang),
            NelevTab.INFO to Strings.get("tab_info", lang),
            NelevTab.SETTINGS to Strings.get("tab_settings", lang)
        )
    }
    val tabs = remember(tabOrder, labels) { tabOrder.map { it to (labels[it] ?: it.name) } }
    val tabCount = tabs.size
    var stripWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val navHeight = 62.dp
    val navCorner = 28.dp
    val innerPadPx = with(density) { 4.dp.toPx() }

    val tabWidthPx = if (stripWidthPx > 0) stripWidthPx.toFloat() / tabCount else 0f
    val pillLeft = if (stripWidthPx > 0) tabWidthPx * indicatorIndex + innerPadPx else 0f

    val pressTokens = remember {
        mutableStateMapOf<NelevTab, Int>().apply {
            NelevTab.entries.forEach { put(it, 0) }
        }
    }

    val shellBrush = Brush.linearGradient(
        listOf(
            Color.Transparent,
            navBg.copy(alpha = 0.18f),
            Color.Transparent
        )
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, top = 4.dp, end = 30.dp, bottom = 32.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(navHeight)
                .clip(RoundedCornerShape(navCorner))
                .background(navBg.copy(alpha = 0.55f))
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(navHeight)
                .clip(RoundedCornerShape(navCorner))
                .onSizeChanged { stripWidthPx = it.width }
        ) {
            if (stripWidthPx > 0) {
                val pillW = tabWidthPx - innerPadPx * 2f
                val pillH = with(density) { navHeight.toPx() } - innerPadPx * 2f
                val cornerPx = with(density) { 20.dp.toPx() }
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = activeBg,
                        topLeft = Offset(pillLeft, innerPadPx),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                }
            }

            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, (tab, label) ->
                    val sel = abs(indicatorIndex - index.toFloat()) < 0.04f
                    val iconTint = if (sel) activeContent else ghostIcon
                    val labelTint = if (sel) activeContent else ghostTxt

                    val token = pressTokens[tab] ?: 0
                    val pressPulse = remember(tab) { Animatable(1f) }
                    LaunchedEffect(token) {
                        if (token == 0 || !effectsEnabled) return@LaunchedEffect
                        pressPulse.snapTo(0.94f)
                        pressPulse.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) // OPTIMIZATION: 220ms → 150ms - быстрый отклик
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (tab == NelevTab.CONNECT) {
                                    Modifier.onGloballyPositioned { coordinates ->
                                        val pos = coordinates.positionInRoot()
                                        val target = Offset(
                                            pos.x + coordinates.size.width / 2f,
                                            pos.y + coordinates.size.height / 2f
                                        )
                                        if (EasterEggGuide.connectTabCenter.value != target) {
                                            EasterEggGuide.connectTabCenter.value = target
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .scale(pressPulse.value)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (soundClickEnabled) ClickPlayer.play(ctx)
                                pressTokens[tab] = (pressTokens[tab] ?: 0) + 1
                                onSelectTab(tab)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SimpleNavIcon(
                                tab = tab,
                                tint = iconTint,
                                selected = sel,
                                effectsEnabled = effectsEnabled
                            )
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = labelTint,
                                maxLines = 1,
                                lineHeight = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleNavIcon(
    tab: NelevTab,
    tint: Color,
    selected: Boolean,
    effectsEnabled: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = tween(if (effectsEnabled) 120 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 200ms → 120ms
        label = "iconScale_$tab"
    )

    // Шестерёнка крутится только у выбранной вкладки Settings, остальные иконки статичны.
    val animPhase = rememberPhaseTick(
        selected && effectsEnabled && tab == NelevTab.SETTINGS,
        1000f / 300f,
        2f * PI.toFloat()
    )

    // Вращение шестеренки в 60 FPS
    val rotation = when {
        !selected || !effectsEnabled -> 0f
        tab == NelevTab.SETTINGS -> ((animPhase / (2f * PI.toFloat())) * 360f) % 360f
        else -> 0f
    }

    // Дыхание для инфо
    val breathScale = when {
        !selected || !effectsEnabled -> 1f
        tab == NelevTab.INFO -> 1f + sin(animPhase * 1.5f) * 0.15f
        else -> 1f
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        // OPTIMIZATION: Убрали частицы для микрофона - слишком тяжело для производительности

        val waveY = if (selected && effectsEnabled && tab == NelevTab.FILTERS) sin(animPhase * 2.2f) * 2.5f else 0f

        Icon(
            when (tab) {
                NelevTab.CONNECT  -> IconsEx.Mic
                NelevTab.FILTERS  -> IconsEx.GraphicEq
                NelevTab.INFO     -> Icons.Filled.Info
                NelevTab.SETTINGS -> Icons.Filled.Settings
            },
            null,
            Modifier
                .size(20.dp)
                .scale(scale * breathScale)
                .graphicsLayer {
                    rotationZ = rotation
                    if (tab == NelevTab.FILTERS) {
                        translationY = waveY
                    }
                },
            tint = tint
        )
    }
}

@Composable
private fun AnimatedNavIcon(
    tab: NelevTab,
    tint: Color,
    trigger: Int,
    selected: Boolean,
    effectsEnabled: Boolean,
    globalPhase: Float
) {
    val pulseProgress = remember(tab) { Animatable(1f) }
    val selectedBoost by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 260 else 0, easing = FastOutSlowInEasing),
        label = "selected_$tab"
    )

    LaunchedEffect(trigger, effectsEnabled) {
        if (trigger == 0 || !effectsEnabled) {
            pulseProgress.snapTo(1f)
            return@LaunchedEffect
        }
        pulseProgress.snapTo(0f)
        pulseProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    val pulse = pulseProgress.value

    // Используем кэшированное значение вместо пересчета каждый кадр
    val continuousPhase = remember(globalPhase) { globalPhase * (2f * PI.toFloat()) }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (effectsEnabled) {
            Canvas(Modifier.matchParentSize()) {
                val ringAlpha = ((1f - pulse) * 0.16f + selectedBoost * 0.1f).coerceIn(0f, 0.24f)
                if (ringAlpha > 0.002f) {
                    drawCircle(
                        color = tint.copy(alpha = ringAlpha),
                        radius = size.minDimension * (0.28f + (1f - pulse) * 0.24f + selectedBoost * 0.08f),
                        center = center,
                        style = Stroke(width = size.minDimension * 0.08f)
                    )
                }
            }
        }

        Icon(
            when (tab) {
                NelevTab.CONNECT  -> IconsEx.Mic
                NelevTab.FILTERS  -> IconsEx.GraphicEq
                NelevTab.INFO     -> Icons.Filled.Info
                NelevTab.SETTINGS -> Icons.Filled.Settings
            },
            null,
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    val baseScale = 1f + selectedBoost * 0.08f + if (effectsEnabled) (1f - pulse) * 0.05f else 0f
                    scaleX = baseScale
                    scaleY = baseScale
                    translationY = -1.6f * selectedBoost
                    rotationZ = when {
                        !effectsEnabled -> 0f
                        tab == NelevTab.SETTINGS -> (1f - pulse) * 14f + sin(continuousPhase) * 2f
                        tab == NelevTab.INFO -> sin((1f - pulse) * PI.toFloat()) * 3.5f + cos(continuousPhase * 0.8f) * 1.5f
                        tab == NelevTab.FILTERS -> sin((1f - pulse) * PI.toFloat()) * 5f + sin(continuousPhase * 1.1f) * 2.5f
                        tab == NelevTab.CONNECT -> sin(continuousPhase * 0.9f) * 1.8f
                        else -> 0f
                    }
                    transformOrigin = TransformOrigin.Center
                },
            tint = tint
        )
    }
}

// ─── Connection bar ───────────────────────────────────────────────────────────
@Composable
fun ConnectionBar(isStreaming: Boolean, pcConnected: Boolean, colors: NelevColors, effectsEnabled: Boolean = true) {
    val base   = colors["connBase"]!!
    val active = colors["connActive"]!!
    val target = when { !isStreaming -> 0f; pcConnected -> 1f; else -> 0.5f }
    val prog   by animateFloatAsState(target, tween(if (effectsEnabled) 400 else 0, easing = FastOutSlowInEasing), label = "cp") // OPTIMIZATION: 600ms → 400ms

    var phonePulse by remember { mutableStateOf(1f) }
    LaunchedEffect(isStreaming, effectsEnabled) {
        if (!isStreaming || !effectsEnabled) { phonePulse = 1f; return@LaunchedEffect }
        // Пульс телефона: пересчёт на каждом кадре (120 fps), без искусственных пауз
        val start = withFrameMillis { it }
        while (isStreaming && effectsEnabled) {
            withFrameMillis { now ->
                val t = ((now - start) % 1600L) / 1600f
                phonePulse = 0.4f + 0.6f * abs(sin(t * PI.toFloat()))
            }
        }
        phonePulse = 1f
    }
    var heartPulse by remember { mutableStateOf(1f) }
    LaunchedEffect(isStreaming, pcConnected, effectsEnabled) {
        if (isStreaming && !pcConnected && effectsEnabled) {
            // Пульс «нет связи»: тоже покадрово, плавно на 120 Гц
            val start = withFrameMillis { it }
            while (isStreaming && !pcConnected && effectsEnabled) {
                withFrameMillis { now ->
                    val t = ((now - start) % 1000L) / 1000f
                    heartPulse = 0.3f + 0.7f * abs(sin(t * PI.toFloat()))
                }
            }
        }
        heartPulse = 1f
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIcon("phone", isStreaming, active, base, if (isStreaming) phonePulse else 1f)
        Box(Modifier.weight(1f).padding(horizontal = 5.dp)) { ConnectorLine((prog * 2f).coerceIn(0f, 1f), active, base) }
        val hc by animateColorAsState(if (prog >= 0.5f) active else base, tween(600), label = "hc")
        Canvas(Modifier.size(22.dp).alpha(if (isStreaming && !pcConnected) heartPulse else 1f)) { drawHeart(hc) }
        Box(Modifier.weight(1f).padding(horizontal = 5.dp)) { ConnectorLine(((prog - .5f) * 2f).coerceIn(0f, 1f), active, base) }
        val pcA by animateFloatAsState(if (pcConnected) 1f else 0.72f, tween(500), label = "pca")
        DeviceIcon("pc", pcConnected, active, base, pcA)
    }
}

@Composable
fun DeviceIcon(type: String, lit: Boolean, litColor: Color, dimColor: Color, alpha: Float) {
    val color by animateColorAsState(if (lit) litColor else dimColor, tween(300), label = "dc") // OPTIMIZATION: 500ms → 300ms
    Canvas(Modifier.size(28.dp).alpha(alpha)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (type == "phone") {
            drawPath(Path().apply { addRoundRect(RoundRect(w * .2f, 0f, w * .8f, h, w * .12f, w * .12f)) }, color, style = stroke)
            drawLine(color, Offset(w * .38f, h * .07f), Offset(w * .62f, h * .07f), 2f, StrokeCap.Round)
        } else {
            drawPath(Path().apply { addRoundRect(RoundRect(0f, 0f, w, h * .72f, w * .08f, w * .08f)) }, color, style = stroke)
            drawLine(color, Offset(w * .5f, h * .72f), Offset(w * .5f, h * .9f), 2.2f, StrokeCap.Round)
            drawLine(color, Offset(w * .28f, h * .9f), Offset(w * .72f, h * .9f), 2.2f, StrokeCap.Round)
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(color: Color) {
    val w = size.width; val h = size.height
    drawPath(Path().apply {
        moveTo(w * .5f, h * .82f)
        cubicTo(w * .1f, h * .55f, 0f, h * .32f, w * .25f, h * .18f)
        cubicTo(w * .38f, h * .08f, w * .5f, h * .2f, w * .5f, h * .2f)
        cubicTo(w * .5f, h * .2f, w * .62f, h * .08f, w * .75f, h * .18f)
        cubicTo(w, h * .32f, w, h * .55f, w * .5f, h * .82f)
        close()
    }, color)
}

@Composable
fun ConnectorLine(progress: Float, activeColor: Color, baseColor: Color) {
    val dotCount = 5
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { i ->
            val lit = progress >= (i + 1f) / (dotCount + 1f)
            val dc  by animateColorAsState(if (lit) activeColor else baseColor, tween(200, i * 40), label = "d$i") // OPTIMIZATION: 300ms → 200ms, delay 60 → 40
            val ds: Dp by animateDpAsState(if (lit) 5.dp else 3.5.dp, tween(200, i * 40), label = "ds$i")
            Box(Modifier.size(ds).clip(RoundedCornerShape(50)).background(dc))
        }
    }
}

// ─── Ping indicator ───────────────────────────────────────────────────────────

@Composable
fun PingBadge(latency: Long, isDark: Boolean) {
    val color = when {
        latency < 50  -> Color(0xFF4CAF50)
        latency < 150 -> Color(0xFFFFC107)
        else          -> Color(0xFFFF5252)
    }
    val label = "${latency}ms"

    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ─── Slider без конфликта со свайпом ─────────────────────────────────────────

@Composable
fun NoSwipeSlider(
    value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    sliderDragging: MutableState<Boolean>, enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(), modifier: Modifier = Modifier
) {
    var sliderWidthPx by remember { mutableStateOf(1f) }
    fun valueAt(positionX: Float): Float {
        val fraction = (positionX / sliderWidthPx).coerceIn(0f, 1f)
        return valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .onSizeChanged { sliderWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, valueRange.start, valueRange.endInclusive) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    sliderDragging.value = true
                    try {
                        onValueChange(valueAt(down.position.x))
                        down.consume()
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            ev.changes.forEach { change ->
                                onValueChange(valueAt(change.position.x))
                                change.consume()
                            }
                        } while (ev.changes.any { it.pressed })
                    } finally {
                        sliderDragging.value = false
                    }
                }
            }
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(vertical = 6.dp)
        )
    }
}

// ─── Кнопка Start/Stop с переливающимся градиентом ───────────────────────────
@Composable
fun NeonToggleButton(
    isRunning: Boolean, enabled: Boolean, lang: String,
    isDark: Boolean, effectsEnabled: Boolean,
    soundClickEnabled: Boolean, onClick: () -> Unit
) {
    val ctx = LocalContext.current
    val phase = rememberPhaseTick(effectsEnabled, 1f / 3f, 1f)

    val animScale by animateFloatAsState(
        if (isRunning) 0.985f else 1f,
        tween(if (effectsEnabled) 150 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 220ms → 150ms
        label = "sc"
    )

    // Переливающиеся цвета как у Telegram
    val gradientColorsRaw = if (!isRunning) {
        if (isDark) {
            listOf(
                Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFEEEEEE),
                Color(0xFFF8F8F8), Color(0xFFFFFFFF)
            )
        } else {
            listOf(
                Color(0xFFFFB6C1), Color(0xFFFF69B4), Color(0xFFFFAEC9),
                Color(0xFFFFC1CC), Color(0xFFFFB6C1)
            )
        }
    } else {
        if (isDark) {
            listOf(
                Color(0xFF383838), Color(0xFF2A2A2A), Color(0xFF1A1A1A),
                Color(0xFF303030), Color(0xFF383838)
            )
        } else {
            listOf(
                Color(0xFFD5698C), Color(0xFFC85A7E), Color(0xFFDB7A9A),
                Color(0xFFCE6485), Color(0xFFD5698C)
            )
        }
    }
    val btnSpec: AnimationSpec<Color> = tween(if (effectsEnabled) 200 else 0, easing = FastOutSlowInEasing)
    val ag0 by animateColorAsState(gradientColorsRaw[0], btnSpec, label = "bg0")
    val ag1 by animateColorAsState(gradientColorsRaw[1], btnSpec, label = "bg1")
    val ag2 by animateColorAsState(gradientColorsRaw[2], btnSpec, label = "bg2")
    val ag3 by animateColorAsState(gradientColorsRaw[3], btnSpec, label = "bg3")
    val ag4 by animateColorAsState(gradientColorsRaw[4], btnSpec, label = "bg4")
    val gradientColors = listOf(ag0, ag1, ag2, ag3, ag4)

    val colorIndex = (phase * gradientColors.size).toInt() % gradientColors.size
    val nextColorIndex = (colorIndex + 1) % gradientColors.size
    val colorProgress = (phase * gradientColors.size) % 1f

    val currentColor = if (effectsEnabled) {
        lerp(gradientColors[colorIndex], gradientColors[nextColorIndex], colorProgress)
    } else {
        gradientColors[0]
    }

    val nextColor = if (effectsEnabled) {
        lerp(gradientColors[nextColorIndex], gradientColors[(nextColorIndex + 1) % gradientColors.size], colorProgress)
    } else {
        gradientColors[1]
    }

    val w = 1200f
    val textColor = if (!isRunning && isDark) Color.Black else Color.White
    val labelText = remember(lang, isRunning) {
        if (isRunning) Strings.get("stop", lang) else Strings.get("start", lang)
    }
    val buttonShape = RoundedCornerShape(24.dp)

    Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = { if (soundClickEnabled) ClickPlayer.play(ctx); onClick() },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(66.dp).scale(animScale),
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = textColor,
                disabledContainerColor = Color(0xFF181818),
                disabledContentColor = Color(0xFF444444)
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(currentColor, nextColor),
                            start = Offset(0f, 0f),
                            end = Offset(w, 66f)
                        ),
                        buttonShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), buttonShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                            ),
                            buttonShape
                        )
                )
                Text(labelText, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
            }
        }
    }
}

// ─── Кнопка мута ─────────────────────────────────────────────────────────────

@Composable
fun MicMuteButton(
    isMuted: Boolean,
    cardBg: Color,
    iconColor: Color,
    soundClickEnabled: Boolean,
    effectsEnabled: Boolean,
    onToggle: () -> Unit
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(28.dp)
    val rotation by animateFloatAsState(
        if (isMuted) 45f else 0f,
        tween(if (effectsEnabled) 150 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 240ms → 150ms
        label = "r"
    )
    val scale by animateFloatAsState(
        if (isMuted) 0.93f else 1.02f,
        tween(if (effectsEnabled) 120 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 200ms → 120ms
        label = "s"
    )
    val buttonBg by animateColorAsState(
        if (isMuted) {
            if (cardBg.luminance() < 0.2f) Color(0xFF171717) else Color(0xFFF3EDE2)
        } else {
            cardBg
        },
        tween(if (effectsEnabled) 120 else 0), // OPTIMIZATION: 200ms → 120ms
        label = "muteBg"
    )
    val outline by animateColorAsState(
        if (isMuted) Color(0xFFE79BC0).copy(alpha = 0.42f)
        else if (cardBg.luminance() < 0.2f) Color.White.copy(alpha = 0.08f)
        else Color.Black.copy(alpha = 0.08f),
        tween(if (effectsEnabled) 120 else 0), // OPTIMIZATION: 200ms → 120ms
        label = "muteOutline"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        if (isPressed) 0.94f else 1f,
        tween(if (effectsEnabled) 80 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 120ms → 80ms - мгновенный отклик
        label = "pressScale"
    )

    Box(
        Modifier
            .size(88.dp)
            .scale(pressScale)
            .clip(shape)
            .background(buttonBg)
            .border(1.dp, outline, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            if (soundClickEnabled) ClickPlayer.play(ctx)
                            onToggle()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            IconsEx.Mic,
            null,
            Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            if (isMuted) Color(0xFFD28AAF) else iconColor
        )
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }, contentAlignment = Alignment.Center) {
            if (isMuted) Box(Modifier.width(54.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE79BC0)))
        }
    }
}

// ─── Искры ───────────────────────────────────────────────────────────────────

data class SparkParticle(val angle: Float, val speed: Float, val length: Float, val startTime: Long)
data class HeartBurstParticle(val angle: Float, val speed: Float, val size: Float, val delayMs: Long, val startTime: Long)
private fun ClosedFloatingPointRange<Float>.random() = start + (endInclusive - start) * kotlin.random.Random.nextFloat()

// ─── Фоновые кометы ──────────────────────────────────────────────────────────

data class Comet(val id: Int, val startX: Float, val startY: Float, val angle: Float, val speed: Float, val length: Float, val startTime: Long)

@Composable
fun BackgroundComets(isDark: Boolean, effectsEnabled: Boolean) {
    // OPTIMIZATION: Полностью убрали кометы - слишком тяжело для производительности
    // Приложение теперь летает без фоновых эффектов
}

// ─── Карточка с фразой ────────────────────────────────────────────────────────

@Composable
fun PhraseCard(
    phrase: String,
    colors: NelevColors,
    effectsEnabled: Boolean,
    heartBurstEnabled: Boolean = false,
    onTap: () -> Unit
) {
    val bgColor   = colors["card"]!!
    val textColor = colors["text"]!!
    val sparkBase = colors["text"]!!
    val shape = RoundedCornerShape(24.dp)
    val outline   = colors["border"]!!
    var heartBurstSize by remember { mutableStateOf(IntSize.Zero) }

    var displayText by remember { mutableStateOf("") }
    LaunchedEffect(phrase, effectsEnabled) {
        if (!effectsEnabled) {
            displayText = phrase
            return@LaunchedEffect
        }
        displayText = ""
        for (i in phrase.indices) {
            delay(15)
            displayText = phrase.take(i + 1)
        }
    }

    var sparks by remember { mutableStateOf<List<SparkParticle>>(emptyList()) }
    var heartBurst by remember { mutableStateOf<List<HeartBurstParticle>>(emptyList()) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val clock = remember { mutableStateOf(0L) }

    LaunchedEffect(phrase, heartBurstEnabled, effectsEnabled) {
        if (heartBurstEnabled && effectsEnabled) {
            val now = System.currentTimeMillis()
            heartBurst = (0 until 24).map {
                HeartBurstParticle(
                    angle = (205f..335f).random(),
                    speed = (1.3f..3.9f).random(),
                    size = (15f..31f).random(),
                    delayMs = (it % 7) * 28L,
                    startTime = now
                )
            }
        }
    }

    LaunchedEffect(sparks, heartBurst, effectsEnabled) {
        if ((sparks.isNotEmpty() || heartBurst.isNotEmpty()) && effectsEnabled) {
            val end = System.currentTimeMillis() + 1250
            while (System.currentTimeMillis() < end) {
                clock.value = System.currentTimeMillis()
                withFrameMillis { it }
            }
            sparks = emptyList()
            heartBurst = emptyList()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInRoot()
                val target = Offset(
                    pos.x + coordinates.size.width / 2f,
                    pos.y + coordinates.size.height / 2f
                )
                if (EasterEggGuide.phraseBoxCenter.value != target) {
                    EasterEggGuide.phraseBoxCenter.value = target
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bgColor)
                .border(1.dp, outline, shape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { off ->
                        if (effectsEnabled) {
                            tapOffset = off
                            val now = System.currentTimeMillis()
                            sparks = (0 until 6).map {
                                SparkParticle(it * 60f + (-10f..10f).random(), (2f..3.5f).random(), (6f..12f).random(), now)
                            }
                        }
                        onTap()
                    })
                }
                .padding(horizontal = 18.dp, vertical = 15.dp)
        ) {
            if (effectsEnabled && sparks.isNotEmpty()) {
                Canvas(Modifier.matchParentSize()) {
                    val now = clock.value
                    sparks.forEach { sp ->
                        val elapsed = (now - sp.startTime) / 600f
                        if (elapsed in 0f..1f) {
                            val rad  = (sp.angle * PI / 180f).toFloat()
                            val dist = sp.speed * elapsed * 60f
                            val cx   = tapOffset.x + dist * sin(rad); val cy = tapOffset.y - dist * cos(rad)
                            val td   = dist - sp.length.coerceAtMost(dist)
                            val tx   = tapOffset.x + td * sin(rad); val ty = tapOffset.y - td * cos(rad)
                            drawLine(
                                sparkBase.copy(alpha = ((1f - elapsed).pow(2)).coerceIn(0f, 1f) * 0.7f),
                                Offset(tx, ty),
                                Offset(cx, cy),
                                sp.length * 0.12f,
                                StrokeCap.Round
                            )
                        }
                    }
                }
            }
            Text(
                displayText,
                fontSize = 13.sp,
                color = textColor,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }

        if (effectsEnabled && heartBurst.isNotEmpty()) {
            val now = clock.value
            Box(
                Modifier
                    .matchParentSize()
                    .onSizeChanged { heartBurstSize = it }
            ) {
                val boxWidthPx = heartBurstSize.width.toFloat()
                val boxHeightPx = heartBurstSize.height.toFloat()
                heartBurst.forEach { heart ->
                    val elapsed = ((now - heart.startTime - heart.delayMs) / 900f).coerceIn(0f, 1.2f)
                    if (elapsed in 0f..1f) {
                        val rad = (heart.angle * PI / 180f).toFloat()
                        val dist = heart.speed * elapsed * 72f
                        Text(
                            "♡",
                            fontSize = heart.size.sp,
                            color = Color(0xFFFF69B4).copy(alpha = (1f - elapsed).pow(1.35f).coerceIn(0f, 1f)),
                            modifier = Modifier.offset {
                                IntOffset(
                                    (boxWidthPx / 2f + dist * cos(rad)).roundToInt(),
                                    (boxHeightPx / 2f + dist * sin(rad) - elapsed * 42f).roundToInt()
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── IP поле ──────────────────────────────────────────────────────────────────

@Composable
fun BasicIpField(
    value: String, onChange: (String) -> Unit, hint: String, label: String,
    textColor: Color, hintColor: Color, cursorColor: Color
) {
    Column {
        Text(label, fontSize = 11.sp, color = hintColor, modifier = Modifier.padding(bottom = 2.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 16.sp),
            cursorBrush = SolidColor(cursorColor),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (value.isEmpty()) Text(hint, color = hintColor, fontSize = 16.sp)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun lerp(a: Color, b: Color, t: Float) = Color(
    (a.red + (b.red - a.red) * t).coerceIn(0f, 1f),
    (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f), 1f)

fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ─── Connect screen ───────────────────────────────────────────────────────────

@Composable
fun ConnectScreen(
    state: NelevAppState,
    appSettings: MutableState<AppSettings>,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    sliderDragging: MutableState<Boolean>,
    effectsEnabled: Boolean,
    isTransitioning: Boolean,
    setTransitioning: (Boolean) -> Unit,
    startService: (String) -> Unit,
    stopService: () -> Unit,
    menuCentered: Boolean = false,
    onScanQr: () -> Unit = {}
) {
    val bg = colors["bg"]!!; val card = colors["card"]!!; val text = colors["text"]!!
    val muted = colors["muted"]!!; val border = colors["border"]!!; val accent = colors["accent"]!!
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val heroShape = RoundedCornerShape(36.dp)
    val insetShape = RoundedCornerShape(28.dp)
    val heroBrush = Brush.linearGradient(
        listOf(card, bg, card),
        start = Offset.Zero,
        end = Offset(1200f, 900f)
    )
    val heroStroke = border

    var hasMicPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(state.isStreaming.value) {
        if (!state.isStreaming.value) {
            while (!state.isStreaming.value) {
                hasMicPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                delay(600)
            }
        }
    }

    val volumeAnim by animateFloatAsState(
        state.volumeTarget.value,
        if (effectsEnabled) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
        label = "vol"
    )
    LaunchedEffect(volumeAnim) { state.volume.value = volumeAnim }

    fun snapVolume(raw: Float): Float {
        val pct = raw.coerceIn(0f, 2f) * 100f
        val near = listOf(0f, 100f, 200f).minByOrNull { abs(it - pct) }!!
        return if (abs(near - pct) <= 6f) near / 100f else raw.coerceIn(0f, 2f)
    }

    val volPct = (state.volume.value * 100).roundToInt().coerceIn(0, 200)
    val contentSpacing = 16.dp
    val statusText = if (state.pcConnected.value) Strings.get("connected_pc", lang) else Strings.get("waiting_pc", lang)
    val statusColor = if (state.pcConnected.value) Color(0xFF4CAF50) else muted
    val motion = if (effectsEnabled) 260 else 0
    val statusChipBg by animateColorAsState(
        when {
            state.pcConnected.value -> if (isDark) Color.White else Color(0xFFE79BC0)
            state.isStreaming.value -> if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE79BC0).copy(alpha = 0.14f)
            else -> Color.Transparent
        },
        tween(motion),
        label = "statusChipBg"
    )
    val statusChipText by animateColorAsState(
        text,
        tween(motion),
        label = "statusChipText"
    )

    val waitingVisible = state.isStreaming.value && !state.pcConnected.value
    val waitingAlpha by animateFloatAsState(
        targetValue = if (waitingVisible) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 120 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 190ms → 120ms
        label = "waitingAlpha"
    )

    val pingVisible by remember {
        derivedStateOf { state.isStreaming.value && state.pcConnected.value && state.latencyMs.value >= 0L }
    }
    val pingAlpha by animateFloatAsState(
        targetValue = if (pingVisible) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 120 else 0, easing = FastOutSlowInEasing), // OPTIMIZATION: 170ms → 120ms
        label = "pingAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = if (menuCentered) Alignment.Center else Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var shouldExpand by remember { mutableStateOf(false) }
            var hasAnimatedOnce by remember { mutableStateOf(false) }
            
            LaunchedEffect(state.isStreaming.value, state.pcConnected.value) {
                if (state.isStreaming.value || state.pcConnected.value) {
                    shouldExpand = true
                    if (!hasAnimatedOnce) {
                        hasAnimatedOnce = true
                    }
                } else {
                    delay(300)
                    shouldExpand = false
                }
            }
            
            val heroHeightTarget by animateDpAsState(
                targetValue = 280.dp,
                animationSpec = tween(0),
                label = "heroHeight"
            )
            
            var showChip by remember { mutableStateOf(state.isStreaming.value || state.pcConnected.value) }
            
            LaunchedEffect(shouldExpand) {
                if (shouldExpand) {
                    delay(150)
                    showChip = true
                } else {
                    showChip = false
                }
            }
            
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = heroHeightTarget)
                    .clip(heroShape)
                    .background(heroBrush)
                    .border(1.dp, heroStroke, heroShape)
                    .drawWithContent {
                        drawContent()
                        // Liquid glass highlight
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.05f else 0.08f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.3f
                            ),
                            cornerRadius = CornerRadius(32.dp.toPx(), 32.dp.toPx())
                        )
                    }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedContent(
                                    targetState = showChip,
                                    transitionSpec = {
                                        fadeIn(tween(if (effectsEnabled) 300 else 0)) togetherWith fadeOut(tween(if (effectsEnabled) 200 else 0))
                                    },
                                    label = "chipAppearance"
                                ) { show ->
                                    if (show) {
                                        AnimatedContent(
                                            targetState = when {
                                                state.pcConnected.value -> "connected"
                                                state.isStreaming.value -> "waiting"
                                                else -> "idle"
                                            },
                                            transitionSpec = {
                                                fadeIn(tween(if (effectsEnabled) 200 else 0)) togetherWith fadeOut(tween(if (effectsEnabled) 150 else 0))
                                            },
                                            label = "connectionStatus"
                                        ) { status ->
                                            Text(
                                                when (status) {
                                                    "connected" -> Strings.get("connected_pc", lang)
                                                    "waiting" -> Strings.get("waiting_pc", lang)
                                                    else -> Strings.get("connect_title", lang)
                                                },
                                                fontSize = 28.sp,
                                                lineHeight = 30.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusChipText
                                            )
                                        }
                                    } else {
                                        AnimatedContent(
                                            targetState = when {
                                                state.pcConnected.value -> "connected"
                                                state.isStreaming.value -> "waiting"
                                                else -> "idle"
                                            },
                                            transitionSpec = {
                                                fadeIn(tween(if (effectsEnabled) 200 else 0)) togetherWith fadeOut(tween(if (effectsEnabled) 150 else 0))
                                            },
                                            label = "connectionStatus"
                                        ) { status ->
                                            Text(
                                                when (status) {
                                                    "connected" -> Strings.get("connected_pc", lang)
                                                    "waiting" -> Strings.get("waiting_pc", lang)
                                                    else -> Strings.get("connect_title", lang)
                                                },
                                                fontSize = 28.sp,
                                                lineHeight = 30.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = text
                                            )
                                        }
                                    }
                                }
                                
                                Text(
                                    Strings.get("connect_subtitle", lang),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = muted,
                                    maxLines = 2,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }

                            if (state.isStreaming.value || state.pcConnected.value) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 5.dp, y = 8.dp)
                                        .zIndex(10f)
                                ) {
                                    PingBadgeSlot(latencyMs = state.latencyMs, isDark = isDark, effectsEnabled = effectsEnabled)
                                }
                            }
                        }
                    }

                    if (appSettings.value.connectionBarEnabled) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(insetShape)
                                .background(card)
                                .border(1.dp, heroStroke, insetShape)
                                .padding(vertical = 8.dp)
                        ) {
                            ConnectionBar(
                                isStreaming = state.isStreaming.value,
                                pcConnected = state.pcConnected.value,
                                colors = colors,
                                effectsEnabled = effectsEnabled
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .weight(0.9f)
                                .clip(insetShape)
                                .background(card)
                                .border(1.dp, border, insetShape)
                                .clickable(remember { MutableInteractionSource() }, null) {}
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            BasicIpField(
                                value = state.ip.value,
                                onChange = { state.ip.value = it; NelevService.targetIp = it },
                                hint = Strings.get("ip_hint", lang),
                                label = Strings.get("ip_label", lang),
                                textColor = text,
                                hintColor = muted,
                                cursorColor = text
                            )
                        }

                        // QR-code scan button
                        val qrShape = RoundedCornerShape(20.dp)
                        var qrPressed by remember { mutableStateOf(false) }
                        val qrScale by animateFloatAsState(
                            if (qrPressed) 0.90f else 1f,
                            tween(if (effectsEnabled) 100 else 0, easing = FastOutSlowInEasing),
                            label = "qrScale"
                        )
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(56.dp)
                                .scale(qrScale)
                                .clip(qrShape)
                                .background(card)
                                .border(1.dp, border, qrShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            qrPressed = true
                                            val released = tryAwaitRelease()
                                            qrPressed = false
                                            if (released) {
                                                if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                                onScanQr()
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                IconsEx.QrCodeScanner,
                                contentDescription = if (lang == "ru") "Сканировать QR" else "Scan QR",
                                modifier = Modifier.size(28.dp),
                                tint = accent.copy(alpha = if (isDark) 0.7f else 1f)
                            )
                        }
                    }

                    AnimatedVisibility(
                        !hasMicPerm,
                        enter = fadeIn(tween(if (effectsEnabled) 250 else 0)) + expandVertically(tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing)), // OPTIMIZATION: 400ms → 250ms
                        exit  = fadeOut(tween(if (effectsEnabled) 250 else 0)) + shrinkVertically(tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0x22FF4444)
                        ) {
                            Text(
                                text = Strings.get("mic_permission", lang),
                                color = Color(0xFFFF6D6D),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(14.dp).clickable {
                                    ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", ctx.packageName, null)
                                    })
                                }
                            )
                        }
                    }

                    NeonToggleButton(
                        isRunning = state.isStreaming.value,
                        enabled = (state.isStreaming.value || (state.ip.value.isNotBlank() && hasMicPerm)) && !isTransitioning,
                        lang = lang,
                        isDark = isDark,
                        effectsEnabled = effectsEnabled,
                        soundClickEnabled = appSettings.value.soundClickEnabled
                    ) {
                        if (isTransitioning) return@NeonToggleButton
                        setTransitioning(true)
                        scope.launch {
                            hasMicPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!state.isStreaming.value) {
                                if (!hasMicPerm) { setTransitioning(false); return@launch }
                                state.isStreaming.value = true
                                if (appSettings.value.phrasesEnabled) state.currentPhrase.value = Phrases.next(lang)
                            } else {
                                state.isStreaming.value = false
                                if (appSettings.value.phrasesEnabled) state.currentPhrase.value = Phrases.next(lang)
                            }
                            delay(if (effectsEnabled) 200 else 1) // OPTIMIZATION: 350ms → 200ms - мгновенный отклик
                            setTransitioning(false)
                        }
                    }
                }
            }

            LaunchedEffect(lang) {
                if (state.currentPhrase.value != null && appSettings.value.phrasesEnabled) state.currentPhrase.value = Phrases.next(lang)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(card)
                    .border(1.dp, border, RoundedCornerShape(30.dp))
                    .drawWithContent {
                        drawContent()
                        // Liquid glass highlight
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.04f else 0.06f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.4f
                            ),
                            cornerRadius = CornerRadius(30.dp.toPx(), 30.dp.toPx())
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${Strings.get("volume", lang)}  $volPct%",
                            color = text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedVisibility(
                            visible = state.isMuted.value,
                            enter = fadeIn(tween(if (effectsEnabled) 120 else 0)), // OPTIMIZATION: 160ms → 120ms
                            exit = fadeOut(tween(if (effectsEnabled) 80 else 0)) // OPTIMIZATION: 120ms → 80ms
                        ) {
                            Text(
                                text = if (lang == "en") "Muted" else "Выключено",
                                color = muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NoSwipeSlider(
                                value = volumeAnim,
                                onValueChange = { state.volumeTarget.value = snapVolume(it); FpsLogger.trackInteraction("volume→${"%.2f".format(snapVolume(it))}") },
                                valueRange = 0f..2f,
                                sliderDragging = sliderDragging,
                                enabled = !state.isMuted.value,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (!state.isMuted.value) accent else muted,
                                    activeTrackColor = if (!state.isMuted.value) accent else muted,
                                    inactiveTrackColor = border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("0%", color = muted, fontSize = 12.sp)
                                Text("100%", color = muted, fontSize = 12.sp)
                                Text("200%", color = muted, fontSize = 12.sp)
                            }
                        }

                        MicMuteButton(
                            state.isMuted.value,
                            if (isDark) Color(0xFF151515) else Color(0xFFFFFFFF),
                            text,
                            appSettings.value.soundClickEnabled,
                            effectsEnabled
                        ) {
                            state.isMuted.value = !state.isMuted.value
                            FpsLogger.trackInteraction("mute→${state.isMuted.value}")
                        }
                    }
                }
            }

            AnimatedVisibility(
                state.currentPhrase.value != null && (appSettings.value.phrasesEnabled || EasterEggGuide.guidedTourTarget.value == GuidedTourTarget.PHRASE_BOX),
                enter = fadeIn(tween(if (effectsEnabled) 300 else 0)) + expandVertically(tween(if (effectsEnabled) 300 else 0, easing = FastOutSlowInEasing)), // OPTIMIZATION: 500ms → 300ms
                exit  = fadeOut(tween(if (effectsEnabled) 250 else 0)) + shrinkVertically(tween(if (effectsEnabled) 250 else 0)) // OPTIMIZATION: 400ms → 250ms
            ) {
                state.currentPhrase.value?.let { phrase ->
                    PhraseCard(
                        phrase = phrase,
                        colors = colors,
                        effectsEnabled = effectsEnabled,
                        heartBurstEnabled = phrase == easterLovePhrase(lang) && EasterEggGuide.guidedTourTarget.value == GuidedTourTarget.PHRASE_BOX
                    ) {
                        if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                        state.currentPhrase.value = Phrases.next(lang)
                    }
                }
            }
        }
    }
}

// ─── Filters screen ───────────────────────────────────────────────────────────
@Composable
fun FiltersScreen(
    state: NelevAppState, colors: NelevColors, lang: String,
    sliderDragging: MutableState<Boolean>, soundClickEnabled: Boolean, effectsEnabled: Boolean,
    isDark: Boolean, menuCentered: Boolean = true
) {
    val bg = colors["bg"]!!; val card = colors["card"]!!; val text = colors["text"]!!; val muted = colors["muted"]!!; val border = colors["border"]!!; val accent = colors["accent"]!!
    val ctx = LocalContext.current

    fun snapPitch(v: Float) = if (abs(v) <= 0.07f) 0f else v
    fun snapEffect(v: Float): Float { val pct=v*100f; val near=listOf(0f,100f).minByOrNull{ abs(it-pct)}!!; return if(abs(near-pct)<=7f) near/100f else v }
    fun snapBipolar(v: Float): Float { val pct=v*100f; val near=listOf(-100f,0f,100f).minByOrNull{ abs(it-pct)}!!; return if(abs(near-pct)<=7f) near/100f else v }

    val filterAnimSpec: AnimationSpec<Float> = if (effectsEnabled) {
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    } else {
        tween(0)
    }
    val reverbAnim by animateFloatAsState(state.reverbTarget.value, filterAnimSpec, label = "rev")
    val bassAnim   by animateFloatAsState(state.bassTarget.value,   filterAnimSpec, label = "bas")
    val pitchAnim  by animateFloatAsState(state.pitchTarget.value,  filterAnimSpec, label = "pit")
    val eqLowAnim  by animateFloatAsState(state.eqLowTarget.value,  filterAnimSpec, label = "eql")
    val eqMidAnim  by animateFloatAsState(state.eqMidTarget.value,  filterAnimSpec, label = "eqm")
    val eqHighAnim by animateFloatAsState(state.eqHighTarget.value, filterAnimSpec, label = "eqh")
    LaunchedEffect(reverbAnim, bassAnim, pitchAnim, eqLowAnim, eqMidAnim, eqHighAnim) {
        state.reverb.value = reverbAnim
        state.bass.value   = bassAnim
        state.pitch.value  = pitchAnim
        state.eqLow.value  = eqLowAnim
        state.eqMid.value  = eqMidAnim
        state.eqHigh.value = eqHighAnim
    }

    @Composable
    fun FilterRow(labelKey: String, value: Float, onDrag: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, snap: ((Float) -> Float)? = null) {
        Text("${Strings.get(labelKey, lang)}: ${(value * 100).roundToInt()}%", color = text, fontSize = 14.sp)
        NoSwipeSlider(value, { onDrag(snap?.invoke(it) ?: it) }, range, sliderDragging,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = border
            ),
            modifier = Modifier.fillMaxWidth())
    }
    @Composable
    fun EnhancerRow(labelKey: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { if (soundClickEnabled) ClickPlayer.play(ctx); onCheckedChange(!checked) }.padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.get(labelKey, lang), color = text, fontSize = 13.sp, modifier = Modifier.weight(1f).clickable { if (soundClickEnabled) ClickPlayer.play(ctx); onCheckedChange(!checked) })
            GlassSwitch(checked = checked, onCheckedChange = { if (soundClickEnabled) ClickPlayer.play(ctx); onCheckedChange(it) }, isDark = isDark, effectsEnabled = effectsEnabled)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = if (menuCentered) Alignment.Center else Alignment.TopCenter
    ) {
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        var resetPressed by remember { mutableStateOf(false) }
        var undoPressed by remember { mutableStateOf(false) }
        var snapshot by remember { mutableStateOf<Prefs.State?>(null) }
        var showUndo by remember { mutableStateOf(false) }
        val resetScale by animateFloatAsState(
            if (resetPressed) 0.94f else 1f,
            if (effectsEnabled) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
            label = "resetScale"
        )
        val undoScale by animateFloatAsState(
            if (undoPressed) 0.94f else 1f,
            if (effectsEnabled) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
            label = "undoScale"
        )
        LaunchedEffect(showUndo) { if (showUndo) { delay(4000); showUndo = false } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(Strings.get("filters_title", lang), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = showUndo,
                    enter = fadeIn(tween(if (effectsEnabled) 180 else 0)) + scaleIn(tween(if (effectsEnabled) 180 else 0)),
                    exit = fadeOut(tween(if (effectsEnabled) 150 else 0)) + scaleOut(tween(if (effectsEnabled) 150 else 0))) {
                    OutlinedButton(onClick = {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        undoPressed = true
                        snapshot?.let { snap ->
                            state.reverbTarget.value = snap.reverb; state.bassTarget.value = snap.bass; state.pitchTarget.value = snap.pitch
                            state.eqLowTarget.value = snap.eqLow; state.eqMidTarget.value = snap.eqMid; state.eqHighTarget.value = snap.eqHigh
                            state.enhancerDc.value = snap.enhancerDc; state.enhancerHp.value = snap.enhancerHp; state.enhancerLimiter.value = snap.enhancerLimiter; state.enhancerGate.value = snap.enhancerGate
                        }
                        showUndo = false
                    },
                        modifier = Modifier.scale(undoScale),
                        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent)) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp), accent)
                    }
                }
                OutlinedButton(onClick = {
                    if (soundClickEnabled) ClickPlayer.play(ctx)
                    // сохранить снапшот для отмены
                    snapshot = Prefs.State(ip = state.ip.value, volume = state.volume.value,
                        reverb = state.reverbTarget.value, bass = state.bassTarget.value, pitch = state.pitchTarget.value,
                        enhancerDc = state.enhancerDc.value, enhancerHp = state.enhancerHp.value, enhancerLimiter = state.enhancerLimiter.value, enhancerGate = state.enhancerGate.value,
                        eqLow = state.eqLowTarget.value, eqMid = state.eqMidTarget.value, eqHigh = state.eqHighTarget.value)
                    resetPressed = true
                    state.reverbTarget.value = 0f; state.bassTarget.value = 0f; state.pitchTarget.value = 0f
                    state.eqLowTarget.value = 0f; state.eqMidTarget.value = 0f; state.eqHighTarget.value = 0f
                    state.enhancerDc.value = false; state.enhancerHp.value = false; state.enhancerLimiter.value = false; state.enhancerGate.value = false
                    showUndo = true
                },
                    modifier = Modifier.scale(resetScale),
                    shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = muted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, border)) {
                    Text(Strings.get("reset", lang), fontSize = 13.sp, color = muted)
                }
            }
        }
        LaunchedEffect(resetPressed) {
            if (!resetPressed) return@LaunchedEffect
            delay(130)
            resetPressed = false
        }
        LaunchedEffect(undoPressed) {
            if (!undoPressed) return@LaunchedEffect
            delay(130)
            undoPressed = false
        }
        // ── Запись микрофона 30с (с активными фильтрами) ──
        val recScope = rememberCoroutineScope()
        var isRecording by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(false) }
        var autoPlay by remember { mutableStateOf(false) }
        var recordSeconds by remember { mutableStateOf(0) }
        var recordFile by remember { mutableStateOf<java.io.File?>(null) }
        var recordStop by remember { mutableStateOf(false) }
        var recordJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }
        var hasMicPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }

        fun playRecorded(f: java.io.File) {
            try {
                playerRef?.release()
                playerRef = MediaPlayer().apply {
                    setDataSource(f.absolutePath); prepare(); start()
                    setOnCompletionListener { isPlaying = false; it.release(); playerRef = null }
                }
                isPlaying = true
            } catch (_: Exception) {}
        }

        LaunchedEffect(isRecording) {
            if (!isRecording) return@LaunchedEffect
            recordSeconds = 0
            while (isRecording && recordSeconds < 30) { delay(1000); if (isRecording) recordSeconds++ }
            if (isRecording && recordSeconds >= 30) { recordStop = true; isRecording = false }
        }
        DisposableEffect(Unit) {
            onDispose {
                recordStop = true
                try { recordJob?.cancel() } catch (_: Exception) {}
                try { playerRef?.release() } catch (_: Exception) {}
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(card).border(1.dp, border, RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    drawRoundRect(brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (isDark) 0.04f else 0.06f), Color.Transparent), startY = 0f, endY = size.height*0.4f), cornerRadius = CornerRadius(28.dp.toPx(),28.dp.toPx()))
                }.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isRecording) Strings.get("record_active", lang).replace("%d", recordSeconds.toString()) else if (recordFile != null) Strings.get("record_done", lang).replace("%d", recordSeconds.toString()) else Strings.get("record_title", lang), color = if (isRecording) Color(0xFFFF4444) else text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Strings.get("record_auto", lang), color = muted, fontSize = 11.sp)
                        GlassSwitch(checked = autoPlay, onCheckedChange = { if (soundClickEnabled) ClickPlayer.play(ctx); autoPlay = it }, isDark = isDark, effectsEnabled = effectsEnabled, modifier = Modifier.scale(0.75f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val recScale by animateFloatAsState(if (isRecording) 0.96f else 1f, tween(if (effectsEnabled) 120 else 0), label = "recScale")
                    OutlinedButton(onClick = {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        hasMicPerm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (!hasMicPerm) { Toast.makeText(ctx, Strings.get("mic_permission", lang), Toast.LENGTH_SHORT).show(); return@OutlinedButton }
                        if (isRecording) {
                            recordStop = true
                            isRecording = false
                        } else {
                            try { playerRef?.release(); playerRef = null; isPlaying = false } catch (_: Exception) {}
                            val f = java.io.File(ctx.cacheDir, "nelev_preview_${System.currentTimeMillis()}.wav")
                            recordFile = f
                            recordSeconds = 0
                            recordStop = false
                            isRecording = true
                            recordJob = recScope.launch {
                                try {
                                    recordFilteredWav(
                                        file = f,
                                        maxSeconds = 30,
                                        params = {
                                            RecordFilters(
                                                volume = state.volume.value,
                                                reverb = state.reverb.value,
                                                bass = state.bass.value,
                                                pitch = state.pitch.value,
                                                eqLow = state.eqLow.value,
                                                eqMid = state.eqMid.value,
                                                eqHigh = state.eqHigh.value,
                                                enhancerDc = state.enhancerDc.value,
                                                enhancerHp = state.enhancerHp.value,
                                                enhancerLimiter = state.enhancerLimiter.value,
                                                enhancerGate = state.enhancerGate.value,
                                                isMuted = state.isMuted.value
                                            )
                                        },
                                        isCancelled = { recordStop }
                                    )
                                } finally {
                                    isRecording = false
                                    if (autoPlay && f.exists() && f.length() > 44L) playRecorded(f)
                                }
                            }
                        }
                    }, modifier = Modifier.scale(recScale), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isRecording) Color(0xFFFF4444) else accent), border = androidx.compose.foundation.BorderStroke(1.dp, if (isRecording) Color(0xFFFF4444) else accent)) {
                        Text(if (isRecording) Strings.get("record_stop", lang) else Strings.get("record_start", lang), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val playScale by animateFloatAsState(if (isPlaying) 0.96f else 1f, tween(if (effectsEnabled) 120 else 0), label = "playScale")
                    OutlinedButton(onClick = {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        val f = recordFile
                        if (f == null || !f.exists()) { Toast.makeText(ctx, Strings.get("record_no_data", lang), Toast.LENGTH_SHORT).show(); return@OutlinedButton }
                        if (isPlaying) { try { playerRef?.stop(); playerRef?.release() } catch (_: Exception) {}; playerRef = null; isPlaying = false }
                        else {
                            try {
                                playerRef?.release()
                                playerRef = MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); start(); setOnCompletionListener { isPlaying = false; it.release(); playerRef = null } }
                                isPlaying = true
                            } catch (e: Exception) { Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }, modifier = Modifier.scale(playScale), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = muted), border = androidx.compose.foundation.BorderStroke(1.dp, border), enabled = recordFile != null && !isRecording) {
                        Text(if (isPlaying) Strings.get("record_stop", lang) else Strings.get("record_play", lang), fontSize = 13.sp)
                    }
                }
                if (isRecording) Text(Strings.get("record_hint", lang), fontSize = 11.sp, color = muted)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(card)
                .border(1.dp, border, RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (isDark) 0.04f else 0.06f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.4f
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                    )
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FilterRow("reverb", reverbAnim, { state.reverbTarget.value = snapEffect(it); FpsLogger.trackInteraction("reverb→${"%.2f".format(snapEffect(it))}") }, 0f..1f, snap = ::snapEffect)
                FilterRow("bass",   bassAnim,   { state.bassTarget.value   = snapEffect(it); FpsLogger.trackInteraction("bass→${"%.2f".format(snapEffect(it))}") }, 0f..1f, snap = ::snapEffect)
                FilterRow("pitch",  pitchAnim,  { state.pitchTarget.value  = snapBipolar(it); FpsLogger.trackInteraction("pitch→${"%.2f".format(snapBipolar(it))}") }, -1f..1f, snap = ::snapBipolar)
                Text(Strings.get("filters_scale_hint", lang), fontSize = 12.sp, color = muted)
            }
        }
        // ── Улучшайзер (опционально, по умолчанию выкл = RAW) ──
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(card)
                .border(1.dp, border, RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = if (isDark) 0.04f else 0.06f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.4f
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                    )
                }
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(Strings.get("enhancer_title", lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = text)
                EnhancerRow("enhancer_dc", state.enhancerDc.value) { state.enhancerDc.value = it; FpsLogger.trackInteraction("enhDC→$it") }
                EnhancerRow("enhancer_hp", state.enhancerHp.value) { state.enhancerHp.value = it; FpsLogger.trackInteraction("enhHP→$it") }
                EnhancerRow("enhancer_limiter", state.enhancerLimiter.value) { state.enhancerLimiter.value = it; FpsLogger.trackInteraction("enhLimiter→$it") }
                EnhancerRow("enhancer_gate", state.enhancerGate.value) { state.enhancerGate.value = it; FpsLogger.trackInteraction("enhGate→$it") }
                Text(Strings.get("raw_hint", lang), fontSize = 11.sp, color = muted)
            }
        }
        // ── Эквалайзер голоса (3 полосы, -100..+100, 0 = без изменений) ──
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(card)
                .border(1.dp, border, RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = if (isDark) 0.04f else 0.06f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.4f
                        ),
                        cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
                    )
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(Strings.get("eq_title", lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = text)
                FilterRow("eq_low",  eqLowAnim,  { state.eqLowTarget.value  = snapBipolar(it); FpsLogger.trackInteraction("eqLow→${"%.2f".format(snapBipolar(it))}") }, -1f..1f, snap = ::snapBipolar)
                FilterRow("eq_mid",  eqMidAnim,  { state.eqMidTarget.value  = snapBipolar(it); FpsLogger.trackInteraction("eqMid→${"%.2f".format(snapBipolar(it))}") }, -1f..1f, snap = ::snapBipolar)
                FilterRow("eq_high", eqHighAnim, { state.eqHighTarget.value = snapBipolar(it); FpsLogger.trackInteraction("eqHigh→${"%.2f".format(snapBipolar(it))}") }, -1f..1f, snap = ::snapBipolar)
                Text(Strings.get("eq_hint", lang), fontSize = 12.sp, color = muted)
            }
        }
        Spacer(Modifier.height(100.dp))
    }
    }
}

// Easter egg: breathing heart with guided tour

enum class GuidedTourTarget {
    CLOSE_BUTTON, CONNECT_TAB, PHRASE_BOX, DONE
}

object EasterEggGuide {
    val guidedTourTarget = mutableStateOf<GuidedTourTarget?>(null)
    val guidedTourHighlight = mutableStateOf<Pair<Float, Float>?>(null)
    val closeButtonCenter = mutableStateOf<Offset?>(null)
    val connectTabCenter = mutableStateOf<Offset?>(null)
    val phraseBoxCenter = mutableStateOf<Offset?>(null)
    var guidedTourPhase by mutableStateOf<GuidedTourTarget?>(null)
}

enum class EasterPhase { NONE, ACTIVE }

val easterPhase = mutableStateOf(EasterPhase.NONE)

fun easterLovePhrase(lang: String): String = when (lang) {
    "ru" -> "Вы мой любимый пользователь ♡"
    "es" -> "Eres mi usuario favorito ♡"
    "zh" -> "你是我最喜欢的用户 ♡"
    "ja" -> "あなたは私のお気に入りのユーザーです ♡"
    "ko" -> "당신은 제가 가장 좋아하는 사용자예요 ♡"
    "pt" -> "Você é meu usuário favorito ♡"
    "uk" -> "Ви мій улюблений користувач ♡"
    else -> "You are my favorite user ♡"
}

@Composable
fun EasterHeart(
    isDark: Boolean,
    effectsEnabled: Boolean,
    lang: String,
    guidedTourTarget: MutableState<GuidedTourTarget?>,
    onCloseAbout: () -> Unit,
    onOpenConnect: () -> Unit,
    onShowPhrase: () -> Unit,
    onDone: () -> Unit
) {
    val density = LocalDensity.current
    val travel = if (effectsEnabled) 620 else 0
    val tap = if (effectsEnabled) 130 else 0
    val pause = if (effectsEnabled) 220L else 0L
    val breathAnim = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val pressScale = remember { Animatable(1f) }
    var position by remember { mutableStateOf(Offset.Zero) }
    var overlayRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .onGloballyPositioned { coordinates ->
                overlayRoot = coordinates.positionInRoot()
            }
    ) {
        val widthPx = overlaySize.width.toFloat()
        val heightPx = overlaySize.height.toFloat()
        val center = Offset(widthPx / 2f, heightPx / 2f)
        fun targetPoint(measured: Offset?, fallback: Offset): Offset =
            measured?.let { Offset(it.x - overlayRoot.x, it.y - overlayRoot.y) } ?: fallback
        val heartSize = 92.dp

        LaunchedEffect(widthPx, heightPx) {
            if (widthPx <= 0f || heightPx <= 0f) return@LaunchedEffect
            position = center
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(if (effectsEnabled) 260 else 0, easing = FastOutSlowInEasing))

            val breathJob = launch {
                while (true) {
                    breathAnim.animateTo(1f, tween(if (effectsEnabled) 850 else 0, easing = FastOutSlowInEasing))
                    breathAnim.animateTo(0f, tween(if (effectsEnabled) 850 else 0, easing = FastOutSlowInEasing))
                }
            }

            suspend fun moveTo(target: Offset, targetState: GuidedTourTarget) {
                guidedTourTarget.value = targetState
                val start = position
                val progress = Animatable(0f)
                progress.animateTo(1f, tween(travel, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f))) {
                    position = Offset(
                        x = start.x + (target.x - start.x) * value,
                        y = start.y + (target.y - start.y) * value
                    )
                }
            }

            suspend fun press(action: () -> Unit) {
                pressScale.animateTo(0.76f, tween(tap, easing = FastOutSlowInEasing))
                action()
                pressScale.animateTo(1f, tween(tap, easing = FastOutSlowInEasing))
                delay(pause)
            }

            delay(if (effectsEnabled) 900 else 0)
            moveTo(
                targetPoint(EasterEggGuide.closeButtonCenter.value, Offset(widthPx - 72f, heightPx * 0.30f)),
                GuidedTourTarget.CLOSE_BUTTON
            )
            press(onCloseAbout)
            delay(if (effectsEnabled) 240 else 0)

            moveTo(
                targetPoint(EasterEggGuide.connectTabCenter.value, Offset(widthPx * 0.18f, heightPx - 100f)),
                GuidedTourTarget.CONNECT_TAB
            )
            press(onOpenConnect)
            delay(if (effectsEnabled) 380 else 0)

            moveTo(
                targetPoint(EasterEggGuide.phraseBoxCenter.value, Offset(widthPx / 2f, heightPx * 0.76f)),
                GuidedTourTarget.PHRASE_BOX
            )
            press(onShowPhrase)
            delay(if (effectsEnabled) 900 else 0)

            guidedTourTarget.value = GuidedTourTarget.DONE
            alpha.animateTo(0f, tween(if (effectsEnabled) 520 else 0, easing = FastOutSlowInEasing))
            breathJob.cancel()
            guidedTourTarget.value = null
            onDone()
        }

        Text(
            "♡",
            fontSize = 86.sp,
            color = if (isDark) Color(0xFFFF69B4) else Color.Black,
            modifier = Modifier
                .size(heartSize)
                .offset {
                    IntOffset(
                        (position.x - with(density) { heartSize.toPx() } / 2f).roundToInt(),
                        (position.y - with(density) { heartSize.toPx() } / 2f).roundToInt()
                    )
                }
                .alpha(alpha.value)
                .scale((1.0f + breathAnim.value * 0.12f) * pressScale.value),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Info screen ─────────────────────────────────────────────────────────────

@Composable
fun InfoScreen(colors: NelevColors, lang: String, effectsEnabled: Boolean) {
    val bg = colors["bg"]!!; val text = colors["text"]!!; val muted = colors["muted"]!!; val card = colors["card"]!!
    val isDark = bg.luminance() < 0.1f
    fun tr(en: String, ru: String, es: String = en, zh: String = en, ja: String = en, ko: String = en, pt: String = en, uk: String = ru): String = when (lang) {
        "ru" -> ru; "es" -> es; "zh" -> zh; "ja" -> ja; "ko" -> ko; "pt" -> pt; "uk" -> uk; else -> en
    }

    var simStep by remember { mutableStateOf(0) }
    LaunchedEffect(effectsEnabled) {
        if (!effectsEnabled) {
            simStep = 2
            return@LaunchedEffect
        }
        while (effectsEnabled) {
            simStep = 0; delay(2000)
            simStep = 1; delay(3500)
            simStep = 2; delay(3000)
            simStep = 3; delay(3000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(tr("How it works","Как работает",
            es="Cómo funciona",zh="工作原理",ja="仕組み",ko="작동 방식",pt="Como funciona",uk="Як це працює"),
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
        InfoStep("1", tr("Enter PC IP","Введи IP компьютера",
            es="Ingresa IP del PC",zh="输入电脑IP",ja="PCのIPを入力",ko="PC IP 입력",pt="Insira o IP do PC",uk="Введи IP комп'ютера"),
            tr("Open nelev-server on your PC. Your PC IP is shown there — enter it in the Connect tab.",
                "Открой nelev-server на ПК. IP ПК отображается в нём — введи его во вкладке Подключение.",
                es="Abre nelev-server en tu PC. Tu IP del PC se muestra allí — ingrésala en la pestaña Conectar.",
                zh="在你的电脑上打开 nelev-server。你的电脑IP会显示在那里——在连接标签页中输入它。",
                ja="PCでnelev-serverを開きます。PCのIPが表示されるので、接続タブに入力してください。",
                ko="PC에서 nelev-server를 엽니다. PC IP가 표시되며 연결 탭에 입력하세요.",
                pt="Abra o nelev-server no seu PC. O IP do PC é mostrado lá — insira-o no separador Conectar.",
                uk="Відкрий nelev-server на ПК. IP ПК відображається в ньому — введи його у вкладці Підключення."),
            text, muted, card)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("2  ${tr("Tap Start","Нажми Начать",
                    es="Toca Iniciar",zh="点击开始",ja="開始をタップ",ko="시작을 탭하세요",pt="Toque em Iniciar",uk="Натисни Почати")}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = text)
                Text(tr("The app waits for your PC to respond, then activates the mic:",
                    "Приложение ждёт ответа ПК, затем активирует микрофон:",
                    es="La app espera la respuesta de tu PC, luego activa el micrófono:",
                    zh="应用等待电脑响应，然后激活麦克风：",
                    ja="アプリがPCの応答を待ち、マイクを起動します：",
                    ko="앱이 PC 응답을 기다린 후 마이크를 활성화합니다：",
                    pt="A app aguarda a resposta do PC, depois ativa o microfone:",
                    uk="Додаток чекає відповіді ПК, потім активує мікрофон:"),
                    fontSize = 13.sp, color = muted, lineHeight = 19.sp)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(card).padding(vertical = 4.dp)) {
                    ConnectionBar(simStep >= 1, simStep == 2, colors, effectsEnabled)
                }
                val sc = when (simStep) { 2 -> Color(0xFF4CAF50); 3 -> Color(0xFFFF5252); else -> muted }
                Text(when (simStep) {
                    0 -> tr("Idle — not streaming","Не запущено",
                        es="Inactivo — sin transmisión",zh="空闲 — 未传输",ja="アイドル — 未ストリーミング",ko="대기 중 — 스트리밍 안 함",pt="Inativo — sem transmissão",uk="Не запущено")
                    1 -> tr("Waiting for PC…","Ожидание ПК…",
                        es="Esperando al PC…",zh="等待电脑…",ja="PCを待機中…",ko="PC 기다리는 중…",pt="A aguardar PC…",uk="Очікування ПК…")
                    2 -> tr("✓ Connected — mic is live","✓ Подключено — микрофон активен",
                        es="✓ Conectado — micrófono activo",zh="✓ 已连接 — 麦克风已开启",ja="✓ 接続済み — マイク起動中",ko="✓ 연결됨 — 마이크 활성",pt="✓ Ligado — microfone ativo",uk="✓ Підключено — мікрофон активен")
                    else -> tr("Connection lost · retrying…","Соединение потеряно…",
                        es="Conexión perdida · reintentando…",zh="连接丢失 · 重试中…",ja="接続切れ · 再試行中…",ko="연결 끊김 · 재시도 중…",pt="Conexão perdida · a tentar novamente…",uk="З'єднання втрачено…")
                }, fontSize = 12.sp, color = sc, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        InfoStep("3", tr("Adjust filters","Настрой фильтры",
            es="Ajustar filtros",zh="调整滤镜",ja="フィルター調整",ko="필터 조정",pt="Ajustar filtros",uk="Налаштуй фільтри"),
            tr("Reverb, bass boost, pitch shift — all processed on-device before sending.",
                "Реверберация, усиление баса, тон — DSP работает на телефоне до отправки.",
                es="Reverberación, refuerzo de graves, desplazamiento de tono — todo procesado en el dispositivo antes de enviar.",
                zh="混响、低音增强、音高偏移——均在发送前在设备上处理。",
                ja="リバーブ、低音ブースト、ピッチシフト — 送信前にすべて端末で処理されます。",
                ko="리버브, 베이스 부스트, 피치 시프트 — 전송 전에 모두 기기에서 처리됩니다.",
                pt="Reverberação, reforço de graves, deslocamento de tom — tudo processado no dispositivo antes de enviar.",
                uk="Реверберація, підсилення басів, зсув тону — DSP працює на телефоні до відправки."),
            text, muted, card)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(card).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Heart keeps pulsing?","Сердечко мигает?",
                    es="¿El corazón sigue latiendo?",zh="心形一直在跳？",ja="ハートが点滅し続ける？",ko="하트가 계속 깜빡이나요?",pt="O coração continua a pulsar?",uk="Сердечко блимає?"),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = text)
                listOf(
                    tr("Make sure nelev-server is running on your PC","Убедись, что nevel-server запущен на ПК",
                        es="Asegúrate de que nelev-server esté ejecutándose en tu PC",zh="确保 nelev-server 正在你的电脑上运行",ja="PCでnelev-serverが実行されていることを確認してください",ko="PC에서 nelev-server가 실행 중인지 확인하세요",pt="Certifique-se de que o nelev-server está em execução no seu PC",uk="Переконайся, що nevel-server запущено на ПК"),
                    tr("Phone and PC must be on the same Wi-Fi","Телефон и ПК должны быть в одной Wi-Fi сети",
                        es="El teléfono y el PC deben estar en la misma red Wi-Fi",zh="手机和电脑必须在同一Wi-Fi网络下",ja="スマホとPCは同じWi-Fiネットワークに接続してください",ko="휴대폰과 PC가 동일한 Wi-Fi 네트워크에 있어야 합니다",pt="O telemóvel e o PC devem estar na mesma rede Wi-Fi",uk="Телефон і ПК мають бути в одній Wi-Fi мережі"),
                    tr("Check firewall — UDP port 55000 must be open","Проверь брандмауэр — порт UDP 55000 должен быть открыт",
                        es="Revisa el firewall — el puerto UDP 55000 debe estar abierto",zh="检查防火墙 — UDP端口55000必须开放",ja="ファイアウォールを確認 — UDPポート55000を開放してください",ko="방화벽 확인 — UDP 포트 55000이 열려 있어야 합니다",pt="Verifique a firewall — a porta UDP 55000 deve estar aberta",uk="Перевір брандмауер — порт UDP 55000 має бути відкритий"),
                    tr("Restart both app and nelev-server","Перезапусти приложение и nelev-server",
                        es="Reinicia la app y nelev-server",zh="重启应用和 nelev-server",ja="アプリとnelev-serverを再起動",ko="앱과 nelev-server를 다시 시작하세요",pt="Reinicie a app e o nelev-server",uk="Перезапусти додаток і nelev-server")
                ).forEach { tip ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = muted, fontSize = 13.sp); Text(tip, color = muted, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Text(tr("Need help? Write to us:","Нужна помощь? Пиши:",
            es="¿Necesitas ayuda? Escríbenos:",zh="需要帮助？联系我们：",ja="ヘルプが必要ですか？お書きください：",ko="도움이 필요하신가요? 문의하세요：",pt="Precisa de ajuda? Escreva-nos:",uk="Потрібна допомога? Пиши:"),
            fontSize = 13.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TelegramLink(isDark = isDark, effectsEnabled = effectsEnabled) }
    }
}

@Composable
fun InfoStep(num: String, title: String, body: String, textColor: Color, mutedColor: Color, cardColor: Color) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cardColor).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$num  $title", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(body, fontSize = 13.sp, color = mutedColor, lineHeight = 19.sp)
        }
    }
}

@Composable
fun TelegramLink(isDark: Boolean, effectsEnabled: Boolean = true, onClickSound: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val phase = rememberPhaseTick(effectsEnabled, (2f * PI.toFloat()) / 3f, 2f * PI.toFloat())

    // Переливающиеся нежные розовые цвета
    val pinkColors = listOf(
        Color(0xFFFFC0CB), // Светло-розовый
        Color(0xFFFFB6C1), // Светло-розовый 2
        Color(0xFFFF69B4), // Ярко-розовый
        Color(0xFFFFAEC9), // Нежно-розовый
        Color(0xFFFFC1CC), // Пастельно-розовый
        Color(0xFFFFDAE9)  // Очень светло-розовый
    )

    val colorIndex = (phase / (2f * PI.toFloat()) * pinkColors.size).toInt() % pinkColors.size
    val nextColorIndex = (colorIndex + 1) % pinkColors.size
    val colorProgress = (phase / (2f * PI.toFloat()) * pinkColors.size) % 1f

    val currentColor = if (effectsEnabled) {
        lerp(pinkColors[colorIndex], pinkColors[nextColorIndex], colorProgress)
    } else {
        if (isDark) Color(0xFFFFFFFF) else Color(0xFFF2BFD8)
    }

    val color2 = if (effectsEnabled) {
        lerp(pinkColors[nextColorIndex], pinkColors[(nextColorIndex + 1) % pinkColors.size], colorProgress)
    } else {
        if (isDark) Color(0xFFF1F1F1) else Color(0xFFE7A8C8)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(currentColor.copy(alpha = 0.88f), color2.copy(alpha = 0.9f)),
                    start = Offset(0f, 0f),
                    end = Offset(200f, 200f)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable {
                onClickSound?.invoke()
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/nelevdev")))
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(20.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.5f)
                    lineTo(size.width * 0.85f, size.height * 0.15f)
                    lineTo(size.width * 0.65f, size.height * 0.85f)
                    lineTo(size.width * 0.5f, size.height * 0.65f)
                    close()
                }
                drawPath(path, Color.White)
            }
            Text(
                "nelevdev",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val trackShape = RoundedCornerShape(20.dp)
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(if (effectsEnabled) 220 else 0, easing = FastOutSlowInEasing),
        label = "glassSwitchOffset"
    )
    val trackC1 = if (checked) {
        if (isDark) Color(0xFFF2F6FB).copy(alpha = 0.9f) else Color(0xFFE79BC0)
    } else {
        if (isDark) Color(0xFF2A2A2A) else Color(0xFFF6DCEB)
    }
    val trackC2 = if (checked) {
        if (isDark) Color(0xFFDDE8F6).copy(alpha = 0.88f) else Color(0xFFDF8EB7)
    } else {
        if (isDark) Color(0xFF222222) else Color(0xFFF2D3E4)
    }
    val trackBrush = Brush.linearGradient(listOf(trackC1, trackC2))
    val thumbColor = if (checked) {
        if (isDark) Color(0xFF0D141D) else Color(0xFFFFF7FC)
    } else {
        if (isDark) Color(0xFFAAAAAA) else Color(0xFFE8A8C7)
    }
    val borderColor = if (isDark) Color.White.copy(alpha = if (checked) 0.24f else 0.08f) else Color(0xFFF1C4DA)

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 26.dp)
            .clip(trackShape)
            .background(trackBrush)
            .border(
                1.dp,
                borderColor,
                trackShape
            )
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .offset(x = thumbOffset, y = 2.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(thumbColor)
                .border(1.dp, borderColor.copy(alpha = if (isDark) 0.18f else 0.8f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun GlowingActionButton(
    text: String,
    icon: ImageVector,
    isDark: Boolean,
    effectsEnabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val phase = rememberPhaseTick(effectsEnabled, PI.toFloat() / 3.2f, 2f * PI.toFloat())

    var particles by remember { mutableStateOf<List<SparkParticle>>(emptyList()) }
    var clickCenter by remember { mutableStateOf(Offset.Zero) }
    val clock = remember { mutableStateOf(0L) }

    LaunchedEffect(particles, effectsEnabled) {
        if (particles.isEmpty() || !effectsEnabled) return@LaunchedEffect
        val end = System.currentTimeMillis() + 500L // OPTIMIZATION: короче анимация
        while (System.currentTimeMillis() < end) {
            clock.value = System.currentTimeMillis()
            withFrameMillis { it }
        }
        particles = emptyList()
    }

    val glowAlpha = if (effectsEnabled) 0.06f + ((sin(phase) + 1f) * 0.5f) * 0.06f else 0f // OPTIMIZATION: меньше свечение
    val glowScale = if (effectsEnabled) 1.01f + ((sin(phase) + 1f) * 0.5f) * 0.02f else 1f // OPTIMIZATION: меньше масштаб
    val c1 = Color(0xFF2AABEE)
    val c2 = Color(0xFF229ED9)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (effectsEnabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .scale(glowScale)
                    .clip(shape)
                    .background((if (isDark) Color(0xFF8FD5FF) else Color(0xFF4FB8F2)).copy(alpha = glowAlpha))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(c1, c2), start = Offset.Zero, end = Offset(500f, 300f)))
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .pointerInput(effectsEnabled) {
                    detectTapGestures(
                        onTap = { off ->
                            clickCenter = off
                            if (effectsEnabled) {
                                val now = System.currentTimeMillis()
                                particles = (0 until 7).map { // OPTIMIZATION: меньше частиц
                                    SparkParticle(
                                        angle = it * 51f + (-10f..10f).random(),
                                        speed = (1.4f..2.8f).random(),
                                        length = (6f..10f).random(),
                                        startTime = now
                                    )
                                }
                            }
                            onClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            if (effectsEnabled && particles.isNotEmpty()) {
                Canvas(Modifier.matchParentSize()) {
                    val now = clock.value
                    particles.forEach { p ->
                        val t = ((now - p.startTime) / 500f).coerceIn(0f, 1f) // OPTIMIZATION: короче анимация
                        if (t < 1f) {
                            val rad = (p.angle * PI / 180f).toFloat()
                            val dist = p.speed * 40f * t // OPTIMIZATION: меньше дистанция
                            val x = clickCenter.x + cos(rad) * dist
                            val y = clickCenter.y + sin(rad) * dist
                            drawCircle(
                                color = Color.White.copy(alpha = (1f - t).pow(2) * 0.3f), // OPTIMIZATION: меньше прозрачность
                                radius = (2f - t * 1f).coerceAtLeast(0.6f),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Session history screen ───────────────────────────────────────────────────

@Composable
fun SessionHistoryScreen(
    colors: NelevColors, lang: String, soundClickEnabled: Boolean, effectsEnabled: Boolean,
    onBack: () -> Unit, onGoConnect: () -> Unit
) {
    val bg = colors["bg"]!!; val card = colors["card"]!!; val text = colors["text"]!!
    val muted = colors["muted"]!!; val border = colors["border"]!!; val accent = colors["accent"]!!
    val ctx = LocalContext.current
    val sessions = remember { mutableStateListOf<SessionRecord>().also { it.addAll(SessionStore.load(ctx).reversed()) } }
    val fmt = remember(lang) { SimpleDateFormat("dd MMM, HH:mm", Strings.locale(lang)) }
    val scope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    val hidingIds = remember { mutableStateOf(setOf<Long>()) }
    
    // Активная сессия + тик каждую секунду для живого таймера
    var activeStart by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            activeStart = if (NelevService.streaming) SessionStore.loadActiveStart(ctx) else 0L
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val motion = if (effectsEnabled) 220 else 0
    var trashPressed by remember { mutableStateOf(false) }
    var backPressed by remember { mutableStateOf(false) }

    val trashScale by animateFloatAsState(
        if (trashPressed) 0.93f else if (showClearConfirm) 1.12f else 1f,
        if (effectsEnabled) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
        label = "trashScale"
    )
    val backScale by animateFloatAsState(
        if (backPressed) 0.93f else 1f,
        if (effectsEnabled) spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium) else tween(0),
        label = "backScale"
    )
    val trashColor by animateColorAsState(
        if (showClearConfirm) Color(0xFFFF6666) else text,
        tween(motion),
        label = "trashColor"
    )

    fun clearAllAnimated() {
        scope.launch {
            SessionStore.clear(ctx)
            val copy = sessions.toList()
            if (!effectsEnabled) {
                sessions.clear()
                hidingIds.value = emptySet()
                showClearConfirm = false
                return@launch
            }
            for (s in copy) {
                hidingIds.value = hidingIds.value + s.startMs
                delay(60L)
            }
            delay(280L)
            sessions.clear()
            hidingIds.value = emptySet()
            showClearConfirm = false
        }
    }

    fun fmtDuration(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val h = m / 60
        return if (h > 0) "${h}h ${m % 60}m" else if (m > 0) "${m}m ${s % 60}s" else "${s}s"
    }

    if (showClearConfirm) {
        LaunchedEffect(showClearConfirm, effectsEnabled) {
            delay(if (effectsEnabled) 3000 else 1)
            showClearConfirm = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            var btnPressed by remember { mutableStateOf(false) }
            val btnScale by animateFloatAsState(
                if (btnPressed) 0.94f else 1f,
                tween(if (effectsEnabled) 140 else 0, easing = FastOutSlowInEasing),
                label = "firstSessionBtnScale"
            )

            if (sessions.isEmpty() && activeStart <= 0L) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(IconsEx.History, null, Modifier.size(48.dp), muted.copy(alpha = 0.4f))
                        Text(Strings.get("no_sessions", lang), color = muted, fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }

    val phase = rememberPhaseTick(effectsEnabled, 1f / 3f, 1f)

        // Переливающиеся цвета как у Telegram
        val isDarkTheme = bg.luminance() < 0.1f
        val btnGradColors = if (isDarkTheme) {
            listOf(
                Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFEEEEEE),
                Color(0xFFF8F8F8), Color(0xFFFFFFFF)
            )
        } else {
            listOf(
                Color(0xFFFFB6C1), Color(0xFFFF69B4), Color(0xFFFFAEC9),
                Color(0xFFFFC1CC), Color(0xFFFFB6C1)
            )
        }

        val colorIndex = (phase * btnGradColors.size).toInt() % btnGradColors.size
        val nextColorIndex = (colorIndex + 1) % btnGradColors.size
        val colorProgress = (phase * btnGradColors.size) % 1f

        val currentColor = if (effectsEnabled) {
            lerp(btnGradColors[colorIndex], btnGradColors[nextColorIndex], colorProgress)
        } else {
            btnGradColors[0]
        }

        val nextColor = if (effectsEnabled) {
            lerp(btnGradColors[nextColorIndex], btnGradColors[(nextColorIndex + 1) % btnGradColors.size], colorProgress)
        } else {
            btnGradColors[1]
        }

        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .scale(btnScale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(currentColor, nextColor),
                            Offset(0f, 0f),
                            Offset(400f, 48f)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                btnPressed = true
                                val released = tryAwaitRelease()
                                btnPressed = false
                                if (released) {
                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                    onGoConnect()
                                }
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Strings.get("first_session", lang), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isDarkTheme) Color.Black else Color.White)
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                    // Активная сессия (живой таймер)
                    if (activeStart > 0L) {
                        key("active") {
                            val activeDur = (tick - activeStart).coerceAtLeast(0L)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(card.copy(alpha = 0.9f))
                                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent))
                                        Text(fmt.format(Date(activeStart)), fontSize = 13.sp, color = muted)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(IconsEx.History, null, Modifier.size(14.dp), accent)
                                            Text(fmtDuration(activeDur), fontSize = 14.sp, color = text, fontWeight = FontWeight.Medium)
                                        }
                                        val liveLabel = when (lang) {
                                            "ru" -> "В ЭФИРЕ"
                                            "uk" -> "В ЕФІРІ"
                                            "en" -> "LIVE"
                                            "es" -> "EN VIVO"
                                            "zh" -> "直播中"
                                            "ja" -> "配信中"
                                            "ko" -> "방송 중"
                                            "pt" -> "AO VIVO"
                                            else -> "LIVE"
                                        }
                                        Text(liveLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                                    }
                                }
                            }
                        }
                    }
                    sessions.forEach { s ->
                        key(s.startMs) {
                            val isVisible = !hidingIds.value.contains(s.startMs)
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(if (effectsEnabled) 220 else 0)) + expandVertically(tween(if (effectsEnabled) 220 else 0)),
                                exit = fadeOut(tween(if (effectsEnabled) 200 else 0)) + shrinkVertically(tween(if (effectsEnabled) 200 else 0, easing = FastOutSlowInEasing))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(card.copy(alpha = 0.6f))
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(fmt.format(Date(s.startMs)), fontSize = 13.sp, color = muted)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(IconsEx.History, null, Modifier.size(14.dp), muted)
                                                Text(fmtDuration(s.durationMs), fontSize = 14.sp, color = text, fontWeight = FontWeight.Medium)
                                            }
                                            if (s.avgPingMs > 0) {
                                                val pc = accent.copy(alpha = when { 
                                                    s.avgPingMs < 50 -> 1f
                                                    s.avgPingMs < 150 -> 0.7f
                                                    else -> 0.5f 
                                                })
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(pc))
                                                    Text("${s.avgPingMs}ms", fontSize = 12.sp, color = pc)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        // Кнопки внизу
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sessions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(trashScale)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (soundClickEnabled) ClickPlayer.play(ctx)
                            trashPressed = true
                            if (showClearConfirm) clearAllAnimated() else showClearConfirm = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .blur(if (effectsEnabled) 8.dp else 0.dp)
                            .background(
                                if (showClearConfirm) {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFF6666).copy(alpha = 0.25f),
                                            Color(0xFFFF4444).copy(alpha = 0.3f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            if (bg.luminance() < 0.1f) Color(0xFF2A2A2A).copy(alpha = 0.55f) else Color(0xFFF5F5F5).copy(alpha = 0.55f),
                                            if (bg.luminance() < 0.1f) Color(0xFF1E1E1E).copy(alpha = 0.56f) else Color(0xFFEEEEEE).copy(alpha = 0.56f)
                                        )
                                    )
                                }
                            )
                            .border(
                                1.dp,
                                if (bg.luminance() < 0.1f) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                                RoundedCornerShape(18.dp)
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                                )
                            )
                    )
                    Icon(Icons.Filled.Delete, contentDescription = if (lang == "ru") "Удалить историю" else "Delete history",
                        tint = trashColor, modifier = Modifier.size(22.dp))
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(backScale)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        backPressed = true
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .blur(if (effectsEnabled) 8.dp else 0.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    if (bg.luminance() < 0.1f) Color(0xFF2A2A2A).copy(alpha = 0.55f) else Color(0xFFF5F5F5).copy(alpha = 0.55f),
                                    if (bg.luminance() < 0.1f) Color(0xFF1E1E1E).copy(alpha = 0.56f) else Color(0xFFEEEEEE).copy(alpha = 0.56f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            if (bg.luminance() < 0.1f) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                            RoundedCornerShape(18.dp)
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                            )
                        )
                )
                Icon(Icons.Filled.ArrowBack, contentDescription = Strings.get("back", lang),
                    tint = text, modifier = Modifier.size(22.dp))
            }
        }
        LaunchedEffect(trashPressed) {
            if (!trashPressed) return@LaunchedEffect
            delay(120)
            trashPressed = false
        }
        LaunchedEffect(backPressed) {
            if (!backPressed) return@LaunchedEffect
            delay(120)
            backPressed = false
        }
    }
}

// ─── Settings screen ─────────────────────────────────────────────────────────

@Composable
fun AnimatedOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: androidx.compose.foundation.BorderStroke? = null,
    effectsEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        if (isPressed) 0.94f else 1f,
        tween(if (effectsEnabled) 140 else 0, easing = FastOutSlowInEasing),
        label = "btnPressScale"
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .clip(shape)
            .background(colors.containerColor)
            .then(if (border != null) Modifier.border(border.width, border.brush, shape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) {
                            onClick()
                        }
                    }
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(this)
        }
    }
}

@Composable
fun SettingsScreen(
    appSettings: MutableState<AppSettings>,
    colors: NelevColors,
    lang: String,
    ctx: Context,
    showHistory: MutableState<Boolean>,
    selectedTab: MutableState<NelevTab>,
    showAbout: MutableState<Boolean>,
    onCheckUpdates: () -> Unit = {},
    onShowPauseDialog: () -> Unit = {}
) {
    val bg = colors["bg"]!!
    val text = colors["text"]!!
    val muted = colors["muted"]!!
    val border = colors["border"]!!
    val card = colors["card"]!!
    val accent = colors["accent"] ?: text
    val isDark = appSettings.value.isDark
    val buttonContent = if (isDark) Color.Black else Color.White
    val soundClickEnabled = appSettings.value.soundClickEnabled
    val effectsEnabled = appSettings.value.visualEffectsEnabled
    val backScale = remember { mutableStateOf(1f) }
    val trashScale = remember { mutableStateOf(1f) }
    val btnScale = remember { mutableStateOf(1f) }
    val backPressed = remember { mutableStateOf(false) }
    val trashPressed = remember { mutableStateOf(false) }
    val btnPressed = remember { mutableStateOf(false) }
    val hidingIds = remember { mutableStateOf(setOf<Long>()) }
    val showClearConfirm = remember { mutableStateOf(false) }
    val sessions = remember { mutableStateOf(emptyList<SessionRecord>()) }
    var langExpanded by remember { mutableStateOf(false) }
    val langOptions = listOf("ru" to "Русский", "en" to "English", "es" to "Español", "zh" to "中文", "ja" to "日本語", "ko" to "한국어", "pt" to "Português", "uk" to "Українська")
    var soundMenuExpanded by remember { mutableStateOf(false) }
    val panelShape = RoundedCornerShape(24.dp)
    val rowShape = RoundedCornerShape(20.dp)

    BackHandler {
        when {
            showClearConfirm.value -> showClearConfirm.value = false
            langExpanded -> langExpanded = false
            soundMenuExpanded -> soundMenuExpanded = false
            else -> selectedTab.value = NelevTab.CONNECT
        }
    }

    fun save(ns: AppSettings) {
        appSettings.value = ns
        SettingsStore.save(ctx, ns)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bg)
            .pointerInput(langExpanded, soundMenuExpanded) {
                detectTapGestures {
                    if (langExpanded) langExpanded = false
                    if (soundMenuExpanded) soundMenuExpanded = false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(Strings.get("settings_title", lang), fontSize = 24.sp, fontWeight = FontWeight.Black, color = text)

        Text(Strings.get("lang_label", lang), color = muted, fontSize = 12.sp)
        Box {
            OutlinedButton(
                onClick = { if (soundClickEnabled) ClickPlayer.play(ctx); langExpanded = !langExpanded },
                shape = panelShape, modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = card, contentColor = text),
                border = androidx.compose.foundation.BorderStroke(1.dp, border)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(langOptions.find { it.first == lang }?.second ?: "English", color = text, fontSize = 15.sp)
                    val arrowRot by animateFloatAsState(
                        if (langExpanded) 180f else 0f,
                        tween(if (effectsEnabled) 300 else 0),
                        label = "arr"
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRot }, muted)
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 62.dp)) {
                AnimatedVisibility(langExpanded,
                    enter = fadeIn(tween(if (effectsEnabled) 180 else 0)) + expandVertically(tween(if (effectsEnabled) 220 else 0, easing = FastOutSlowInEasing)),
                    exit  = fadeOut(tween(if (effectsEnabled) 150 else 0)) + shrinkVertically(tween(if (effectsEnabled) 180 else 0))) {
                    Box(Modifier.fillMaxWidth().clip(panelShape)) {
                        Surface(modifier = Modifier.fillMaxWidth(), shape = panelShape,
                            color = card, shadowElevation = 8.dp, tonalElevation = 0.dp) {
                            Column(Modifier.padding(6.dp)) {
                                langOptions.forEach { (code, name) ->
                                    val isSel = code == lang
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (isSel) {
                                                    if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFFDEAF4)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable {
                                                if (soundClickEnabled) ClickPlayer.play(ctx)
                                                save(appSettings.value.copy(language = code)); langExpanded = false
                                                try {
                                                    val intent = Intent(ctx, NelevService::class.java).apply {
                                                        action = "ACTION_REFRESH"
                                                    }
                                                    ctx.startService(intent)
                                                } catch (_: Exception) {}
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSel) {
                                            Box(
                                                Modifier
                                                    .width(4.dp)
                                                    .height(20.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(if (isDark) Color.White else Color(0xFFE79BC0))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                        }
                                        Text(name, color = text, fontSize = 14.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(Strings.get("theme_label", lang), color = muted, fontSize = 12.sp)
val phase = rememberPhaseTick(effectsEnabled, 1f / 3f, 1f)

        // Переливающиеся цвета как у Telegram
        val tGradColors = if (isDark) {
            listOf(
                Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFEEEEEE),
                Color(0xFFF8F8F8), Color(0xFFFFFFFF)
            )
        } else {
            listOf(
                Color(0xFFFFB6C1), Color(0xFFFF69B4), Color(0xFFFFAEC9),
                Color(0xFFFFC1CC), Color(0xFFFFB6C1)
            )
        }

        val colorIndex = (phase * tGradColors.size).toInt() % tGradColors.size
        val nextColorIndex = (colorIndex + 1) % tGradColors.size
        val colorProgress = (phase * tGradColors.size) % 1f

        val currentColor = if (effectsEnabled) {
            lerp(tGradColors[colorIndex], tGradColors[nextColorIndex], colorProgress)
        } else {
            tGradColors[0]
        }

        val nextColor = if (effectsEnabled) {
            lerp(tGradColors[nextColorIndex], tGradColors[(nextColorIndex + 1) % tGradColors.size], colorProgress)
        } else {
            tGradColors[1]
        }

        val themeLabel = remember(lang, isDark) { if (isDark) Strings.get("switch_to_light", lang) else Strings.get("switch_to_dark", lang) }
        Button(onClick = { if (soundClickEnabled) ClickPlayer.play(ctx); save(appSettings.value.copy(isDark = !isDark)) },
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = panelShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            elevation = ButtonDefaults.buttonElevation(0.dp), contentPadding = PaddingValues(0.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(currentColor, nextColor),
                            Offset(0f, 0f),
                            Offset(600f, 56f)
                        ),
                        panelShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), panelShape),
                contentAlignment = Alignment.Center) {
                Text(themeLabel, color = if (isDark) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedOutlinedButton(
            onClick = { if (soundClickEnabled) ClickPlayer.play(ctx); showHistory.value = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = panelShape,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = card, contentColor = text),
            border = androidx.compose.foundation.BorderStroke(1.dp, border),
            effectsEnabled = effectsEnabled
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.get("history_btn", lang), color = text, fontSize = 15.sp)
                Icon(IconsEx.History, null, Modifier.size(18.dp), muted)
            }
        }

        // Кнопка "Проверить обновление" + иконка "Что нового"
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedOutlinedButton(
                onClick = {
                    if (soundClickEnabled) ClickPlayer.play(ctx)
                    onCheckUpdates()
                },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = card,
                    contentColor = text
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
                effectsEnabled = effectsEnabled
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = accent)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            Strings.get("check_update", lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = text
                        )
                        Text(
                            "v$APP_VERSION",
                            fontSize = 10.sp,
                            color = muted
                        )
                    }
                }
            }
            
            var showWhatsNewLocal by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(card)
                    .border(1.dp, border, RoundedCornerShape(14.dp))
                    .clickable {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        showWhatsNewLocal = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    Modifier.size(20.dp), tint = muted
                )
            }
            
            if (showWhatsNewLocal) {
                val currentCode = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            it.longVersionCode.toInt()
                        } else {
                            @Suppress("DEPRECATION")
                            it.versionCode
                        }
                    }
                } catch (_: Exception) { 0 }
                val changelog = remember(currentCode, lang) { VersionHistory.changelog(ctx, currentCode, lang) ?: "" }
                WhatsNewDialog(
                    versionName = APP_VERSION,
                    changelog = changelog,
                    colors = colors,
                    lang = lang,
                    isDark = appSettings.value.isDark,
                    onDismiss = { showWhatsNewLocal = false }
                )
            }
        }


        @Composable
        fun ToggleRow(labelKey: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            var isPressed by remember { mutableStateOf(false) }
            val pressScale by animateFloatAsState(
                if (isPressed) 0.97f else 1f,
                tween(if (effectsEnabled) 100 else 0),
                label = "pressScale_$labelKey"
            )
            val rowBgColor = if (checked) {
                if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFFFF2FA)
            } else {
                card
            }
            val rowBorderColor = if (checked) {
                if (isDark) Color.White.copy(alpha = 0.16f) else Color(0xFFF3C6DC)
            } else {
                border
            }
            val labelColor = text

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pressScale)
                    .clip(rowShape)
                    .background(rowBgColor)
                    .border(1.dp, rowBorderColor, rowShape)
                    .drawWithContent {
                        drawContent()
                        // Liquid glass highlight
                        if (checked) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        if (isDark) Color.White.copy(alpha = 0.04f) else Color(0xFFDDDDDD).copy(alpha = 0.06f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.5f
                                ),
                                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                            )
                        }
                    }
                    .clickable {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        onChange(!checked)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.get(labelKey, lang), color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                GlassSwitch(
                    checked = checked,
                    onCheckedChange = {
                        if (soundClickEnabled) ClickPlayer.play(ctx)
                        onChange(it)
                    },
                    isDark = isDark,
                    effectsEnabled = effectsEnabled
                )
            }
        }

        ToggleRow("visual_fx_label", appSettings.value.visualEffectsEnabled) { save(appSettings.value.copy(visualEffectsEnabled = it)) }
        ToggleRow("phrases_label", appSettings.value.phrasesEnabled) { save(appSettings.value.copy(phrasesEnabled = it)) }
        ToggleRow("conn_bar_label", appSettings.value.connectionBarEnabled) { save(appSettings.value.copy(connectionBarEnabled = it)) }
        ToggleRow("menu_centered_label", appSettings.value.menuCentered) { save(appSettings.value.copy(menuCentered = it)) }
        ToggleRow("auto_start", appSettings.value.autoStartEnabled) { save(appSettings.value.copy(autoStartEnabled = it)) }
        ToggleRow("sync_with_pc", appSettings.value.syncWithPc) { save(appSettings.value.copy(syncWithPc = it)) }

        val anySoundOn = appSettings.value.soundNotifEnabled || appSettings.value.soundClickEnabled
        val motion = if (effectsEnabled) 260 else 0
        val soundBottomRadius by animateDpAsState(
            targetValue = if (soundMenuExpanded) 0.dp else 20.dp,
            animationSpec = tween(if (effectsEnabled) 240 else 0, easing = FastOutSlowInEasing),
            label = "soundBottomRadius"
        )
        val soundHeaderShape = RoundedCornerShape(20.dp, 20.dp, soundBottomRadius, soundBottomRadius)
        val soundRowBg = if (anySoundOn) {
            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        } else {
            card
        }
        val soundRowBorder = if (anySoundOn) {
            if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.14f)
        } else {
            border
        }

        Text(Strings.get("sounds_section", lang), color = muted, fontSize = 12.sp)
        Column {
            Row(modifier = Modifier.fillMaxWidth()
                .clip(soundHeaderShape)
                .background(soundRowBg)
                .border(1.dp, soundRowBorder, soundHeaderShape)
                .drawWithContent {
                    drawContent()
                    // Liquid glass highlight
                    if (anySoundOn) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = if (isDark) 0.04f else 0.06f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.5f
                            ),
                            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                        )
                    }
                }
                .animateContentSize(tween(if (effectsEnabled) 240 else 0, easing = FastOutSlowInEasing)),
                verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f)
                    .clickable(remember { MutableInteractionSource() }, null) {
                        if (soundClickEnabled) ClickPlayer.play(ctx); soundMenuExpanded = !soundMenuExpanded
                    }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Strings.get("sounds_section", lang), color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    val arrowRot by animateFloatAsState(if (soundMenuExpanded) 180f else 0f, tween(motion), label = "sarr")
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRot }, muted)
                }
                Box(Modifier.width(0.5.dp).height(28.dp).background(border))
                Box(Modifier.width(72.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    GlassSwitch(
                        checked = anySoundOn,
                        onCheckedChange = { on ->
                            if (soundClickEnabled) ClickPlayer.play(ctx)
                            val ns = if (on) {
                                appSettings.value.copy(soundNotifEnabled = appSettings.value.soundNotifEnabledBeforeMaster, soundClickEnabled = appSettings.value.soundClickEnabledBeforeMaster)
                            } else {
                                appSettings.value.copy(soundNotifEnabledBeforeMaster = appSettings.value.soundNotifEnabled, soundClickEnabledBeforeMaster = appSettings.value.soundClickEnabled, soundNotifEnabled = false, soundClickEnabled = false)
                            }
                            save(ns)
                        },
                        isDark = isDark,
                        effectsEnabled = effectsEnabled
                    )
                }
            }

            AnimatedVisibility(soundMenuExpanded,
                enter = fadeIn(tween(if (effectsEnabled) 200 else 0)) + expandVertically(tween(if (effectsEnabled) 240 else 0, easing = FastOutSlowInEasing)),
                exit  = fadeOut(tween(if (effectsEnabled) 160 else 0)) + shrinkVertically(tween(if (effectsEnabled) 200 else 0))) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp))) {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
                        color = card, shadowElevation = 4.dp, tonalElevation = 0.dp) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(0.5.dp).background(border))
                            listOf(
                                SndItem("sound_notif_label", appSettings.value.soundNotifEnabled) {
                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                    val newVal = !appSettings.value.soundNotifEnabled
                                    val ns = appSettings.value.copy(soundNotifEnabled = newVal, soundNotifEnabledBeforeMaster = newVal)
                                    save(ns)
                                },
                                SndItem("sound_click_label", appSettings.value.soundClickEnabled) {
                                    if (appSettings.value.soundClickEnabled) ClickPlayer.play(ctx)
                                    val newVal = !appSettings.value.soundClickEnabled
                                    val ns = appSettings.value.copy(soundClickEnabled = newVal, soundClickEnabledBeforeMaster = newVal)
                                    save(ns)
                                }
                            ).forEachIndexed { i, item ->
                                var itemPressed by remember { mutableStateOf(false) }
                                val itemScale by animateFloatAsState(
                                    if (itemPressed) 0.96f else 1f,
                                    tween(if (effectsEnabled) 120 else 0, easing = FastOutSlowInEasing),
                                    label = "soundItemScale_$i"
                                )
                                val rowAlpha = if (item.on) 1f else 0.38f
                                val rowBg by animateColorAsState(
                                    if (item.on) {
                                        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                                    } else {
                                        Color.Transparent
                                    },
                                    tween(if (effectsEnabled) 200 else 0),
                                    label = "soundRowBg_$i"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(itemScale)
                                        .background(rowBg)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    itemPressed = true
                                                    val released = tryAwaitRelease()
                                                    itemPressed = false
                                                    if (released) {
                                                        item.toggle()
                                                    }
                                                }
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 17.dp)
                                ) {
                                    Text(Strings.get(item.key, lang), color = text.copy(alpha = rowAlpha), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                if (i == 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(border))
                            }
                        }
                    }
                }
            }
        }

        AnimatedOutlinedButton(
            onClick = { if (soundClickEnabled) ClickPlayer.play(ctx); showAbout.value = !showAbout.value },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = panelShape,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = card, contentColor = text),
            border = androidx.compose.foundation.BorderStroke(1.dp, border),
            effectsEnabled = effectsEnabled
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.get("about_app", lang), color = text, fontSize = 15.sp)
                Icon(Icons.Filled.Info, null, Modifier.size(18.dp), muted)
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun AnimatedLightbulb(isDark: Boolean, effectsEnabled: Boolean) {
    val infinite = rememberInfiniteTransition(label = "lightbulb")
    val glowAlpha by infinite.animateFloat(
        initialValue = if (effectsEnabled) 0.22f else 0.16f,
        targetValue = if (effectsEnabled) 0.5f else 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (effectsEnabled) 900 else 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (effectsEnabled) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (effectsEnabled) 900 else 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val bulbColor = if (isDark) Color(0xFFFFF3B0) else Color(0xFFE79BC0)

    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .scale(scale)
                .clip(RoundedCornerShape(50))
                .background(bulbColor.copy(alpha = glowAlpha))
                .blur(6.dp)
        )
        Icon(
            imageVector = IconsEx.Lightbulb,
            contentDescription = null,
            tint = bulbColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

// Overlay для окна "О приложении" - должен быть вне Column, на уровне Box
@Composable
fun AboutAppOverlay(
    showAbout: Boolean,
    onDismiss: () -> Unit,
    appSettings: MutableState<AppSettings>,
    colors: NelevColors,
    lang: String,
    soundClickEnabled: Boolean,
    effectsEnabled: Boolean,
    ctx: Context,
    save: (AppSettings) -> Unit,
    showStatsToggle: MutableState<Boolean>,
    showStats: MutableState<Boolean>
) {
    val bg = colors["bg"]!!
    val text = colors["text"]!!
    val muted = colors["muted"]!!
    val border = colors["border"]!!
    var easterTaps by remember { mutableStateOf(0) }
    var statsTapCount by remember { mutableStateOf(0) }
    val isDark = appSettings.value.isDark

    var showFeedback by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var showContactField by remember { mutableStateOf(false) }
    var showPauseUpdates by remember { mutableStateOf(false) }
    var selectedPauseOption by remember { mutableStateOf<Int?>(null) }
    var customDays by remember { mutableStateOf("") }

    BackHandler {
        if (showPauseUpdates) {
            showPauseUpdates = false
            selectedPauseOption = null
            customDays = ""
        } else if (showFeedback) {
            showFeedback = false
        } else {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = showAbout,
        enter = fadeIn(tween(if (effectsEnabled) 300 else 0, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(if (effectsEnabled) 250 else 0, easing = FastOutSlowInEasing))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null) {
                    if (showPauseUpdates) {
                        showPauseUpdates = false
                        selectedPauseOption = null
                        customDays = ""
                    } else if (showFeedback) {
                        showFeedback = false
                    } else {
                        onDismiss()
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.wrapContentSize()) {
                    // Содержимое окна
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (isDark) {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF1E1E1E).copy(alpha = 0.48f),
                                            Color(0xFF151515).copy(alpha = 0.50f)
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFFFCFF).copy(alpha = 0.48f),
                                            Color(0xFFFFF2FA).copy(alpha = 0.50f)
                                        )
                                    )
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.1f),
                                RoundedCornerShape(24.dp)
                            )
                            .drawWithContent {
                                drawContent()
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = if (isDark) 0.06f else 0.1f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = size.height * 0.35f
                                    ),
                                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                                )
                            }
                            .clickable(remember { MutableInteractionSource() }, null) { }
                            .padding(24.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        if (showPauseUpdates) UpdateText.get("pause_title", lang)
                                        else if (showFeedback) Strings.get("feedback_title", lang) 
                                        else Strings.get("about_app", lang),
                                        color = text,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (showPauseUpdates) {
                                            showPauseUpdates = false
                                            selectedPauseOption = null
                                            customDays = ""
                                        } else if (showFeedback) {
                                            showFeedback = false
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.onGloballyPositioned { coordinates ->
                                        val pos = coordinates.positionInRoot()
                                        val target = Offset(
                                            pos.x + coordinates.size.width / 2f,
                                            pos.y + coordinates.size.height / 2f
                                        )
                                        if (EasterEggGuide.closeButtonCenter.value != target) {
                                            EasterEggGuide.closeButtonCenter.value = target
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, null, tint = if (isDark) Color.White.copy(alpha = 0.85f) else muted)
                                }
                            }

                            Box(Modifier.fillMaxWidth().height(1.dp).background(border))

                            // Единый контейнер содержимого: при переключении
                            // «О приложении» ⇄ «Обратная связь» ⇄ «Приостановка обновлений» высота карточки плавно
                            // перетекает из одной в другую через SizeTransform, вместо
                            // двух встречных expand/shrink, из-за которых окно
                            // «телепортировало» в конце анимации.
                            AnimatedContent(
                                targetState = if (showPauseUpdates) 2 else if (showFeedback) 1 else 0,
                                transitionSpec = {
                                    val enterSpec = tween<Float>(
                                        if (effectsEnabled) 280 else 0,
                                        easing = FastOutSlowInEasing
                                    )
                                    val exitSpec = tween<Float>(
                                        if (effectsEnabled) 180 else 0,
                                        easing = FastOutSlowInEasing
                                    )
                                    val sizeSpec = tween<IntSize>(
                                        if (effectsEnabled) 340 else 0,
                                        easing = FastOutSlowInEasing
                                    )
                                    (fadeIn(enterSpec) + scaleIn(initialScale = 0.985f, animationSpec = enterSpec))
                                        .togetherWith(fadeOut(exitSpec))
                                        .using(SizeTransform(clip = true) { _, _ -> sizeSpec })
                                },
                                label = "about_content_swap"
                            ) { currentView ->
                                if (currentView == 0) {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {



                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Info, null, Modifier.size(20.dp), muted)
                                        Column {
                                            Text(
                                                "${Strings.get("version", lang)}: $APP_VERSION",
                                                color = text,
                                                fontSize = 15.sp,
                                                modifier = Modifier.clickable {
                                                    easterTaps++
                                                    if (easterTaps >= 10) {
                                                        easterTaps = 0
                                                        easterPhase.value = EasterPhase.ACTIVE
                                                    }
                                                    statsTapCount++
                                                    if (statsTapCount >= 3) {
                                                        statsTapCount = 0
                                                        showStatsToggle.value = !showStatsToggle.value
                                                    }
                                                }
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    "UID",
                                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else text.copy(alpha = 0.5f),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.8.sp
                                                )
                                                Box(
                                                    Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (isDark) Color.White.copy(alpha = 0.12f)
                                                            else Color.Black.copy(alpha = 0.06f)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isDark) Color.White.copy(alpha = 0.09f)
                                                            else Color.Black.copy(alpha = 0.07f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        appSettings.value.userUID,
                                                        color = if (isDark) Color.White.copy(alpha = 0.8f) else text.copy(alpha = 0.78f),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        letterSpacing = 0.5.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Person, null, Modifier.size(20.dp), muted)
                                        Column {
                                            Text(
                                                Strings.get("developer", lang),
                                                color = muted,
                                                fontSize = 12.sp
                                            )
                                            TelegramLink(
                                                isDark = isDark,
                                                effectsEnabled = effectsEnabled,
                                                onClickSound = { if (soundClickEnabled) ClickPlayer.play(ctx) })
                                        }
                                    }

                                    Box(Modifier.fillMaxWidth().height(1.dp).background(border))

                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(
                                                        alpha = 0.08f
                                                    )
                                                )
                                                .clickable {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    val newValue = !appSettings.value.sendStatistics
                                                    save(appSettings.value.copy(sendStatistics = newValue))
                                                    if (newValue) {
                                                        Toast.makeText(ctx, if (lang == "ru") "Статистика будет отправлена при следующем запуске" else "Statistics will be sent on next launch", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    Strings.get("send_statistics", lang),
                                                    color = text,
                                                    fontSize = 14.sp
                                                )
                                                // Таймер до следующей отправки
                                                val timeLeft = remember(appSettings.value.lastStatisticsSentMs) {
                                                    val twoDaysMs = 2 * 24 * 60 * 60 * 1000L
                                                    val elapsed = System.currentTimeMillis() - appSettings.value.lastStatisticsSentMs
                                                    val remaining = twoDaysMs - elapsed
                                                    if (remaining > 0 && appSettings.value.lastStatisticsSentMs > 0) {
                                                        val days = remaining / (24 * 60 * 60 * 1000)
                                                        val hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                                                        val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
                                                        if (days > 0) "${days}д ${hours}ч" else "${hours}ч ${minutes}м"
                                                    } else null
                                                }
                                                if (timeLeft != null && appSettings.value.sendStatistics) {
                                                    Text(
                                                        if (lang == "ru") "Следующая через: $timeLeft" else "Next in: $timeLeft",
                                                        color = muted,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            var switchPressed by remember { mutableStateOf(false) }
                                            var switchDragOffset by remember { mutableStateOf(Offset.Zero) }

                                            val switchScale by animateFloatAsState(
                                                if (switchPressed) 0.88f else 1f,
                                                tween(if (effectsEnabled) 150 else 0, easing = FastOutSlowInEasing),
                                                label = "switchScale"
                                            )

                                            GlassSwitch(
                                                checked = appSettings.value.sendStatistics,
                                                onCheckedChange = { newValue ->
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    save(appSettings.value.copy(sendStatistics = newValue))

                                                    // Статистика отправится при следующем запуске приложения
                                                    if (newValue) {
                                                        Toast.makeText(ctx, if (lang == "ru") "Статистика будет отправлена при следующем запуске" else "Statistics will be sent on next launch", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                isDark = isDark,
                                                effectsEnabled = effectsEnabled,
                                                modifier = Modifier.scale(switchScale)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(
                                                        alpha = 0.08f
                                                    )
                                                )
                                                .clickable {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    save(appSettings.value.copy(participateInBeta = !appSettings.value.participateInBeta))
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                Strings.get("participate_beta", lang),
                                                color = text,
                                                fontSize = 14.sp
                                            )
                                            GlassSwitch(
                                                checked = appSettings.value.participateInBeta,
                                                onCheckedChange = {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    save(appSettings.value.copy(participateInBeta = it))
                                                },
                                                isDark = isDark,
                                                effectsEnabled = effectsEnabled
                                            )
                                        }
                                        
                                        // Кнопка приостановки обновлений
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.08f)
                                                )
                                                .clickable {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    showPauseUpdates = true
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    Strings.get("pause_updates", lang),
                                                    color = text,
                                                    fontSize = 14.sp
                                                )
                                                if (appSettings.value.updatesPaused()) {
                                                    val now = System.currentTimeMillis()
                                                    val statusText = if (appSettings.value.updatesPausedForever) {
                                                        UpdateText.get("paused_forever_status", lang)
                                                    } else {
                                                        val days = ((appSettings.value.updatesPausedUntilMs - now) / (24 * 60 * 60 * 1000L)).toInt() + 1
                                                        UpdateText.get("paused_until_status", lang).replace("%d", days.toString())
                                                    }
                                                    Text(
                                                        statusText,
                                                        color = Color(0xFFFFA726),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Filled.KeyboardArrowRight,
                                                null,
                                                Modifier.size(20.dp),
                                                tint = muted
                                            )
                                        }
                                    }

                                    // Кнопка "Отправить лог FPS"
                                    if (showStatsToggle.value) {
                                        var fpsLogSent by remember { mutableStateOf(false) }
                                        Button(
                                            onClick = {
                                                if (soundClickEnabled) ClickPlayer.play(ctx)
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                    TelegramSender.sendFpsLog(ctx)
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        fpsLogSent = true
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE79BC0).copy(alpha = 0.2f),
                                                contentColor = text
                                            )
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Send, null, Modifier.size(18.dp))
                                                Text(
                                                    if (fpsLogSent) Strings.get("fps_log_sent", lang).ifEmpty { "Log sent" }
                                                    else Strings.get("fps_log_send", lang).ifEmpty { "Send FPS log" },
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    // Кнопка "Предложить изменение"
                                    if (showStatsToggle.value) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.08f)
                                                )
                                                .clickable {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    showStats.value = !showStats.value
                                                }
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                Strings.get("system_stats", lang).ifEmpty { "System stats" },
                                                color = text,
                                                fontSize = 14.sp
                                            )
                                            GlassSwitch(
                                                checked = showStats.value,
                                                onCheckedChange = {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    showStats.value = it
                                                },
                                                isDark = isDark,
                                                effectsEnabled = effectsEnabled
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            if (soundClickEnabled) ClickPlayer.play(ctx)
                                            showFeedback = true
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFFE79BC0).copy(alpha = 0.2f),
                                            contentColor = text
                                        )
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(Strings.get("feedback_btn", lang), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    }
                                } else if (currentView == 1) {
                                    // Форма обратной связи
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = feedbackText,
                                        onValueChange = { 
                                            val newText = it
                                            if (newText.length <= 3000) {
                                                feedbackText = newText
                                            } else {
                                                feedbackText = newText.take(3000)
                                            }
                                        },
                                        placeholder = { Text(Strings.get("feedback_hint", lang), color = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp),
                                        textStyle = TextStyle(color = text, fontSize = 14.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (isDark) Color.White.copy(alpha = 0.28f) else Color(0xFFE79BC0),
                                            unfocusedBorderColor = border,
                                            focusedTextColor = text,
                                            unfocusedTextColor = text,
                                            cursorColor = text
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    Text(
                                        "${feedbackText.length}/3000",
                                        color = muted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.End)
                                    )

                                    // Кнопка для показа поля контактов
                                    TextButton(
                                        onClick = {
                                            if (soundClickEnabled) ClickPlayer.play(ctx)
                                            showContactField = !showContactField
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (showContactField) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                                null,
                                                Modifier.size(16.dp),
                                                tint = muted
                                            )
                                            Text(
                                                Strings.get("feedback_contact_toggle", lang),
                                                color = if (isDark) Color.White.copy(alpha = 0.78f) else text.copy(alpha = 0.75f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Поле контактов: та же схема — высота меняется плавно
                                    // через SizeTransform, без «выдвижения/задвижения» с рывком.
                                    AnimatedContent(
                                        targetState = showContactField,
                                        transitionSpec = {
                                            val sizeSpec = tween<IntSize>(
                                                if (effectsEnabled) 320 else 0,
                                                easing = FastOutSlowInEasing
                                            )
                                            fadeIn(tween(if (effectsEnabled) 240 else 0, easing = FastOutSlowInEasing))
                                                .togetherWith(fadeOut(tween(if (effectsEnabled) 160 else 0, easing = FastOutSlowInEasing)))
                                                .using(SizeTransform(clip = true) { _, _ -> sizeSpec })
                                        },
                                        label = "feedback_contact_field"
                                    ) { contactOpen ->
                                        if (contactOpen) {
                                            OutlinedTextField(
                                                value = contactInfo,
                                                onValueChange = { contactInfo = it },
                                                placeholder = { Text(Strings.get("feedback_contact_hint", lang), color = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = TextStyle(color = text, fontSize = 14.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (isDark) Color.White.copy(alpha = 0.28f) else Color(0xFFE79BC0),
                                                    unfocusedBorderColor = border,
                                                    focusedTextColor = text,
                                                    unfocusedTextColor = text,
                                                    cursorColor = text
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                singleLine = true
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (feedbackText.isNotBlank()) {
                                                TelegramSender.sendFeedback(ctx, feedbackText, contactInfo)
                                                Toast.makeText(ctx, if (lang == "ru") "Спасибо! Идея отправлена" else "Thanks! Idea sent", Toast.LENGTH_SHORT).show()
                                                // Сначала плавно закрываем форму, а поля очищаем только
                                                // после окончания анимации, иначе текст пропадает на глазах.
                                                showFeedback = false
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    kotlinx.coroutines.delay(if (effectsEnabled) 380L else 0L)
                                                    feedbackText = ""
                                                    contactInfo = ""
                                                    showContactField = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDark) Color.White else Color(0xFFE79BC0),
                                            contentColor = if (isDark) Color.Black else Color.White
                                        )
                                    ) {
                                        Text(Strings.get("feedback_send", lang), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                } else {
                                    // Форма приостановки обновлений
                                    val now = System.currentTimeMillis()
                                    val isPaused = appSettings.value.updatesPausedForever || appSettings.value.updatesPausedUntilMs > now
                                    
                                    val presets = listOf(
                                        UpdateText.get("pause_1week", lang) to 7L,
                                        UpdateText.get("pause_2weeks", lang) to 14L,
                                        UpdateText.get("pause_1month", lang) to 30L,
                                        UpdateText.get("pause_forever", lang) to -1L,
                                        UpdateText.get("pause_custom", lang) to 0L
                                    )
                                    
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                        modifier = Modifier.animateContentSize(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    ) {
                                        if (isPaused) {
                                            Text(
                                                if (appSettings.value.updatesPausedForever) {
                                                    UpdateText.get("paused_forever_status", lang)
                                                } else {
                                                    val days = ((appSettings.value.updatesPausedUntilMs - now) / (24 * 60 * 60 * 1000L)).toInt() + 1
                                                    UpdateText.get("paused_until_status", lang).replace("%d", days.toString())
                                                },
                                                fontSize = 12.sp,
                                                color = Color(0xFFFFA726)
                                            )
                                        }
                                        
                                        Text(
                                            UpdateText.get("pause_subtitle", lang),
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = muted
                                        )
                                        
                                        Box {
                                            // Фоновая плашка которая переплывает
                                            val selectedOffset by animateDpAsState(
                                                targetValue = if (selectedPauseOption != null) (selectedPauseOption!! * 52).dp else 0.dp,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                label = "pause_selection_offset"
                                            )
                                            
                                            if (selectedPauseOption != null) {
                                                Box(
                                                    Modifier
                                                        .offset(y = selectedOffset)
                                                        .fillMaxWidth()
                                                        .height(44.dp)
                                                        .padding(horizontal = 10.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(colors["accent"]!!.copy(alpha = 0.12f))
                                                        .border(1.dp, colors["accent"]!!.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                                )
                                            }
                                            
                                            Column(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                                                    .border(1.dp, border, RoundedCornerShape(16.dp))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                presets.forEachIndexed { idx, (label, days) ->
                                                    val isSelected = selectedPauseOption == idx
                                                    Row(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(44.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                if (soundClickEnabled) ClickPlayer.play(ctx)
                                                                selectedPauseOption = idx
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val buttonContent = if (isDark) Color.Black else Color.White
                                                        Box(
                                                            Modifier
                                                                .size(20.dp)
                                                                .clip(CircleShape)
                                                                .border(2.dp, if (isSelected) colors["accent"]!! else muted, CircleShape)
                                                                .background(if (isSelected) colors["accent"]!! else Color.Transparent),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            androidx.compose.animation.AnimatedVisibility(
                                                                visible = isSelected,
                                                                enter = scaleIn(animationSpec = tween(150, easing = FastOutSlowInEasing)),
                                                                exit = scaleOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                                                            ) {
                                                                Box(
                                                                    Modifier
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(buttonContent)
                                                                )
                                                            }
                                                        }
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(
                                                            label,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                            color = text,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        if (selectedPauseOption == 4) {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    UpdateText.get("pause_custom_hint", lang),
                                                    fontSize = 12.sp,
                                                    color = muted
                                                )
                                                OutlinedTextField(
                                                    value = customDays,
                                                    onValueChange = { 
                                                        val filtered = it.filter { c -> c.isDigit() }
                                                        customDays = if (filtered.isNotEmpty() && filtered.toIntOrNull()?.let { n -> n > 9999 } == true) {
                                                            "9999"
                                                        } else {
                                                            filtered.take(4)
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    label = { Text(UpdateText.get("pause_custom_label", lang), fontSize = 12.sp) },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedBorderColor = colors["accent"]!!,
                                                        unfocusedBorderColor = border,
                                                        focusedTextColor = text,
                                                        unfocusedTextColor = text,
                                                        cursorColor = colors["accent"]!!,
                                                        focusedLabelColor = colors["accent"]!!,
                                                        unfocusedLabelColor = muted
                                                    ),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            }
                                        }
                                        
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isPaused) {
                                                TextButton(onClick = {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    save(appSettings.value.copy(
                                                        updatesPausedUntilMs = 0L,
                                                        updatesPausedForever = false
                                                    ))
                                                    showPauseUpdates = false
                                                }) {
                                                    Text(UpdateText.get("pause_resume", lang), color = muted)
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Button(
                                                onClick = {
                                                    if (soundClickEnabled) ClickPlayer.play(ctx)
                                                    val option = selectedPauseOption ?: return@Button
                                                    val (_, days) = presets[option]
                                                    when {
                                                        days == -1L -> {
                                                            save(appSettings.value.copy(
                                                                updatesPausedUntilMs = 0L,
                                                                updatesPausedForever = true
                                                            ))
                                                            showPauseUpdates = false
                                                        }
                                                        days == 0L -> {
                                                            val custom = customDays.toLongOrNull() ?: return@Button
                                                            if (custom < 1) return@Button
                                                            val until = now + custom * 24 * 60 * 60 * 1000L
                                                            save(appSettings.value.copy(
                                                                updatesPausedUntilMs = until,
                                                                updatesPausedForever = false
                                                            ))
                                                            showPauseUpdates = false
                                                        }
                                                        else -> {
                                                            val until = now + days * 24 * 60 * 60 * 1000L
                                                            save(appSettings.value.copy(
                                                                updatesPausedUntilMs = until,
                                                                updatesPausedForever = false
                                                            ))
                                                            showPauseUpdates = false
                                                        }
                                                    }
                                                },
                                                enabled = selectedPauseOption != null && (selectedPauseOption != 4 || customDays.isNotBlank()),
                                                shape = RoundedCornerShape(13.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors["accent"]!!,
                                                    contentColor = if (isDark) Color.Black else Color.White
                                                )
                                            ) {
                                                Text(
                                                    UpdateText.get("pause_apply", lang),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                                }
                        }
                    }
                }
            }
        }
    }
}

// ─── Статистика системы ───

data class AppStats(val fps: Int = 0, val cpu: Float = 0f, val ram: Int = 0, val gpu: Float = 0f)

private var prevCpuTime = 0L
private var prevWallTime = 0L

fun getCpuUsage(): Float {
    return try {
        val cpuTime = android.os.Process.getElapsedCpuTime()
        val wallTime = System.nanoTime()
        if (prevCpuTime == 0L) { prevCpuTime = cpuTime; prevWallTime = wallTime; return 0f }
        val dCpu = cpuTime - prevCpuTime
        val dWall = wallTime - prevWallTime
        prevCpuTime = cpuTime; prevWallTime = wallTime
        if (dWall <= 0) return 0f
        (dCpu.toFloat() / dWall * 100f).coerceIn(0f, 100f)
    } catch (_: Exception) { 0f }
}

private fun readAppMem(ctx: Context): Int {
    return try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
        if (info.isNotEmpty()) info[0].totalPrivateDirty / 1024 else 0
    } catch (_: Exception) { 0 }
}

private object FpsLogger {
    private var lastLogTime = 0L
    private var frameCount = 0
    private var lastFps = 0
    private val recentInteractions = mutableListOf<String>()
    private val logFile by lazy {
        java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "nelev_fps_log.txt").also {
            it.parentFile?.mkdirs()
            it.createNewFile()
        }
    }

    fun init(ctx: Context) {
        recentInteractions.clear()
        try {
            logFile.appendText("\n=== Session started ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ===\n")
        } catch (_: Exception) {}
    }

    fun trackInteraction(action: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        synchronized(recentInteractions) {
            recentInteractions.add("[$ts] $action")
            if (recentInteractions.size > 20) recentInteractions.removeAt(0)
        }
    }

    fun logFrame(ctx: Context, fps: Int, cpu: Float, ram: Int, now: Long) {
        frameCount++
        if (now - lastLogTime < 100) return
        lastLogTime = now

        val cpuStr = if (cpu > 0) "${cpu.toInt()}%" else "N/A"
        val streaming = NelevService.streaming
        val muted = NelevService.isMuted
        val vol = NelevService.volume
        val target = NelevService.targetIp.ifEmpty { "none" }
        val effects = buildList {
            if (NelevService.reverbAmount > 0f) add("reverb=${NelevService.reverbAmount}")
            if (NelevService.bassAmount > 0f) add("bass=${NelevService.bassAmount}")
            if (NelevService.pitchAmount != 1f) add("pitch=${NelevService.pitchAmount}")
            if (NelevService.enhancerDc) add("DC")
            if (NelevService.enhancerHp) add("HP")
            if (NelevService.enhancerLimiter) add("limiter")
            if (NelevService.enhancerGate) add("gate")
            if (NelevService.eqLow != 0f) add("eqLow=${NelevService.eqLow}")
            if (NelevService.eqMid != 0f) add("eqMid=${NelevService.eqMid}")
            if (NelevService.eqHigh != 0f) add("eqHigh=${NelevService.eqHigh}")
        }

        val fpsDelta = fps - lastFps
        val fpsIndicator = when {
            fps >= 55 -> "OK"
            fps >= 30 -> "SLOW"
            fps >= 15 -> "LOW"
            else -> "CRIT"
        }

        val recentActions = synchronized(recentInteractions) {
            if (recentInteractions.isNotEmpty()) " | recent: ${recentInteractions.takeLast(3).joinToString("; ")}"
            else ""
        }

        val entry = buildString {
            append("[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}] ")
            append("FPS=$fps[$fpsIndicator] ")
            append("Δ${if (fpsDelta >= 0) "+" else ""}$fpsDelta ")
            append("CPU=$cpuStr RAM=${ram}MB ")
            append("stream=${if (streaming) "ON→$target" else "OFF"} ")
            append("vol=${"%.1f".format(vol)}${if (muted) " MUTE" else ""} ")
            if (effects.isNotEmpty()) append("fx=[${effects.joinToString(",")}] ")
            append("frames=$frameCount")
            append(recentActions)
        }

        lastFps = fps
        try {
            logFile.appendText("$entry\n")
        } catch (_: Exception) {}
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, bgColor: Color = Color(0x80000000), labelColor: Color = Color.White) {
    Surface(
        color = bgColor.copy(alpha = 0.6f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(min = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Normal)
            Text(
                value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
