import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 85,
    "versionName": "1.9.50",
    "releaseDate": "2026-11-08",
    "changelog": {
        "ru": "• Смена темы теперь происходит мгновенно по нажатию, без ожидания анимации\n• Кнопка темы: вместо резкой волны — мягкое «растворение краски в воде», старый градиент плавно перетекает в новый\n• Убрана жёсткая светящаяся кромка, добавлены мягкие расплывающиеся пятна новой палитры\n• Переливание градиента кнопки стало более плавным и живым",
        "en": "• Theme switching is now instant on tap, with no waiting for the animation\n• Theme button: instead of a sharp wave, a soft paint-in-water dissolve — the old gradient melts smoothly into the new one\n• Removed the hard glowing edge, added soft spreading blots of the new palette\n• The button gradient shimmer is smoother and more alive",
        "es": "• El cambio de tema ahora es instantáneo al tocar, sin esperar la animación\n• Botón de tema: en lugar de una onda brusca, una disolución suave como pintura en agua; el degradado antiguo se funde con el nuevo\n• Se eliminó el borde brillante duro y se añadieron manchas suaves de la nueva paleta\n• El brillo del degradado del botón es más suave y vivo",
        "zh": "• 点击后主题立即切换，不再等待动画\n• 主题按钮：用柔和的“颜料溶于水”效果取代生硬的波浪，旧渐变平滑过渡到新渐变\n• 移除了生硬的发光边缘，加入了新配色的柔和扩散斑点\n• 按钮渐变的光泽更平滑自然",
        "ja": "• タップするとテーマが即座に切り替わり、アニメーションを待たなくなりました\n• テーマボタン: 鋭い波の代わりに「水に溶ける絵の具」のような柔らかい溶解表現。古いグラデーションが新しいものへ滑らかに移行します\n• 硬い光る縁を削除し、新しい配色の柔らかい広がるしみを追加\n• ボタンのグラデーションの煌めきがより滑らかで生き生きとしました",
        "ko": "• 탭하면 테마가 애니메이션을 기다리지 않고 즉시 전환됩니다\n• 테마 버튼: 날카로운 물결 대신 \"물에 번지는 물감\" 같은 부드러운 용해 효과로 이전 그라데이션이 새 그라데이션으로 자연스럽게 변합니다\n• 딱딱한 빛나는 테두리를 제거하고 새 팔레트의 부드러운 번짐을 추가했습니다\n• 버튼 그라데이션의 흐름이 더 부드럽고 생동감 있게 바뀌었습니다",
        "pt": "• A troca de tema agora é instantânea ao tocar, sem esperar a animação\n• Botão de tema: em vez de uma onda brusca, uma dissolução suave como tinta na água; o gradiente antigo se funde suavemente no novo\n• Removida a borda brilhante dura e adicionadas manchas suaves da nova paleta\n• O brilho do gradiente do botão ficou mais suave e vivo",
        "uk": "• Зміна теми тепер відбувається миттєво після натискання, без очікування анімації\n• Кнопка теми: замість різкої хвилі — м'яке «розчинення фарби у воді», старий градієнт плавно перетікає в новий\n• Прибрано жорстку світну кромку, додано м'які розпливчасті плями нової палітри\n• Переливання градієнта кнопки стало плавнішим і живішим"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
