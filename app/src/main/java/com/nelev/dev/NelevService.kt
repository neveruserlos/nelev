package com.nelev.dev

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class NelevService : Service() {

    companion object {
        const val CHANNEL_ID = "nelev_service"
        const val NOTIF_ID   = 1
        const val PORT       = 55000
        const val ACTION_RESTORE_NOTIFICATION = "com.nelev.dev.ACTION_RESTORE_NOTIFICATION"

        @Volatile var targetIp    : String  = ""
        @Volatile var volume      : Float   = 1f
        @Volatile var isMuted     : Boolean = false
        @Volatile var reverbAmount: Float   = 0f
        @Volatile var bassAmount  : Float   = 0f
        @Volatile var pitchAmount : Float   = 0f
        @Volatile var streaming   : Boolean = false

        // ── Улучшайзер (опционально, по умолчанию выкл = RAW) ──
        @Volatile var enhancerDc      : Boolean = false
        @Volatile var enhancerHp      : Boolean = false
        @Volatile var enhancerLimiter : Boolean = false
        @Volatile var enhancerGate    : Boolean = false

        // ── Эквалайзер голоса (опционально, 0 = без изменений) ──
        @Volatile var eqLow     : Float = 0f   // -1..1  → low-shelf 250Гц ±12dB (теплота)
        @Volatile var eqMid     : Float = 0f   // -1..1  → peaking 1.5кГц ±9dB (разборчивость)
        @Volatile var eqHigh    : Float = 0f   // -1..1  → high-shelf 5кГц ±9dB (воздух/шипение)
    }

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationWatchdog: Job? = null

    // Пользователь может скрыть уведомление из шторки (на Android 14+ это
    // разрешено даже для ongoing). Ловим удаление и сразу возвращаем его.
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_RESTORE_NOTIFICATION) {
                repostNotification()
            }
        }
    }

    @Volatile private var streaming = false

    private val SAMPLE_RATE = 48_000
    private val CHANNEL_IN  = AudioFormat.CHANNEL_IN_MONO
    private val ENCODING    = AudioFormat.ENCODING_PCM_16BIT
    // LATENCY: 96 samples = 2ms - минимальная задержка на фрейм
    private val FRAME_SIZE  = 96

    // RAW: максимально честный сигнал — UNPROCESSED если есть, иначе MIC без обработки
    // VOICE_RECOGNITION / перефильтрация меняют тембр, поэтому по просьбе «как с микрофона»
    // возвращаем классический MIC (сырой, без нашей обработки).
    private fun bestAudioSource(): Int {
        if (Build.VERSION.SDK_INT >= 24) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
                return MediaRecorder.AudioSource.UNPROCESSED
            }
        }
        return MediaRecorder.AudioSource.MIC
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        registerDismissReceiver()
        startNotificationWatchdog()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_REFRESH" -> {
                updateNotification()
                return START_STICKY
            }
            "ACTION_STOP" -> {
                // Финализируем активную сессию так же, как кнопка Stop в приложении
                try {
                    val activeStart = SessionStore.loadActiveStart(this)
                    if (activeStart > 0L) {
                        val dur = (System.currentTimeMillis() - activeStart).coerceAtLeast(0L)
                        SessionStore.append(this, SessionRecord(startMs = activeStart, durationMs = dur, avgPingMs = 0L))
                    }
                } catch (_: Exception) {}
                streaming = false
                Companion.streaming = false
                job.cancel()
                // Сообщаем открытому UI, что передача остановлена из уведомления
                try {
                    sendBroadcast(Intent("com.nelev.dev.ACTION_STOPPED").setPackage(packageName))
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        if (!streaming) {
            streaming = true
            Companion.streaming = true
            scope.launch {
                try {
                    var retryDelay = 1000L
                    while (streaming && job.isActive) {
                        try {
                            streamLoop()
                        } catch (_: Exception) {}
                        if (!streaming || !job.isActive) break
                        delay(retryDelay.coerceAtMost(5000L))
                        retryDelay = (retryDelay * 2).coerceAtMost(5000L)
                        ensureActive()
                    }
                } finally {
                    streaming = false
                    Companion.streaming = false
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationWatchdog?.cancel()
        notificationWatchdog = null
        try { unregisterReceiver(dismissReceiver) } catch (_: Exception) {}
        job.cancel()
        streaming = false
        Companion.streaming = false
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, NelevService::class.java).apply { setPackage(packageName) }
        val pi = PendingIntent.getService(applicationContext, 1, restart, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.ELAPSED_REALTIME, 1500, pi)
        super.onTaskRemoved(rootIntent)
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nelev:mic_stream").also { it.acquire() }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    private fun ensureChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "nelev Mic Service", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Фоновый стриминг микрофона nelev"
            setShowBadge(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE)
        
        // Получаем язык напрямую из настроек (надёжнее рефлексии)
        val lang = try {
            SettingsStore.load(this).language.ifBlank { "en" }
        } catch (_: Exception) { "en" }
        
        // Многоязычный текст
        val contentText = when (lang) {
            "ru" -> "с ♡  ${targetIp.ifBlank { "PC" }}"
            "en" -> "with ♡  ${targetIp.ifBlank { "PC" }}"
            "es" -> "con ♡  ${targetIp.ifBlank { "PC" }}"
            "zh" -> "用 ♡  ${targetIp.ifBlank { "PC" }}"
            "ja" -> "♡ で  ${targetIp.ifBlank { "PC" }}"
            "ko" -> "♡ 로  ${targetIp.ifBlank { "PC" }}"
            "pt" -> "com ♡  ${targetIp.ifBlank { "PC" }}"
            "uk" -> "з ♡  ${targetIp.ifBlank { "PC" }}"
            else -> "with ♡  ${targetIp.ifBlank { "PC" }}"
        }
        
        // Текст кнопки Stop
        val stopText = when (lang) {
            "ru" -> "Остановить"
            "en" -> "Stop"
            "es" -> "Detener"
            "zh" -> "停止"
            "ja" -> "停止"
            "ko" -> "중지"
            "pt" -> "Parar"
            "uk" -> "Зупинити"
            else -> "Stop"
        }
        
        // Intent для кнопки "Остановить"
        val stopIntent = Intent(this, NelevService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(contentText)
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    this, 3,
                    Intent(ACTION_RESTORE_NOTIFICATION).setPackage(packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, stopText, stopPendingIntent)
            .build()
    }

    private fun registerDismissReceiver() {
        try {
            val filter = IntentFilter(ACTION_RESTORE_NOTIFICATION)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(dismissReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    // Страховка: если система не доставила deleteIntent (OEM-прошивки,
    // Android 14+), раз в пару секунд проверяем, что наше уведомление
    // действительно висит в шторке, и возвращаем его.
    private fun startNotificationWatchdog() {
        if (notificationWatchdog?.isActive == true) return
        notificationWatchdog = scope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive) {
                delay(2500)
                try {
                    if (Build.VERSION.SDK_INT >= 23 && streaming) {
                        val alive = nm.activeNotifications.any { it.id == NOTIF_ID }
                        if (!alive) repostNotification()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun repostNotification() {
        try {
            if (!streaming) return
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
            // startForeground (а не notify) — надёжно перепривязывает
            // уведомление к foreground-сервису после ручного скрытия.
            startForeground(NOTIF_ID, buildNotification())
        } catch (_: Exception) {}
    }
    
    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    // ─── QUALITY без задержки: DC-blocker + Low-cut 80 Hz ─────────────────────
    // Убирает постоянную составляющую, гулы, ветер, вибрации корпуса — голос чище.
    // Оба фильтра — IIR 1-го/2-го порядка, нулевая дополнительная задержка (per-sample).

    class DcBlocker {
        private var x1 = 0f; private var y1 = 0f
        // R=0.995 → срез ~15 Hz на 48кГц, убирает DC без влияния на голос
        private val r = 0.995f
        fun process(buf: FloatArray) {
            for (i in buf.indices) {
                val x = buf[i]
                val y = x - x1 + r * y1
                x1 = x; y1 = y
                buf[i] = y
            }
        }
        fun reset() { x1 = 0f; y1 = 0f }
    }

    class HighPass80 {
        // Biquad HPF 80 Hz, Q=0.707 (Butterworth) — убирает гул/бубнёж, оставляет тело голоса
        private var x1 = 0f; private var x2 = 0f
        private var y1 = 0f; private var y2 = 0f
        private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
        private var a1 = 0f; private var a2 = 0f
        private var inited = false
        fun init(sampleRate: Int) {
            val fc = 80f; val q = 0.7071f
            val w0 = 2f * PI.toFloat() * fc / sampleRate
            val cosW = cos(w0); val sinW = sin(w0)
            val alpha = sinW / (2f * q)
            val a0 = 1f + alpha
            b0 = ((1f + cosW) / 2f) / a0
            b1 = (-(1f + cosW)) / a0
            b2 = ((1f + cosW) / 2f) / a0
            a1 = (-2f * cosW) / a0
            a2 = (1f - alpha) / a0
            inited = true
        }
        fun process(buf: FloatArray, sampleRate: Int) {
            if (!inited) init(sampleRate)
            for (i in buf.indices) {
                val x = buf[i]
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y
                buf[i] = y
            }
        }
        fun reset() { x1=0f; x2=0f; y1=0f; y2=0f }
    }

    class SoftLimiter {
        // Мягкий лимитер без lookahead (0 задержка): вместо жёсткого клипа — плавная компрессия
        // threshold -1dB, soft-knee → нет хрипов при volume>1, громкость выше без искажений
        fun process(buf: FloatArray, threshold: Float = 0.92f) {
            for (i in buf.indices) {
                var s = buf[i]
                val a = abs(s)
                if (a > threshold) {
                    // кубическая сатурация: y = thr + (1-thr)*tanh((|x|-thr)/(1-thr))
                    // аппроксимация tanh через полином — быстро и без задержки
                    val over = (a - threshold) / (1f - threshold)
                    val compressed = (tanh(over.toDouble()).toFloat())
                    s = s.sign * (threshold + (1f - threshold) * compressed * 0.6f)
                }
                buf[i] = s
            }
        }
    }

    class Gate {
        // Простой гейт с плавной огибающей — режет шипение в паузах, 0 задержка
        private var env = 0f
        fun process(buf: FloatArray) {
            // RMS за фрейм (~2мс)
            var sum = 0f; for (s in buf) sum += s*s
            val rms = sqrt(sum / buf.size)
            // attack 5мс, release 80мс при 48кГц — по фреймам 2мс
            val target = if (rms < 0.008f) 0f else 1f   // -42дБ порог
            val coeff = if (target > env) 0.35f else 0.05f
            env += (target - env) * coeff
            if (env < 0.5f) {
                val g = env * 0.8f // плавное закрытие, не щелчок
                for (i in buf.indices) buf[i] *= g
            }
        }
        fun reset() { env = 0f }
    }

    class VoiceEQ {
        // 3-полосный EQ для голоса: low-shelf 250Гц, peaking 1.5кГц, high-shelf 5кГц
        // Все biquad, 0 доп. задержки
        private var lx1=0f; private var lx2=0f; private var ly1=0f; private var ly2=0f
        private var mx1=0f; private var mx2=0f; private var my1=0f; private var my2=0f
        private var hx1=0f; private var hx2=0f; private var hy1=0f; private var hy2=0f
        private var prevLow=-99f; private var prevMid=-99f; private var prevHigh=-99f
        private var lb0=0f; private var lb1=0f; private var lb2=0f; private var la1=0f; private var la2=0f
        private var mb0=0f; private var mb1=0f; private var mb2=0f; private var ma1=0f; private var ma2=0f
        private var hb0=0f; private var hb1=0f; private var hb2=0f; private var ha1=0f; private var ha2=0f
        private fun recompute(low: Float, mid: Float, high: Float, sr: Int) {
            if (abs(low-prevLow)>0.01f) {
                prevLow=low
                if (abs(low)<0.01f) { lb0=1f; lb1=0f; lb2=0f; la1=0f; la2=0f }
                else {
                    val dB=low*9f; val amp=10f.pow(dB/40f); val fc=250f; val w0=2f*PI.toFloat()*fc/sr
                    val cosW=cos(w0); val sinW=sin(w0); val sqrtA=sqrt(amp); val alpha=sinW/2f*sqrt((amp+1/amp)*(1/0.7f-1)+2f)
                    val b0=amp*((amp+1)-(amp-1)*cosW+2*sqrtA*alpha); val b1=2*amp*((amp-1)-(amp+1)*cosW); val b2=amp*((amp+1)-(amp-1)*cosW-2*sqrtA*alpha)
                    val a0=(amp+1)+(amp-1)*cosW+2*sqrtA*alpha; lb0=b0/a0; lb1=b1/a0; lb2=b2/a0; la1=(-2*((amp-1)+(amp+1)*cosW))/a0; la2=((amp+1)+(amp-1)*cosW-2*sqrtA*alpha)/a0
                }
            }
            if (abs(mid-prevMid)>0.01f) {
                prevMid=mid
                if (abs(mid)<0.01f) { mb0=1f; mb1=0f; mb2=0f; ma1=0f; ma2=0f }
                else {
                    val dB=mid*9f; val amp=10f.pow(dB/20f); val fc=1500f; val q=1.0f; val w0=2f*PI.toFloat()*fc/sr
                    val cosW=cos(w0); val sinW=sin(w0); val alpha=sinW/(2*q); val a0=1+alpha/amp
                    val b0=(1+alpha*amp)/a0; val b1=(-2*cosW)/a0; val b2=(1-alpha*amp)/a0; val a1=(-2*cosW)/a0; val a2=(1-alpha/amp)/a0
                    mb0=b0; mb1=b1; mb2=b2; ma1=a1; ma2=a2
                }
            }
            if (abs(high-prevHigh)>0.01f) {
                prevHigh=high
                if (abs(high)<0.01f) { hb0=1f; hb1=0f; hb2=0f; ha1=0f; ha2=0f }
                else {
                    val dB=high*9f; val amp=10f.pow(dB/40f); val fc=5000f; val w0=2f*PI.toFloat()*fc/sr
                    val cosW=cos(w0); val sinW=sin(w0); val sqrtA=sqrt(amp); val alpha=sinW/2f*sqrt((amp+1/amp)*(1/0.7f-1)+2f)
                    val b0=amp*((amp+1)+(amp-1)*cosW+2*sqrtA*alpha); val b1=-2*amp*((amp-1)+(amp+1)*cosW); val b2=amp*((amp+1)+(amp-1)*cosW-2*sqrtA*alpha)
                    val a0=(amp+1)-(amp-1)*cosW+2*sqrtA*alpha; hb0=b0/a0; hb1=b1/a0; hb2=b2/a0; ha1=(2*((amp-1)-(amp+1)*cosW))/a0; ha2=((amp+1)-(amp-1)*cosW-2*sqrtA*alpha)/a0
                }
            }
        }
        fun process(buf: FloatArray, low: Float, mid: Float, high: Float, sr: Int) {
            if (abs(low)<0.01f && abs(mid)<0.01f && abs(high)<0.01f) return
            recompute(low, mid, high, sr)
            for (i in buf.indices) {
                var x = buf[i]
                if (abs(low)>0.01f) { val y=lb0*x+lb1*lx1+lb2*lx2-la1*ly1-la2*ly2; lx2=lx1; lx1=x; ly2=ly1; ly1=y; x=y }
                if (abs(mid)>0.01f) { val y=mb0*x+mb1*mx1+mb2*mx2-ma1*my1-ma2*my2; mx2=mx1; mx1=x; my2=my1; my1=y; x=y }
                if (abs(high)>0.01f){ val y=hb0*x+hb1*hx1+hb2*hx2-ha1*hy1-ha2*hy2; hx2=hx1; hx1=x; hy2=hy1; hy1=y; x=y }
                buf[i]=x
            }
        }
        fun reset(){ lx1=0f; lx2=0f; ly1=0f; ly2=0f; mx1=0f; mx2=0f; my1=0f; my2=0f; hx1=0f; hx2=0f; hy1=0f; hy2=0f }
    }

    // ─── FIX 6: Bass — очень мягкий low-shelf с малым усилением ──────────────────
    // Ранее усиление было до +24 dB на 400 Hz, что давало резкое, грязное звучание.
    // Теперь максимум +10 dB, частота среза снижена до 200 Hz, добавлен плавный gain ramp.

    class SoftBassFilter {
        private var x1 = 0f; private var x2 = 0f
        private var y1 = 0f; private var y2 = 0f
        private var prevAmt = -1f
        private var nb0 = 0f; private var nb1 = 0f; private var nb2 = 0f
        private var na1 = 0f; private var na2 = 0f

        fun process(buf: FloatArray, bassAmt: Float, sampleRate: Int) {
            if (bassAmt < 0.01f) {
                x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f; prevAmt = -1f; return
            }
            // Пересчитываем коэффициенты только при изменении bassAmt
            if (abs(bassAmt - prevAmt) > 0.005f) {
                prevAmt = bassAmt
                // FIX 6: мягкое усиление — максимум +10 dB вместо +24
                val dB   = bassAmt * 10f
                val amp  = 10f.pow(dB / 40f)           // половинное усиление для shelf
                // FIX 6: частота среза 200 Hz — только суббасс/низкий бас
                val fc   = 200f
                val w0   = 2f * PI.toFloat() * fc / sampleRate
                val cosW = cos(w0); val sinW = sin(w0)
                val sqrtA = sqrt(amp)
                // FIX 6: Q = 0.5 — очень широкий, плавный shelf без пиков
                val alpha = sinW / 2f * sqrt((amp + 1f / amp) * (1f / 0.5f - 1f) + 2f)
                val b0 = amp * ((amp + 1f) - (amp - 1f) * cosW + 2f * sqrtA * alpha)
                val b1 = 2f * amp * ((amp - 1f) - (amp + 1f) * cosW)
                val b2 = amp * ((amp + 1f) - (amp - 1f) * cosW - 2f * sqrtA * alpha)
                val a0 = (amp + 1f) + (amp - 1f) * cosW + 2f * sqrtA * alpha
                nb0 = b0 / a0; nb1 = b1 / a0; nb2 = b2 / a0
                na1 = (-2f * ((amp - 1f) + (amp + 1f) * cosW)) / a0
                na2 = ((amp + 1f) + (amp - 1f) * cosW - 2f * sqrtA * alpha) / a0
            }
            for (i in buf.indices) {
                val x = buf[i]
                val y = nb0 * x + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y
                buf[i] = y
            }
        }

        fun reset() { x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f; prevAmt = -1f }
    }

    // ─── FIX 6: Pitch — гранулярный сдвиг с перекрытием Hann без артефактов ─────
    // Предыдущий OLA давал помехи из-за накопления ошибки позиции и переполнения буфера.
    // Новый алгоритм:
    // 1. Берём грейн (зерно) из входного буфера с окном Hann
    // 2. Записываем его в выход со сдвигом hopOut = hopIn / rate
    // 3. Нормализуем перекрытия
    // Это классический SOLA без поиска — быстро и достаточно чисто для голоса ±0.5 окт.

    class GranularPitch {
        // LATENCY: зерно 256 (≈5.3ms) вместо 512 (≈10.7ms) — вдвое меньше задержка
        private val grainSize = 256                        // размер зерна
        private val hopIn     = grainSize / 2              // шаг анализа
        private val hann      = FloatArray(grainSize) {
            (0.5f * (1.0 - cos(2.0 * PI * it / grainSize))).toFloat()
        }

        // Скользящий буфер входа — 4 * grainSize хватит
        private val inRing    = FloatArray(grainSize * 4)
        private var inWrPos   = 0
        private var inRdFloat = 0.0          // позиция чтения с дробной частью

        // Буфер выхода с аккумулятором
        private val outRing   = FloatArray(grainSize * 4)
        private val outNorm   = FloatArray(grainSize * 4)
        private val outSize   = outRing.size
        private var outWrPos  = 0
        private var outRdPos  = 0

        fun process(input: FloatArray, semitones: Float): FloatArray {
            // FIX: сбрасываем состояние когда pitch близок к нулю —
            // иначе накапливаются артефакты из предыдущих вызовов → "роботизация"
            if (abs(semitones) < 0.05f) { reset(); return input }

            // QUALITY FIX: правильная формула 2^(semitones/12), было 2^(semitones*0.5) = до 64x ошибки
            val rate   = 2.0.pow(semitones.toDouble() / 12.0)
            val hopOut = (hopIn / rate).toInt().coerceAtLeast(1)

            val output = FloatArray(input.size)
            var outIdx = 0

            // Записываем вход в кольцевой буфер
            for (s in input) {
                inRing[inWrPos % inRing.size] = s
                inWrPos++
            }

            // Генерируем зёрна пока в выходном буфере недостаточно данных
            while (outWrPos - outRdPos < input.size) {
                // Читаем зерно с дробной позиции через линейную интерполяцию
                for (i in 0 until grainSize) {
                    val pos  = inRdFloat + i
                    val iPos = pos.toLong()
                    val frac = (pos - iPos).toFloat()
                    val a    = inRing[(iPos % inRing.size).toInt()]
                    val b    = inRing[((iPos + 1) % inRing.size).toInt()]
                    val s    = a + (b - a) * frac
                    val oi   = (outWrPos + i) % outSize
                    outRing[oi] += s * hann[i]
                    outNorm[oi] += hann[i]
                }
                inRdFloat += hopIn.toDouble()
                // LATENCY/STABILITY: не уходить в чтение "будущих"/устаревших сэмплов
                if (inRdFloat > inWrPos - 1.0) inRdFloat = (inWrPos - 1.0).coerceAtLeast(0.0)
                outWrPos  += hopOut
            }

            // Читаем output
            while (outIdx < output.size && outRdPos < outWrPos) {
                val oi = outRdPos % outSize
                output[outIdx++] = if (outNorm[oi] > 1e-6f) outRing[oi] / outNorm[oi] else 0f
                outRing[oi]  = 0f
                outNorm[oi]  = 0f
                outRdPos++
            }

            return output
        }

        fun reset() {
            inWrPos = 0
            inRdFloat = 0.0
            outWrPos = 0
            outRdPos = 0
            for (i in inRing.indices) inRing[i] = 0f
            for (i in outRing.indices) outRing[i] = 0f
            for (i in outNorm.indices) outNorm[i] = 0f
        }
    }

    private val resolveExecutor = java.util.concurrent.Executors.newCachedThreadPool { r ->
        Thread(r, "nelev-dns").apply { isDaemon = true }
    }

    private fun resolveIpWithTimeout(ip: String, timeoutMs: Long): InetAddress? {
        return try {
            val future = resolveExecutor.submit<InetAddress> { InetAddress.getByName(ip) }
            future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun streamLoop() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(this@NelevService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return@withContext
        }

        // LATENCY+QUALITY: максимальный приоритет + LOW_LATENCY mode
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        // LATENCY: минимальный буфер AudioRecord = 1 фрейм × 4 (стерео-запас)
        // QUALITY: пробуем PERFORMANCE_MODE_LOW_LATENCY через Builder
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufSize = maxOf(minBuf, FRAME_SIZE * 4)

        val audioSource = bestAudioSource()
        val recorder: AudioRecord = try {
            if (Build.VERSION.SDK_INT >= 23) {
                val fmt = AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_IN)
                    .build()
                val builder = AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(bufSize)
                // PERFORMANCE_MODE_LOW_LATENCY via reflection — без compile-time зависимости
                if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        val m = builder.javaClass.getMethod("setPerformanceMode", Int::class.javaPrimitiveType)
                        m.invoke(builder, 1) // 1 = PERFORMANCE_MODE_LOW_LATENCY
                    } catch (_: Exception) {}
                }
                builder.build()
            } else {
                AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufSize)
            }
        } catch (_: Exception) {
            AudioRecord(audioSource, SAMPLE_RATE, CHANNEL_IN, ENCODING, bufSize)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); stopSelf(); return@withContext }
        // RAW: не трогаем AGC/NS — оставляем как даёт HAL (иначе меняем громкость/тембр)
        recorder.startRecording()

        val socket = try {
            DatagramSocket().also {
                it.sendBufferSize = 65536   // LATENCY: меньший send buffer = меньше буферизации в ядре
                it.trafficClass  = 0xB8     // LATENCY: DSCP EF (Expedited Forwarding) — приоритет QoS
            }
        } catch (_: Exception) { recorder.stop(); recorder.release(); stopSelf(); return@withContext }

        val pcmBuf   = ShortArray(FRAME_SIZE)
        val MAGIC    = "NLEV".toByteArray(Charsets.US_ASCII)

        // OPTIMIZATION: переиспользуем буферы — ноль аллокаций в горячем цикле
        val packetPayloadSize = 20 + FRAME_SIZE * 2
        val pktData  = ByteArray(packetPayloadSize)
        val pktBuf   = ByteBuffer.wrap(pktData).order(ByteOrder.LITTLE_ENDIAN)
        // Заполняем MAGIC + reserved один раз
        pktBuf.put(MAGIC)
        repeat(4) { pktBuf.putInt(0) }
        val audioOffset = pktBuf.position()   // = 20

        // OPTIMIZATION: кэшируем InetAddress — getByName() делает DNS lookup каждый раз
        var cachedIp   = ""
        var cachedAddr: InetAddress? = null
        // Переиспользуем DatagramPacket
        val datagramPkt = DatagramPacket(pktData, pktData.size)

        // Reverb comb filter state
        // LATENCY: максимум 30ms вместо 60ms — меньше задержка при включённом ревербе
        val reverbDelay = IntArray(4) { (0.008f * SAMPLE_RATE * (it + 1)).toInt() }
        val reverbLines = Array(4) { FloatArray(reverbDelay[it]) }
        val reverbPos   = IntArray(4)

        // ── Улучшайзер + EQ (опционально, по умолчанию выкл = RAW) ──
        val dcBlocker    = DcBlocker()
        val highPass80   = HighPass80()
        val limiter      = SoftLimiter()
        val gate         = Gate()
        val voiceEq      = VoiceEQ()
        val bassFilter   = SoftBassFilter()
        val pitchShifter = GranularPitch()

        var smoothBass  = 0f
        var smoothPitch = 0f
        
        // Heartbeat активной сессии — переживает аварийное завершение
        var lastHeartbeat = 0L

        try {
            while (job.isActive) {
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastHeartbeat > 3000L) {
                    lastHeartbeat = nowMs
                    try {
                        val st = SessionStore.loadActiveStart(this@NelevService)
                        if (st > 0L) SessionStore.saveActiveSession(this@NelevService, st, nowMs)
                    } catch (_: Exception) {}
                }
                
                val read = recorder.read(pcmBuf, 0, FRAME_SIZE)
                if (read <= 0) continue

                var buf = FloatArray(read) { pcmBuf[it] / 32768f }

                if (isMuted) {
                    buf.fill(0f)
                    dcBlocker.reset(); highPass80.reset(); gate.reset(); voiceEq.reset()
                } else {
                    // ── 1. Улучшайзер (только если включен) — 0мс задержка ──
                    if (enhancerDc) dcBlocker.process(buf)
                    if (enhancerGate) gate.process(buf)
                    if (enhancerHp) highPass80.process(buf, SAMPLE_RATE)

                    // ── 2. Базовые эффекты (как раньше, только если >0) ──
                    val hasBass   = bassAmount > 0.01f || smoothBass > 0.01f
                    val hasPitch  = abs(pitchAmount) > 0.01f || abs(smoothPitch) > 0.01f
                    val hasReverb = reverbAmount > 0.01f
                    val hasEq     = abs(eqLow) > 0.01f || abs(eqMid) > 0.01f || abs(eqHigh) > 0.01f

                    if (hasBass || hasPitch) {
                        smoothBass  += (bassAmount  - smoothBass)  * 0.08f
                        smoothPitch += (pitchAmount - smoothPitch) * 0.08f
                        if (hasBass) bassFilter.process(buf, smoothBass, SAMPLE_RATE)
                        if (hasPitch && abs(smoothPitch) > 0.05f) {
                            buf = pitchShifter.process(buf, smoothPitch * 12f)
                        }
                    } else {
                        smoothBass = 0f; smoothPitch = 0f
                        bassFilter.reset(); pitchShifter.reset()
                    }

                    if (hasEq) voiceEq.process(buf, eqLow, eqMid, eqHigh, SAMPLE_RATE)
                    else voiceEq.reset()

                    if (hasReverb) {
                        val revAmt = reverbAmount
                        val feedback = 0.5f + revAmt * 0.35f
                        val wet = revAmt * 0.6f; val dry = 1f - wet * 0.3f
                        for (i in buf.indices) {
                            val inp = buf[i]; var out = 0f
                            for (c in 0..3) {
                                val p = reverbPos[c]; val d = reverbLines[c][p]
                                reverbLines[c][p] = inp + d * feedback
                                reverbPos[c] = (p + 1) % reverbDelay[c]; out += d
                            }
                            buf[i] = inp * dry + out * wet * 0.25f
                        }
                    }

                    // ── 3. Гейн + лимитер (опционально) ──
                    val vol = volume
                    if (vol != 1f) for (i in buf.indices) buf[i] *= vol
                    if (enhancerLimiter) limiter.process(buf, 0.92f)
                }

                // OPTIMIZATION: пишем аудио прямо в переиспользуемый pktData начиная с audioOffset
                val audioEnd = audioOffset + buf.size * 2
                var wi = audioOffset
                for (i in buf.indices) {
                    val s = (buf[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
                    pktData[wi++] = (s.toInt() and 0xFF).toByte()
                    pktData[wi++] = (s.toInt() ushr 8).toByte()
                }

                val ip = targetIp
                if (ip.isNotBlank()) {
                    try {
                        if (ip != cachedIp) {
                            cachedIp = ip
                            cachedAddr = resolveIpWithTimeout(ip, 800L)
                        }
                        cachedAddr?.let { addr ->
                            datagramPkt.data    = pktData
                            datagramPkt.length  = audioEnd
                            datagramPkt.address = addr
                            datagramPkt.port    = PORT
                            socket.send(datagramPkt)
                        }
                    } catch (_: Exception) {
                        cachedAddr = null; cachedIp = "" // сбросить кэш при ошибке
                    }
                }
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
            try { socket.close() } catch (_: Exception) {}
            streaming = false
            Companion.streaming = false
        }
    }
}