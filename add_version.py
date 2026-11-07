import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 74,
    "versionName": "1.9.39",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Кнопка обновления теперь автоматически подстраивается под размер текста без анимации",
        "en": "• Update button now automatically adjusts to text size without animation",
        "es": "• El botón de actualización ahora se ajusta automáticamente al tamaño del texto sin animación",
        "zh": "• 更新按钮现在自动调整到文本大小，无动画",
        "ja": "• 更新ボタンがアニメーションなしでテキストサイズに自動調整されるようになりました",
        "ko": "• 업데이트 버튼이 이제 애니메이션 없이 텍스트 크기에 자동으로 조정됩니다",
        "pt": "• O botão de atualização agora se ajusta automaticamente ao tamanho do texto sem animação",
        "uk": "• Кнопка оновлення тепер автоматично підлаштовується під розмір тексту без анімації"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
