# NELEV — Android

Приложение передаёт микрофон телефона на ПК по Wi-Fi (UDP 55000) с фильтрами
и эффектами в реальном времени. UI — Jetpack Compose, аудио — foreground
service.

- Пакет: `com.nelev.dev`
- Минимум: Android 8.0 (minSdk 26), targetSdk 36
- ПК-сервер: https://github.com/neveruserlos/nelev-server

## Версия

Единственный источник версии — [`version.properties`](version.properties):

```properties
versionCode=34
versionName=1.9 release
```

`app/build.gradle.kts` читает файл, а экран «О приложении» и отчёты берут
`BuildConfig.VERSION_NAME`. При каждом релизе поднимай `versionCode` на 1:

```powershell
.\tools\bump-version.ps1 -VersionName "2.0 release"
```

## Сборка

Нужны JDK 21 и Android SDK 36.

```powershell
cd X:\nelev\app
.\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
```

APK: `app\build\outputs\apk\release\app-release.apk`.
Release подписан debug-ключом — ставится обновлением поверх прошлых версий.

## OTA-обновления

Приложение проверяет `version.json` последнего GitHub Release:

```
https://github.com/neveruserlos/nelev/releases/latest/download/version.json
```

Если `versionCode` в манифесте больше установленного, показывается диалог:
скачивание APK, проверка SHA-256 и установка через `FileProvider` +
`REQUEST_INSTALL_PACKAGES`. Проверка также доступна вручную:
«Меню → Проверить обновление».

Публикация релиза (нужен `gh auth login`):

```powershell
.\tools\release.ps1 -Notes "Что нового"
```

Скрипт собирает APK, создаёт `version.json`, тег `v<версия>` и прикрепляет
оба файла к GitHub Release.

## Структура

```
app/src/main/java/com/nelev/dev/
  MainActivity.kt        UI на Compose (вкладки, онбординг, настройки, запись)
  NelevService.kt        сервис стриминга: микрофон, DSP, UDP, heartbeat
  Updater.kt             проверка/скачивание/установка OTA-обновлений
  UpdateDialog.kt        диалог обновления (8 языков)
  BarcodeScannerActivity.kt
  data/ media/ ui/       настройки, уведомления, тема, строки
```
