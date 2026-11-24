package com.dichgame.app

import com.dichgame.update.Update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private fun asset(name: String, size: Long, state: String = "uploaded") =
        """{"name":"$name","size":$size,"state":"$state","browser_download_url":"https://github.com/x/y/releases/download/T/$name"}"""

    private fun rel(tag: String, name: String, assets: String, body: String = "", extra: String = "") =
        """{"tag_name":"$tag","name":"$name","html_url":"https://github.com/x/y/releases/tag/$tag","body":${org.json.JSONObject.quote(body)},"assets":[$assets]$extra}"""

    private fun list(vararg r: String) = "[" + r.joinToString(",") + "]"

    @Test fun picksHighestBuildThatHasTheAsset() {
        val json = list(
            rel("build-716", "Bản dựng 716", ""),                                   // đang đẩy file lên dở: bỏ qua
            rel("build-715", "Bản dựng 715", asset("DichGame.apk", 67423177) + "," + asset("DichGame-TV.apk", 836197)),
            rel("build-712", "Bản dựng 712", asset("DichGame.apk", 67000000))
        )
        val i = Update.parse(json, "DichGame.apk")
        assertNotNull(i)
        assertEquals(715, i!!.no)
        assertEquals(67423177L, i.size)
        assertTrue(i.apk.endsWith("/DichGame.apk"))
        assertEquals("https://github.com/x/y/releases/tag/build-715", i.page)
    }

    @Test fun tvAppGetsItsOwnFile() {
        val json = list(rel("build-715", "Bản dựng 715", asset("DichGame.apk", 67423177) + "," + asset("DichGame-TV.apk", 836197)))
        val i = Update.parse(json, "DichGame-TV.apk")!!
        assertTrue(i.apk.endsWith("/DichGame-TV.apk"))
        assertEquals(836197L, i.size)
    }

    @Test fun ignoresDraftPrereleaseAndNonBuildTags() {
        val json = list(
            rel("build-800", "Bản dựng 800", asset("DichGame.apk", 1), extra = ""","draft":true"""),
            rel("build-799", "Bản dựng 799", asset("DichGame.apk", 1), extra = ""","prerelease":true"""),
            rel("notes", "ghi chú", asset("DichGame.apk", 1)),
            rel("build-700", "Bản dựng 700", asset("DichGame.apk", 5))
        )
        assertEquals(700, Update.parse(json, "DichGame.apk")!!.no)
    }

    @Test fun ignoresAssetStillUploading() {
        val json = list(
            rel("build-720", "Bản dựng 720", asset("DichGame.apk", 10, "starter")),
            rel("build-719", "Bản dựng 719", asset("DichGame.apk", 10))
        )
        assertEquals(719, Update.parse(json, "DichGame.apk")!!.no)
    }

    @Test fun releaseFromVersionTagUsesNumberInName() {
        val json = list(
            rel("v2.1", "Bản dựng 730 · v2.1", asset("DichGame.apk", 10)),
            rel("build-715", "Bản dựng 715", asset("DichGame.apk", 10))
        )
        assertEquals(730, Update.parse(json, "DichGame.apk")!!.no)
    }

    @Test fun noUsableReleaseGivesNull() {
        assertNull(Update.parse("[]", "DichGame.apk"))
        assertNull(Update.parse(list(rel("build-1", "Bản dựng 1", asset("Other.apk", 1))), "DichGame.apk"))
    }

    @Test fun notesDropSignaturesAndChangelogLine() {
        val raw = "Màn chơi: thêm nút Xong và Đặt lại\n\nBảng Cài đặt có giọng đọc.\n\nCo-Authored-By: Claude <x@y>\nClaude-Session: https://claude.ai/code/s\n**Full Changelog**: https://github.com/x/y/compare/a...b"
        assertEquals("Màn chơi: thêm nút Xong và Đặt lại\n\nBảng Cài đặt có giọng đọc.", Update.cleanNotes(raw))
        assertEquals("", Update.cleanNotes("**Full Changelog**: https://github.com/x/y/compare/build-1...build-2"))
    }

    @Test fun releaseDigestIsParsedAndValidated() {
        val hash = "ab".repeat(32)
        assertEquals(hash, Update.digest("sha256:" + hash.uppercase()))
        assertNull(Update.digest("sha256:bad"))
        val f = kotlin.io.path.createTempFile().toFile()
        try {
            f.writeText("abc")
            assertTrue(Update.verifyDigest(f, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
            assertTrue(!Update.verifyDigest(f, hash))
        } finally { f.delete() }
    }

    @Test fun assetUrlMustPointToThisRepository() {
        val url = "https://github.com/${Update.REPO}/releases/download/build-123/DichGame.apk"
        assertTrue(Update.validAssetUrl(url, "DichGame.apk"))
        assertTrue(!Update.validAssetUrl(url.replace("https:", "http:"), "DichGame.apk"))
        assertTrue(!Update.validAssetUrl(url.replace("github.com/", "github.com.example.org/"), "DichGame.apk"))
        assertTrue(!Update.validAssetUrl(url, "DichGame-TV.apk"))
    }

    @Test fun notesAreTruncated() {
        val n = Update.cleanNotes("a".repeat(2000))
        assertTrue(n.length <= 701)
        assertTrue(n.endsWith("…"))
    }
}
