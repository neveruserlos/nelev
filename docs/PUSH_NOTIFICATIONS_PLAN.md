# План реализации Push-уведомлений по UID

## Текущее состояние
- UID уже постоянный: основан на `ANDROID_ID`, сохраняется при переустановке
- UID генерируется детерминированно из `SHA-256("nelev:{ANDROID_ID}:{packageName}")`
- Формат: 10 символов A-Z0-9
- Отправка статистики уже работает раз в 2 дня

## Архитектура

### 1. Регистрация устройства
При первой отправке статистики (или по запросу пользователя):
- Отправить UID в базу данных сервера
- Сохранить `fcmToken` (Firebase Cloud Messaging) если будет использоваться FCM
- Альтернатива: использовать Telegram Bot API для отправки уведомлений

### 2. Серверная часть
**Вариант A: Firebase Cloud Messaging (FCM)**
- Бесплатно, официально поддерживается Google
- Требует интеграцию Firebase SDK в приложение
- Сервер хранит: `{UID -> fcmToken}`
- Отправка: FCM API → устройство

**Вариант B: Telegram Bot (текущий подход)**
- Уже есть `TelegramSender` в приложении
- Создать Telegram-бота для обратной связи
- Пользователь подписывается: `/start {UID}` в боте
- Сервер хранит: `{UID -> telegramChatId}`
- Отправка: Telegram Bot API → пользователю

**Вариант C: WebSocket или HTTP Polling**
- Более сложная инфраструктура
- Постоянное подключение или периодические запросы
- Расход батареи

### 3. База данных
```
TABLE user_devices (
  uid VARCHAR(10) PRIMARY KEY,
  fcm_token VARCHAR(255),      -- для FCM
  telegram_chat_id BIGINT,     -- для Telegram
  app_version INT,
  last_seen TIMESTAMP,
  registration_date TIMESTAMP
)

TABLE notifications (
  id SERIAL PRIMARY KEY,
  target_uid VARCHAR(10),      -- NULL = всем
  title VARCHAR(255),
  message TEXT,
  action_url VARCHAR(500),
  created_at TIMESTAMP,
  sent_at TIMESTAMP
)
```

### 4. Типы уведомлений
- **Критические обновления**: "Вышла версия X.X.X с важными исправлениями"
- **Новые функции**: "Добавлена поддержка..."
- **Персональные**: "Ваша статистика за неделю: X сессий"
- **Системные**: "Сервер будет недоступен с 00:00 до 02:00"

### 5. Интеграция в приложение

#### 5.1. Добавить зависимости (FCM)
```gradle
implementation 'com.google.firebase:firebase-messaging:23.4.0'
implementation 'com.google.firebase:firebase-analytics:21.5.0'
```

#### 5.2. Регистрация токена
```kotlin
class NelevFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = SettingsStore.load(this).uid
        // Отправить {uid, token} на сервер
        sendRegistrationToServer(uid, token)
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Показать уведомление
        showNotification(remoteMessage)
    }
}
```

#### 5.3. UI настройки
В Settings добавить переключатель:
```
□ Получать уведомления
  └── "О новых версиях, важных исправлениях и новостях"
```

### 6. Безопасность
- UID не содержит персональных данных (SHA-256 хеш)
- Пользователь может отключить уведомления
- Токены FCM/Telegram не передаются третьим лицам
- HTTPS для всех запросов к серверу

### 7. Приватность (GDPR)
- Явное согласие пользователя (переключатель)
- Возможность удалить данные: кнопка "Удалить мои данные"
- Privacy Policy: объяснить, что собирается и зачем

## Рекомендуемый вариант для NELEV

**Telegram Bot (вариант B)** — оптимален для начала:

### Преимущества
✅ Быстрая реализация (1-2 дня)
✅ Не требует Firebase SDK (уменьшение размера APK)
✅ Пользователь видит историю уведомлений в Telegram
✅ Двусторонняя связь: пользователь может ответить
✅ Нет ограничений Google Play на уведомления

### Недостатки
❌ Требует установленный Telegram
❌ Пользователь должен подписаться вручную
❌ Нет нативных Android-уведомлений (можно добавить через Telegram API)

### Реализация за 3 шага

**Шаг 1: Создать Telegram-бота**
1. Написать [@BotFather](https://t.me/BotFather): `/newbot`
2. Получить токен бота
3. Настроить команды: `/start`, `/subscribe {UID}`, `/unsubscribe`

**Шаг 2: Backend (простой Python скрипт)**
```python
# server.py
import telebot
import sqlite3

bot = telebot.TeleBot("YOUR_BOT_TOKEN")
db = sqlite3.connect("nelev.db")

@bot.message_handler(commands=['start'])
def start(message):
    bot.reply_to(message, "Привет! Отправь /subscribe {твой_UID}")

@bot.message_handler(commands=['subscribe'])
def subscribe(message):
    uid = message.text.split()[1]
    chat_id = message.chat.id
    db.execute("INSERT OR REPLACE INTO users (uid, chat_id) VALUES (?, ?)", (uid, chat_id))
    bot.reply_to(message, f"✅ Подписка активирована для {uid}")

# Отправка уведомлений
def notify_all(text):
    for chat_id in db.execute("SELECT chat_id FROM users"):
        bot.send_message(chat_id[0], text)

bot.polling()
```

**Шаг 3: В приложении добавить кнопку**
```kotlin
// Settings -> О приложении -> "Подписаться на уведомления"
Button(onClick = {
    val uid = appSettings.value.uid
    val url = "https://t.me/nelev_bot?start=$uid"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    ctx.startActivity(intent)
}) {
    Text("📬 Подписаться на уведомления в Telegram")
}
```

## Следующие шаги
1. Создать Telegram-бота
2. Развернуть простой backend (Python/Node.js)
3. Добавить кнопку в настройки приложения
4. Протестировать на 5-10 пользователях
5. (Опционально) Добавить FCM для нативных уведомлений

## Альтернатива: гибридный подход
- Telegram для анонсов и обновлений (всем подписчикам)
- FCM для критических уведомлений (если включено в Settings)
- Выбор пользователя: "Только Telegram" или "Telegram + Push"
