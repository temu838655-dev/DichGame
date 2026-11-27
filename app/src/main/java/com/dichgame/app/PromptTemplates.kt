package com.dichgame.app

import org.json.JSONObject

internal data class PromptTemplates(val selected: String, val entries: Map<String, String>) {
    fun save(old: String?, name: String, body: String): PromptTemplates {
        require(name.isNotBlank() && body.isNotBlank()) { "Hãy nhập tên mẫu và chỉ dẫn." }
        require(name == old || name !in entries) { "Tên mẫu đã có." }
        val updated = entries.toMutableMap()
        if (old != null) updated.remove(old)
        updated[name] = body
        return PromptTemplates(name, updated)
    }

    fun remove(name: String) = PromptTemplates(if (selected == name) "" else selected, entries - name)

    fun encode() = JSONObject().put("selected", selected).put("entries", JSONObject(entries)).toString()

    companion object {
        fun decode(raw: String?, legacy: String): PromptTemplates {
            if (raw == null) return if (legacy.isBlank()) PromptTemplates("", emptyMap())
                else PromptTemplates("Mẫu hiện có", mapOf("Mẫu hiện có" to legacy))
            val doc = JSONObject(raw)
            val entries = doc.getJSONObject("entries")
            return PromptTemplates(doc.getString("selected"), entries.keys().asSequence().associateWith { entries.getString(it) })
        }
    }
}
