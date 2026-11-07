import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 77,
    "versionName": "1.9.42",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Диалог «Что нового» теперь сразу открывается финального размера — кнопка «Отлично» больше не прыгает и окно не сужается\n• Уведомление сервиса автоматически возвращается в шторку, если его скрыть",
        "en": "• The What's New dialog now opens at its final size right away — the OK button no longer jumps and the window no longer shrinks\n• The service notification is automatically restored to the shade if dismissed",
        "es": "• El diálogo Novedades ahora se abre directamente con su tamaño final — el botón Aceptar ya no salta y la ventana no se encoge\n• La notificación del servicio se restaura automáticamente en la cortina si se descarta",
        "zh": "• “新功能”对话框现在直接以最终尺寸打开——确定按钮不再跳动，窗口不再收缩\n• 服务通知被关闭后会自动恢复到通知栏",
        "ja": "• 「新機能」ダイアログが最初から最終サイズで開くようになりました — OKボタンが跳ねたり、ウィンドウが縮んだりしません\n• サービスの通知を閉じても通知シェードに自動的に復元されます",
        "ko": "• \"새 기능\" 대화상자가 이제 처음부터 최종 크기로 열립니다 — 확인 버튼이 튀거나 창이 줄어들지 않습니다\n• 서비스 알림을 닫아도 알림 창에 자동으로 복원됩니다",
        "pt": "• O diálogo Novidades agora abre direto no tamanho final — o botão OK não pula mais e a janela não encolhe\n• A notificação do serviço é restaurada automaticamente na gaveta se for dispensada",
        "uk": "• Діалог «Що нового» тепер одразу відкривається фінального розміру — кнопка «Гаразд» більше не стрибає і вікно не звужується\n• Сповіщення сервісу автоматично повертається в шторку, якщо його приховати"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
