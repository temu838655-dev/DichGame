package com.dichgame.app

import android.content.Context
import com.jcraft.jsch.Session
import org.json.JSONObject

/** LG webOS developer install protocol, based on webos-tools/cli command-service.json. */
internal object LgDeveloperInstaller {
    private const val APP = "com.dichgame.subtitles"

    private fun replies(output: String) = output.lineSequence().mapNotNull {
        runCatching { JSONObject(it.trim()) }.getOrNull()
    }

    fun install(context: Context, session: Session) {
        val remote = LgPackageTransfer.upload(context, session)
        try {
            val payload = JSONObject().put("id", "com.ares.defaultName")
                .put("ipkUrl", remote).put("subscribe", true)
            var installed = false
            val result = LgDeveloperCommand.execute(session,
                "/usr/bin/luna-send-pub -i luna://com.webos.appInstallService/dev/install '$payload'",
                120000) { output ->
                replies(output).any { reply ->
                    val details = reply.optJSONObject("details")
                    val state = details?.optString("state").orEmpty()
                    check(reply.optBoolean("returnValue", true) && !state.contains("FAILED", true)) {
                        details?.optString("reason")?.takeIf { it.isNotBlank() }
                            ?: reply.optString("errorText", "Cài đặt LG thất bại")
                    }
                    installed = state.equals("installed", true) || state.startsWith("SUCCESS", true)
                    installed
                }
            }
            check(result.exitCode == 0 && installed) { "TV chưa xác nhận cài đặt thành công" }
        } finally {
            runCatching { LgPackageTransfer.remove(session, remote) }
        }
    }

    fun launch(session: Session, phoneIp: String? = null) {
        val payload = JSONObject().put("id", APP).put("subscribe", false)
        if (phoneIp != null && LgDeveloperKey.isPrivateIpv4(phoneIp)) {
            payload.put("params", JSONObject().put("server", "$phoneIp:${TvOut.WEB_PORT}"))
        }
        val result = LgDeveloperCommand.execute(session,
            "/usr/bin/luna-send-pub -n 1 luna://com.webos.applicationManager/launch '$payload'", 15000)
        check(result.exitCode == 0 && replies(result.output).any { it.optBoolean("returnValue", false) }) {
            "Không mở được DichGame trên TV LG"
        }
    }
}
