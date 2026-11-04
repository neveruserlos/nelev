package com.nelev.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Обновление ──────────────────────────────────────────────────────────────

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val border = colors["border"] ?: Color.DarkGray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var needPermission by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = card,
                tonalElevation = 0.dp,
                shadowElevation = 20.dp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 430.dp)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UpdateHero(
                        title = UpdateText.get("title", lang),
                        subtitle = UpdateText.get("subtitle", lang).replace("%s", info.versionName),
                        accent = accent,
                        buttonContent = buttonContent,
                        text = text,
                        muted = muted
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VersionBadge(
                                label = UpdateText.get("current", lang).replace(": %s", ""),
                                value = APP_VERSION,
                                text = text, muted = muted, border = border,
                                modifier = Modifier.weight(1f)
                            )
                            VersionBadge(
                                label = UpdateText.get("new_version", lang).replace(": %s", ""),
                                value = info.versionName,
                                text = text, muted = muted, border = accent.copy(alpha = 0.45f),
                                highlighted = true, accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (info.changelog.isNotBlank()) {
                            val arrow by animateFloatAsState(
                                if (showNotes) 180f else 0f,
                                tween(220),
                                label = "update_notes_arrow"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(alpha = 0.08f))
                                    .clickable { showNotes = !showNotes }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    UpdateText.get("whats_new", lang),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = text
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, null,
                                    Modifier.size(18.dp).graphicsLayer { rotationZ = arrow },
                                    tint = muted
                                )
                            }
                            AnimatedVisibility(visible = showNotes) {
                                Text(
                                    info.changelog,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = muted,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = accent,
                                trackColor = border
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    UpdateText.get("downloading", lang).replace("%d", progress.toString()),
                                    fontSize = 12.sp,
                                    color = muted
                                )
                                Text("$progress%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = text)
                            }
                        }

                        if (needPermission) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(accent.copy(alpha = 0.08f))
                                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        UpdateText.get("permission_title", lang),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = text
                                    )
                                }
                                Text(
                                    UpdateText.get("permission_body", lang),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = muted
                                )
                                Button(
                                    onClick = { Updater.openInstallSettings(ctx) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = buttonContent
                                    )
                                ) {
                                    Text(
                                        UpdateText.get("permission_grant", lang),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        error?.let {
                            Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFE5484D))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss, enabled = !downloading) {
                                Text(UpdateText.get("later", lang), color = muted)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = !downloading,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = buttonContent
                                ),
                                onClick = {
                                    if (!Updater.canInstall(ctx)) {
                                        // Не открываем настройки сразу: сначала показываем
                                        // объяснение и отдельную кнопку «Выдать разрешение».
                                        needPermission = true
                                        return@Button
                                    }
                                    needPermission = false
                                    scope.launch {
                                        downloading = true
                                        error = null
                                        progress = 0
                                        try {
                                            val file = Updater.download(ctx, info) { p ->
                                                withContext(Dispatchers.Main) { progress = p }
                                            }
                                            Updater.install(ctx, file)
                                            onDismiss()
                                        } catch (_: Exception) {
                                            error = UpdateText.get("error_download", lang)
                                        } finally {
                                            downloading = false
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    UpdateText.get("install", lang),
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

@Composable
private fun UpdateHero(
    title: String,
    subtitle: String,
    accent: Color,
    buttonContent: Color,
    text: Color,
    muted: Color
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(138.dp)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f), Color.Transparent)
                )
            )
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 38.dp, y = (-38).dp)
                .size(140.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 22.dp, y = 34.dp)
                .size(86.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.08f))
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent, accent.copy(alpha = 0.72f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsEx.SystemUpdate, null, Modifier.size(27.dp), tint = buttonContent)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
            }
        }
    }
}

@Composable
private fun VersionBadge(
    label: String,
    value: String,
    text: Color,
    muted: Color,
    border: Color,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accent: Color = text
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) accent.copy(alpha = 0.09f) else Color.Transparent)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, fontSize = 10.sp, color = muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = text)
    }
}

// ─── Ошибка проверки обновлений ──────────────────────────────────────────────

@Composable
fun UpdateErrorDialog(
    colors: NelevColors,
    lang: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val border = colors["border"] ?: Color.DarkGray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val danger = Color(0xFFE5484D)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = card,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(danger.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Warning, null, Modifier.size(24.dp), tint = danger)
                    }
                    Text(
                        UpdateText.get("error_check_title", lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = text
                    )
                }

                Text(
                    UpdateText.get("error_check_subtitle", lang),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = muted
                )

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ErrorReason(UpdateText.get("error_reason_1", lang), accent, muted)
                    ErrorReason(UpdateText.get("error_reason_2", lang), accent, muted)
                    ErrorReason(UpdateText.get("error_reason_3", lang), accent, muted)
                }

                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(border)
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(UpdateText.get("close", lang), color = muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if ((colors["bg"]?.luminance() ?: 0f) < 0.2f) Color.Black else Color.White
                        )
                    ) {
                        Text(UpdateText.get("retry", lang), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorReason(reason: String, accent: Color, muted: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(accent)
        )
        Text(reason, fontSize = 12.sp, lineHeight = 17.sp, color = muted)
    }
}

// ─── Что нового после обновления ─────────────────────────────────────────────

@Composable
fun WhatsNewDialog(
    versionName: String,
    changelog: String?,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = card,
            tonalElevation = 0.dp,
            shadowElevation = 20.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0.08f), Color.Transparent)
                            )
                        )
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 30.dp, y = (-34).dp)
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.10f))
                    )
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle, null,
                                Modifier.size(28.dp), tint = buttonContent
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                UpdateText.get("whats_new_title", lang).replace("%s", versionName),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = text
                            )
                            Text(
                                UpdateText.get("updated_to", lang).replace("%s", versionName),
                                fontSize = 12.sp,
                                color = muted
                            )
                        }
                    }
                }

                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        changelog?.takeIf { it.isNotBlank() }
                            ?: UpdateText.get("no_changelog", lang),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = muted,
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = buttonContent
                        )
                    ) {
                        Text(
                            UpdateText.get("whats_new_ok", lang),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─── Строки ──────────────────────────────────────────────────────────────────

internal object UpdateText {

    private val EN = mapOf(
        "title" to "Update available",
        "subtitle" to "Version %s is ready to install",
        "current" to "Current: %s",
        "new_version" to "New version: %s",
        "install" to "Update",
        "later" to "Later",
        "downloading" to "Downloading… %d%%",
        "error_check" to "Could not check for updates",
        "error_check_title" to "Couldn't check for updates",
        "error_check_subtitle" to "One of these usually causes the problem:",
        "error_reason_1" to "No internet connection — check Wi-Fi or mobile data",
        "error_reason_2" to "The update server is temporarily unavailable",
        "error_reason_3" to "VPN or network restrictions may be blocking GitHub",
        "retry" to "Retry",
        "close" to "Close",
        "error_download" to "Download failed. Try again",
        "permission_title" to "Permission needed",
        "permission_body" to "Android needs permission to install apps from this source. Tap the button below, enable NELEV, then come back and tap Update again.",
        "permission_grant" to "Grant permission",
        "permission" to "Allow installing apps from this source, then tap Update again",
        "up_to_date" to "You're up to date",
        "checking" to "Checking for updates…",
        "allow" to "Allow install",
        "whats_new" to "What's new",
        "whats_new_title" to "What's new in %s",
        "whats_new_ok" to "Got it",
        "updated_to" to "You've updated to %s",
        "no_changelog" to "Bug fixes and improvements.",
        "version_check_title" to "Version check",
        "version_check_subtitle" to "Let's make sure you're on the latest version",
        "version_current" to "Installed: %s",
        "version_latest" to "Latest: %s",
        "version_actual" to "You're up to date",
        "version_available" to "Version %s is available",
        "version_unknown" to "Couldn't get the latest version",
        "check_again" to "Check again",
        "back" to "Back",
        "next" to "Next",
        "onboarding_update" to "Update now"
    )

    private val RU = mapOf(
        "title" to "Доступно обновление",
        "subtitle" to "Версия %s готова к установке",
        "current" to "Сейчас: %s",
        "new_version" to "Новая версия: %s",
        "install" to "Обновить",
        "later" to "Позже",
        "downloading" to "Загрузка… %d%%",
        "error_check" to "Не удалось проверить обновления",
        "error_check_title" to "Не удалось проверить обновления",
        "error_check_subtitle" to "Обычно причина одна из этих:",
        "error_reason_1" to "Нет подключения к интернету — проверьте Wi-Fi или мобильные данные",
        "error_reason_2" to "Сервер обновлений временно недоступен",
        "error_reason_3" to "VPN или ограничения сети могут блокировать GitHub",
        "retry" to "Повторить",
        "close" to "Закрыть",
        "error_download" to "Ошибка загрузки. Попробуйте ещё раз",
        "permission_title" to "Нужно разрешение",
        "permission_body" to "Android требует разрешить установку приложений из этого источника. Нажмите кнопку ниже, включите NELEV, затем вернитесь и снова нажмите «Обновить».",
        "permission_grant" to "Выдать разрешение",
        "permission" to "Разрешите установку приложений из этого источника, затем нажмите «Обновить» ещё раз",
        "up_to_date" to "У вас последняя версия",
        "checking" to "Проверка обновлений…",
        "allow" to "Разрешить установку",
        "whats_new" to "Что нового",
        "whats_new_title" to "Что нового в %s",
        "whats_new_ok" to "Отлично",
        "updated_to" to "Вы обновились до версии %s",
        "no_changelog" to "Исправления и улучшения.",
        "version_check_title" to "Проверка версии",
        "version_check_subtitle" to "Проверим, не вышло ли обновление",
        "version_current" to "Установлено: %s",
        "version_latest" to "Последняя: %s",
        "version_actual" to "У вас актуальная версия",
        "version_available" to "Доступна версия %s",
        "version_unknown" to "Не удалось узнать последнюю версию",
        "check_again" to "Проверить снова",
        "back" to "Назад",
        "next" to "Далее",
        "onboarding_update" to "Обновить сейчас"
    )

    private val ES = mapOf(
        "title" to "Actualización disponible",
        "subtitle" to "La versión %s está lista para instalar",
        "current" to "Ahora: %s",
        "new_version" to "Nueva versión: %s",
        "install" to "Actualizar",
        "later" to "Más tarde",
        "downloading" to "Descargando… %d%%",
        "error_check" to "No se pudo buscar actualizaciones",
        "error_check_title" to "No se pudieron buscar actualizaciones",
        "error_check_subtitle" to "Normalmente la causa es una de estas:",
        "error_reason_1" to "Sin conexión a internet: revisa el Wi-Fi o los datos móviles",
        "error_reason_2" to "El servidor de actualizaciones no está disponible temporalmente",
        "error_reason_3" to "Una VPN o las restricciones de red pueden bloquear GitHub",
        "retry" to "Reintentar",
        "close" to "Cerrar",
        "error_download" to "Error de descarga. Inténtalo de nuevo",
        "permission_title" to "Permiso necesario",
        "permission_body" to "Android necesita permiso para instalar apps desde esta fuente. Pulsa el botón de abajo, activa NELEV y luego vuelve y pulsa Actualizar de nuevo.",
        "permission_grant" to "Dar permiso",
        "permission" to "Permite instalar aplicaciones desde esta fuente y pulsa Actualizar de nuevo",
        "up_to_date" to "Ya tienes la última versión",
        "checking" to "Buscando actualizaciones…",
        "allow" to "Permitir instalación",
        "whats_new" to "Novedades",
        "whats_new_title" to "Novedades de %s",
        "whats_new_ok" to "Entendido",
        "updated_to" to "Te has actualizado a %s",
        "no_changelog" to "Correcciones y mejoras.",
        "version_check_title" to "Comprobar versión",
        "version_check_subtitle" to "Comprobemos si hay una versión más reciente",
        "version_current" to "Instalada: %s",
        "version_latest" to "Última: %s",
        "version_actual" to "Tienes la versión más reciente",
        "version_available" to "La versión %s está disponible",
        "version_unknown" to "No se pudo obtener la última versión",
        "check_again" to "Comprobar de nuevo",
        "back" to "Atrás",
        "next" to "Siguiente",
        "onboarding_update" to "Actualizar ahora"
    )

    private val ZH = mapOf(
        "title" to "有可用更新",
        "subtitle" to "版本 %s 已可安装",
        "current" to "当前版本：%s",
        "new_version" to "新版本：%s",
        "install" to "更新",
        "later" to "稍后",
        "downloading" to "下载中… %d%%",
        "error_check" to "无法检查更新",
        "error_check_title" to "无法检查更新",
        "error_check_subtitle" to "通常是以下原因之一：",
        "error_reason_1" to "没有网络连接——请检查 Wi-Fi 或移动数据",
        "error_reason_2" to "更新服务器暂时不可用",
        "error_reason_3" to "VPN 或网络限制可能阻止了 GitHub",
        "retry" to "重试",
        "close" to "关闭",
        "error_download" to "下载失败，请重试",
        "permission_title" to "需要权限",
        "permission_body" to "Android 需要允许从此来源安装应用。点击下面的按钮，启用 NELEV，然后返回并再次点击更新。",
        "permission_grant" to "授予权限",
        "permission" to "请允许从此来源安装应用，然后再次点击更新",
        "up_to_date" to "已是最新版本",
        "checking" to "正在检查更新…",
        "allow" to "允许安装",
        "whats_new" to "新功能",
        "whats_new_title" to "%s 的新功能",
        "whats_new_ok" to "知道了",
        "updated_to" to "已更新到 %s",
        "no_changelog" to "错误修复和改进。",
        "version_check_title" to "检查版本",
        "version_check_subtitle" to "确认是否已是最新版本",
        "version_current" to "已安装：%s",
        "version_latest" to "最新：%s",
        "version_actual" to "已是最新版本",
        "version_available" to "有版本 %s 可用",
        "version_unknown" to "无法获取最新版本",
        "check_again" to "重新检查",
        "back" to "返回",
        "next" to "下一步",
        "onboarding_update" to "立即更新"
    )

    private val JA = mapOf(
        "title" to "アップデートがあります",
        "subtitle" to "バージョン %s をインストールできます",
        "current" to "現在: %s",
        "new_version" to "新しいバージョン: %s",
        "install" to "更新",
        "later" to "後で",
        "downloading" to "ダウンロード中… %d%%",
        "error_check" to "アップデートを確認できませんでした",
        "error_check_title" to "アップデートを確認できませんでした",
        "error_check_subtitle" to "通常は次のいずれかが原因です：",
        "error_reason_1" to "インターネットに接続されていません — Wi-Fi かモバイルデータを確認してください",
        "error_reason_2" to "アップデートサーバーが一時的に利用できません",
        "error_reason_3" to "VPN やネットワーク制限が GitHub をブロックしている可能性があります",
        "retry" to "再試行",
        "close" to "閉じる",
        "error_download" to "ダウンロードに失敗しました。もう一度お試しください",
        "permission_title" to "権限が必要です",
        "permission_body" to "この提供元からアプリをインストールする権限が必要です。下のボタンを押して NELEV を有効にし、戻ってもう一度「更新」を押してください。",
        "permission_grant" to "権限を許可",
        "permission" to "この提供元からのインストールを許可して、もう一度「更新」を押してください",
        "up_to_date" to "最新バージョンです",
        "checking" to "アップデートを確認中…",
        "allow" to "インストールを許可",
        "whats_new" to "新機能",
        "whats_new_title" to "%s の新機能",
        "whats_new_ok" to "わかりました",
        "updated_to" to "%s に更新しました",
        "no_changelog" to "不具合修正と改善。",
        "version_check_title" to "バージョン確認",
        "version_check_subtitle" to "最新バージョンか確認します",
        "version_current" to "インストール済み: %s",
        "version_latest" to "最新: %s",
        "version_actual" to "最新バージョンです",
        "version_available" to "バージョン %s が利用可能です",
        "version_unknown" to "最新バージョンを取得できませんでした",
        "check_again" to "再確認",
        "back" to "戻る",
        "next" to "次へ",
        "onboarding_update" to "今すぐ更新"
    )

    private val KO = mapOf(
        "title" to "업데이트가 있습니다",
        "subtitle" to "버전 %s을(를) 설치할 수 있습니다",
        "current" to "현재: %s",
        "new_version" to "새 버전: %s",
        "install" to "업데이트",
        "later" to "나중에",
        "downloading" to "다운로드 중… %d%%",
        "error_check" to "업데이트를 확인할 수 없습니다",
        "error_check_title" to "업데이트를 확인할 수 없습니다",
        "error_check_subtitle" to "보통 다음 중 하나가 원인입니다:",
        "error_reason_1" to "인터넷에 연결되어 있지 않습니다 — Wi-Fi 또는 모바일 데이터를 확인하세요",
        "error_reason_2" to "업데이트 서버를 일시적으로 사용할 수 없습니다",
        "error_reason_3" to "VPN 또는 네트워크 제한이 GitHub를 차단할 수 있습니다",
        "retry" to "다시 시도",
        "close" to "닫기",
        "error_download" to "다운로드 실패. 다시 시도하세요",
        "permission_title" to "권한이 필요합니다",
        "permission_body" to "이 출처에서 앱을 설치하려면 권한이 필요합니다. 아래 버튼을 눌러 NELEV를 활성화한 뒤 돌아와서 다시 업데이트를 누르세요.",
        "permission_grant" to "권한 허용",
        "permission" to "이 출처에서 앱 설치를 허용한 후 다시 업데이트를 누르세요",
        "up_to_date" to "최신 버전입니다",
        "checking" to "업데이트 확인 중…",
        "allow" to "설치 허용",
        "whats_new" to "새 기능",
        "whats_new_title" to "%s의 새 기능",
        "whats_new_ok" to "확인",
        "updated_to" to "%s(으)로 업데이트되었습니다",
        "no_changelog" to "버그 수정 및 개선.",
        "version_check_title" to "버전 확인",
        "version_check_subtitle" to "최신 버전인지 확인합니다",
        "version_current" to "설치됨: %s",
        "version_latest" to "최신: %s",
        "version_actual" to "최신 버전입니다",
        "version_available" to "버전 %s을(를) 사용할 수 있습니다",
        "version_unknown" to "최신 버전을 가져오지 못했습니다",
        "check_again" to "다시 확인",
        "back" to "뒤로",
        "next" to "다음",
        "onboarding_update" to "지금 업데이트"
    )

    private val PT = mapOf(
        "title" to "Atualização disponível",
        "subtitle" to "A versão %s está pronta para instalar",
        "current" to "Atual: %s",
        "new_version" to "Nova versão: %s",
        "install" to "Atualizar",
        "later" to "Depois",
        "downloading" to "Baixando… %d%%",
        "error_check" to "Não foi possível verificar atualizações",
        "error_check_title" to "Não foi possível verificar atualizações",
        "error_check_subtitle" to "Normalmente a causa é uma destas:",
        "error_reason_1" to "Sem conexão com a internet — verifique o Wi-Fi ou os dados móveis",
        "error_reason_2" to "O servidor de atualizações está temporariamente indisponível",
        "error_reason_3" to "VPN ou restrições de rede podem estar bloqueando o GitHub",
        "retry" to "Tentar novamente",
        "close" to "Fechar",
        "error_download" to "Falha no download. Tente novamente",
        "permission_title" to "Permissão necessária",
        "permission_body" to "O Android precisa de permissão para instalar apps desta fonte. Toque no botão abaixo, ative o NELEV e volte para tocar em Atualizar novamente.",
        "permission_grant" to "Conceder permissão",
        "permission" to "Permita instalar apps desta fonte e toque em Atualizar novamente",
        "up_to_date" to "Você já tem a versão mais recente",
        "checking" to "Verificando atualizações…",
        "allow" to "Permitir instalação",
        "whats_new" to "Novidades",
        "whats_new_title" to "Novidades da %s",
        "whats_new_ok" to "Entendi",
        "updated_to" to "Você atualizou para %s",
        "no_changelog" to "Correções e melhorias.",
        "version_check_title" to "Verificar versão",
        "version_check_subtitle" to "Vamos confirmar se você está na versão mais recente",
        "version_current" to "Instalada: %s",
        "version_latest" to "Mais recente: %s",
        "version_actual" to "Você está na versão mais recente",
        "version_available" to "A versão %s está disponível",
        "version_unknown" to "Não foi possível obter a versão mais recente",
        "check_again" to "Verificar novamente",
        "back" to "Voltar",
        "next" to "Próximo",
        "onboarding_update" to "Atualizar agora"
    )

    private val UK = mapOf(
        "title" to "Доступне оновлення",
        "subtitle" to "Версія %s готова до встановлення",
        "current" to "Зараз: %s",
        "new_version" to "Нова версія: %s",
        "install" to "Оновити",
        "later" to "Пізніше",
        "downloading" to "Завантаження… %d%%",
        "error_check" to "Не вдалося перевірити оновлення",
        "error_check_title" to "Не вдалося перевірити оновлення",
        "error_check_subtitle" to "Зазвичай причина одна з цих:",
        "error_reason_1" to "Немає підключення до інтернету — перевірте Wi-Fi або мобільні дані",
        "error_reason_2" to "Сервер оновлень тимчасово недоступний",
        "error_reason_3" to "VPN або обмеження мережі можуть блокувати GitHub",
        "retry" to "Повторити",
        "close" to "Закрити",
        "error_download" to "Помилка завантаження. Спробуйте ще раз",
        "permission_title" to "Потрібен дозвіл",
        "permission_body" to "Android вимагає дозволу на встановлення програм із цього джерела. Натисніть кнопку нижче, увімкніть NELEV, потім поверніться й знову натисніть «Оновити».",
        "permission_grant" to "Надати дозвіл",
        "permission" to "Дозвольте встановлення програм із цього джерела, потім натисніть «Оновити» ще раз",
        "up_to_date" to "У вас остання версія",
        "checking" to "Перевірка оновлень…",
        "allow" to "Дозволити встановлення",
        "whats_new" to "Що нового",
        "whats_new_title" to "Що нового у %s",
        "whats_new_ok" to "Чудово",
        "updated_to" to "Ви оновилися до версії %s",
        "no_changelog" to "Виправлення та покращення.",
        "version_check_title" to "Перевірка версії",
        "version_check_subtitle" to "Перевіримо, чи не вийшло оновлення",
        "version_current" to "Встановлено: %s",
        "version_latest" to "Остання: %s",
        "version_actual" to "У вас актуальна версія",
        "version_available" to "Доступна версія %s",
        "version_unknown" to "Не вдалося дізнатися останню версію",
        "check_again" to "Перевірити знову",
        "back" to "Назад",
        "next" to "Далі",
        "onboarding_update" to "Оновити зараз"
    )

    fun get(key: String, lang: String): String {
        val map = when (lang) {
            "ru" -> RU
            "es" -> ES
            "zh" -> ZH
            "ja" -> JA
            "ko" -> KO
            "pt" -> PT
            "uk" -> UK
            else -> EN
        }
        return map[key] ?: EN[key] ?: key
    }
}
