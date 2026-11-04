package com.nelev.dev

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = card,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    UpdateText.get("title", lang),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = text
                )
                Text(
                    UpdateText.get("current", lang).replace("%s", APP_VERSION),
                    fontSize = 13.sp,
                    color = muted
                )
                Text(
                    UpdateText.get("new_version", lang).replace("%s", info.versionName),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = text
                )

                if (info.changelog.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(border))
                    Text(info.changelog, fontSize = 13.sp, color = muted, maxLines = 8)
                }

                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent,
                        trackColor = border
                    )
                    Text(
                        UpdateText.get("downloading", lang).replace("%d", progress.toString()),
                        fontSize = 12.sp,
                        color = muted
                    )
                }

                if (needPermission) {
                    Text(UpdateText.get("permission", lang), fontSize = 12.sp, color = muted)
                }

                error?.let {
                    Text(it, fontSize = 12.sp, color = Color(0xFFE5484D))
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = buttonContent
                        ),
                        onClick = {
                            if (!Updater.canInstall(ctx)) {
                                Updater.openInstallSettings(ctx)
                                needPermission = true
                                return@Button
                            }
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
                            if (needPermission) UpdateText.get("allow", lang)
                            else UpdateText.get("install", lang)
                        )
                    }
                }
            }
        }
    }
}

internal object UpdateText {

    private val EN = mapOf(
        "title" to "Update available",
        "current" to "Current: %s",
        "new_version" to "New version: %s",
        "install" to "Update",
        "later" to "Later",
        "downloading" to "Downloading… %d%%",
        "error_check" to "Could not check for updates",
        "error_download" to "Download failed. Try again",
        "permission" to "Allow installing apps from this source, then tap Update again",
        "up_to_date" to "You're up to date",
        "checking" to "Checking for updates…",
        "allow" to "Allow install"
    )

    private val RU = mapOf(
        "title" to "Доступно обновление",
        "current" to "Сейчас: %s",
        "new_version" to "Новая версия: %s",
        "install" to "Обновить",
        "later" to "Позже",
        "downloading" to "Загрузка… %d%%",
        "error_check" to "Не удалось проверить обновления",
        "error_download" to "Ошибка загрузки. Попробуйте ещё раз",
        "permission" to "Разрешите установку приложений из этого источника, затем нажмите «Обновить» ещё раз",
        "up_to_date" to "У вас последняя версия",
        "checking" to "Проверка обновлений…",
        "allow" to "Разрешить установку"
    )

    private val ES = mapOf(
        "title" to "Actualización disponible",
        "current" to "Ahora: %s",
        "new_version" to "Nueva versión: %s",
        "install" to "Actualizar",
        "later" to "Más tarde",
        "downloading" to "Descargando… %d%%",
        "error_check" to "No se pudo buscar actualizaciones",
        "error_download" to "Error de descarga. Inténtalo de nuevo",
        "permission" to "Permite instalar aplicaciones desde esta fuente y pulsa Actualizar de nuevo",
        "up_to_date" to "Ya tienes la última versión",
        "checking" to "Buscando actualizaciones…",
        "allow" to "Permitir instalación"
    )

    private val ZH = mapOf(
        "title" to "有可用更新",
        "current" to "当前版本：%s",
        "new_version" to "新版本：%s",
        "install" to "更新",
        "later" to "稍后",
        "downloading" to "下载中… %d%%",
        "error_check" to "无法检查更新",
        "error_download" to "下载失败，请重试",
        "permission" to "请允许从此来源安装应用，然后再次点击更新",
        "up_to_date" to "已是最新版本",
        "checking" to "正在检查更新…",
        "allow" to "允许安装"
    )

    private val JA = mapOf(
        "title" to "アップデートがあります",
        "current" to "現在: %s",
        "new_version" to "新しいバージョン: %s",
        "install" to "更新",
        "later" to "後で",
        "downloading" to "ダウンロード中… %d%%",
        "error_check" to "アップデートを確認できませんでした",
        "error_download" to "ダウンロードに失敗しました。もう一度お試しください",
        "permission" to "この提供元からのインストールを許可して、もう一度「更新」を押してください",
        "up_to_date" to "最新バージョンです",
        "checking" to "アップデートを確認中…",
        "allow" to "インストールを許可"
    )

    private val KO = mapOf(
        "title" to "업데이트가 있습니다",
        "current" to "현재: %s",
        "new_version" to "새 버전: %s",
        "install" to "업데이트",
        "later" to "나중에",
        "downloading" to "다운로드 중… %d%%",
        "error_check" to "업데이트를 확인할 수 없습니다",
        "error_download" to "다운로드 실패. 다시 시도하세요",
        "permission" to "이 출처에서 앱 설치를 허용한 후 다시 업데이트를 누르세요",
        "up_to_date" to "최신 버전입니다",
        "checking" to "업데이트 확인 중…",
        "allow" to "설치 허용"
    )

    private val PT = mapOf(
        "title" to "Atualização disponível",
        "current" to "Atual: %s",
        "new_version" to "Nova versão: %s",
        "install" to "Atualizar",
        "later" to "Depois",
        "downloading" to "Baixando… %d%%",
        "error_check" to "Não foi possível verificar atualizações",
        "error_download" to "Falha no download. Tente novamente",
        "permission" to "Permita instalar apps desta fonte e toque em Atualizar novamente",
        "up_to_date" to "Você já tem a versão mais recente",
        "checking" to "Verificando atualizações…",
        "allow" to "Permitir instalação"
    )

    private val UK = mapOf(
        "title" to "Доступне оновлення",
        "current" to "Зараз: %s",
        "new_version" to "Нова версія: %s",
        "install" to "Оновити",
        "later" to "Пізніше",
        "downloading" to "Завантаження… %d%%",
        "error_check" to "Не вдалося перевірити оновлення",
        "error_download" to "Помилка завантаження. Спробуйте ще раз",
        "permission" to "Дозвольте встановлення програм із цього джерела, потім натисніть «Оновити» ще раз",
        "up_to_date" to "У вас остання версія",
        "checking" to "Перевірка оновлень…",
        "allow" to "Дозволити встановлення"
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
