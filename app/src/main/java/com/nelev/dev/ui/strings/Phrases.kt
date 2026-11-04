package com.nelev.dev.ui.strings

// ─── Phrases ─────────────────────────────────────────────────────────────────

object Phrases {
    private val EN = listOf(
        "Today is a great day, isn't it?","You're looking good today!","You're my favourite user \uD83C\uDF99",
        "Ready to conquer the airwaves?","Scientists confirm: your voice improves moods by 147%",
        "UDP packets flying to your PC at nearly the speed of light","All bits in place. You may speak.",
        "Your voice today is especially wonderful","Streaming is an art. And you are the artist.",
        "Late night streaming hits different","The mic is hot. Go for it.",
        "Every great stream starts with a single packet","Your signal is strong. Your presence is stronger.",
        "You sound like someone who knows what they're doing","The connection is live. So are you.",
        "Your voice deserves to be heard","Another session, another masterpiece",
        "The mic doesn't lie — you're on fire today","Quality audio, quality vibes",
        "48kHz of pure you","Latency is low. Energy is high.",
        "Small device, big sound","Let the packets flow",
        "Stream like nobody's listening. But they are.","One mic. Infinite possibilities.",
        "Even your silence streams at 48kHz","Today you're the broadcast",
        "Mic check: flawless","Your voice is the main character today",
        "The connection is yours. Use it well.","You didn't just open an app. You opened a channel.",
        "Broadcast yourself. Unapologetically.","Some people talk. You transmit.",
        "The world has enough silence. Fill it.","Ready when you are. Always.",
        "Packet loss: 0%. Confidence: 100%.","Your frequency is unlike any other",
        "No cables. No limits. Just you.","Low latency, high impact",
        "The buffer is loaded. The moment is yours.",
        "You hit start. Everything else is just physics.",
        "Today's forecast: clear signal, strong delivery, zero dropouts",
        "Signal received. World updated.","You showed up. That's already the hardest part.",
        "Consider this your daily reminder: your voice matters",
        "You had a good idea. nelev made sure it was heard."
    )
    private val RU = listOf(
        "Сегодня хороший день, не правда ли?","Вы сегодня хороши как никогда!",
        "Вы мой любимый пользователь \uD83C\uDF99","Готов покорить эфир?",
        "Учёные доказали: твой голос повышает настроение на 147%",
        "UDP пакеты летят к ПК со скоростью света (почти)","Все биты на месте. Можно говорить.",
        "Твой голос сегодня особенно хорош","Стриминг — это искусство. А ты — художник.",
        "Ночной стриминг — особый вайб","Микрофон горячий. Вперёд.",
        "Каждый великий стрим начинается с одного пакета","Твой сигнал сильный. Твоё присутствие — сильнее.",
        "Ты звучишь как человек, который знает что делает","Соединение живое. И ты тоже.",
        "Твой голос заслуживает быть услышанным","Ещё одна сессия, ещё один шедевр",
        "Микрофон не врёт — сегодня ты в огне","Качественный звук, качественный вайб",
        "48кГц чистого тебя","Задержка минимальна. Энергия максимальна.",
        "Маленькое устройство, большой звук","Пусть пакеты текут",
        "Стримь как будто никто не слушает. Но слушают.","Один микрофон. Бесконечные возможности.",
        "Даже твоя тишина стримится на 48кГц","Сегодня ты — вещание",
        "Проверка микрофона: безупречно","Твой голос сегодня — главный персонаж",
        "Соединение твоё. Используй его хорошо.","Ты открыл не просто приложение. Ты открыл канал.",
        "Транслируй себя. Без извинений.","Одни говорят. Ты передаёшь.",
        "В мире достаточно тишины. Заполни её.","Готов, когда ты. Всегда.",
        "Потеря пакетов: 0%. Уверенность: 100%.","Твоя частота ни на кого не похожа",
        "Никаких кабелей. Никаких ограничений. Только ты.","Минимальная задержка, максимальный эффект",
        "Буфер загружен. Момент твой.",
        "Ты нажал старт. Всё остальное — просто физика.",
        "Прогноз на сегодня: чистый сигнал, уверенная подача, ноль обрывов",
        "Сигнал принят. Мир обновлён.","Ты пришёл. Это уже самая сложная часть.",
        "Ежедневное напоминание: твой голос важен",
        "У тебя была хорошая идея. nelev позаботился, чтобы её услышали."
    )
    private val ES = listOf(
        "Hoy es un gran día, ¿verdad?","Hoy te ves fantástico","Eres mi usuario favorito \uD83C\uDF99",
        "¿Listo para conquistar las ondas?","Los científicos confirman: tu voz mejora el ánimo un 147%",
        "Paquetes UDP volando a tu PC casi a la velocidad de la luz","Todos los bits en su sitio. Puedes hablar.",
        "Tu voz hoy es especialmente maravillosa","El streaming es arte. Y tú eres el artista.",
        "El streaming nocturno tiene un rollo especial","El micrófono está caliente. Adelante.",
        "Todo gran stream comienza con un solo paquete","Tu señal es fuerte. Tu presencia lo es más.",
        "Suenas como alguien que sabe lo que hace","La conexión está viva. Tú también.",
        "Tu voz merece ser escuchada","Otra sesión, otra obra maestra",
        "El micro no miente — hoy estás imparable","Audio de calidad, vibras de calidad",
        "48 kHz de puro tú","La latencia es baja. La energía es alta.",
        "Dispositivo pequeño, sonido grande","Deja fluir los paquetes",
        "Stream como si nadie escuchara. Pero escuchan.","Un micrófono. Posibilidades infinitas.",
        "Hasta tu silencio se transmite a 48 kHz","Hoy tú eres la emisión",
        "Prueba de micro: impecable","Tu voz es el personaje principal hoy",
        "La conexión es tuya. Úsala bien.","No solo abriste una app. Abriste un canal.",
        "Transmítete. Sin disculpas.","Algunos hablan. Tú transmites.",
        "El mundo ya tiene suficiente silencio. Llénalo.","Listo cuando tú. Siempre.",
        "Pérdida de paquetes: 0%. Confianza: 100%.","Tu frecuencia no se parece a ninguna otra",
        "Sin cables. Sin límites. Solo tú.","Baja latencia, alto impacto",
        "El búfer está cargado. El momento es tuyo.",
        "Presionaste inicio. Todo lo demás es solo física.",
        "Pronóstico de hoy: señal clara, entrega fuerte, cero cortes",
        "Señal recibida. Mundo actualizado.","Apareciste. Esa ya es la parte más difícil.",
        "Recordatorio diario: tu voz importa",
        "Tuviste una buena idea. nelev se aseguró de que se escuchara."
    )
    private val ZH = listOf(
        "今天是个好日子，不是吗？","你今天看起来真棒！","你是我最喜欢的用户 \uD83C\uDF99",
        "准备好征服电波了吗？","科学家证实：你的声音能让心情提升147%",
        "UDP数据包以接近光速飞向你的电脑","所有位都到位了。可以开始说了。",
        "你今天的声音格外动听","流媒体是艺术。而你就是艺术家。",
        "深夜直播别有一番风味","麦克风已热。开始吧。",
        "每个伟大的直播都始于一个数据包","你的信号很强。你的存在更强。",
        "你听起来像个懂行的人","连接在线。你也在线。",
        "你的声音值得被听见","又一个会话，又一部杰作",
        "麦克风不说谎——你今天状态火爆","高质量音频，高质量氛围",
        "48kHz的纯粹的你","延迟低。能量高。",
        "小设备，大声音","让数据包流动起来",
        "像没人在听一样直播。但他们在听。","一支麦克风。无限可能。",
        "连你的沉默都以48kHz传输","今天你就是广播本身",
        "麦克风检查：完美无瑕","你的声音今天是主角",
        "连接是你的。好好利用它。","你打开的不仅是一个应用。你打开了一个频道。",
        " broadcasting yourself。无需道歉。","有些人说话。你传输。",
        "世界上已经有足够多的沉默。去填补它。","随时准备着。",
        "丢包率：0%。信心：100%。","你的频率与众不同",
        "没有线缆。没有限制。只有你。","低延迟，高影响",
        "缓冲区已加载。这一刻属于你。",
        "你按下了开始。剩下的只是物理。",
        "今日预报：信号清晰，传输有力，零中断",
        "信号已接收。世界已更新。","你来了。这已经是最难的部分。",
        "每日提醒：你的声音很重要",
        "你有一个好主意。nelev确保它被听到。"
    )
    private val JA = listOf(
        "今日はいい日ですね、そう思いませんか？","今日は最高に見えますよ！","あなたは私のお気に入りのユーザーです \uD83C\uDF99",
        "電波を制圧する準備はできましたか？","科学者も認める：あなたの声は気分を147%向上させます",
        "UDPパケットが光速であなたのPCへ飛んでいます","すべてのビットが整いました。話してください。",
        "今日のあなたの声は特に素晴らしいです","ストリーミングはアート。そしてあなたはアーティスト。",
        "夜のストリーミングは格別","マイクはホットです。どうぞ。",
        "すべての偉大な配信は一つのパケットから始まる","あなたの信号は強い。あなたの存在はもっと強い。",
        "あなたは何かをわかっている人のように聞こえます","接続は生きています。あなたもね。",
        "あなたの声は聞かれる価値がある","また一つのセッション、また一つの傑作",
        "マイクは嘘をつかない — 今日は絶好調です","高品質な音声、高品質な雰囲気",
        "48kHzの純粋なあなた","レイテンシは低い。エネルギーは高い。",
        "小さなデバイス、大きな音","パケットを流そう",
        "誰も聞いていないかのように配信しよう。でも聞いている。","一つのマイク。無限の可能性。",
        "あなたの沈黙さえ48kHzでストリーミング","今日あなたは放送そのもの",
        "マイクチェック：完璧","あなたの声が今日の主役",
        "この接続はあなたのもの。上手に使って。","アプリを開いただけじゃない。チャンネルを開いたんだ。",
        "自分自身を発信しよう。謝らずに。","話す人もいる。あなたは伝える。",
        "世界にはもう十分な静けさがある。それを満たして。","いつでも準備万端。",
        "パケットロス：0%。自信：100%。","あなたの周波数は他とは違う",
        "ケーブルなし。制限なし。ただあなただけ。","低遅延、高インパクト",
        "バッファが読み込まれました。瞬間はあなたのもの。",
        "スタートを押した。あとはただの物理学。",
        "今日の予報：クリアな信号、強力な配信、ゼロの途切れ",
        "信号受信。世界更新。","あなたが現れた。それがもう一番難しい部分だ。",
        "日々のリマインダー：あなたの声は重要です",
        "あなたは良いアイデアを持っていた。nelevはそれが届くようにした。"
    )
    private val KO = listOf(
        "오늘 좋은 하루입니다, 그렇죠?","오늘 정말 멋져 보여요!","당신은 내가 가장 좋아하는 사용자예요 \uD83C\uDF99",
        "전파를 정복할 준비됐나요?","과학자들이 확인했습니다: 당신의 목소리는 기분을 147% 향상시킵니다",
        "UDP 패킷이 빛의 속도로 PC로 날아가고 있습니다","모든 비트가 준비됐습니다. 말씀하세요.",
        "오늘 당신의 목소리는 특히 멋져요","스트리밍은 예술입니다. 그리고 당신은 아티스트입니다.",
        "늦은 밤 스트리밍은 특별해요","마이크가 준비됐습니다. 시작하세요.",
        "모든 위대한 스트림은 하나의 패킷에서 시작됩니다","당신의 신호는 강력합니다. 당신의 존재는 더 강력합니다.",
        "무엇을 하는지 아는 사람처럼 들려요","연결이 살아있습니다. 당신도요.",
        "당신의 목소리는 들릴 가치가 있습니다","또 하나의 세션, 또 하나의 걸작",
        "마이크는 거짓말하지 않아요 — 오늘 당신은 불타오르고 있어요","고품질 오디오, 고품질 분위기",
        "48kHz의 순수한 당신","지연은 낮습니다. 에너지는 높습니다.",
        "작은 기기, 큰 사운드","패킷을 흘려보내세요",
        "아무도 듣지 않는 것처럼 스트리밍하세요. 하지만 듣고 있어요.","하나의 마이크. 무한한 가능성.",
        "당신의 침묵조차 48kHz로 스트리밍됩니다","오늘 당신은 방송 자체입니다",
        "마이크 체크: 완벽","당신의 목소리가 오늘의 주인공",
        "연결은 당신의 것입니다. 잘 사용하세요.","앱을 열었을 뿐만 아니라 채널을 열었습니다.",
        "자신을 방송하세요. 사과하지 말고.","어떤 사람들은 말합니다. 당신은 전송합니다.",
        "세상에는 이미 충분한 침묵이 있습니다. 채워주세요.","당신이 준비되면 언제든. 항상.",
        "패킷 손실: 0%. 자신감: 100%.","당신의 주파수는 다른 누구와도 다릅니다",
        "케이블 없음. 제한 없음. 오직 당신.","낮은 지연, 높은 영향력",
        "버퍼가 로드되었습니다. 순간은 당신의 것입니다.",
        "시작을 눌렀습니다. 나머지는 그냥 물리학일 뿐입니다.",
        "오늘의 예보: 깨끗한 신호, 강력한 전달, 제로 끊김",
        "신호 수신. 세계 업데이트.","당신이 나타났습니다. 그것만으로도 가장 어려운 부분입니다.",
        "매일의 알림: 당신의 목소리는 중요합니다",
        "당신은 좋은 아이디어를 가졌습니다. nelev이 그것이 들리도록 했습니다."
    )
    private val PT = listOf(
        "Hoje é um ótimo dia, não é?","Hoje você está fantástico!","És o meu utilizador favorito \uD83C\uDF99",
        "Pronto para conquistar as ondas?","Cientistas confirmam: a tua voz melhora o humor em 147%",
        "Pacotes UDP a voar para o PC quase à velocidade da luz","Todos os bits no lugar. Podes falar.",
        "A tua voz hoje está especialmente maravilhosa","Streaming é arte. E tu és o artista.",
        "Streaming noturno tem um vibe especial","O microfone está quente. Avança.",
        "Todo grande stream começa com um único pacote","O teu sinal é forte. A tua presença é mais forte.",
        "Soas como alguém que sabe o que faz","A conexão está viva. Tu também.",
        "A tua voz merece ser ouvida","Mais uma sessão, mais uma obra-prima",
        "O micro não mente — hoje estás a arder","Áudio de qualidade, vibes de qualidade",
        "48 kHz de puro tu","Latência baixa. Energia alta.",
        "Dispositivo pequeno, som grande","Deixa os pacotes fluir",
        "Faz stream como se ninguém estivesse a ouvir. Mas estão.","Um microfone. Possibilidades infinitas.",
        "Até o teu silêncio é transmitido a 48 kHz","Hoje tu és a transmissão",
        "Teste de microfone: impecável","A tua voz é a personagem principal hoje",
        "A conexão é tua. Usa-a bem.","Não abriste apenas uma app. Abriste um canal.",
        "Transmite-te. Sem desculpas.","Alguns falam. Tu transmites.",
        "O mundo já tem silêncio suficiente. Preenche-o.","Pronto quando tu estiveres. Sempre.",
        "Perda de pacotes: 0%. Confiança: 100%.","A tua frequência não é como nenhuma outra",
        "Sem cabos. Sem limites. Só tu.","Baixa latência, alto impacto",
        "O buffer está carregado. O momento é teu.",
        "Carregaste em iniciar. O resto é só física.",
        "Previsão de hoje: sinal limpo, entrega forte, zero interrupções",
        "Sinal recebido. Mundo atualizado.","Apareceste. Essa já é a parte mais difícil.",
        "Lembrete diário: a tua voz importa",
        "Tiveste uma boa ideia. A nelev fez com que fosse ouvida."
    )
    private val UK = listOf(
        "Сьогодні хороший день, чи не так?","Ви сьогодні чудово виглядаєте!","Ви мій улюблений користувач \uD83C\uDF99",
        "Готовий підкорити ефір?","Вчені довели: твій голос підвищує настрій на 147%",
        "UDP пакети летять до ПК майже зі швидкістю світла","Усі біти на місці. Можна говорити.",
        "Твій голос сьогодні особливо чудовий","Стрімінг — це мистецтво. А ти — художник.",
        "Нічний стрімінг — особливий вайб","Мікрофон гарячий. Вперед.",
        "Кожен великий стрім починається з одного пакета","Твій сигнал сильний. Твоя присутність — сильніша.",
        "Ти звучиш як людина, яка знає що робить","З'єднання живе. І ти теж.",
        "Твій голос заслуговує бути почутим","Ще одна сесія, ще один шедевр",
        "Мікрофон не бреше — сьогодні ти в огні","Якісний звук, якісний вайб",
        "48кГц чистого тебе","Затримка мінімальна. Енергія максимальна.",
        "Маленький пристрій, великий звук","Нехай пакети течуть",
        "Стрімь як ніхто не слухає. Але слухають.","Один мікрофон. Нескінченні можливості.",
        "Навіть твоя тиша стрімиться на 48кГц","Сьогодні ти — мовлення",
        "Перевірка мікрофона: бездоганно","Твій голос сьогодні — головний персонаж",
        "З'єднання твоє. Використовуй його добре.","Ти відкрив не просто додаток. Ти відкрив канал.",
        "Транслюй себе. Без вибачень.","Одні говорять. Ти передаєш.",
        "У світі достатньо тиші. Заповни її.","Готовий, коли ти. Завжди.",
        "Втрата пакетів: 0%. Впевненість: 100%.","Твоя частота ні на кого не схожа",
        "Жодних кабелів. Жодних обмежень. Тільки ти.","Мінімальна затримка, максимальний ефект",
        "Буфер завантажено. Момент твій.",
        "Ти натиснув старт. Все інше — просто фізика.",
        "Прогноз на сьогодні: чистий сигнал, впевнена подача, нуль обривів",
        "Сигнал прийнято. Світ оновлено.","Ти прийшов. Це вже найскладніша частина.",
        "Щоденне нагадування: твій голос важливий",
        "У тебе була хороша ідея. nelev подбав, щоб її почули."
    )

    private val shuffledEN by lazy { EN.shuffled().toMutableList() }
    private val shuffledRU by lazy { RU.shuffled().toMutableList() }
    private val shuffledES by lazy { ES.shuffled().toMutableList() }
    private val shuffledZH by lazy { ZH.shuffled().toMutableList() }
    private val shuffledJA by lazy { JA.shuffled().toMutableList() }
    private val shuffledKO by lazy { KO.shuffled().toMutableList() }
    private val shuffledPT by lazy { PT.shuffled().toMutableList() }
    private val shuffledUK by lazy { UK.shuffled().toMutableList() }
    private var iEN = 0; private var iRU = 0; private var iES = 0
    private var iZH = 0; private var iJA = 0; private var iKO = 0
    private var iPT = 0; private var iUK = 0

    fun next(lang: String): String {
        val (list, idx) = when (lang) {
            "en" -> shuffledEN to iEN; "ru" -> shuffledRU to iRU; "es" -> shuffledES to iES
            "zh" -> shuffledZH to iZH; "ja" -> shuffledJA to iJA; "ko" -> shuffledKO to iKO
            "pt" -> shuffledPT to iPT; "uk" -> shuffledUK to iUK
            else -> shuffledEN to iEN
        }
        var i = idx
        if (i >= list.size) i = 0
        val phrase = list[i]
        i++
        when (lang) {
            "en" -> iEN = i; "ru" -> iRU = i; "es" -> iES = i
            "zh" -> iZH = i; "ja" -> iJA = i; "ko" -> iKO = i
            "pt" -> iPT = i; "uk" -> iUK = i
        }
        return phrase
    }
}

