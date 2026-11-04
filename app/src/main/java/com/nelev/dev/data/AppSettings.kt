package com.nelev.dev.data

import android.content.Context
import java.io.File

// Version comes from version.properties via BuildConfig.VERSION_NAME.

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
    val syncWithPc: Boolean = true
)

object SettingsStore {
    private const val FILE = "nelev_app.txt"
    private fun generateUID(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..10).map { chars.random() }.joinToString("")
    }
    fun load(ctx: Context): AppSettings = try {
        val file = File(ctx.filesDir, FILE)
        val isFirstLaunch = !file.exists()
        val m = if (isFirstLaunch) emptyMap() else file.readLines().mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        val uid = m["userUID"]?.takeIf { it.isNotEmpty() } ?: generateUID()
        AppSettings(
            isDark = m["isDark"] == "true",
            language = m["language"] ?: "en",
            phrasesEnabled = m["phrasesEnabled"] != "false",
            connectionBarEnabled = m["connectionBarEnabled"] != "false",
            soundNotifEnabled = m["soundNotifEnabled"] != "false",
            soundClickEnabled = m["soundClickEnabled"] != "false",
            soundNotifEnabledBeforeMaster = m["soundNotifEnabledBefore"] != "false",
            soundClickEnabledBeforeMaster = m["soundClickEnabledBefore"] != "false",
            menuCentered = if (isFirstLaunch) true else m["menuCentered"] == "true",
            visualEffectsEnabled = if (isFirstLaunch) true else m["visualEffectsEnabled"] != "false",
            isFirstLaunch = isFirstLaunch,
            sendStatistics = m["sendStatistics"] == "true",
            participateInBeta = m["participateInBeta"] == "true",
            lastStatisticsSentMs = m["lastStatisticsSentMs"]?.toLongOrNull() ?: 0L,
            userUID = uid,
            autoStartEnabled = m["autoStartEnabled"] == "true",
            syncWithPc = m["syncWithPc"] != "false"
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
                    "syncWithPc=${s.syncWithPc}\n"
        )
    } catch (_: Exception) {}
}
