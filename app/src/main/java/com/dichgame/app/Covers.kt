package com.dichgame.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Ảnh bìa game cho Kho game. Không đóng gói sẵn ảnh nào trong app: khi có mạng, app tìm ảnh bìa **dọc** theo tên game
 * (không cần khóa): trước hết Steam, nếu game không có trên Steam hoặc Steam không có ảnh dọc thì thử ảnh bìa hộp game ở trang
 * Wikipedia của game. Chỉ khi không tìm được ảnh dọc nào mới dùng ảnh ngang của Steam (đặt giữa nền mờ), còn không thì dùng
 * ảnh vẽ sẵn (màu theo tên game). Ảnh tải về lưu trên máy (thư mục đệm), lần sau dùng lại không cần mạng.
 * Ảnh bìa thuộc về nhà phát hành game. Tắt được trong Kho game.
 */
object Covers {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)
    private val mem = object : LruCache<String, Bitmap>(min(Runtime.getRuntime().maxMemory() / 12, 48L * 1024 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    /** Các nơi đang chờ cùng một ảnh (chỉ dùng trên luồng chính), để không tải trùng. */
    private val waiting = HashMap<String, MutableList<(Bitmap?) -> Unit>>()
    private const val NONE_MS = 14L * 24 * 3600 * 1000
    /** Tên file ảnh bìa dọc của Steam: bản thường, rồi bản 2x cho game chỉ có bản này. */
    private val PORTRAIT_FILES = listOf("library_600x900.jpg", "library_600x900_2x.jpg")
    private val BASES = listOf(
        "https://cdn.cloudflare.steamstatic.com/steam/apps/",
        "https://cdn.akamai.steamstatic.com/steam/apps/"
    )

    private fun key(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48) + "-" + Integer.toHexString(name.hashCode())

    /** Gọi lại trên luồng chính. null nghĩa là chưa có ảnh bìa (dùng ảnh vẽ sẵn). */
    fun get(ctx: Context, name: String, cb: (Bitmap?) -> Unit) {
        val k = key(name)
        mem.get(k)?.let { cb(it); return }
        val list = waiting[k]
        if (list != null) { list.add(cb); return }
        waiting[k] = mutableListOf(cb)
        val app = ctx.applicationContext
        io.execute {
            val bmp = try { load(app, name, k) } catch (_: Throwable) { null }
            main.post {
                if (bmp != null) mem.put(k, bmp)
                waiting.remove(k)?.forEach { it(bmp) }
            }
        }
    }

    private fun load(ctx: Context, name: String, k: String): Bitmap? {
        // bản cũ lưu cả ảnh ngang và dấu "không có": xoá đi để tìm lại ảnh dọc
        File(ctx.cacheDir, "covers").takeIf { it.exists() }?.deleteRecursively()
        val dir = File(ctx.cacheDir, "covers2").apply { mkdirs() }
        val img = File(dir, "$k.jpg")
        if (img.exists()) return decode(img)
        val none = File(dir, "$k.none")
        if (none.exists() && System.currentTimeMillis() - none.lastModified() < NONE_MS) return null
        if (!Prefs(ctx).covers) return null
        // lỗi mạng ném IOException: không đánh dấu "không có", lần sau thử lại
        var bytes: ByteArray? = null
        var wide: ByteArray? = null
        // game đã biết là không có trên Steam thì khỏi hỏi Steam
        val ps = Catalog.platformsOf(name)
        if (ps.isEmpty() || Plat.STEAM in ps) {
            val id = steamId(name)
            if (id != null) {
                for (file in PORTRAIT_FILES) {
                    for (b in BASES) {
                        bytes = fetch("$b$id/$file")
                        if (bytes != null) break
                    }
                    if (bytes != null) break
                }
                if (bytes == null) wide = fetch("${BASES[0]}$id/header.jpg")
            }
        }
        if (bytes == null || bytes.size < 1024) bytes = wikiCover(name)
        if (bytes == null || bytes.size < 1024) bytes = wide     // không có ảnh dọc nào: dùng ảnh ngang của Steam
        if (bytes == null || bytes.size < 1024) { none.writeText("x"); return null }
        val tmp = File(dir, "$k.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(img)
        return decode(img)
    }

    /**
     * Ảnh bìa hộp game ở trang Wikipedia của game (ảnh đầu bài). Chỉ nhận khi tên bài khớp tên game và ảnh là ảnh dọc,
     * để khỏi nhầm sang phim, nhạc, game khác cùng tên hay ảnh logo ngang.
     */
    private fun wikiCover(name: String): ByteArray? {
        val want = CoverMatch.norm(name)
        if (want.isEmpty()) return null
        val q = URLEncoder.encode("$name video game", "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$q&gsrlimit=6&gsrnamespace=0" +
            "&prop=pageimages&piprop=thumbnail&pilicense=any&pithumbsize=600&format=json&formatversion=2"
        val body = fetch(url) ?: return null
        val pages = JSONObject(String(body, Charsets.UTF_8)).optJSONObject("query")?.optJSONArray("pages") ?: return null
        val cand = ArrayList<JSONObject>()
        for (i in 0 until pages.length()) cand.add(pages.getJSONObject(i))
        cand.sortBy { it.optInt("index", 99) }
        for (o in cand) {
            if (!CoverMatch.same(CoverMatch.norm(CoverMatch.stripGameSuffix(o.optString("title"))), want)) continue
            val th = o.optJSONObject("thumbnail") ?: continue
            val src = th.optString("source")
            val tw = th.optInt("width")
            val tl = th.optInt("height")
            // ảnh dọc: cao hơn rộng ít nhất 15% (bìa hộp game); ảnh vuông hay ngang là logo, bỏ
            if (src.isEmpty() || !src.startsWith("https://") || tw <= 0 || tl < tw * 1.15f) continue
            return fetch(src)
        }
        return null
    }

    private fun decode(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) { f.delete(); return null }
        var s = 1
        while (o.outWidth / (s * 2) >= 330) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun steamId(name: String): Int? {
        val q = URLEncoder.encode(name, "UTF-8")
        val body = fetch("https://store.steampowered.com/api/storesearch/?term=$q&cc=us&l=english") ?: return null
        val items = JSONObject(String(body, Charsets.UTF_8)).optJSONArray("items") ?: return null
        val want = CoverMatch.norm(name)
        if (want.isEmpty()) return null
        var loose: Int? = null
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val n = CoverMatch.norm(o.optString("name"))
            if (n == want) return o.optInt("id")
            // tên gần giống (bản Remastered, Edition...) chỉ nhận khi dài ngắn xấp xỉ nhau
            if (loose == null && CoverMatch.same(n, want)) loose = o.optInt("id")
        }
        return loose
    }

    /** Trả về nội dung, hoặc null nếu máy chủ nói không có (404/403). Lỗi mạng ném IOException. */
    private fun fetch(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "DichGame/1.0 (https://github.com/temu838655-dev/DichGame; app Android dịch game)")
            c.setRequestProperty("Accept-Language", "en")
            val code = c.responseCode
            if (code == 404 || code == 403) return null
            if (code != 200) throw IOException("HTTP $code")
            if (c.contentLength > 3 * 1024 * 1024) return null
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}

/** Ô ảnh bìa dọc: ảnh thật nếu có, không thì ảnh vẽ sẵn. Có nhãn "VIỆT HÓA" và viền vàng khi đang chọn. */
class CoverView(c: Context, private val title: String) : View(c) {
    var bmp: Bitmap? = null
        set(v) {
            field = v
            // mipmap: thu nhỏ ảnh 600x900 xuống ô nhỏ vẫn nét, không răng cưa
            v?.setHasMipMap(true)
            // ảnh ngang (game không có ảnh dọc trên Steam): làm nền mờ từ chính ảnh để đặt ảnh gốc nét vào giữa, không phóng to
            backdrop = if (v != null && v.width > v.height * 1.1f) Bitmap.createScaledBitmap(v, 24, 16, true) else null
            invalidate()
        }
    private var backdrop: Bitmap? = null
    var chosen = false
        set(v) { field = v; invalidate() }
    var vietHoa = false
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Vẽ ảnh với lọc song tuyến và khử nhiễu màu: thiếu cờ này ảnh bị thu phóng thô, trông vỡ và mờ. */
    private val sharp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val clip = Path()
    private val dst = RectF()
    private val tmp = RectF()
    private val initials = Catalog.initials(title)

    override fun onMeasure(w: Int, h: Int) {
        val ws = MeasureSpec.getSize(w)
        setMeasuredDimension(ws, (ws * 1.36f).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = 14f * d
        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW)
        cv.save()
        cv.clipPath(clip)
        val b = bmp
        if (b != null) {
            if (backdrop != null) landscape(cv, b, w, h) else {
                val s = max(w / b.width, h / b.height)
                val dw = b.width * s
                val dh = b.height * s
                dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
                p.shader = null
                cv.drawBitmap(b, null, dst, sharp)
            }
        } else poster(cv, w, h)
        cv.restore()
        if (vietHoa) {
            tp.textSize = 9.5f * d
            tp.color = Color.parseColor("#2A1A08")
            val label = "VIỆT HÓA"
            val tw = tp.measureText(label)
            tmp.set(6f * d, h - 23f * d, 6f * d + tw + 12f * d, h - 6f * d)
            p.shader = LinearGradient(tmp.left, 0f, tmp.right, 0f, Ui.GOLD, Ui.GOLD2, Shader.TileMode.CLAMP)
            cv.drawRoundRect(tmp, 9f * d, 9f * d, p)
            p.shader = null
            cv.drawText(label, tmp.centerX(), tmp.bottom - 5f * d, tp)
        }
        if (chosen) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f * d
            p.color = Ui.GOLD
            tmp.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
            cv.drawRoundRect(tmp, r, r, p)
            p.style = Paint.Style.FILL
        }
    }

    /**
     * Ảnh ngang (460x215...): không cắt giữa rồi phóng to 2 lần như trước (vỡ, mờ). Nền là bản mờ của chính ảnh, phủ tối;
     * ảnh gốc đặt vừa chiều ngang ở giữa, chỉ thu nhỏ nên nét.
     */
    private fun landscape(cv: Canvas, b: Bitmap, w: Float, h: Float) {
        dst.set(0f, 0f, w, h)
        backdrop?.let { cv.drawBitmap(it, null, dst, sharp) }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xB8000000.toInt(), 0x70000000, 0xD0000000.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val ih = w * b.height / b.width
        val top = Math.round((h - ih) / 2f).toFloat()
        dst.set(0f, top, w, top + ih)
        cv.drawBitmap(b, null, dst, sharp)
        // viền sáng mảnh trên và dưới ảnh cho gọn mắt
        p.color = 0x33FFFFFF
        cv.drawRect(0f, top, w, top + 1f, p)
        cv.drawRect(0f, top + ih - 1f, w, top + ih, p)
    }

    /** Ảnh bìa vẽ sẵn khi chưa có ảnh thật: nền chuyển màu theo tên game, chữ cái đầu to ở giữa. */
    private fun poster(cv: Canvas, w: Float, h: Float) {
        val hue = Catalog.hue(title).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.66f))
        val bot = Color.HSVToColor(floatArrayOf((hue + 26f) % 360f, 0.72f, 0.20f))
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w * 0.45f, h, top, bot, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        p.color = 0x1EFFFFFF
        clip.reset()
        clip.moveTo(w * 0.55f, 0f); clip.lineTo(w, 0f); clip.lineTo(w, h * 0.45f); clip.lineTo(w * 0.15f, h)
        clip.lineTo(0f, h); clip.lineTo(0f, h * 0.9f); clip.close()
        cv.drawPath(clip, p)
        tp.color = 0xF2FFFFFF.toInt()
        tp.textSize = w * (if (initials.length > 3) 0.26f else 0.34f)
        cv.drawText(initials, w / 2f, h * 0.55f, tp)
        p.color = 0x66FFFFFF
        cv.drawRect(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.62f + 2f * d, p)
    }
}
