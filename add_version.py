import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 80,
    "versionName": "1.9.45",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Анимации окон/свайпов теперь мгновенно выключаются при отключении «Визуальные эффекты»\n• Блюр при открытых окнах (история/About) теперь работает всегда, независимо от настройки визуальных эффектов\n• Смена темы стала живей: 650 мс с FastOutSlowInEasing и overshoot-ощущением\n• Кнопка смены темы: press-анимация (scale down до 0.95× + сдвиг градиента) при нажатии",
        "en": "• Window/swipe animations now instantly disable when \"Visual effects\" is turned off\n• Blur on open overlays (history/About) now works always, independent of visual effects setting\n• Theme switching is livelier: 650 ms with FastOutSlowInEasing and overshoot feel\n• Theme button: press animation (scale down to 0.95× + gradient shift) on tap",
        "es": "• Las animaciones de ventanas/deslizamientos ahora se desactivan al instante al desactivar \"Efectos visuales\"\n• El desenfoque en ventanas abiertas (historial/About) ahora funciona siempre, independientemente de los efectos visuales\n• El cambio de tema es más fluido: 650 ms con FastOutSlowInEasing y sensación de overshoot\n• Botón de tema: animación al pulsar (escala a 0,95× + cambio de gradiente)",
        "zh": "• 关闭\"视觉效果\"时，窗口/滑动动画现在会瞬间禁用\n• 打开覆盖层(历史记录/关于)时的模糊效果现在始终有效，与视觉效果设置无关\n• 主题切换更生动：650 ms 配合 FastOutSlowInEasing 和过冲感\n• 主题按钮：按下时有动画（缩放至 0.95× + 渐变位移）",
        "ja": "• 「視覚効果」をオフにするとウィンドウ/スワイプアニメーションが即座に無効になります\n• 開いているオーバーレイ(履歴/About)のぼかしが常に有効になり、視覚効果設定に依存しません\n• テーマ切り替えがより生き生きと: 650 ms、FastOutSlowInEasing、オーバーシュート感あり\n• テーマボタン: タップ時にプレスアニメーション(0.95倍へのスケールダウン + グラデーションシフト)",
        "ko": "• \"시각 효과\"를 끄면 창/스와이프 애니메이션이 즉시 비활성화됩니다\n• 열린 오버레이(기록/정보)의 블러가 항상 작동하며 시각 효과 설정과 무관합니다\n• 테마 전환이 더 생동감 있게: 650 ms, FastOutSlowInEasing, 오버슈트 느낌\n• 테마 버튼: 누를 때 프레스 애니메이션(0.95배로 축소 + 그라데이션 이동)",
        "pt": "• Animações de janela/deslize agora desativam instantaneamente ao desligar \"Efeitos visuais\"\n• Desfoque em overlays abertos (histórico/Sobre) agora funciona sempre, independente dos efeitos visuais\n• Troca de tema mais viva: 650 ms com FastOutSlowInEasing e sensação de overshoot\n• Botão de tema: animação ao pressionar (escala para 0,95× + deslocamento de gradiente)",
        "uk": "• Анімації вікон/свайпів тепер миттєво вимикаються при вимкненні \"Візуальні ефекти\"\n• Блур при відкритих вікнах (історія/About) тепер працює завжди, незалежно від налаштування візуальних ефектів\n• Зміна теми став живішою: 650 мс з FastOutSlowInEasing та відчуттям overshoot\n• Кнопка зміни теми: press-анімація (scale down до 0,95× + зміщення градієнта) при натисканні"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))