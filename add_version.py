import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 72,
    "versionName": "1.9.37",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Уведомление сервиса теперь действительно нельзя скрыть — повышен приоритет канала и уведомления",
        "en": "• Service notification now truly cannot be hidden — channel and notification priority increased",
        "es": "• La notificación del servicio ahora realmente no se puede ocultar — prioridad de canal y notificación aumentada",
        "zh": "• 服务通知现在真的无法隐藏——提高了频道和通知优先级",
        "ja": "• サービス通知が本当に非表示にできなくなりました — チャンネルと通知の優先度を上げました",
        "ko": "• 서비스 알림을 이제 정말로 숨길 수 없음 — 채널 및 알림 우선순위 증가",
        "pt": "• A notificação do serviço agora realmente não pode ser ocultada — prioridade do canal e notificação aumentada",
        "uk": "• Сповіщення сервісу тепер дійсно не можна приховати — підвищено пріоритет каналу та сповіщення"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
