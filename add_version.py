import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 83,
    "versionName": "1.9.48",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Окно «Что нового» больше не выезжает снизу вверх: системная/OEM-анимация окна отключена на уровне темы\n• Окна обновлений и уведомлений теперь тоже появляются без анимации окна\n• Кнопка темы: вместо pop-анимации градиент новой темы плавно проносится слева направо, ускоряется и затем переключает тему\n• При выключенных «Визуальных эффектах» кнопка темы переключает тему мгновенно, без прогона градиента",
        "en": "• The What's New window no longer slides up from the bottom: system/OEM window animation is disabled at the theme level\n• Update and notice dialogs also appear without window animation now\n• Theme button: instead of a pop, the new theme gradient sweeps left to right, accelerates, then switches the theme\n• With Visual effects off, the theme button switches instantly without the gradient sweep",
        "es": "• La ventana de Novedades ya no sube desde abajo: la animación de ventana del sistema/OEM está desactivada a nivel de tema\n• Los diálogos de actualización y avisos también aparecen sin animación de ventana\n• Botón de tema: en lugar de pop, el gradiente del nuevo tema recorre de izquierda a derecha, acelera y luego cambia el tema\n• Con \"Efectos visuales\" apagado, el botón cambia el tema al instante sin recorrido de gradiente",
        "zh": "• \"新功能\"窗口不再从底部向上滑入：系统/OEM 窗口动画已在主题层面禁用\n• 更新和通知对话框现在也没有窗口动画\n• 主题按钮：新主题渐变从左到右扫过并加速，然后切换主题（取代弹出动画）\n• 关闭\"视觉效果\"时，主题按钮立即切换，不播放渐变扫过",
        "ja": "• 「新機能」ウィンドウが下から上へスライドしなくなりました。システム/OEMのウィンドウアニメーションをテーマ側で無効化\n• 更新ダイアログと通知ダイアログもウィンドウアニメーションなしで表示されます\n• テーマボタン: ポップの代わりに新しいテーマのグラデーションが左から右へ流れ、加速してからテーマを切り替えます\n• 「視覚効果」オフ時はグラデーションを流さず即座にテーマを切り替えます",
        "ko": "• \"새 기능\" 창이 더 이상 아래에서 위로 슬라이드하지 않습니다. 시스템/OEM 창 애니메이션을 테마 수준에서 비활성화했습니다\n• 업데이트 및 알림 대화상자도 창 애니메이션 없이 나타납니다\n• 테마 버튼: 팝 대신 새 테마 그라데이션이 왼쪽에서 오른쪽으로 흐르고 가속된 뒤 테마를 전환합니다\n• \"시각 효과\"가 꺼져 있으면 그라데이션 없이 즉시 테마가 전환됩니다",
        "pt": "• A janela Novidades não sobe mais de baixo: a animação de janela do sistema/OEM foi desativada no tema\n• Os diálogos de atualização e avisos também aparecem sem animação de janela\n• Botão de tema: em vez de pop, o gradiente do novo tema passa da esquerda para a direita, acelera e então troca o tema\n• Com \"Efeitos visuais\" desligado, o botão troca o tema instantaneamente, sem o gradiente",
        "uk": "• Вікно «Що нового» більше не виїжджає знизу вгору: системну/OEM-анімацію вікна вимкнено на рівні теми\n• Вікна оновлень і повідомлень тепер теж з'являються без анімації вікна\n• Кнопка теми: замість pop-анімації градієнт нової теми плавно проноситься зліва направо, прискорюється й потім перемикає тему\n• При вимкнених «Візуальних ефектах» кнопка теми перемикає тему миттєво, без прогону градієнта"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
