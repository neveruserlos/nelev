import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 70,
    "versionName": "1.9.35",
    "releaseDate": "2026-11-06",
    "changelog": {
        "ru": "• Полностью исправлена двойная анимация свайпа — теперь используется отдельная логика для drag и click",
        "en": "• Completely fixed double swipe animation — now uses separate logic for drag and click",
        "es": "• Corregida completamente la animación de doble deslizamiento — ahora usa lógica separada para arrastrar y hacer clic",
        "zh": "• 完全修复了双重滑动动画——现在对拖动和点击使用单独的逻辑",
        "ja": "• ダブルスワイプアニメーションを完全に修正 — ドラッグとクリックに別々のロジックを使用するようになりました",
        "ko": "• 이중 스와이프 애니메이션 완전히 수정 — 이제 드래그 및 클릭에 대해 별도의 논리 사용",
        "pt": "• Corrigida completamente a animação de deslizar duplo — agora usa lógica separada para arrastar e clicar",
        "uk": "• Повністю виправлено подвійну анімацію свайпу — тепер використовується окрема логіка для drag і click"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
