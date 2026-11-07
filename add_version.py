import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 76,
    "versionName": "1.9.41",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Исправлено отображение текста «Доступно обновление» на всех языках — раньше показывался технический ключ",
        "en": "• Fixed \"Update available\" text display on all languages — previously showed the technical key",
        "es": "• Corregida la visualización del texto \"Actualización disponible\" en todos los idiomas — antes mostraba la clave técnica",
        "zh": "• 修复了所有语言下\"有可用更新\"文本的显示——之前显示技术键名",
        "ja": "• 全言語で「アップデート利用可能」のテキスト表示を修正 — 以前は技術的なキーが表示されていました",
        "ko": "• 모든 언어에서 \"업데이트 사용 가능\" 텍스트 표시 수정 — 이전에는 기술 키가 표시됨",
        "pt": "• Corrigida a exibição do texto \"Atualização disponível\" em todos os idiomas — antes mostrava a chave técnica",
        "uk": "• Виправлено відображення тексту «Доступне оновлення» на всіх мовах — раніше показувався технічний ключ"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
