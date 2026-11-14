package com.dichgame.app

import android.content.Context
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.RegistHost
import com.metallic.chiaki.lib.Target
import com.metallic.chiaki.lib.VideoFPSPreset
import com.metallic.chiaki.lib.VideoResolutionPreset
import org.json.JSONArray
import org.json.JSONObject

/** Một người chơi đã ghép nối với máy: mỗi tài khoản PSN ghép một lần và có bộ khóa riêng. */
class PsPlayer(
    val label: String,
    val registKey: ByteArray,
    val keyType: Int,
    val key: ByteArray,
    val mac: ByteArray,
    val nickname: String,
)

/** Máy PS5/PS4 đã ghép nối. Chỉ nhớ một máy, nhiều người chơi. */
class PsConsole(
    var host: String,
    var ps5: Boolean,
    var name: String,
    val players: MutableList<PsPlayer> = mutableListOf(),
    /** Người chơi dùng khi "Dịch khi chơi TV" (nên là người chơi phụ). */
    var tvPlayer: Int = 0,
    /** Người chơi dùng khi "Chơi trên điện thoại". */
    var phonePlayer: Int = 0,
)

/** Lưu máy đã ghép nối trong SharedPreferences "ps" (bộ khóa Remote Play chỉ nằm trên máy này). */
object PsStore {
    private const val FILE = "ps"
    private const val K_CONSOLE = "console"
    private const val K_RES = "res"
    private const val K_RATE = "rate"
    private const val K_ASK = "noask"

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** Decode dữ liệu hex từ SharedPreferences; không chấp nhận chuỗi lẻ hoặc ký tự ngoài hex. */
    internal fun parseHex(value: String): ByteArray? {
        if (value.length % 2 != 0 || value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return try {
            ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) { null }
    }

    /** Máy đã ghép bằng màn Remote Play bản đầu (một máy, một người chơi) được chuyển sang kho mới, chỉ một lần. */
    private fun migrateOld(ctx: Context): PsConsole? {
        val old = ctx.getSharedPreferences("ps_remote_play", Context.MODE_PRIVATE)
        val host = old.getString("host", null) ?: return null
        return try {
            val reg = android.util.Base64.decode(old.getString("regist_key", ""), android.util.Base64.DEFAULT)
            val key = android.util.Base64.decode(old.getString("rp_key", ""), android.util.Base64.DEFAULT)
            if (reg.isEmpty() || key.isEmpty()) return null
            val ps5 = old.getBoolean("ps5", true)
            val nick = old.getString("nickname", "") ?: ""
            val c = PsConsole(host, ps5, nick.ifBlank { if (ps5) "PS5" else "PS4" })
            c.players.add(PsPlayer("Người chơi 1", reg, old.getInt("rp_key_type", 0), key, ByteArray(0), nick))
            save(ctx, c)
            old.edit().clear().apply()
            c
        } catch (_: Exception) { null }
    }

    fun load(ctx: Context): PsConsole? {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_CONSOLE, null) ?: return migrateOld(ctx)
        return try {
            val o = JSONObject(raw)
            val host = o.getString("host").trim()
            if (host.isEmpty()) return null
            val c = PsConsole(host, o.optBoolean("ps5", true), o.optString("name", "PS5"))
            val a = o.optJSONArray("players") ?: JSONArray()
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val regist = parseHex(p.getString("regist")) ?: return null
                val key = parseHex(p.getString("key")) ?: return null
                val mac = parseHex(p.optString("mac", "")) ?: return null
                if (regist.isEmpty() || key.isEmpty()) return null
                c.players.add(PsPlayer(
                    p.optString("label", "Người chơi ${i + 1}"),
                    regist, p.optInt("type", 0), key, mac, p.optString("nick", ""),
                ))
            }
            if (c.players.isEmpty()) return null
            c.tvPlayer = o.optInt("tv", 0).coerceIn(0, (c.players.size - 1).coerceAtLeast(0))
            c.phonePlayer = o.optInt("phone", 0).coerceIn(0, (c.players.size - 1).coerceAtLeast(0))
            c
        } catch (_: Exception) { null }
    }

    fun save(ctx: Context, c: PsConsole?) {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (c == null) e.remove(K_CONSOLE) else {
            val a = JSONArray()
            for (p in c.players) a.put(JSONObject()
                .put("label", p.label).put("regist", hex(p.registKey)).put("type", p.keyType)
                .put("key", hex(p.key)).put("mac", hex(p.mac)).put("nick", p.nickname))
            e.putString(K_CONSOLE, JSONObject().put("host", c.host).put("ps5", c.ps5).put("name", c.name)
                .put("players", a).put("tv", c.tvPlayer).put("phone", c.phonePlayer).toString())
        }
        e.apply()
    }

    /** Thêm người chơi vừa ghép xong. Máy khác địa chỉ thì thay luôn máy cũ. Trả về số thứ tự người chơi. */
    fun addPlayer(ctx: Context, host: String, ps5: Boolean, r: RegistHost): Int {
        val old = load(ctx)
        val c = if (old != null && old.host == host) old else PsConsole(host, ps5, r.serverNickname.ifBlank { if (ps5) "PS5" else "PS4" })
        c.ps5 = ps5
        if (r.serverNickname.isNotBlank()) c.name = r.serverNickname
        val same = c.players.indexOfFirst { it.mac.contentEquals(r.serverMac) && it.registKey.contentEquals(r.rpRegistKey) }
        if (same >= 0) c.players.removeAt(same)
        val p = PsPlayer("Người chơi ${c.players.size + 1}", r.rpRegistKey, r.rpKeyType.toInt(), r.rpKey, r.serverMac, r.serverNickname)
        c.players.add(p)
        save(ctx, c)
        return c.players.size - 1
    }

    // ---- chất lượng hình ----
    /** 0 = tự động (720p), 1 = 720p, 2 = 1080p. */
    fun res(ctx: Context): Int = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(K_RES, 0)
    fun setRes(ctx: Context, v: Int) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(K_RES, v).apply() }

    /** Tốc độ mạng tối đa (Mbps), 0 = tự động. */
    fun rate(ctx: Context): Int = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(K_RATE, 0)
    fun setRate(ctx: Context, v: Int) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(K_RATE, v).apply() }

    /** Không hỏi lại bảng chất lượng khi bấm Chơi. */
    fun noAsk(ctx: Context): Boolean = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(K_ASK, false)
    fun setNoAsk(ctx: Context, v: Boolean) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(K_ASK, v).apply() }

    fun profile(ctx: Context): ConnectVideoProfile {
        val preset = if (res(ctx) == 2) VideoResolutionPreset.RES_1080P else VideoResolutionPreset.RES_720P
        val base = ConnectVideoProfile.preset(preset, VideoFPSPreset.FPS_60, Codec.CODEC_H264)
        val mbps = rate(ctx)
        return if (mbps > 0) base.copy(bitrate = mbps * 1000) else base
    }

    fun connectInfo(ctx: Context, c: PsConsole, player: Int): ConnectInfo {
        require(c.players.isNotEmpty()) { "Chưa có người chơi được ghép nối." }
        val p = c.players[player.coerceIn(0, c.players.size - 1)]
        return ConnectInfo(c.ps5, c.host, p.registKey, p.key, profile(ctx))
    }

    fun target(ps5: Boolean): Target = if (ps5) Target.PS5_1 else Target.PS4_10
}
