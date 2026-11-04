package com.nelev.dev.ui.state

import androidx.compose.runtime.mutableStateOf
import com.nelev.dev.data.Prefs

// ─── App state ────────────────────────────────────────────────────────────────

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

