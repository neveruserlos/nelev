# NELEV 1.9.14 - Краткая инструкция для следующего чата

## Текущее состояние проекта

**Версия:** 1.9.14 (versionCode 49)  
**Репозиторий:** https://github.com/neveruserlos/nelev  
**Путь:** X:\nelev\app  
**Release:** https://github.com/neveruserlos/nelev/releases/tag/v1.9.14

## Что было сделано в версии 1.9.14

### Исправлены 3 проблемы с анимациями:

1. **Плашка выбора в паузе обновлений** - исправлено расположение
   - Файл: `MainActivity.kt` строка ~8435
   - Offset: `(index * 52).dp` вместо `(index * 44).dp`
   - Учитывает spacing между пунктами

2. **Раскрытие "Что нового" в уведомлении** - убрано дёрганье
   - Файл: `UpdateDialog.kt` строка ~155
   - Заменён `animateContentSize` на `AnimatedVisibility`
   - Используется `expandVertically` + `fadeIn`

3. **Окно "Что нового"** - убрано телепание вниз
   - Файл: `UpdateDialog.kt` строка ~509
   - Surface обёрнут в Box с `wrapContentHeight()`

### Также исправлено в предыдущих версиях:

- Локализация changelog для всех языков (v1.9.13)
- Плавные spring анимации для всех переходов

## Структура проекта

```
X:\nelev\app\
├── app/
│   ├── src/main/
│   │   ├── java/com/nelev/dev/
│   │   │   ├── MainActivity.kt        # Основная логика, UI
│   │   │   ├── UpdateDialog.kt        # Диалоги обновлений
│   │   │   ├── Updater.kt             # Проверка обновлений
│   │   │   └── VersionHistory.kt      # Загрузка changelog
│   │   └── assets/
│   │       └── version_history.json   # Переводы changelog
│   └── build.gradle.kts
├── version.properties                  # Версия приложения
└── publish_release.ps1                 # Скрипт публикации
```

## Процесс релиза (пошагово)

### 1. Обновить версию
```
version.properties: versionCode++, versionName++
version_history.json: добавить новую версию с changelog на 8 языках
publish_release.ps1: обновить versionName, versionCode, changelog
```

### 2. Собрать APK
```powershell
cd X:\nelev\app
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:GRADLE_OPTS="-Xmx768m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:CICompilerCount=2"
.\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease --no-daemon --max-workers=1
```

### 3. Закоммитить и запушить
```bash
git add .
git commit -m "v1.9.X: Description"
git push origin main
git tag -a v1.9.X -m "NELEV 1.9.X - Title"
git push origin v1.9.X
```

### 4. Опубликовать Release
```powershell
.\publish_release.ps1
```

## Важные моменты

### Система обновлений
- Updater.kt проверяет версию через GitHub API
- version.json создаётся скриптом и загружается в release
- VersionHistory.kt загружает переводы из version_history.json

### Анимации
- `animateContentSize` - для изменения размера существующих элементов
- `AnimatedVisibility` - для появления/исчезновения элементов
- `animateDpAsState` - для плавного движения позиций
- Spring параметры: `DampingRatioMediumBouncy` для bounce, `DampingRatioNoBouncy` без bounce

### 8 поддерживаемых языков
ru, en, es, zh, ja, ko, pt, uk - все должны иметь переводы в version_history.json

## Типичные проблемы и решения

**Приложение не видит обновление:**
- Проверить что versionCode увеличен
- Проверить что version.json опубликован в release
- Проверить URL в Updater.kt

**Дёрганье анимаций:**
- Использовать правильный тип анимации (см. выше)
- Изолировать анимацию только для нужного элемента
- Добавить stabilizing wrapper (Box с wrapContentHeight)

**Неправильные переводы:**
- Проверить version_history.json синтаксис
- Убедиться что versionCode совпадает
- Проверить что VersionHistory.changelog() используется вместо Updater.changelog()

## Полезные команды

```bash
# Проверить текущую версию
cat version.properties

# Проверить статус git
git status

# Посмотреть последние коммиты
git log --oneline -5

# Удалить тег (если нужно переделать)
git tag -d v1.9.X
git push origin :refs/tags/v1.9.X

# Удалить release на GitHub
gh release delete v1.9.X --yes
```

## Контакты и ресурсы

- **Репозиторий:** https://github.com/neveruserlos/nelev
- **Releases:** https://github.com/neveruserlos/nelev/releases
- **Текущая версия:** 1.9.14
- **Следующая версия:** 1.9.15 (versionCode 50)

---

**Дата создания:** 2026-11-06  
**Статус:** Проект стабилен, версия 1.9.14 опубликована  
**Следующие шаги:** Ожидание фидбека от пользователя
