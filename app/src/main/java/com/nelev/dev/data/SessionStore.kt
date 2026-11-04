package com.nelev.dev.data

import android.content.Context
import java.io.File

data class SndItem(val key: String, val on: Boolean, val toggle: () -> Unit)
data class SessionRecord(val startMs: Long, val durationMs: Long, val avgPingMs: Long)

object SessionStore {
    private const val FILE = "nelev_sessions.txt"
    private const val MAX = 50
    fun load(ctx: Context): List<SessionRecord> = try {
        File(ctx.filesDir, FILE).readLines().mapNotNull { line ->
            val p = line.split(",")
            if (p.size >= 3) SessionRecord(p[0].toLong(), p[1].toLong(), p[2].toLong()) else null
        }
    } catch (_: Exception) { emptyList() }
    fun append(ctx: Context, record: SessionRecord) {
        // Не сохраняем сессии короче 2 секунд
        if (record.durationMs < 2000) return
        val list = load(ctx).toMutableList().also { it.add(record) }.takeLast(MAX)
        try { File(ctx.filesDir, FILE).writeText(list.joinToString("\n") { "${it.startMs},${it.durationMs},${it.avgPingMs}" }) } catch (_: Exception) {}
    }
    fun clear(ctx: Context) { try { File(ctx.filesDir, FILE).writeText("") } catch (_: Exception) {} }
}
