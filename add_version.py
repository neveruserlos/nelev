import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 68,
    "versionName": "1.9.33",
    "releaseDate": "2026-11-06",
    "changelog": {
        "ru": "• Исправлена анимация свайпа — больше не дёргается назад во время перехода",
        "en": "• Fixed swipe animation — no longer jerks back during transition",
        "es": "• Corregida la animación de deslizamiento — ya no se sacude hacia atrás durante la transición",
        "zh": "• 修复了滑动动画——过渡期间不再向后抖动",
        "ja": "• スワイプアニメーションを修正 — 遷移中に後ろに引っかからなくなりました",
        "ko": "• 스와이프 애니메이션 수정 — 전환 중 더 이상 뒤로 튀지 않음",
        "pt": "• Corrigida a animação de deslizar — não treme mais para trás durante a transição",
        "uk": "• Виправлено анімацію свайпу — більше не смикається назад під час переходу"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
