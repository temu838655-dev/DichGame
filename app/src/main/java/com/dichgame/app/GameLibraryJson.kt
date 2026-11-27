package com.dichgame.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Thư viện Việt hoá của một game dạng JSON: {"schema":1,"game":"...","items":[{"id","en_normalized","en_raw","vi"}]}. */
internal object GameLibraryJson {
    const val MAX_ITEMS = PackText.MAX_LINES
    private const val MAX_EN = 2_000
    private const val MAX_VI = 4_000

    class Library(val game: String, val version: Int, val lines: List<Pair<String, String>>, val renormalized: Int)

    /** Chữ thường, bỏ dấu câu thừa và dấu nháy trong từ, gộp khoảng trắng. */
    fun normalize(raw: String): String {
        val n = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val sb = StringBuilder(n.length)
        var space = false
        for (ch in n) {
            if (ch == '\'' || ch == '’' || ch == '‘' || ch == 'ʼ' || ch == '`') continue
            if (ch.isLetterOrDigit()) {
                if (space && sb.isNotEmpty()) sb.append(' ')
                sb.append(ch)
                space = false
            } else space = true
        }
        return sb.toString()
    }

    fun looksLike(raw: String): Boolean {
        val t = raw.trimStart('﻿', ' ', '\n', '\r', '\t')
        return t.startsWith("{") && t.contains("\"items\"")
    }

    fun format(game: String, version: Int, items: List<Pair<String, String>>): String {
        val arr = JSONArray()
        items.forEachIndexed { i, (en, vi) ->
            arr.put(JSONObject().put("id", "%06d".format(i + 1)).put("en_normalized", normalize(en)).put("en_raw", en).put("vi", vi))
        }
        return JSONObject().put("schema", 1).put("game", game).put("version", version).put("items", arr).toString()
    }

    /** Đọc và kiểm tra nghiêm ngặt: sai một mục là từ chối cả gói. en_normalized trong file chỉ để đối chiếu, app luôn tự chuẩn hoá lại từ en_raw. */
    fun parse(raw: String, selectedGame: String? = null): Library {
        val obj = JSONObject(raw)
        require(obj.optInt("schema", 0) == 1) { "Định dạng thư viện JSON chưa được hỗ trợ." }
        val game = obj.optString("game").trim()
        require(game.isNotBlank()) { "Thư viện JSON thiếu tên game." }
        if (selectedGame != null) require(GamePackMatch.selected(selectedGame, game)) { "Thư viện JSON không đúng game đang chọn." }
        val version = obj.optInt("version", 1)
        require(version > 0) { "Phiên bản thư viện không hợp lệ." }
        val items = obj.optJSONArray("items") ?: throw IllegalArgumentException("Thư viện JSON thiếu danh sách items.")
        require(items.length() in 1..MAX_ITEMS) { "Số câu trong thư viện không hợp lệ (tối đa $MAX_ITEMS)." }
        val ids = HashSet<String>(items.length() * 2)
        val lines = ArrayList<Pair<String, String>>(items.length())
        var renormalized = 0
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: throw IllegalArgumentException("Mục ${i + 1} không phải đối tượng JSON.")
            val id = it.optString("id").trim()
            val en = it.optString("en_raw").trim()
            val vi = it.optString("vi").trim()
            require(id.isNotEmpty() && ids.add(id)) { "Mục ${i + 1}: id trống hoặc trùng." }
            require(en.isNotEmpty() && en.length <= MAX_EN) { "Mục $id: en_raw trống hoặc quá dài." }
            require(vi.isNotEmpty() && vi.length <= MAX_VI) { "Mục $id: vi trống hoặc quá dài." }
            require(normalize(en).isNotEmpty()) { "Mục $id: en_raw không có chữ nào." }
            val declared = it.optString("en_normalized")
            if (declared.isNotEmpty() && declared != normalize(en)) renormalized++
            lines.add(en to vi)
        }
        return Library(game, version, lines, renormalized)
    }
}
