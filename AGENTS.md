# NELEV Android — правила репозитория (читать перед любой работой)

## 🔴 Главное правило: версия приложения

Единственный источник версии — **`version.properties`** в корне репозитория:

```properties
versionCode=33
versionName=1.8 release
```

**При каждом релизе / крупном обновлении обязательно:**

1. Открыть `version.properties`.
2. Увеличить `versionCode` ровно на 1 (33 → 34 → 35 …). Android не даст
   поставить APK с меньшим или равным `versionCode`.
3. Записать новый `versionName` (например `1.9 release`).
4. Больше НИЧЕГО не менять: `app/build.gradle.kts` читает этот файл,
   экран «О приложении» и отчёты в Telegram берут `BuildConfig.VERSION_NAME`.
5. Собрать APK и проверить:

   ```powershell
   cd X:\nelev\app
   .\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
   & "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.1.0\aapt.exe" dump badging app\build\outputs\apk\release\app-release.apk | Select-String "package:"
   ```

Нельзя хардкодить версию в Kotlin-коде. Константа `APP_VERSION` в
`MainActivity.kt` — это `BuildConfig.VERSION_NAME`, а не литерал.

Быстрый бамп версии:

```powershell
.\tools\bump-version.ps1 -VersionName "1.9 release"
```

## OTA-обновления (GitHub Releases)

Обновления раздаются через GitHub Releases. Контракт:

| Что | Значение |
|---|---|
| Тег релиза | `v<slug>` — `versionName`, где пробелы заменены на `-` (например `v1.8-release`) |
| APK-ассет | `nelev-app-release-<slug>.apk` |
| Манифест | `version.json` (ассет релиза) |
| Ключ сравнения | `versionCode` (число), не `versionName` |

`version.json`:

```json
{
  "versionCode": 33,
  "versionName": "1.8 release",
  "minSdk": 26,
  "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v1.8-release/nelev-app-release-1.8-release.apk",
  "sha256": "<sha256 apk>",
  "sizeBytes": 2921444,
  "publishedAt": "2026-11-04T00:00:00Z",
  "changelog": "…"
}
```

Релиз собирается одной командой (нужен `gh auth login`):

```powershell
.\tools\release.ps1 -Notes "Что нового"
```

Реализация в приложении: `Updater.kt` (проверка, скачивание, SHA-256,
установка через FileProvider) и `UpdateDialog.kt` (диалог, 8 языков).
Автопроверка — при запуске, не чаще раза в 3 часа; «Позже» запоминает
пропущенный `versionCode`. Ручная проверка — «Меню → Проверить обновление».

## Сборка

```powershell
cd X:\nelev\app
.\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
```

Выход: `app\build\outputs\apk\release\app-release.apk`.
Release подписан debug-ключом намеренно (обновление поверх без потери данных).
R8 включён. Перед публикацией в Google Play нужен свой keystore.

## Git

- Репозиторий Android: `X:\nelev\app`, ветка `main`.
- Репозиторий PC-сервера: `X:\pc` (отдельный).
- Коммиты: короткий императив + версия, например
  `1.9: fix recording filters`.
- APK, `dist/`, `build/`, логи и keystore в git не попадают (см. `.gitignore`).

## Тесты перед релизом

- Онбординг: `adb install -r`, затем `adb shell pm clear com.nelev.dev`.
- Back не закрывает приложение ни на одном экране.
- Запись с фильтрами: WAV содержит эффекты, лимит 30 с.
- История: LIVE-карточка, восстановление после краша.
- Уведомление: Stop завершает сессию.
