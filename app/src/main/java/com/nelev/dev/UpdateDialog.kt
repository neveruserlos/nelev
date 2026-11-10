package com.nelev.dev

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Обновление ──────────────────────────────────────────────────────────────

// Отключает системную анимацию появления/исчезновения окна диалога,
// когда визуальные эффекты выключены.
@Composable
private fun DialogWindowAnimation(effectsEnabled: Boolean) {
    val view = LocalView.current
    LaunchedEffect(effectsEnabled) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        if (!effectsEnabled) window.setWindowAnimations(0)
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    effectsEnabled: Boolean = true,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Если между проверкой и открытием окна успела выйти новая версия,
    // подтягиваем её, чтобы не обновлять пользователя на устаревший релиз.
    var activeInfo by remember(info.versionCode) { mutableStateOf(info) }
    LaunchedEffect(info.versionCode) {
        Updater.check(ctx, respectSkip = false).onSuccess { fresh ->
            if (fresh != null && fresh.versionCode > activeInfo.versionCode) {
                activeInfo = fresh
            }
        }
    }

    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    // Приглушённый, но читаемый цвет — как в окнах паузы обновлений и обратной связи
    val muted = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)
    val border = colors["border"] ?: Color.DarkGray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    
    // Блокировка закрытия на 3 секунды
    var canDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        canDismiss = true
    }

    // Приоритет: переводы из манифеста (тот же домен, что и проверка обновлений),
    // затем встроенные assets, затем raw GitHub и в конце changelog из манифеста.
    val manifestNote = activeInfo.changelogs[lang]?.takeIf { it.isNotBlank() }
    var localizedNotes by remember(activeInfo.versionCode, lang, manifestNote) { mutableStateOf<String?>(manifestNote) }
    LaunchedEffect(activeInfo.versionCode, lang, manifestNote) {
        if (manifestNote == null) {
            localizedNotes = VersionHistory.changelog(ctx, activeInfo.versionCode, lang)
                ?: VersionHistoryManager.fetchChangelogFromGitHub(activeInfo.versionName, lang)
        }
    }
    val notesText = localizedNotes?.takeIf { it.isNotBlank() } ?: activeInfo.changelog

    Dialog(
        onDismissRequest = { if (!downloading && canDismiss) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogWindowAnimation(effectsEnabled)
        // Окно фиксированного размера: при раскрытии «Что нового» меняется только
        // контент внутри, а не размер окна — так анимация не дёргается.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { if (!downloading && canDismiss) onDismiss() },
            contentAlignment = Alignment.Center
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
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UpdateHero(
                        title = UpdateText.get("title", lang),
                        subtitle = UpdateText.get("subtitle", lang).replace("%s", activeInfo.versionName),
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
                                label = versionLabel("current", lang),
                                value = APP_VERSION,
                                text = text, muted = muted, border = border,
                                modifier = Modifier.weight(1f)
                            )
                            VersionBadge(
                                label = versionLabel("new_version", lang),
                                value = activeInfo.versionName,
                                text = text, muted = muted, border = accent.copy(alpha = 0.45f),
                                highlighted = true, accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (notesText.isNotBlank()) {
                            val arrow by animateFloatAsState(
                                if (showNotes) 180f else 0f,
                                tween(if (effectsEnabled) 220 else 0),
                                label = "update_notes_arrow"
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                AnimatedVisibility(
                                    visible = showNotes,
                                    enter = expandVertically(
                                        animationSpec = tween(
                                            durationMillis = if (effectsEnabled) 320 else 0,
                                            easing = FastOutSlowInEasing
                                        ),
                                        expandFrom = Alignment.Top,
                                        clip = true
                                    ) + fadeIn(
                                        animationSpec = tween(
                                            durationMillis = if (effectsEnabled) 220 else 0,
                                            delayMillis = if (effectsEnabled) 60 else 0,
                                            easing = FastOutSlowInEasing
                                        )
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = tween(
                                            durationMillis = if (effectsEnabled) 240 else 0,
                                            easing = FastOutSlowInEasing
                                        ),
                                        shrinkTowards = Alignment.Top,
                                        clip = true
                                    ) + fadeOut(
                                        animationSpec = tween(
                                            durationMillis = if (effectsEnabled) 160 else 0,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                ) {
                                    Text(
                                        notesText,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = muted,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = downloading,
                            enter = expandVertically(
                                animationSpec = tween(
                                    durationMillis = if (effectsEnabled) 350 else 0,
                                    easing = FastOutSlowInEasing
                                ),
                                expandFrom = Alignment.Top,
                                clip = true
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = if (effectsEnabled) 250 else 0,
                                    delayMillis = if (effectsEnabled) 50 else 0,
                                    easing = FastOutSlowInEasing
                                )
                            ),
                            exit = shrinkVertically(
                                animationSpec = tween(
                                    durationMillis = if (effectsEnabled) 300 else 0,
                                    easing = FastOutSlowInEasing
                                ),
                                shrinkTowards = Alignment.Top,
                                clip = true
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = if (effectsEnabled) 200 else 0,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    color = accent,
                                    trackColor = border
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        UpdateText.get("downloading", lang)
                                            .replace("%d", progress.toString())
                                            .replace("%%", "%"),
                                        fontSize = 12.sp,
                                        color = muted
                                    )
                                }
                            }
                        }

                        if (showPermissionDialog) {
                            InstallPermissionDialog(
                                colors = colors,
                                lang = lang,
                                isDark = isDark,
                                onGrant = {
                                    showPermissionDialog = false
                                    Updater.openInstallSettings(ctx)
                                },
                                onDismiss = { showPermissionDialog = false }
                            )
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
                                        // Никакого автоматического перехода в настройки:
                                        // сначала отдельное окно с кнопкой «Разрешить доступ».
                                        showPermissionDialog = true
                                        return@Button
                                    }
                                    showPermissionDialog = false
                                    scope.launch {
                                        downloading = true
                                        error = null
                                        progress = 0
                                        try {
                                            // Перед скачиванием ещё раз проверяем манифест:
                                            // если вышла более новая версия, качаем именно её.
                                            val latest = Updater.check(ctx, respectSkip = false).getOrNull()
                                            val target = if (latest != null && latest.versionCode > activeInfo.versionCode) {
                                                latest
                                            } else {
                                                activeInfo
                                            }
                                            if (target.versionCode > activeInfo.versionCode) {
                                                activeInfo = target
                                            }
                                            val file = Updater.download(ctx, target) { p ->
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
}

// Убирает плейсхолдер и разделитель из подписи версии:
// «Текущая версия: %s» -> «Текущая версия», «当前版本：%s» -> «当前版本».
private fun versionLabel(key: String, lang: String): String =
    UpdateText.get(key, lang)
        .replace("%s", "")
        .trim()
        .trimEnd(':', '：')
        .trim()

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
    effectsEnabled: Boolean = true,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    // Приглушённый, но читаемый цвет — как в окнах паузы обновлений и обратной связи
    val muted = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogWindowAnimation(effectsEnabled)
        // Окно диалога всегда полноэкранное: оно создаётся сразу фиксированного
        // размера и больше никогда не меняет габариты — поэтому карточка не
        // «телепортируется» и не дёргается при открытии.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
            // Высота текста измеряется ДО первого кадра, поэтому карточка
            // открывается уже финального размера и не сужается после появления.
            val surfaceWidth = if (maxWidth < 430.dp) maxWidth else 430.dp
            val bodyText = changelog?.takeIf { it.isNotBlank() }
                ?: UpdateText.get("no_changelog", lang)
            val bodyStyle = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
            val density = LocalDensity.current
            val textMeasurer = rememberTextMeasurer()
            val bodyHeight = remember(bodyText, surfaceWidth, density) {
                val widthPx = with(density) { (surfaceWidth - 40.dp).roundToPx() }
                val measuredPx = textMeasurer.measure(
                    text = AnnotatedString(bodyText),
                    style = bodyStyle,
                    constraints = Constraints(maxWidth = widthPx)
                ).size.height
                with(density) { measuredPx.toDp() }
            }
            val maxBodyHeight = 300.dp
            val scrollHeight =
                if (bodyHeight + 12.dp > maxBodyHeight) maxBodyHeight else bodyHeight + 12.dp

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = card,
                tonalElevation = 0.dp,
                shadowElevation = 20.dp,
                modifier = Modifier.width(surfaceWidth)
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
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    val scroll = rememberScrollState()
                    val canScrollDown by remember {
                        derivedStateOf { scroll.value < scroll.maxValue - 20 }
                    }
                    // Фиксированная высота из предварительного измерения:
                    // размер известен до первого кадра и не меняется.
                    Box(Modifier.fillMaxWidth().height(scrollHeight)) {
                        Text(
                            bodyText,
                            style = bodyStyle,
                            color = muted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scroll)
                                .padding(bottom = 12.dp)
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = canScrollDown,
                            enter = fadeIn(tween(if (effectsEnabled) 300 else 0)),
                            exit = fadeOut(tween(if (effectsEnabled) 300 else 0)),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, card)
                                            )
                                        )
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown, null,
                                        Modifier.size(16.dp), tint = accent
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        UpdateText.get("scroll_down", lang),
                                        fontSize = 11.sp,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
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
}
}

// ─── Разрешение на установку ─────────────────────────────────────────────────

@Composable
fun InstallPermissionDialog(
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = card,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Warning, null, Modifier.size(24.dp), tint = accent)
                    }
                    Text(
                        UpdateText.get("permission_title", lang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = text
                    )
                }
                Text(
                    UpdateText.get("permission_body", lang),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = muted
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
                        onClick = onGrant,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = buttonContent
                        )
                    ) {
                        Text(
                            UpdateText.get("permission_grant", lang),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─── Уведомление о новой версии (при запуске) ────────────────────────────────

@Composable
fun UpdateAvailableNotice(
    versionName: String,
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    effectsEnabled: Boolean = true,
    onOpenMenu: () -> Unit,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    Dialog(onDismissRequest = onDismiss) {
        DialogWindowAnimation(effectsEnabled)
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = card,
            tonalElevation = 0.dp,
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IconsEx.SystemUpdate, null, Modifier.size(25.dp), tint = accent)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            UpdateText.get("update_notice_title", lang),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = text
                        )
                        Text(
                            UpdateText.get("update_notice_version", lang).replace("%s", versionName),
                            fontSize = 12.sp,
                            color = muted
                        )
                    }
                }
                Text(
                    UpdateText.get("update_notice_body", lang),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = muted
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(UpdateText.get("later", lang), color = muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onOpenMenu,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = buttonContent
                        )
                    ) {
                        Text(
                            UpdateText.get("update_notice_open", lang),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ─── Пауза уведомлений об обновлениях ────────────────────────────────────────

@Composable
fun UpdatePauseDialog(
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    pausedUntilMs: Long,
    pausedForever: Boolean,
    onPause: (untilMs: Long, forever: Boolean) -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val card = colors["card"] ?: Color(0xFF101010)
    val text = colors["text"] ?: Color.White
    val muted = colors["muted"] ?: Color.Gray
    val border = colors["border"] ?: Color.DarkGray
    val accent = colors["accent"] ?: Color(0xFFE79BC0)
    val buttonContent = if (isDark) Color.Black else Color.White

    val now = System.currentTimeMillis()
    val isPaused = pausedForever || pausedUntilMs > now

    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var customDays by remember { mutableStateOf("") }

    val presets = listOf(
        UpdateText.get("pause_1week", lang) to 7L,
        UpdateText.get("pause_1month", lang) to 30L,
        UpdateText.get("pause_forever", lang) to -1L,
        UpdateText.get("pause_custom", lang) to 0L
    )

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {
                onDismiss()
            }
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.wrapContentSize()) {
            Box(
                Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { }
                    .background(
                        if (isDark) {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1E1E1E).copy(alpha = 0.95f),
                                    Color(0xFF151515).copy(alpha = 0.97f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFFFCFF).copy(alpha = 0.95f),
                                    Color(0xFFFFF2FA).copy(alpha = 0.97f)
                                )
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = selectedOption,
                    transitionSpec = {
                        fadeIn(tween(300)) + expandVertically(tween(300)) togetherWith
                                fadeOut(tween(200)) + shrinkVertically(tween(200))
                    },
                    label = "pause_content"
                ) { currentOption ->
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    UpdateText.get("pause_title", lang),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = text
                                )
                                if (isPaused) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (pausedForever) {
                                            UpdateText.get("paused_forever_status", lang)
                                        } else {
                                            val days = ((pausedUntilMs - now) / (24 * 60 * 60 * 1000L)).toInt() + 1
                                            UpdateText.get("paused_until_status", lang).replace("%d", days.toString())
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFA726)
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, null, tint = if (isDark) Color.White.copy(alpha = 0.85f) else muted)
                            }
                        }

                        Text(
                            UpdateText.get("pause_subtitle", lang),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)
                        )

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
                                val isSelected = selectedOption == idx
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) accent.copy(alpha = 0.12f) else Color.Transparent)
                                        .border(
                                            1.dp,
                                            if (isSelected) accent.copy(alpha = 0.35f) else Color.Transparent,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedOption = idx
                                        }
                                        .padding(horizontal = 14.dp, vertical = if (days == 0L && isSelected) 6.dp else 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, if (isSelected) accent else muted, CircleShape)
                                            .background(if (isSelected) accent else Color.Transparent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(buttonContent)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    if (days == 0L && isSelected) {
                                        // Поле ввода прямо в строке «Свой срок»
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = customDays,
                                            onValueChange = { v ->
                                                val filtered = v.filter { c -> c.isDigit() }
                                                customDays = if (filtered.isNotEmpty() && filtered.toIntOrNull()?.let { n -> n > 9999 } == true) {
                                                    "9999"
                                                } else {
                                                    filtered.take(4)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = text
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                                            decorationBox = { innerTextField ->
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .height(38.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(accent.copy(alpha = if (isDark) 0.10f else 0.08f))
                                                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        Modifier.weight(1f),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        if (customDays.isEmpty()) {
                                                            Text(
                                                                UpdateText.get("pause_custom_hint", lang),
                                                                fontSize = 12.sp,
                                                                color = if (isDark) Color.White.copy(alpha = 0.60f) else text.copy(alpha = 0.55f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        UpdateText.get("pause_custom_label", lang),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = accent
                                                    )
                                                }
                                            }
                                        )
                                    } else {
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

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPaused) {
                                TextButton(onClick = {
                                    onResume()
                                    onDismiss()
                                }) {
                                    Text(
                                        UpdateText.get("pause_resume", lang),
                                        color = if (isDark) Color.White.copy(alpha = 0.72f) else text.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = {
                                    val option = selectedOption ?: return@Button
                                    val (_, days) = presets[option]
                                    when {
                                        days == -1L -> {
                                            onPause(0L, true)
                                            onDismiss()
                                        }
                                        days == 0L -> {
                                            val custom = customDays.toLongOrNull() ?: return@Button
                                            if (custom < 1) return@Button
                                            val until = now + custom * 24 * 60 * 60 * 1000L
                                            onPause(until, false)
                                            onDismiss()
                                        }
                                        else -> {
                                            val until = now + days * 24 * 60 * 60 * 1000L
                                            onPause(until, false)
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = selectedOption != null && (selectedOption != 3 || customDays.isNotBlank()),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = buttonContent
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
        "onboarding_update" to "Update now",
        "scroll_down" to "Scroll to see more",
        "scroll_hint" to "Scroll the list to see more",
        "pause_title" to "Pause update notifications",
        "pause_subtitle" to "Choose how long to pause update checks and notifications. Manual checks still work.",
        "pause_1_week" to "1 week",
        "pause_2_weeks" to "2 weeks",
        "pause_1_month" to "1 month",
        "pause_custom" to "Custom period",
        "pause_days_hint" to "Number of days",
        "pause_apply" to "Pause",
        "pause_forever" to "Forever",
        "pause_active" to "Updates paused",
        "pause_until" to "Notifications will resume on %s",
        "pause_forever_status" to "Notifications are paused until you turn them back on",
        "pause_resume" to "Resume",
        "pause_1week" to "1 week",
        "pause_2weeks" to "2 weeks",
        "pause_1month" to "1 month",
        "pause_custom_hint" to "Enter the number of days",
        "pause_custom_label" to "Days",
        "paused_forever_status" to "Updates paused forever",
        "paused_until_status" to "Paused for %d days",
        "pause_desc_1day" to "You won't see update notifications for 1 day",
        "pause_desc_3days" to "You won't see update notifications for 3 days",
        "pause_desc_7days" to "You won't see update notifications for 1 week",
        "pause_desc_forever" to "You won't see update notifications until you resume them",
        "pause_desc_custom" to "You won't see update notifications for the selected period",
        "update_notice_title" to "New version available",
        "update_notice_version" to "NELEV %s is ready",
        "update_notice_body" to "Go to the Menu tab to update the app.",
        "update_notice_open" to "Open menu"
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
        "onboarding_update" to "Обновить сейчас",
        "scroll_down" to "Прокрутите, чтобы увидеть больше",
        "scroll_hint" to "Прокрутите список, чтобы увидеть больше",
        "pause_title" to "Приостановить обновления",
        "pause_subtitle" to "Вы не будете видеть уведомления об обновлениях в выбранный период. Проверить вручную можно в любой момент.",
        "pause_1week" to "1 неделя",
        "pause_2weeks" to "2 недели",
        "pause_1month" to "1 месяц",
        "pause_forever" to "Навсегда",
        "pause_custom" to "Свой срок",
        "pause_custom_hint" to "Введите количество дней",
        "pause_custom_label" to "Дней",
        "pause_apply" to "Приостановить",
        "pause_resume" to "Возобновить",
        "paused_forever_status" to "Обновления приостановлены навсегда",
        "paused_until_status" to "Приостановлено на %d дн.",
        "pause_desc_1day" to "Вы не будете видеть уведомления об обновлениях 1 день",
        "pause_desc_3days" to "Вы не будете видеть уведомления об обновлениях 3 дня",
        "pause_desc_7days" to "Вы не будете видеть уведомления об обновлениях 1 неделю",
        "pause_desc_forever" to "Вы не будете видеть уведомления об обновлениях, пока не возобновите их",
        "pause_desc_custom" to "Вы не будете видеть уведомления об обновлениях в выбранный период",
        "update_notice_title" to "Доступна новая версия",
        "update_notice_version" to "NELEV %s готов к установке",
        "update_notice_body" to "Перейдите на вкладку «Меню», чтобы обновиться.",
        "update_notice_open" to "Открыть меню"
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
        "onboarding_update" to "Actualizar ahora",
        "scroll_down" to "Desplaza para ver más",
        "scroll_hint" to "Desplaza la lista para ver más",
        "pause_title" to "Pausar actualizaciones",
        "pause_subtitle" to "No verás notificaciones de actualización durante el período seleccionado. Puedes comprobar manualmente en cualquier momento.",
        "pause_1week" to "1 semana",
        "pause_2weeks" to "2 semanas",
        "pause_1month" to "1 mes",
        "pause_forever" to "Para siempre",
        "pause_custom" to "Período personalizado",
        "pause_custom_hint" to "Introduce el número de días",
        "pause_custom_label" to "Días",
        "pause_apply" to "Pausar",
        "pause_resume" to "Reanudar",
        "paused_forever_status" to "Actualizaciones pausadas para siempre",
        "paused_until_status" to "Pausado por %d días",
        "pause_desc_1day" to "No verás notificaciones de actualización durante 1 día",
        "pause_desc_3days" to "No verás notificaciones de actualización durante 3 días",
        "pause_desc_7days" to "No verás notificaciones de actualización durante 1 semana",
        "pause_desc_forever" to "No verás notificaciones de actualización hasta que las reanudes",
        "pause_desc_custom" to "No verás notificaciones de actualización durante el período seleccionado",
        "update_notice_title" to "Nueva versión disponible",
        "update_notice_version" to "NELEV %s está listo",
        "update_notice_body" to "Ve a la pestaña Menú para actualizar la aplicación.",
        "update_notice_open" to "Abrir menú"
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
        "onboarding_update" to "立即更新",
        "scroll_down" to "滚动查看更多",
        "scroll_hint" to "滚动列表查看更多",
        "pause_title" to "暂停更新",
        "pause_subtitle" to "在选定的时间内，您不会看到更新通知。您仍然可以手动检查。",
        "pause_1week" to "1周",
        "pause_2weeks" to "2周",
        "pause_1month" to "1个月",
        "pause_forever" to "永久",
        "pause_custom" to "自定义期限",
        "pause_custom_hint" to "输入天数",
        "pause_custom_label" to "天",
        "pause_apply" to "暂停",
        "pause_resume" to "恢复",
        "paused_forever_status" to "永久暂停更新",
        "paused_until_status" to "暂停 %d 天",
        "pause_desc_1day" to "您将在1天内看不到更新通知",
        "pause_desc_3days" to "您将在3天内看不到更新通知",
        "pause_desc_7days" to "您将在1周内看不到更新通知",
        "pause_desc_forever" to "在您恢复之前，将看不到更新通知",
        "pause_desc_custom" to "在选定的期间内，您将看不到更新通知",
        "update_notice_title" to "有新版本",
        "update_notice_version" to "NELEV %s 已就绪",
        "update_notice_body" to "前往菜单标签页更新应用。",
        "update_notice_open" to "打开菜单"
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
        "onboarding_update" to "今すぐ更新",
        "scroll_down" to "スクロールして詳細を表示",
        "scroll_hint" to "リストをスクロールして詳細を表示",
        "pause_title" to "更新を一時停止",
        "pause_subtitle" to "選択した期間中、更新通知は表示されません。手動でいつでも確認できます。",
        "pause_1week" to "1週間",
        "pause_2weeks" to "2週間",
        "pause_1month" to "1ヶ月",
        "pause_forever" to "永久に",
        "pause_custom" to "カスタム期間",
        "pause_custom_hint" to "日数を入力",
        "pause_custom_label" to "日",
        "pause_apply" to "一時停止",
        "pause_resume" to "再開",
        "paused_forever_status" to "更新が永久に一時停止されました",
        "paused_until_status" to "%d日間一時停止",
        "pause_desc_1day" to "1日間、更新通知は表示されません",
        "pause_desc_3days" to "3日間、更新通知は表示されません",
        "pause_desc_7days" to "1週間、更新通知は表示されません",
        "pause_desc_forever" to "再開するまで、更新通知は表示されません",
        "pause_desc_custom" to "選択した期間中、更新通知は表示されません",
        "update_notice_title" to "新しいバージョンがあります",
        "update_notice_version" to "NELEV %s が利用可能です",
        "update_notice_body" to "メニュータブに移動してアプリを更新してください。",
        "update_notice_open" to "メニューを開く"
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
        "onboarding_update" to "지금 업데이트",
        "scroll_down" to "스크롤하여 더 보기",
        "scroll_hint" to "목록을 스크롤하여 더 보기",
        "pause_title" to "업데이트 일시 중지",
        "pause_subtitle" to "선택한 기간 동안 업데이트 알림이 표시되지 않습니다. 언제든지 수동으로 확인할 수 있습니다.",
        "pause_1week" to "1주",
        "pause_2weeks" to "2주",
        "pause_1month" to "1개월",
        "pause_forever" to "영구적으로",
        "pause_custom" to "사용자 지정 기간",
        "pause_custom_hint" to "일수 입력",
        "pause_custom_label" to "일",
        "pause_apply" to "일시 중지",
        "pause_resume" to "재개",
        "paused_forever_status" to "업데이트가 영구적으로 일시 중지됨",
        "paused_until_status" to "%d일 동안 일시 중지",
        "pause_desc_1day" to "1일 동안 업데이트 알림이 표시되지 않습니다",
        "pause_desc_3days" to "3일 동안 업데이트 알림이 표시되지 않습니다",
        "pause_desc_7days" to "1주 동안 업데이트 알림이 표시되지 않습니다",
        "pause_desc_forever" to "재개할 때까지 업데이트 알림이 표시되지 않습니다",
        "pause_desc_custom" to "선택한 기간 동안 업데이트 알림이 표시되지 않습니다",
        "update_notice_title" to "새 버전 사용 가능",
        "update_notice_version" to "NELEV %s을(를) 사용할 수 있습니다",
        "update_notice_body" to "메뉴 탭으로 이동하여 앱을 업데이트하세요.",
        "update_notice_open" to "메뉴 열기"
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
        "onboarding_update" to "Atualizar agora",
        "scroll_down" to "Role para ver mais",
        "scroll_hint" to "Role a lista para ver mais",
        "pause_title" to "Pausar atualizações",
        "pause_subtitle" to "Você não verá notificações de atualização durante o período selecionado. Você ainda pode verificar manualmente.",
        "pause_1week" to "1 semana",
        "pause_2weeks" to "2 semanas",
        "pause_1month" to "1 mês",
        "pause_forever" to "Para sempre",
        "pause_custom" to "Período personalizado",
        "pause_custom_hint" to "Digite o número de dias",
        "pause_custom_label" to "Dias",
        "pause_apply" to "Pausar",
        "pause_resume" to "Retomar",
        "paused_forever_status" to "Atualizações pausadas para sempre",
        "paused_until_status" to "Pausado por %d dias",
        "pause_desc_1day" to "Você não verá notificações de atualização por 1 dia",
        "pause_desc_3days" to "Você não verá notificações de atualização por 3 dias",
        "pause_desc_7days" to "Você não verá notificações de atualização por 1 semana",
        "pause_desc_forever" to "Você não verá notificações de atualização até retomá-las",
        "pause_desc_custom" to "Você não verá notificações de atualização durante o período selecionado",
        "update_notice_title" to "Nova versão disponível",
        "update_notice_version" to "NELEV %s está pronto",
        "update_notice_body" to "Vá para a aba Menu para atualizar o aplicativo.",
        "update_notice_open" to "Abrir menu"
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
        "onboarding_update" to "Оновити зараз",
        "scroll_down" to "Прокрутіть, щоб побачити більше",
        "scroll_hint" to "Прокрутіть список, щоб побачити більше",
        "pause_title" to "Призупинити оновлення",
        "pause_subtitle" to "Ви не бачитимете сповіщень про оновлення протягом обраного періоду. Перевірити вручну можна в будь-який момент.",
        "pause_1week" to "1 тиждень",
        "pause_2weeks" to "2 тижні",
        "pause_1month" to "1 місяць",
        "pause_forever" to "Назавжди",
        "pause_custom" to "Свій термін",
        "pause_custom_hint" to "Введіть кількість днів",
        "pause_custom_label" to "Днів",
        "pause_apply" to "Призупинити",
        "pause_resume" to "Відновити",
        "paused_forever_status" to "Оновлення призупинено назавжди",
        "paused_until_status" to "Призупинено на %d дн.",
        "pause_desc_1day" to "Ви не бачитимете сповіщень про оновлення 1 день",
        "pause_desc_3days" to "Ви не бачитимете сповіщень про оновлення 3 дні",
        "pause_desc_7days" to "Ви не бачитимете сповіщень про оновлення 1 тиждень",
        "pause_desc_forever" to "Ви не бачитимете сповіщень про оновлення, поки не відновите їх",
        "pause_desc_custom" to "Ви не бачитимете сповіщень про оновлення протягом обраного періоду",
        "update_notice_title" to "Доступна нова версія",
        "update_notice_version" to "NELEV %s готовий до встановлення",
        "update_notice_body" to "Перейдіть на вкладку «Меню», щоб оновитися.",
        "update_notice_open" to "Відкрити меню"
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
