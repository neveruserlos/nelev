import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 78,
    "versionName": "1.9.43",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Уведомление сервиса теперь появляется только при запуске передачи и исчезает при остановке — при открытии приложения без стриминга его нет\n• Каждые 5 секунд проверяется наличие уведомления: если его скрыли, оно возвращается — то же самое, без дублей и спама",
        "en": "• The service notification now appears only when streaming starts and disappears when stopped — no notification when opening the app without streaming\n• Every 5 seconds the notification is checked: if dismissed, it is restored — the same one, with no duplicates or spam",
        "es": "• La notificación del servicio ahora aparece solo al iniciar la transmisión y desaparece al detenerla — no hay notificación al abrir la app sin streaming\n• Cada 5 segundos se comprueba la notificación: si se descarta, se restaura — la misma, sin duplicados ni spam",
        "zh": "• 服务通知现在仅在开始传输时出现，停止时消失——未开启传输时打开应用不会显示通知\n• 每5秒检查一次通知：如果被关闭，会自动恢复——始终是同一条，无重复、无刷屏",
        "ja": "• サービスの通知はストリーミング開始時のみ表示され、停止時に消えます — ストリーミングなしでアプリを開いても通知は出ません\n• 5秒ごとに通知の有無を確認し、閉じられていたら復元します — 同じ通知で、重複やスパムはありません",
        "ko": "• 서비스 알림이 이제 스트리밍 시작 시에만 나타나고 중지 시 사라집니다 — 스트리밍 없이 앱을 열면 알림이 없습니다\n• 5초마다 알림 존재를 확인하고, 닫혀 있으면 복원합니다 — 동일한 알림 하나만, 중복이나 스팸 없음",
        "pt": "• A notificação do serviço agora aparece apenas ao iniciar a transmissão e desaparece ao parar — sem notificação ao abrir o app sem streaming\n• A cada 5 segundos a notificação é verificada: se for dispensada, é restaurada — a mesma, sem duplicados nem spam",
        "uk": "• Сповіщення сервісу тепер з'являється лише під час запуску передачі та зникає після зупинки — при відкритті застосунку без стрімінгу його немає\n• Кожні 5 секунд перевіряється наявність сповіщення: якщо його приховали, воно повертається — те саме, без дублів і спаму"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
