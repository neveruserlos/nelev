package com.nelev.dev.data

import android.content.Context
import java.io.File

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
        val m = File(ctx.filesDir, FILE).readLines().mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
        State(m["ip"] ?: "", m["volume"]?.toFloatOrNull() ?: 1f, m["reverb"]?.toFloatOrNull() ?: 0f,
            m["bass"]?.toFloatOrNull() ?: 0f, m["pitch"]?.toFloatOrNull() ?: 0f,
            m["enhancerDc"] == "true", m["enhancerHp"] == "true", m["enhancerLimiter"] == "true", m["enhancerGate"] == "true",
            m["eqLow"]?.toFloatOrNull() ?: 0f, m["eqMid"]?.toFloatOrNull() ?: 0f, m["eqHigh"]?.toFloatOrNull() ?: 0f)
    } catch (_: Exception) { State() }
}
