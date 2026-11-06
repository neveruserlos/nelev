import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 69,
    "versionName": "1.9.34",
    "releaseDate": "2026-11-06",
    "changelog": {
        "ru": "• Исправлена автопроверка обновлений при запуске\n• Добавлена фоновая проверка обновлений каждые 5 секунд\n• Проверка не работает если обновления приостановлены",
        "en": "• Fixed automatic update check on launch\n• Added background update check every 5 seconds\n• Check doesn't run if updates are paused",
        "es": "• Corregida la verificación automática de actualizaciones al iniciar\n• Agregada verificación de actualizaciones en segundo plano cada 5 segundos\n• La verificación no se ejecuta si las actualizaciones están pausadas",
        "zh": "• 修复了启动时的自动更新检查\n• 添加了每5秒后台更新检查\n• 如果更新暂停，则不运行检查",
        "ja": "• 起動時の自動更新チェックを修正\n• 5秒ごとのバックグラウンド更新チェックを追加\n• 更新が一時停止されている場合、チェックは実行されません",
        "ko": "• 시작 시 자동 업데이트 확인 수정\n• 5초마다 백그라운드 업데이트 확인 추가\n• 업데이트가 일시 중지되면 확인이 실행되지 않음",
        "pt": "• Corrigida a verificação automática de atualizações na inicialização\n• Adicionada verificação de atualizações em segundo plano a cada 5 segundos\n• A verificação não é executada se as atualizações estiverem pausadas",
        "uk": "• Виправлено автоперевірку оновлень при запуску\n• Додано фонову перевірку оновлень кожні 5 секунд\n• Перевірка не працює якщо оновлення призупинені"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
