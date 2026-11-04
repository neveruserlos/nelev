# План реализации Бета-версий NELEV

## Концепция

Бета-версии позволяют тестировать новые функции перед их выходом в стабильную версию. Пользователи получают:
- Ранний доступ к новым возможностям
- Возможность влиять на разработку через обратную связь
- Более частые обновления

## Архитектура каналов обновлений

### Вариант A: Два отдельных APK (Google Play подход)
```
com.nelev.dev        → Stable (основной канал)
com.nelev.dev.beta   → Beta (тестовый канал)
```

**Преимущества:**
- Полная изоляция: можно установить оба APK одновременно
- Разные базы данных, настройки, кеш
- Легко переключаться между версиями

**Недостатки:**
- Нужно поддерживать два репозитория/релиза
- Пользователь теряет данные при переключении каналов
- Дублирование кода

### Вариант B: Один APK с выбором канала (рекомендуемый)
```
com.nelev.dev
├── Настройки → "Участие в бета-тестировании"
│   └── [x] Получать бета-версии
└── Обновления идут из разных каналов GitHub
```

**Преимущества:**
- Один репозиторий, один релиз-процесс
- Данные сохраняются при переключении
- Простое включение/выключение бета-режима

**Недостатки:**
- Бета-функции в production коде (можно скрыть за флагами)
- Откат сложнее (нужна миграция данных)

## Рекомендуемая реализация (Вариант B)

### 1. Структура релизов на GitHub

```
Releases:
├── v1.9.4           (stable, latest)
├── v1.10.0-beta.1   (pre-release)
├── v1.10.0-beta.2   (pre-release)
└── v1.10.0          (stable)
```

**Соглашение о версионировании:**
- Stable: `1.9.4` (versionCode 39)
- Beta: `1.10.0-beta.1` (versionCode 40)
- Beta: `1.10.0-beta.2` (versionCode 41)
- Stable: `1.10.0` (versionCode 42)

### 2. Изменения в version.json

Создать два манифеста:

**version.json** (stable):
```json
{
  "versionCode": 39,
  "versionName": "1.9.4",
  "apkUrl": "https://github.com/neveruserlos/nelev/releases/download/v1.9.4/nelev-app-release-1.9.4.apk",
  "apkSha256": "6eb390cd5d69722d8bbba7dafde6b63d8bbad99066939c6bfaecb5e79d07df19",
  "changelog": "..."
}
```

**version-beta.json** (beta):
```json
{
  "versionCode": 41,
  "versionName": "1.10.0-beta.2",
  "apkUrl": "https://github.com/neveruserlos/nelev/releases/download/v1.10.0-beta.2/nelev-app-release-1.10.0-beta.2.apk",
  "apkSha256": "...",
  "changelog": "🧪 Бета-версия:\n• Новая функция X\n• Экспериментальный Y\n• Исправление Z"
}
```

### 3. Код изменений

#### 3.1. AppSettings
```kotlin
data class AppSettings(
    ...
    val betaUpdatesEnabled: Boolean = false,  // Новое поле
    ...
)
```

#### 3.2. Updater.kt
```kotlin
object Updater {
    private const val MANIFEST_STABLE = "https://github.com/neveruserlos/nelev/releases/latest/download/version.json"
    private const val MANIFEST_BETA = "https://github.com/neveruserlos/nelev/releases/download/beta/version-beta.json"
    
    fun check(ctx: Context, respectSkip: Boolean = false): Result<UpdateInfo?> {
        val settings = SettingsStore.load(ctx)
        val manifestUrl = if (settings.betaUpdatesEnabled) MANIFEST_BETA else MANIFEST_STABLE
        
        // ... остальная логика проверки
    }
}
```

#### 3.3. UI Settings
```kotlin
@Composable
fun SettingsScreen(...) {
    ...
    
    // Новая карточка после "Приостановить обновления"
    Card(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(card)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable {
                if (soundClickEnabled) ClickPlayer.play(ctx)
                showBetaDialog = true
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Science, null, Modifier.size(22.dp), tint = Color(0xFFFF9800))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Strings.get("beta_program", lang),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = text
                )
                Text(
                    if (appSettings.value.betaUpdatesEnabled) 
                        Strings.get("beta_active", lang) 
                    else 
                        Strings.get("beta_inactive", lang),
                    fontSize = 11.sp,
                    color = muted
                )
            }
            if (appSettings.value.betaUpdatesEnabled) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF9800)))
            } else {
                Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = muted)
            }
        }
    }
}
```

### 4. Диалог бета-программы

```kotlin
@Composable
fun BetaProgramDialog(
    colors: NelevColors,
    lang: String,
    isDark: Boolean,
    currentlyEnabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = card,
            shadowElevation = 16.dp
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFFFF9800).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Science, null, Modifier.size(24.dp), tint = Color(0xFFFF9800))
                    }
                    Column {
                        Text(
                            Strings.get("beta_title", lang),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = text
                        )
                        if (currentlyEnabled) {
                            Text(
                                Strings.get("beta_active_status", lang),
                                fontSize = 12.sp,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
                
                Text(
                    Strings.get("beta_description", lang),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = muted
                )
                
                // Что включено в бета
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.02f))
                        .border(1.dp, border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        Strings.get("beta_includes", lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = text
                    )
                    listOf(
                        "beta_feature_early_access",
                        "beta_feature_frequent_updates",
                        "beta_feature_experimental",
                        "beta_feature_feedback"
                    ).forEach { key ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", fontSize = 12.sp, color = Color(0xFFFF9800))
                            Text(
                                Strings.get(key, lang),
                                fontSize = 12.sp,
                                color = muted,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Предупреждение
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFEB3B).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFFFEB3B).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = Color(0xFFFFEB3B))
                    Text(
                        Strings.get("beta_warning", lang),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = muted
                    )
                }
                
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(Strings.get("close", lang), color = muted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (currentlyEnabled) onDisable() else onEnable()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentlyEnabled) muted else Color(0xFFFF9800),
                            contentColor = if (isDark && !currentlyEnabled) Color.Black else Color.White
                        )
                    ) {
                        Text(
                            if (currentlyEnabled) 
                                Strings.get("beta_leave", lang) 
                            else 
                                Strings.get("beta_join", lang),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
```

### 5. Строки локализации

Добавить в `Strings.kt`:

```kotlin
"beta_program" to "Бета-программа",
"beta_active" to "Вы участвуете",
"beta_inactive" to "Не участвуете",
"beta_title" to "Бета-тестирование",
"beta_description" to "Получайте новые функции раньше стабильного релиза. Бета-версии могут содержать ошибки.",
"beta_includes" to "Что входит:",
"beta_feature_early_access" to "Ранний доступ к новым функциям",
"beta_feature_frequent_updates" to "Более частые обновления",
"beta_feature_experimental" to "Экспериментальные возможности",
"beta_feature_feedback" to "Возможность влиять на разработку",
"beta_warning" to "Бета-версии могут работать нестабильно. Ваши данные будут сохранены, но рекомендуется резервное копирование.",
"beta_join" to "Присоединиться",
"beta_leave" to "Покинуть программу",
"beta_active_status" to "Вы получаете бета-обновления",
```

### 6. Процесс релиза

#### Создание бета-версии:
```powershell
# 1. Bump версии для беты
.\tools\bump-version.ps1 -VersionName "1.10.0-beta.1"

# 2. Сборка
.\gradlew.bat assembleRelease -x lint

# 3. Публикация как pre-release
gh release create v1.10.0-beta.1 `
  --prerelease `
  --title "1.10.0 Beta 1" `
  --notes "🧪 Бета-версия..." `
  .\app\build\outputs\apk\release\nelev-app-release-1.10.0-beta.1.apk

# 4. Загрузить version-beta.json в релиз с тегом 'beta'
```

#### Выход стабильной версии:
```powershell
# После тестирования бета → stable
.\tools\bump-version.ps1 -VersionName "1.10.0"
.\gradlew.bat assembleRelease -x lint
gh release create v1.10.0 --latest ...
```

### 7. Примеры бета-функций

Что можно тестировать в бета-канале:
- 🎨 Новые темы оформления
- 🔧 Экспериментальные алгоритмы сжатия
- 📊 Расширенная статистика
- 🌐 Интеграция с новыми сервисами
- ⚡ Оптимизации производительности
- 🛠️ Новые настройки

### 8. Миграция при откате

Если пользователь хочет вернуться со стабильной на бета:

```kotlin
// При включении бета-режима
if (!settings.betaUpdatesEnabled && newSettings.betaUpdatesEnabled) {
    // Проверить, что текущая версия >= последней stable
    val currentCode = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    val stableInfo = checkStable(ctx)
    if (currentCode < stableInfo.versionCode) {
        // Предупредить: "Сначала обновитесь до последней стабильной версии"
        return
    }
}
```

### 9. Аналитика

Отправлять в статистику:
```kotlin
"beta_channel": settings.betaUpdatesEnabled,
"version_type": if (APP_VERSION.contains("beta")) "beta" else "stable"
```

Это поможет:
- Видеть количество бета-тестеров
- Анализировать crashes отдельно для beta/stable
- Понимать, когда переводить функцию в stable

## Roadmap реализации

**Этап 1 (1-2 дня):**
- ✅ Добавить `betaUpdatesEnabled` в `AppSettings`
- ✅ Создать диалог `BetaProgramDialog`
- ✅ Добавить карточку в Settings
- ✅ Локализовать строки

**Этап 2 (1 день):**
- ✅ Модифицировать `Updater.check()` для выбора манифеста
- ✅ Создать скрипт `tools/release-beta.ps1`
- ✅ Протестировать переключение каналов

**Этап 3 (по мере выхода обновлений):**
- 🔄 Выпустить первую бета: `1.10.0-beta.1`
- 🔄 Собрать обратную связь
- 🔄 Исправить критичные баги
- 🔄 Выпустить stable: `1.10.0`

## Альтернативные подходы

### Google Play Beta Track
Если позже загрузить APK в Google Play:
- Использовать Internal/Closed/Open Testing треки
- Пользователи присоединяются через ссылку
- Автоматические обновления через Play Store
- Интегрированная отчётность о крашах

### TestFlight подход (для iOS в будущем)
- Пригласить до 10,000 внешних тестеров
- Автоматический сбор крашей и метрик
- Feedback напрямую в TestFlight

## Заключение

**Рекомендация:** начать с варианта B (один APK + переключатель) и GitHub pre-releases.

Это позволит:
- Быстро запустить бета-программу (2-3 дня разработки)
- Получить обратную связь от энтузиастов
- Улучшить качество stable-релизов
- Не усложнять инфраструктуру

После накопления опыта можно перейти на Google Play Beta Track для более широкого охвата аудитории.
