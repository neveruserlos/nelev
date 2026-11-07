import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 73,
    "versionName": "1.9.38",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Исправлена анимация кнопки «Что нового» — больше не дёргается\n• Добавлено мигающее сердечко рядом с «Доступно обновление»",
        "en": "• Fixed What's New button animation — no longer jerky\n• Added pulsing heart icon next to Update available",
        "es": "• Corregida la animación del botón Novedades — ya no se sacude\n• Añadido icono de corazón pulsante junto a Actualización disponible",
        "zh": "• 修复了新功能按钮动画——不再抖动\n• 在有可用更新旁边添加了跳动的心形图标",
        "ja": "• 新機能ボタンのアニメーションを修正 — 引っかからなくなりました\n• アップデート利用可能の横に脈打つハートアイコンを追加",
        "ko": "• 새 기능 버튼 애니메이션 수정 — 더 이상 튀지 않음\n• 업데이트 사용 가능 옆에 맥동하는 하트 아이콘 추가",
        "pt": "• Corrigida a animação do botão Novidades — não treme mais\n• Adicionado ícone de coração pulsante ao lado de Atualização disponível",
        "uk": "• Виправлено анімацію кнопки «Що нового» — більше не смикається\n• Додано пульсуюче серце поруч з «Доступне оновлення»"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
