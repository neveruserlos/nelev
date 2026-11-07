import io, json
p = 'app/src/main/assets/version_history.json'
with io.open(p, encoding='utf-8') as f:
    d = json.load(f)
entry = {
    "versionCode": 79,
    "versionName": "1.9.44",
    "releaseDate": "2026-11-07",
    "changelog": {
        "ru": "• «Сбросить настройки» теперь полностью очищает приложение: язык, тема, все настройки, IP, параметры звука, история сессий, кэш обновлений\n• После сброса приложение сразу переходит на экран первоначальной настройки, как после установки\n• Сброс не сбрасывается фоновыми записями и не показывает окно «Что нового»",
        "en": "• \"Reset settings\" now fully wipes the app: language, theme, all settings, IP, audio parameters, session history, update cache\n• After reset the app immediately returns to the initial setup screen, just like after installation\n• Reset is not overwritten by background writes and does not show the What's New dialog",
        "es": "• \"Restablecer ajustes\" ahora borra completamente la app: idioma, tema, todos los ajustes, IP, parámetros de audio, historial de sesiones y caché de actualizaciones\n• Tras el restablecimiento la app vuelve inmediatamente a la pantalla de configuración inicial, como tras la instalación\n• El restablecimiento no se pierde por escrituras en segundo plano y no muestra el diálogo de novedades",
        "zh": "• “重置设置”现在会彻底清空应用：语言、主题、所有设置、IP、音频参数、会话历史和更新缓存\n• 重置后应用会立即返回初始设置界面，就像刚安装一样\n• 重置不会被后台写入覆盖，也不会弹出“更新内容”对话框",
        "ja": "• 「設定をリセット」で言語・テーマ・すべての設定・IP・音声パラメータ・セッション履歴・更新キャッシュを完全に消去します\n• リセット後はインストール直後と同様に初期設定画面へすぐ戻ります\n• リセットはバックグラウンドの書き込みで上書きされず、「新機能」ダイアログも表示されません",
        "ko": "• “설정 초기화”가 이제 언어, 테마, 모든 설정, IP, 오디오 매개변수, 세션 기록, 업데이트 캐시를 완전히 지웁니다\n• 초기화 후에는 설치 직후처럼 초기 설정 화면으로 즉시 돌아갑니다\n• 초기화는 백그라운드 저장으로 덮어써지지 않으며 “새로운 기능” 대화상자가 표시되지 않습니다",
        "pt": "• \"Redefinir configurações\" agora apaga completamente o app: idioma, tema, todas as configurações, IP, parâmetros de áudio, histórico de sessões e cache de atualizações\n• Após a redefinição o app volta imediatamente à tela de configuração inicial, como após a instalação\n• A redefinição não é sobrescrita por gravações em segundo plano e não mostra o diálogo de novidades",
        "uk": "• «Скинути налаштування» тепер повністю очищає застосунок: мову, тему, усі налаштування, IP, параметри звуку, історію сесій, кеш оновлень\n• Після скидання застосунок одразу повертається на екран початкового налаштування, як після встановлення\n• Скидання не перезаписується фоновими записами і не показує вікно «Що нового»"
    }
}
d['versions'].insert(0, entry)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(d, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('OK', d['versions'][0]['versionCode'], d['versions'][0]['versionName'], len(d['versions'][0]['changelog']))
