import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 84,
    "versionName": "1.9.49",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Исправлен краш при переключении темы кнопкой\n• Текст на кнопке темы снова по центру\n• Надпись «Доступно обновление» снова обычного цвета, как остальные настройки\n• Градиент новой темы по-прежнему плавно проносится слева направо с ускорением в конце",
        "en": "• Fixed a crash when switching the theme with the button\n• Theme button text is centered again\n• The \"Update available\" label is back to the regular text color like other settings\n• The new theme gradient still sweeps left to right with acceleration at the end",
        "es": "• Corregido el fallo al cambiar el tema con el botón\n• El texto del botón de tema vuelve a estar centrado\n• La etiqueta «Actualización disponible» vuelve al color normal como los demás ajustes\n• El degradado del nuevo tema sigue pasando de izquierda a derecha con aceleración al final",
        "zh": "• 修复了使用按钮切换主题时的崩溃\n• 主题按钮文字重新居中\n• “有可用更新”文字恢复为与其他设置相同的常规颜色\n• 新主题渐变仍会从左到右扫过并在末尾加速",
        "ja": "• テーマボタンでテーマを切り替える際のクラッシュを修正\n• テーマボタンのテキストが再び中央に表示されます\n• 「アップデートがあります」の文字が他の設定と同じ通常の色に戻りました\n• 新しいテーマのグラデーションは最後に加速しながら左から右へ流れます",
        "ko": "• 테마 버튼으로 테마를 전환할 때 발생하던 크래시를 수정했습니다\n• 테마 버튼 텍스트가 다시 중앙에 표시됩니다\n• \"업데이트 가능\" 문구가 다른 설정과 같은 일반 텍스트 색상으로 돌아왔습니다\n• 새 테마 그라데이션은 마지막에 가속하며 왼쪽에서 오른쪽으로 흐릅니다",
        "pt": "• Corrigido o crash ao trocar o tema pelo botão\n• O texto do botão de tema voltou ao centro\n• O rótulo \"Atualização disponível\" voltou à cor normal como os demais ajustes\n• O gradiente do novo tema continua passando da esquerda para a direita com aceleração no final",
        "uk": "• Виправлено краш під час перемикання теми кнопкою\n• Текст на кнопці теми знову по центру\n• Напис «Доступне оновлення» знову звичайного кольору, як інші налаштування\n• Градієнт нової теми так само плавно проноситься зліва направо з прискоренням наприкінці"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
