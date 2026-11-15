package com.dichgame.app

import java.util.Locale
import kotlin.math.sqrt

/**
 * Lọc kết quả nhận dạng giọng nói trước khi coi là lời thoại. Mô hình Whisper khi gặp nhạc, tiếng súng, tiếng ồn hay khoảng lặng
 * hay "nghe ra" những câu có sẵn trong dữ liệu huấn luyện (cảm ơn đã xem, phụ đề bởi..., nhãn [Music]) hoặc lặp một câu vô tận.
 * Những thứ đó không được đưa sang bộ dịch. Không phụ thuộc Android.
 */
object AsrGuard {
    class Verdict(val ok: Boolean, val text: String, val reason: String = "", val ambiguous: Boolean = false)

    /** Âm lượng RMS (0..1) của một đoạn PCM 16-bit. */
    fun rms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) { val n = s / 32768.0; sum += n * n }
        return sqrt(sum / samples.size).toFloat()
    }

    /** Câu Whisper hay bịa ra: gần như không bao giờ là lời thoại game, bỏ ở mọi mức âm lượng. */
    private val ALWAYS = listOf(
        "thanksforwatching", "thankyouforwatching", "pleasesubscribe", "subscribetomychannel", "subtitlesby", "subtitledby",
        "amaraorg", "captionsby", "transcribedby", "dimatorzok", "likeandsubscribe", "seeyouinthenextvideo", "seeyounexttime",
        "字幕", "ご視聴ありがとうございました", "ご視聴ありがとう", "チャンネル登録", "最後までご視聴", "请不吝点赞", "謝謝觀看", "感谢观看", "订阅",
        "시청해주셔서", "구독과좋아요", "구독", "спасибозапросмотр", "продолжениеследует", "субтитры", "редакторсубтитров",
        "ขอบคุณที่รับชม", "ขอบคุณที่ติดตาม", "mercidavoirregarde", "dankefürszuschauen", "graciasporverlo", "obrigadoporassistir",
    )

    /** Câu ngắn hay bịa nhưng cũng là lời thoại thật: chỉ bỏ khi đoạn âm thanh gần như im lặng. */
    private val QUIET_ONLY = setOf(
        "thankyou", "thanks", "bye", "byebye", "you", "yeah", "okay", "so", "oh", "uh", "um", "hmm", "mm", "mmm", "ah", "huh", "the", "i",
        "gracias", "merci", "danke", "obrigado", "grazie", "ありがとう", "ありがとうございました", "はい", "ええ", "うん", "谢谢", "謝謝", "你好", "감사합니다", "네", "спасибо", "да",
    )

    private val TAG = Regex("\\[[^\\]]{0,40}]|\\([^)]{0,40}\\)|【[^】]{0,40}】|（[^）]{0,40}）|\\*[^*]{1,40}\\*|[♪♫♬]+")
    private val SENTENCE = Regex("(?<=[.!?。！？…])\\s+")

    /** Âm lượng dưới mức này coi như gần im lặng. */
    const val QUIET_RMS = 0.03f

    fun check(raw: String, level: Float = 1f, expectedLang: String? = null): Verdict {
        var t = raw.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return Verdict(false, t, "trống")
        // nhãn âm thanh và ký hiệu nhạc: [Music], (gunfire), *sighs*, ♪
        val untagged = TAG.replace(t, " ").replace(Regex("\\s+"), " ").trim()
        if (untagged != t) {
            if (untagged.none { it.isLetterOrDigit() }) return Verdict(false, t, "nhãn âm thanh")
            t = untagged
        }
        t = t.trimStart('-', '–', '—', ' ').trim()
        val letters = t.count { it.isLetter() }
        if (letters == 0) return Verdict(false, t, "không có chữ")
        if (letters == 1 && LangDetect.scriptOf(t.first { it.isLetter() }) == 'L') return Verdict(false, t, "một chữ cái")

        val key = OcrGuard.key(t)
        if (ALWAYS.any { key.contains(it) }) return Verdict(false, t, "câu mô hình hay bịa")

        val loop = collapseLoops(t)
        if (loop == null) return Verdict(false, t, "lặp vô tận")
        t = loop

        val k2 = OcrGuard.key(t)
        var ambiguous = false
        if (k2 in QUIET_ONLY) {
            if (level < QUIET_RMS) return Verdict(false, t, "câu hay bịa khi im lặng")
            ambiguous = true
        }

        if (!expectedLang.isNullOrBlank() && expectedLang != "auto" && !LangDetect.scriptFits(t, expectedLang)) {
            return Verdict(false, t, "sai chữ viết")
        }
        return Verdict(true, t, ambiguous = ambiguous)
    }

    /**
     * Gộp một câu lặp liên tiếp từ 3 lần trở lên về một lần; nếu cả đoạn gần như chỉ là vòng lặp thì trả null (bỏ).
     * Cũng bắt trường hợp một từ hoặc một chữ bị lặp liên tục.
     */
    internal fun collapseLoops(text: String): String? {
        val parts = text.split(SENTENCE).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var i = 0
        var collapsed = false
        while (i < parts.size) {
            var j = i
            val k = OcrGuard.key(parts[i])
            while (j + 1 < parts.size && OcrGuard.key(parts[j + 1]) == k && k.isNotEmpty()) j++
            val n = j - i + 1
            if (n >= 3) { out.add(parts[i]); collapsed = true } else for (x in i..j) out.add(parts[x])
            i = j + 1
        }
        val joined = out.joinToString(" ")
        val words = joined.lowercase(Locale.ROOT).split(Regex("[\\s,.!?;:]+")).filter { it.isNotEmpty() }
        if (words.size >= 4) {
            var run = 1
            for (x in 1 until words.size) {
                if (words[x] == words[x - 1]) { run++; if (run >= 4) return null } else run = 1
            }
            if (words.size >= 9 && words.toSet().size * 100 / words.size < 25) return null
        }
        // chữ không có khoảng trắng (CJK, Thái): cùng một chữ lặp từ 7 lần trở lên liên tiếp
        if (Regex("(.)\\1{6,}").containsMatchIn(joined.filter { !it.isWhitespace() })) return null
        return if (collapsed) joined else text
    }
}

/**
 * Theo dõi game có đổi ngôn ngữ không: chỉ báo "đã đổi" khi ba lần đoán gần nhất (đủ tin cậy) cùng chỉ về một ngôn ngữ
 * khác ngôn ngữ hiện tại. Một lần đoán lạ lẻ loi (ví dụ câu có tên riêng tiếng Anh trong game Nhật) không làm đổi.
 */
class LangSwitchTracker(var current: String, private val needed: Int = 3) {
    private val recent = ArrayDeque<String>()

    /** Trả về mã ngôn ngữ mới nếu vừa xác nhận game đổi ngôn ngữ, ngược lại null. */
    fun observe(guess: LangDetect.Guess?): String? {
        if (guess == null) return null
        val minConf = if (guess.script == 'L') 0.3f else 0.6f
        if (guess.confidence < minConf) return null
        // chữ Hán trần cũng gặp trong tiếng Nhật; Cyrillic không có chữ riêng của Ukraina cũng là tiếng Nga: không đủ căn cứ để đổi
        val ambiguous = (current == "ja" && guess.lang == "zh") || (current == "uk" && guess.lang == "ru")
        if (guess.lang == current || ambiguous) {
            recent.clear()
            return null
        }
        recent.addLast(guess.lang)
        while (recent.size > needed + 1) recent.removeFirst()
        val last = recent.takeLast(needed)
        if (last.size >= needed && last.all { it == guess.lang }) {
            current = guess.lang
            recent.clear()
            return guess.lang
        }
        return null
    }
}
