package com.dichgame.app

import java.util.Locale
import kotlin.math.abs

/** Khung bao của một khối chữ trên màn hình (pixel). Thuần Kotlin để kiểm thử được ngoài Android. */
class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w: Int get() = (r - l).coerceAtLeast(1)
    val h: Int get() = (b - t).coerceAtLeast(1)
    val cx: Int get() = (l + r) / 2
    val cy: Int get() = (t + b) / 2
}

/** Một khối chữ đọc được từ màn hình. [conf] là độ tin cậy 0..1 nếu bộ đọc chữ có báo. */
class OcrLine(val text: String, val box: Box?, val conf: Float = 1f)

/**
 * Một câu thoại tách riêng. [speaker] chỉ có khi chắc chắn đó là tên người nói (nằm trong thẻ nhân vật, có dấu hai chấm,
 * hoặc đã thấy làm nhãn trên các câu thoại khác nhau). [nameLike] báo khối chữ trông như một cái tên nhưng chưa đủ căn cứ:
 * nó vẫn được giữ lại làm câu riêng, không bị bỏ và không bị ghép vào câu thoại bên cạnh.
 */
class Utterance(val speaker: String?, val text: String, val nameLike: Boolean = false)

/**
 * Sổ tên người nói trong một phiên. Một cái tên lạ chỉ được tin là tên nhân vật khi nằm trong thẻ nhân vật
 * hoặc đã đứng trên ít nhất hai câu thoại khác nhau; như vậy một từ như "Hello" đứng phía trên câu khác không bị nhận nhầm.
 */
class SpeakerBook(cast: List<String> = emptyList()) {
    private val castKeys: Set<String> = cast.flatMap { it.split('/') }.map { key(it) }.filter { it.length >= 2 }.toSet()
    private val seen = HashMap<String, HashSet<String>>()

    fun isCast(name: String): Boolean = key(name) in castKeys

    fun observe(name: String, dialogue: String) {
        val s = seen.getOrPut(key(name)) { HashSet() }
        if (s.size < 8) s.add(OcrGuard.key(dialogue).take(60))
    }

    fun confirmed(name: String): Boolean = isCast(name) || (seen[key(name)]?.size ?: 0) >= 2

    fun clear() = seen.clear()

    companion object {
        fun key(s: String): String = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }
}

/**
 * Tách các khối chữ trong một khung hình thành các câu thoại riêng và tìm tên người nói (đứng trên hoặc bên trái câu thoại).
 * Không bao giờ bỏ chữ: cái gì không chắc là tên thì vẫn là nội dung. Không ghép hai câu của hai người khác nhau.
 */
object DialogueSplitter {
    private class U(var box: Box?, var lines: MutableList<String>) {
        var speaker: String? = null
        var nameOnly = false
        var consumed = false
        val lineH: Int get() = box?.let { (it.h / lines.size.coerceAtLeast(1)).coerceAtLeast(1) } ?: 1
    }

    private val COLON = Regex("^([\\p{L}][\\p{L}'’.\\- ]{0,22}?)\\s*[:：]\\s*(\\S.*)$")
    private val CJK_BRACKET = Regex("^[【\\[]([^】\\]]{1,10})[】\\]]\\s*(\\S.*)$")
    private val END_PUNCT = "?.!…。？！"
    private val CONNECTORS = setOf("of", "the", "de", "von", "van", "la", "le", "el", "di", "da", "du", "del", "al", "bin", "ibn")

    private val STOP = setOf(
        "yes", "no", "okay", "ok", "hello", "hi", "hey", "thanks", "thank", "please", "sorry", "wait", "stop", "go", "run", "help",
        "look", "come", "fine", "well", "right", "sure", "welcome", "goodbye", "bye", "what", "why", "how", "who", "where", "when",
        "huh", "oh", "ah", "wow", "hmm", "uh", "um", "yeah", "yep", "nope", "maybe", "now", "here", "there", "good", "great", "nice",
        "damn", "hell", "quick", "careful", "move", "fire", "attack", "listen", "again", "never", "always", "alright", "ready", "thank you",
        "oui", "non", "merci", "salut", "bonjour", "allez", "attends", "ja", "nein", "danke", "hallo", "bitte", "si", "sí", "gracias",
        "hola", "vamos", "sim", "não", "obrigado", "olá", "sì", "grazie", "ciao", "andiamo", "da", "нет", "да", "привет", "спасибо",
        "はい", "いいえ", "ええ", "うん", "ああ", "えっ", "はっ", "ねえ", "おい", "ありがとう", "待って", "行くぞ", "你好", "好的", "是的", "不", "好", "对", "嗯", "哦", "谢谢",
        "네", "아니요", "응", "그래", "안녕", "고마워", "잠깐",
    )

    /** Chuỗi trông như tên người (viết hoa chữ đầu mỗi từ, hoặc toàn chữ hoa; ngắn; không dấu câu cuối câu; không phải từ cảm thán). */
    internal fun nameShape(raw: String, cjk: Boolean): Boolean {
        val s = raw.trim().trimEnd(':', '：').trim()
        if (s.isEmpty() || s.any { it.isDigit() }) return false
        if (s.last() in END_PUNCT) return false
        if (s.lowercase(Locale.ROOT) in STOP) return false
        val letters = s.count { it.isLetter() }
        if (letters < 2) return false
        val scriptsCjk = s.any { LangDetect.scriptOf(it).let { c -> c == 'H' || c == 'K' || c == 'G' } }
        if (scriptsCjk || cjk) {
            if (s.length > 8 || s.any { it in ",.!?;()[]、。！？，" } || s.contains(' ')) return false
            return true
        }
        if (s.length > 24 || s.any { it in ",;!?()[]\"“”" }) return false
        val words = s.split(' ').filter { it.isNotEmpty() }
        if (words.size > 3) return false
        if (s == s.uppercase(Locale.ROOT)) return letters >= 3
        return words.withIndex().all { (i, w) ->
            w[0].isUpperCase() || (i > 0 && w.lowercase(Locale.ROOT) in CONNECTORS)
        }
    }

    private fun allCaps(s: String): Boolean {
        val t = s.trim().trimEnd(':', '：')
        return t.count { it.isLetter() } >= 3 && t == t.uppercase(Locale.ROOT) && t.any { it.isLetter() && LangDetect.scriptOf(it) == 'L' }
    }

    private fun cleanName(s: String): String = s.trim().trimEnd(':', '：').trim()

    /** Nhãn tên [a] nằm ngay phía trên hoặc bên trái khối thoại [b]. */
    private fun labels(a: Box?, b: Box?): Boolean {
        if (a == null || b == null) return true
        val below = b.t >= a.b - a.h * 0.3 && b.t - a.b <= a.h * 3
        val leftAligned = abs(a.l - b.l) <= b.w * 0.35 || abs(a.cx - b.cx) <= b.w * 0.25
        if (below && leftAligned) return true
        val sameRow = abs(a.cy - b.cy) <= maxOf(a.h, b.h) * 0.7 && b.l >= a.r - a.w * 0.1 && b.l - a.r <= a.h * 3
        return sameRow
    }

    fun split(blocks: List<OcrLine>, cjk: Boolean, book: SpeakerBook = SpeakerBook()): List<Utterance> {
        val units = ArrayList<U>()
        val sorted = if (blocks.all { it.box != null }) blocks.sortedWith(compareBy({ it.box!!.t }, { it.box!!.l })) else blocks
        for (b in sorted) {
            val lines = OcrGuard.clean(b.text).lines().filter { it.isNotEmpty() }.toMutableList()
            if (lines.isEmpty()) continue
            units.add(U(b.box, lines))
        }

        // nhãn tên ngay trong khối: "Tên: lời thoại", "【Tên】lời thoại", hoặc dòng đầu là tên
        for (u in units) {
            val first = u.lines[0]
            val m = COLON.find(first)
            if (m != null && m.groupValues[1].split(' ').size <= 3) {
                val name = cleanName(m.groupValues[1])
                if (letterLike(m.groupValues[2])) {
                    if (book.confirmed(name)) {
                        u.speaker = name
                        u.lines[0] = m.groupValues[2].trim()
                        continue
                    }
                    if (nameShape(name, cjk)) book.observe(name, m.groupValues[2])
                }
            }
            val br = CJK_BRACKET.find(first)
            if (br != null && (cjk || book.isCast(br.groupValues[1]))) {
                val name = br.groupValues[1].trim()
                if (name.isNotEmpty() && nameShape(name, true)) {
                    u.speaker = name
                    u.lines[0] = br.groupValues[2].trim()
                    continue
                }
            }
            if (u.lines.size >= 2 && nameShape(first, cjk)) {
                val name = cleanName(first)
                val colon = first.trimEnd().let { it.endsWith(':') || it.endsWith('：') }
                if (colon || book.confirmed(name)) {
                    u.speaker = name
                    u.lines.removeAt(0)
                    continue
                }
            }
            if (u.lines.size == 1 && nameShape(first, cjk)) u.nameOnly = true
        }

        // ghép nhãn tên đứng riêng với khối thoại ngay sau nó
        for (i in units.indices) {
            val n = units[i]
            if (!n.nameOnly || n.consumed) continue
            val d = units.getOrNull(i + 1) ?: continue
            if (d.nameOnly || d.consumed || d.speaker != null) continue
            if (!labels(n.box, d.box)) continue
            val name = cleanName(n.lines[0])
            val text = join(d.lines, cjk)
            val colon = n.lines[0].trimEnd().let { it.endsWith(':') || it.endsWith('：') }
            val strongShape = allCaps(name) && (n.box == null || d.box == null || n.box!!.w <= d.box!!.w * 0.75)
            book.observe(name, text)
            if (book.confirmed(name) || colon || strongShape) {
                d.speaker = name
                n.consumed = true
            }
        }

        // ghép các khối thoại liền sát nhau (cùng người nói, hoặc cùng một khung chữ bị tách khối); không ghép khác người nói
        val merged = ArrayList<U>()
        for (u in units) {
            if (u.consumed) continue
            val prev = merged.lastOrNull()
            if (prev != null && !prev.nameOnly && !u.nameOnly && canJoin(prev, u)) {
                prev.lines.addAll(u.lines)
                prev.box = union(prev.box, u.box)
            } else merged.add(u)
        }

        return merged.map { u -> Utterance(u.speaker, join(u.lines, cjk), nameLike = u.nameOnly) }
            .filter { it.text.isNotBlank() }
    }

    private fun letterLike(s: String) = s.any { it.isLetter() }

    private fun canJoin(a: U, b: U): Boolean {
        if (a.speaker != b.speaker) {
            // chỉ khối sau không nhãn mà khối trước có nhãn: cùng một khung chữ bị tách thì vẫn là lời của người đó
            if (!(b.speaker == null && a.speaker != null)) return false
        }
        val x = a.box ?: return false
        val y = b.box ?: return false
        val gap = y.t - x.b
        val overlap = minOf(x.r, y.r) - maxOf(x.l, y.l)
        val h = minOf(a.lineH, b.lineH)
        return gap <= h * 0.6 && gap >= -h && overlap >= minOf(x.w, y.w) * 0.4
    }

    private fun union(a: Box?, b: Box?): Box? =
        if (a == null || b == null) null else Box(minOf(a.l, b.l), minOf(a.t, b.t), maxOf(a.r, b.r), maxOf(a.b, b.b))

    /** Nối các dòng: chữ Latin cách nhau một khoảng trắng (nối liền khi bị ngắt từ bằng dấu gạch nối), CJK nối liền. */
    internal fun join(lines: List<String>, cjk: Boolean): String {
        val sb = StringBuilder()
        for (l0 in lines) {
            val l = l0.trim()
            if (l.isEmpty()) continue
            if (sb.isEmpty()) { sb.append(l); continue }
            val cjkJoin = cjk || LangDetect.scriptOf(sb.last()).let { it == 'H' || it == 'K' || it == 'T' } &&
                LangDetect.scriptOf(l[0]).let { it == 'H' || it == 'K' || it == 'T' }
            when {
                cjkJoin -> sb.append(l)
                sb.last() == '-' && sb.length >= 2 && (sb[sb.length - 2]).isLetter() && l[0].isLowerCase() -> {
                    sb.setLength(sb.length - 1); sb.append(l)
                }
                else -> sb.append(' ').append(l)
            }
        }
        return sb.toString()
    }

    /** Khóa nhận biết "khung hình này vẫn là các câu thoại đó" khi so sánh qua nhiều lần quét. */
    fun signature(list: List<Utterance>): String = list.joinToString("|") { (it.speaker ?: "") + ":" + OcrGuard.key(it.text) }
}
