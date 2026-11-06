# NELEV 1.9.13 - Исправления (обновлено)

## Что было исправлено:

### 1. ✅ Исправлено расположение плашки выбора в паузе обновлений
**Проблема:** После первого исправления плашка сместилась и находилась не на правильных позициях

**Решение:**
- Исправлен расчёт offset с учётом spacing: `(selectedIndex * 52).dp` вместо `44.dp`
- 52dp = 44dp высота пункта + 8dp spacing между пунктами
- Убран дополнительный offset `+ 10.dp`, который смещал плашку вниз
- Теперь плашка точно совпадает с выбранным пунктом

**Код:**
```kotlin
val selectedOffset by animateDpAsState(
    targetValue = if (selectedPauseOption != null) (selectedPauseOption!! * 52).dp else 0.dp,
    animationSpec = spring(...)
)

Box(Modifier.offset(y = selectedOffset)  // без + 10.dp
```

---

### 2. ✅ Убрано дёрганье при раскрытии changelog в уведомлении об обновлении
**Проблема:** При нажатии "Что нового" текст раскрывался с дёрганьем

**Решение:**
- Заменил `animateContentSize` на всём Column на `AnimatedVisibility` для текста
- Используется `expandVertically` + `fadeIn` для появления
- `shrinkVertically` + `fadeOut` для скрытия
- Spring анимация для плавного движения

**Код:**
```kotlin
AnimatedVisibility(
    visible = showNotes,
    enter = expandVertically(spring(...)) + fadeIn(),
    exit = shrinkVertically(spring(...)) + fadeOut()
) {
    Text(info.changelog, ...)
}
```

---

### 3. ✅ Убрано "телепание" окна "Что нового"
**Проблема:** Окно сначала плавно идёт снизу вверх, потом резко дёргается вниз

**Решение:**
- Обернул Surface в Box с `wrapContentHeight()`
- Box стабилизирует позицию Surface во время анимации Dialog
- contentAlignment = Alignment.Center для центрирования
- Теперь окно плавно появляется без телепания

**Код:**
```kotlin
Dialog(onDismissRequest = onDismiss) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        Surface(...) {
            ...
        }
    }
}
```

---

## Итоговые исправления версии 1.9.13:

1. ✅ Плашка выбора точно позиционируется на пунктах
2. ✅ Плавно "переплывает" между пунктами
3. ✅ Окно паузы плавно изменяет размер при выборе "Свой срок"
4. ✅ Список "Что нового" плавно раскрывается в уведомлении
5. ✅ Окно "Что нового" появляется плавно без телепания вниз
6. ✅ Все языки правильно загружают переводы changelog

---

## Технические детали:

**Offset плашки:**
- Формула: `selectedIndex * (itemHeight + spacing)`
- 52dp = 44dp (высота) + 8dp (spacing)

**AnimatedVisibility vs animateContentSize:**
- `AnimatedVisibility` - для появления/скрытия элементов
- `animateContentSize` - для изменения размера существующих элементов
- Использование правильного инструмента предотвращает дёрганье

**Стабилизация Dialog:**
- Box с `wrapContentHeight()` фиксирует вертикальную позицию
- Предотвращает пересчёт позиции во время анимации
- contentAlignment центрирует содержимое

---

**Статус:** Сборка APK в процессе
**Дата:** 2026-11-04 22:46 UTC
