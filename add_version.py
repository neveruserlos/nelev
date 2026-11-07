import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 71,
    "versionName": "1.9.36",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Умная логика обновлений: кнопка \"Позже\" откладывает проверку до перезапуска\n• Кнопка меняется на \"Доступно обновление\" когда обновление найдено\n• Фоновая проверка каждые 5 секунд только для обновления статуса кнопки\n• Уведомление сервиса теперь нельзя скрыть из панели уведомлений",
        "en": "• Smart update logic: \"Later\" button postpones checking until restart\n• Button changes to \"Update available\" when update is found\n• Background check every 5 seconds only updates button status\n• Service notification can no longer be hidden from notification panel",
        "es": "• Lógica de actualización inteligente: el botón \"Más tarde\" pospone la verificación hasta el reinicio\n• El botón cambia a \"Actualización disponible\" cuando se encuentra una actualización\n• La verificación en segundo plano cada 5 segundos solo actualiza el estado del botón\n• La notificación del servicio ya no se puede ocultar del panel de notificaciones",
        "zh": "• 智能更新逻辑：\"稍后\" 按钮将检查推迟到重启\n• 找到更新时按钮变为 \"有可用更新\"\n• 每5秒后台检查仅更新按钮状态\n• 服务通知现在无法从通知面板隐藏",
        "ja": "• スマート更新ロジック：\"後で\" ボタンで再起動までチェックを延期\n• 更新が見つかるとボタンが \"アップデート利用可能\" に変更\n• 5秒ごとのバックグラウンドチェックはボタンの状態のみを更新\n• サービス通知は通知パネルから非表示にできなくなりました",
        "ko": "• 스마트 업데이트 로직: \"나중에\" 버튼으로 재시작까지 확인 연기\n• 업데이트를 찾으면 버튼이 \"업데이트 사용 가능\"으로 변경\n• 5초마다 백그라운드 확인은 버튼 상태만 업데이트\n• 서비스 알림을 알림 패널에서 숨길 수 없게 됨",
        "pt": "• Lógica de atualização inteligente: o botão \"Mais tarde\" adia a verificação até a reinicialização\n• O botão muda para \"Atualização disponível\" quando a atualização é encontrada\n• A verificação em segundo plano a cada 5 segundos apenas atualiza o status do botão\n• A notificação do serviço não pode mais ser ocultada do painel de notificações",
        "uk": "• Розумна логіка оновлень: кнопка \"Пізніше\" відкладає перевірку до перезапуску\n• Кнопка змінюється на \"Доступне оновлення\" коли оновлення знайдено\n• Фонова перевірка кожні 5 секунд тільки оновлює статус кнопки\n• Сповіщення сервісу тепер не можна приховати з панелі сповіщень"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
