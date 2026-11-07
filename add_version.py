import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 75,
    "versionName": "1.9.40",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• Убрана версия из кнопки обновления\n• Исправлена анимация кнопки \"Отлично\" в диалоге \"Что нового\" — больше не дёргается и не сужается",
        "en": "• Removed version from update button\n• Fixed \"OK\" button animation in What's New dialog — no longer jerky or shrinking",
        "es": "• Eliminada la versión del botón de actualización\n• Corregida la animación del botón \"Aceptar\" en el diálogo Novedades — ya no se sacude ni se encoge",
        "zh": "• 从更新按钮中删除了版本\n• 修复了新功能对话框中的\"确定\"按钮动画——不再抖动或收缩",
        "ja": "• 更新ボタンからバージョンを削除\n• 新機能ダイアログの\"OK\"ボタンのアニメーションを修正 — 引っかかったり縮んだりしなくなりました",
        "ko": "• 업데이트 버튼에서 버전 제거\n• 새 기능 대화상자의 \"확인\" 버튼 애니메이션 수정 — 더 이상 튀거나 줄어들지 않음",
        "pt": "• Removida a versão do botão de atualização\n• Corrigida a animação do botão \"OK\" no diálogo Novidades — não treme ou encolhe mais",
        "uk": "• Прибрано версію з кнопки оновлення\n• Виправлено анімацію кнопки \"Гаразд\" у діалозі \"Що нового\" — більше не смикається і не звужується"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
