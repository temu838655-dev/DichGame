package com.dichgame.app

import java.util.Locale

/**
 * Đoán ngôn ngữ của một đoạn chữ (phụ đề đọc được từ màn hình hoặc câu nhận dạng từ giọng nói) để:
 * phát hiện game đổi ngôn ngữ, và loại bỏ kết quả nhận dạng sai chữ viết. Không phụ thuộc Android.
 *
 * Chữ viết khác Latin (Nhật, Hàn, Trung, Thái, Nga, Ả Rập, Hy Lạp, Do Thái, Hindi) nhận ra chắc chắn.
 * Chữ Latin chia nhỏ theo từ phổ biến và dấu đặc trưng; câu quá ngắn thì không đoán (trả null) thay vì đoán bừa.
 */
object LangDetect {
    class Guess(val lang: String, val confidence: Float, val script: Char)

    /** Mã chữ viết: L Latin, K kana, H Hán, G Hangul, T Thái, C Cyrillic, A Ả Rập, E Hy Lạp, W Do Thái, D Devanagari. */
    fun scriptOf(c: Char): Char = when {
        c in '぀'..'ヿ' || c in 'ㇰ'..'ㇿ' || c in 'ｦ'..'ﾟ' -> 'K'
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' -> 'H'
        c in '가'..'힯' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏' -> 'G'
        c in '฀'..'๿' -> 'T'
        c in 'Ѐ'..'ԯ' -> 'C'
        c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' -> 'A'
        c in 'Ͱ'..'Ͽ' || c in 'ἀ'..'῿' -> 'E'
        c in '֐'..'׿' -> 'W'
        c in 'ऀ'..'ॿ' -> 'D'
        c.isLetter() && (c.code < 0x250 || c in 'Ḁ'..'ỿ') -> 'L'
        else -> ' '
    }

    fun scriptCounts(text: String): Map<Char, Int> {
        val m = HashMap<Char, Int>()
        for (c in text) {
            val s = scriptOf(c)
            if (s != ' ') m[s] = (m[s] ?: 0) + 1
        }
        return m
    }

    /** Chữ viết chiếm nhiều nhất và tỉ lệ của nó (0..1), hoặc null nếu không có chữ cái nào. */
    fun dominantScript(text: String): Pair<Char, Float>? {
        val m = scriptCounts(text)
        val total = m.values.sum()
        if (total == 0) return null
        val (s, n) = m.maxByOrNull { it.value }!!
        return s to n.toFloat() / total
    }

    private val STOP: Map<String, Set<String>> = mapOf(
        "en" to "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were am been has had did does said".split(' ').toSet(),
        "fr" to "le la les un une des du de et est en que qui dans pour pas sur au aux avec ce cette ces il elle ils elles nous vous je tu on ne se son sa ses mon ma mes ton ta tes plus mais ou où comme tout être avoir fait faire dit très bien oui non merci suis es sont êtes sommes ai as avons avez ont".split(' ').toSet(),
        "de" to "der die das den dem des ein eine einen einem einer und ist nicht ich du er sie es wir ihr mit für auf von zu im in an bei nach aus auch noch nur wie was wer wo wenn aber oder dass dann schon sehr ja nein bin bist sind war habe hast hat haben mein dein sein kein mich dich sich uns".split(' ').toSet(),
        "es" to "el la los las un una unos unas de del y es en que no por con para se su sus lo al me te mi tu yo él ella nosotros pero como más muy si sí ya hay está están soy eres somos son fue ser estar tengo tiene todo esto eso aquí así cuando donde porque quién".split(' ').toSet(),
        "pt" to "o a os as um uma uns umas de do da dos das e é em que não por com para se seu sua seus suas no na nos nas ao me te meu minha eu ele ela nós você vocês mas como mais muito sim já tem está estão sou são foi ser estar tenho isso isto aqui assim quando onde porque quem obrigado".split(' ').toSet(),
        "it" to "il lo la i gli le un una uno di del della dei delle e è in che non per con si su al alla mi ti ci vi io tu lui lei noi voi loro ma come più molto sì già ho hai ha abbiamo hanno sono sei siamo siete questo questa quello qui così quando dove perché chi grazie".split(' ').toSet(),
        "id" to "yang dan di ke dari ini itu dengan untuk tidak ada akan pada juga saya aku kamu dia kami kita mereka atau tetapi karena sudah belum bisa harus apa siapa mana kapan bagaimana dalam oleh adalah sebuah satu dua banyak sangat terima kasih ya".split(' ').toSet(),
        "nl" to "de het een en van in is dat op te zijn met voor niet aan ik je jij hij zij we wij jullie ze maar als dan nog wel ook er naar bij uit dit deze wat wie waar wanneer hoe heb hebt heeft hebben ben bent ja nee".split(' ').toSet(),
        "tr" to "ve bir bu da de için ile ne o ben sen biz siz onlar değil çok daha en gibi var yok evet hayır ama veya şey mi mı mu mü ki kadar sonra önce nasıl neden nerede kim".split(' ').toSet(),
        "pl" to "i w z na do nie to że się jest jak ale co ja ty on ona my wy oni po za od dla przez tak już tylko czy bardzo jestem jesteś są był była było mam masz ma tego tym tu tam gdzie kiedy dlaczego".split(' ').toSet(),
        "sv" to "och i att det som en är på av för med inte jag du han hon vi ni de den ett har har var men om så kan ska till från mycket ja nej".split(' ').toSet(),
    )

    private val CUE: Map<String, String> = mapOf(
        "vi" to "ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ",
        "es" to "ñ¿¡",
        "pt" to "ãõ",
        "fr" to "œùûëïÿ",
        "de" to "ß",
        "tr" to "şğıİ",
        "pl" to "łąężźćńś",
        "ro" to "ățâî",
        "hu" to "őű",
        "sv" to "å",
    )

    private val WORD = Regex("[\\p{L}']+")

    /** Đoán ngôn ngữ. Null khi chưa đủ căn cứ (câu quá ngắn, toàn số, hoặc nhiều ngôn ngữ ngang nhau). */
    fun guess(text: String): Guess? {
        val dom = dominantScript(text) ?: return null
        val (script, share) = dom
        val counts = scriptCounts(text)
        val letters = counts.values.sum()
        val conf = share.coerceIn(0f, 1f)
        return when (script) {
            'K' -> Guess("ja", conf, script)
            'H' -> if ((counts['K'] ?: 0) > 0) Guess("ja", conf, 'K') else Guess("zh", conf, script)
            'G' -> Guess("ko", conf, script)
            'T' -> Guess("th", conf, script)
            'A' -> Guess("ar", conf, script)
            'E' -> Guess("el", conf, script)
            'W' -> Guess("he", conf, script)
            'D' -> Guess("hi", conf, script)
            'C' -> Guess(if (text.any { it in "іїєґІЇЄҐ" }) "uk" else "ru", conf, script)
            else -> if (letters < 2) null else latin(text, conf)
        }
    }

    private fun latin(text: String, conf: Float): Guess? {
        val low = text.lowercase(Locale.ROOT)
        val words = WORD.findAll(low).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
        if (words.isEmpty()) return null
        val vi = low.count { it in CUE.getValue("vi") }
        if (vi >= 2 || (vi >= 1 && words.size <= 3)) return Guess("vi", conf, 'L')
        val score = HashMap<String, Float>()
        for ((lang, stop) in STOP) score[lang] = words.count { it in stop }.toFloat()
        for ((lang, cue) in CUE) {
            if (lang == "vi") continue
            val hits = low.count { it in cue }
            if (hits == 0) continue
            val key = if (lang == "ro" || lang == "hu") continue else lang
            score[key] = (score[key] ?: 0f) + 1.5f * hits
        }
        // ä, ö, ü: Đức hoặc Thổ/Thuỵ Điển; chỉ cộng cho Đức khi có từ Đức đi kèm
        if (low.any { it in "äöü" } && (score["de"] ?: 0f) >= 1f) score["de"] = score.getValue("de") + 1.5f
        // à, è, é, ê, ç: Pháp (cũng có ở Ý, Bồ); cộng nhẹ khi có từ Pháp đi kèm
        if (low.any { it in "àèéêç" } && (score["fr"] ?: 0f) >= 1f) score["fr"] = score.getValue("fr") + 1f
        val ranked = score.entries.sortedByDescending { it.value }
        val best = ranked[0]
        val second = ranked.getOrNull(1)?.value ?: 0f
        if (best.value < 1f) return null
        // câu 1 từ chỉ tin khi có dấu đặc trưng; câu dài cần chênh lệch rõ
        if (words.size == 1 && best.value < 1.5f) return null
        if (best.value - second < 1f && words.size > 2) return null
        if (best.value == second) return null
        val c = (best.value / (words.size + 1f)).coerceIn(0.2f, 0.95f) * conf
        return Guess(best.key, c, 'L')
    }

    /** Chữ viết mong đợi của một ngôn ngữ. */
    fun scriptOfLang(lang: String): Char = when (lang) {
        "ja" -> 'K'
        "zh" -> 'H'
        "ko" -> 'G'
        "th" -> 'T'
        "ru", "uk" -> 'C'
        "ar" -> 'A'
        "el" -> 'E'
        "he" -> 'W'
        "hi" -> 'D'
        else -> 'L'
    }

    /**
     * Văn bản có hợp với ngôn ngữ [expected] không? Chỉ xét chữ viết (Latin với Latin, Nhật có Hán lẫn kana...).
     * Dùng để loại kết quả nhận dạng giọng nói trả về chữ viết lạ (hay gặp khi chỉ có nhạc hoặc tiếng ồn).
     */
    fun scriptFits(text: String, expected: String): Boolean {
        val m = scriptCounts(text)
        val total = m.values.sum()
        if (total == 0) return true
        val want = scriptOfLang(expected)
        val ok = when (want) {
            'K' -> (m['K'] ?: 0) + (m['H'] ?: 0)
            'H' -> (m['H'] ?: 0) + (m['K'] ?: 0)
            else -> m[want] ?: 0
        }
        return ok.toFloat() / total >= 0.6f
    }
}
