import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 81,
    "versionName": "1.9.46",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Все анимации, включая окна обновлений и «Что нового», теперь мгновенно выключаются при отключённых «Визуальных эффектах»\n• Смена темы: новая тема растекается круговой волной из кнопки переключения (Venom-style)\n• Кнопка темы: pop-анимация с пружинистым отскоком при коротком нажатии\n• Блюр на оверлеях (история/About) остаётся включённым всегда\n• Сканер QR: линия сканирования и вспышка теперь тоже следуют настройке визуальных эффектов\n• Онбординг и сплэш полностью статичны при выключенных эффектах",
        "en": "• All animations, including update dialogs and What's New, now instantly disable when Visual effects is off\n• Theme switch: the new theme spreads as a circular wave from the toggle button (Venom-style)\n• Theme button: pop animation with a springy bounce on short tap\n• Blur on overlays (history/About) always stays on\n• QR scanner: scan line and flash now also follow the visual effects setting\n• Onboarding and splash are fully static when effects are off",
        "es": "• Todas las animaciones, incluidos los diálogos de actualización y What's New, se desactivan al instante con \"Efectos visuales\" apagado\n• Cambio de tema: el nuevo tema se extiende como una onda circular desde el botón (estilo Venom)\n• Botón de tema: animación pop con rebote elástico en toque corto\n• El desenfoque en overlays (historial/About) siempre permanece activo\n• Escáner QR: la línea de escaneo y el flash también siguen la configuración de efectos visuales\n• Onboarding y splash totalmente estáticos sin efectos",
        "zh": "• 关闭\"视觉效果\"后，所有动画（包括更新对话框和\"新功能\"）现在立即禁用\n• 主题切换：新主题从切换按钮以圆形波纹扩散（Venom 风格）\n• 主题按钮：短按时带有弹性回弹的弹出动画\n• 覆盖层（历史记录/关于）的模糊效果始终保持开启\n• QR 扫描器：扫描线和闪光现在也遵循视觉效果设置\n• 关闭效果时，引导页和启动画面完全静态",
        "ja": "• 「視覚効果」オフ時、更新ダイアログや「新機能」を含むすべてのアニメーションが即座に無効になります\n• テーマ切り替え: 新しいテーマが切り替えボタンから円形の波として広がります（Venom風）\n• テーマボタン: 短いタップでバネのあるポップアニメーション\n• オーバーレイ（履歴/About）のぼかしは常に有効\n• QRスキャナー: スキャンラインとフラッシュも視覚効果設定に従います\n• 効果オフ時、オンボーディングとスプラッシュは完全に静止",
        "ko": "• \"시각 효과\"가 꺼져 있으면 업데이트 대화상자와 \"새 기능\"을 포함한 모든 애니메이션이 즉시 비활성화됩니다\n• 테마 전환: 새 테마가 전환 버튼에서 원형 파동으로 퍼집니다 (Venom 스타일)\n• 테마 버튼: 짧은 탭 시 탄성 있는 팝 애니메이션\n• 오버레이(기록/정보)의 블러는 항상 유지됩니다\n• QR 스캐너: 스캔 라인과 플래시도 시각 효과 설정을 따릅니다\n• 효과가 꺼져 있으면 온보딩과 스플래시가 완전히 정적입니다",
        "pt": "• Todas as animações, incluindo diálogos de atualização e Novidades, desativam instantaneamente com \"Efeitos visuais\" desligado\n• Troca de tema: o novo tema se espalha como uma onda circular a partir do botão (estilo Venom)\n• Botão de tema: animação pop com quique elástico em toque curto\n• O desfoque em overlays (histórico/Sobre) permanece sempre ativo\n• Scanner QR: linha de escaneamento e flash também seguem a configuração de efeitos visuais\n• Onboarding e splash totalmente estáticos sem efeitos",
        "uk": "• Усі анімації, включно з вікнами оновлень і «Що нового», тепер миттєво вимикаються при вимкнених «Візуальних ефектах»\n• Зміна теми: нова тема розтікається круговою хвилею з кнопки перемикання (Venom-style)\n• Кнопка теми: pop-анімація з пружним відскоком при короткому натисканні\n• Блюр на оверлеях (історія/About) залишається увімкненим завжди\n• Сканер QR: лінія сканування та спалах тепер також слідують налаштуванню візуальних ефектів\n• Онбординг і сплеш повністю статичні при вимкнених ефектах"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
