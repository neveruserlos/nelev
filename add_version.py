import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 82,
    "versionName": "1.9.47",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Вернули старую плавную смену темы (250 мс crossfade) вместо эффекта «растекания»\n• Окна обновлений и «Что нового» теперь открываются и закрываются мгновенно, без системной анимации окна\n• Pop-анимация кнопки темы при коротком нажатии сохранена\n• Все анимации по-прежнему отключаются при выключенных «Визуальных эффектах»",
        "en": "• Restored the old smooth theme switch (250 ms crossfade) instead of the spreading-circle effect\n• Update and What's New dialogs now open and close instantly, without the system window animation\n• Theme button pop animation on short tap is kept\n• All animations still disable when Visual effects is off",
        "es": "• Se restauró el cambio de tema suave anterior (crossfade de 250 ms) en lugar del efecto de onda\n• Los diálogos de actualización y Novedades ahora se abren y cierran al instante, sin la animación del sistema\n• Se mantiene la animación pop del botón de tema al toque corto\n• Todas las animaciones siguen desactivándose con \"Efectos visuales\" apagado",
        "zh": "• 恢复了原来的平滑主题切换（250 毫秒交叉淡化），取代扩散圆环效果\n• 更新和\"新功能\"对话框现在立即打开和关闭，没有系统窗口动画\n• 保留主题按钮短按时的弹出动画\n• 关闭\"视觉效果\"时所有动画仍会禁用",
        "ja": "• 広がる円の効果をやめ、元の滑らかなテーマ切り替え（250 ms クロスフェード）に戻しました\n• 更新ダイアログと「新機能」ダイアログがシステムのウィンドウアニメーションなしで即座に開閉します\n• テーマボタンの短いタップ時のポップアニメーションは維持\n• 「視覚効果」オフ時はすべてのアニメーションが無効のままです",
        "ko": "• 퍼지는 원 효과 대신 이전의 부드러운 테마 전환(250ms 크로스페이드)으로 되돌렸습니다\n• 업데이트 및 \"새 기능\" 대화상자가 시스템 창 애니메이션 없이 즉시 열리고 닫힙니다\n• 테마 버튼의 짧은 탭 팝 애니메이션은 유지됩니다\n• \"시각 효과\"가 꺼져 있으면 모든 애니메이션이 계속 비활성화됩니다",
        "pt": "• Restaurada a troca de tema suave antiga (crossfade de 250 ms) em vez do efeito de onda\n• Os diálogos de atualização e Novidades agora abrem e fecham instantaneamente, sem a animação do sistema\n• A animação pop do botão de tema em toque curto foi mantida\n• Todas as animações continuam desativadas com \"Efeitos visuais\" desligado",
        "uk": "• Повернули стару зміну теми (250 мс crossfade) замість ефекту «розтікання»\n• Вікна оновлень і «Що нового» тепер відкриваються й закриваються миттєво, без системної анімації вікна\n• Pop-анімація кнопки теми при короткому натисканні збережена\n• Усі анімації так само вимикаються при вимкнених «Візуальних ефектах»"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
