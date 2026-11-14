package com.dichgame.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
    private const val KEY_ALIAS = "dichgame.remoteplay.storage"
    private const val ENVELOPE_VERSION = 1
    private const val GCM_TAG_BITS = 128

    private fun storageKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** Mã hóa toàn bộ JSON bằng AES-GCM; IV ngẫu nhiên được lưu cùng ciphertext. */
    private fun encryptConsoleJson(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, storageKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", ENVELOPE_VERSION)
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
    }

    private fun decryptConsoleJson(envelope: JSONObject): String {
        require(envelope.optInt("format", -1) == ENVELOPE_VERSION) { "Phiên bản dữ liệu ghép nối không được hỗ trợ." }
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        require(iv.size == 12 && ciphertext.size >= 16) { "Dữ liệu ghép nối đã mã hóa không hợp lệ." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, storageKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

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
        val host = old.getString("host", null)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val reg = android.util.Base64.decode(old.getString("regist_key", ""), android.util.Base64.DEFAULT)
            val key = android.util.Base64.decode(old.getString("rp_key", ""), android.util.Base64.DEFAULT)
            if (reg.isEmpty() || key.isEmpty()) return null
            val ps5 = old.getBoolean("ps5", true)
            val nick = old.getString("nickname", "") ?: ""
            val c = PsConsole(host, ps5, nick.ifBlank { if (ps5) "PS5" else "PS4" })
            c.players.add(PsPlayer("Người chơi 1", reg, old.getInt("rp_key_type", 0), key, ByteArray(0), nick))

            // Chỉ xóa dữ liệu cũ sau khi bản mới đã được ghi đồng bộ thành công.
            // Nếu ghi thất bại, giữ nguyên dữ liệu cũ để lần mở sau có thể thử chuyển lại.
            if (!save(ctx, c)) return null
            old.edit().clear().commit()
            c
        } catch (_: Exception) { null }
    }

    /** Có dữ liệu ghép nối được lưu, kể cả khi khóa Keystore hiện không giải mã được. */
    fun hasStoredConsole(ctx: Context): Boolean {
        val current = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (current.contains(K_CONSOLE)) return true

        // Bản cũ có thể chưa chuyển đổi được nếu Keystore/ghi đĩa tạm thời lỗi.
        // Vẫn phải nhận diện để không báo máy hoàn toàn mới hoặc ghi đè luồng chuyển đổi.
        val legacy = ctx.getSharedPreferences("ps_remote_play", Context.MODE_PRIVATE)
        return !legacy.getString("host", null).isNullOrBlank() &&
            !legacy.getString("regist_key", null).isNullOrBlank() &&
            !legacy.getString("rp_key", null).isNullOrBlank()
    }

    fun load(ctx: Context): PsConsole? {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(K_CONSOLE, null) ?: return migrateOld(ctx)
        return try {
            val stored = JSONObject(raw)
            val encrypted = stored.has("format")
            val plainText = if (encrypted) decryptConsoleJson(stored) else raw
            val console = decodeConsole(plainText) ?: return null

            // Dữ liệu cũ dạng JSON/HEX được nâng cấp tại chỗ sau khi mã hóa và ghi thành công.
            // Nếu Keystore hoặc commit gặp lỗi, vẫn trả dữ liệu đã đọc được và giữ nguyên bản cũ.
            if (!encrypted) {
                // Nếu không thể tạo/ghi khóa Keystore, vẫn dùng bản cũ trong phiên này;
                // lần mở sau sẽ thử nâng cấp lại, không làm mất dữ liệu đang lưu.
                runCatching { save(ctx, console) }
            }
            console
        } catch (_: Exception) { null }
    }

    private fun decodeConsole(raw: String): PsConsole? {
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
        c.tvPlayer = o.optInt("tv", 0).coerceIn(0, c.players.size - 1)
        c.phonePlayer = o.optInt("phone", 0).coerceIn(0, c.players.size - 1)
        return c
    }

    private fun encodeConsole(c: PsConsole): String {
        val a = JSONArray()
        for (p in c.players) a.put(JSONObject()
            .put("label", p.label).put("regist", hex(p.registKey)).put("type", p.keyType)
            .put("key", hex(p.key)).put("mac", hex(p.mac)).put("nick", p.nickname))
        return JSONObject().put("host", c.host).put("ps5", c.ps5).put("name", c.name)
            .put("players", a).put("tv", c.tvPlayer).put("phone", c.phonePlayer).toString()
    }

    /** Trả false nếu ghi không thành công; khóa không bao giờ được ghi lại dưới dạng plaintext. */
    fun save(ctx: Context, c: PsConsole?): Boolean {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (c == null) editor.remove(K_CONSOLE)
        else editor.putString(K_CONSOLE, encryptConsoleJson(encodeConsole(c)))
        return editor.commit()
    }

    /** Thêm người chơi vừa ghép xong. Máy khác địa chỉ thì thay luôn máy cũ. Trả về số thứ tự người chơi. */
    fun addPlayer(ctx: Context, host: String, ps5: Boolean, r: RegistHost): Int {
        val old = load(ctx)
        // Không coi dữ liệu mã hóa không giải mã được là máy mới: tránh ghi đè khóa cũ.
        check(old != null || !hasStoredConsole(ctx)) {
            "Dữ liệu ghép nối cũ vẫn còn nhưng không giải mã được bằng khóa bảo mật của thiết bị. App đã giữ nguyên dữ liệu; hãy khôi phục khóa trên đúng thiết bị hoặc chủ động xóa dữ liệu ghép nối trước khi ghép lại."
        }
        val c = if (old != null && old.host == host) old else PsConsole(host, ps5, r.serverNickname.ifBlank { if (ps5) "PS5" else "PS4" })
        c.ps5 = ps5
        if (r.serverNickname.isNotBlank()) c.name = r.serverNickname
        val same = c.players.indexOfFirst { it.mac.contentEquals(r.serverMac) && it.registKey.contentEquals(r.rpRegistKey) }
        if (same >= 0) c.players.removeAt(same)
        val p = PsPlayer("Người chơi ${c.players.size + 1}", r.rpRegistKey, r.rpKeyType.toInt(), r.rpKey, r.serverMac, r.serverNickname)
        c.players.add(p)
        check(save(ctx, c)) { "Không thể lưu khóa ghép nối an toàn. Dữ liệu cũ vẫn được giữ nguyên." }
        return c.players.size - 1
    }

    // ---- chất lượng hình ----
    /** 0 = tự động (720p), 1 = 720p, 2 = 1080p. Giá trị cũ/hỏng được đưa về mặc định an toàn. */
    fun res(ctx: Context): Int {
        val stored = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(K_RES, 0)
        return if (stored in 0..2) stored else 0
    }
    fun setRes(ctx: Context, v: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(K_RES, if (v in 0..2) v else 0).apply()
    }

    /** Bitrate profile (Mbps), 0 = bitrate mặc định của engine. Chỉ nhận các mức có trong giao diện. */
    private val RATE_PRESETS = setOf(0, 2, 3, 5, 8, 10, 15, 20, 30)
    fun rate(ctx: Context): Int {
        val stored = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(K_RATE, 0)
        return if (stored in RATE_PRESETS) stored else 0
    }
    fun setRate(ctx: Context, v: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(K_RATE, if (v in RATE_PRESETS) v else 0).apply()
    }

    /** Chiaki-NG nhận bitrate theo kbps; UI lưu Mbps. Giá trị ngoài preset dùng bitrate mặc định. */
    internal fun bitrateKbps(mbps: Int): Int =
        if (mbps in RATE_PRESETS) mbps * 1_000 else 0


    /** Không hỏi lại bảng chất lượng khi bấm Chơi. */
    fun noAsk(ctx: Context): Boolean = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(K_ASK, false)
    fun setNoAsk(ctx: Context, v: Boolean) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(K_ASK, v).apply() }

    fun profile(ctx: Context): ConnectVideoProfile {
        val preset = if (res(ctx) == 2) VideoResolutionPreset.RES_1080P else VideoResolutionPreset.RES_720P
        val base = ConnectVideoProfile.preset(preset, VideoFPSPreset.FPS_60, Codec.CODEC_H264)
        val bitrate = bitrateKbps(rate(ctx))
        return if (bitrate > 0) base.copy(bitrate = bitrate) else base
    }

    fun connectInfo(ctx: Context, c: PsConsole, player: Int): ConnectInfo {
        require(c.players.isNotEmpty()) { "Chưa có người chơi được ghép nối." }
        val p = c.players[player.coerceIn(0, c.players.size - 1)]
        return ConnectInfo(c.ps5, c.host, p.registKey, p.key, profile(ctx))
    }

    fun target(ps5: Boolean): Target = if (ps5) Target.PS5_1 else Target.PS4_10
}
