package com.dichgame.app

import java.net.URLDecoder
import org.json.JSONObject

internal object SamsungOAuthCallback {
    private fun fields(text: String): Map<String,String> {
        val values = linkedMapOf<String,String>()
        for (field in text.split('&').filter { it.isNotEmpty() }) {
            val i = field.indexOf('=')
            require(i > 0)
            val key = URLDecoder.decode(field.substring(0,i),"UTF-8")
            val value = URLDecoder.decode(field.substring(i+1),"UTF-8")
            require(key.isNotEmpty() && (key !in values || values[key] == value))
            values[key] = value
        }
        return values
    }

    fun parse(request: String, headers: Map<String,String>, body: String, port: Int, state: String): SamsungCertificateAuthority.Account? = runCatching {
        val parts = request.split(' ')
        require(parts.size == 3 && parts[0] in listOf("GET","POST") && parts[2].startsWith("HTTP/1."))
        require(parts[1].substringBefore('?') == "/signin/callback")
        require(headers["host"]?.lowercase() in listOf("localhost:$port","127.0.0.1:$port"))
        require(headers["origin"] == null || headers["origin"] in listOf("https://account.samsung.com","null"))
        if (body.isNotEmpty()) require(parts[0] == "POST" && headers["content-type"]?.lowercase()?.startsWith("application/x-www-form-urlencoded") == true)
        val values = fields(parts[1].substringAfter('?',"")).toMutableMap()
        for ((key,value) in fields(body)) {
            require(key !in values || values[key] == value)
            values[key] = value
        }
        require("dg_state" in values || "state" in values)
        require(values["dg_state"]?.let { it == state } != false && values["state"]?.let { it == state } != false)
        val code = JSONObject(values["code"] ?: error("Missing response"))
        val token = code.optString("access_token")
        val uid = code.optString("userId")
        require(token.isNotBlank() && token.length <= 32768 && uid.isNotBlank() && uid.length <= 512)
        require(code.optLong("access_token_expires_in",0)>0)
        SamsungCertificateAuthority.Account(token,uid,code.optString("inputEmailID").take(320))
    }.getOrNull()
}
