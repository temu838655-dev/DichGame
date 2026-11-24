package com.dichgame.app

import org.junit.Test
import org.junit.Assert.*
import java.net.URLEncoder

class SamsungOAuthCallbackTest {
    @Test fun rejectsConflictingAndMalformedCallbacks() {
        val code = URLEncoder.encode("""{"access_token":"a+b=&test","userId":"test-user","access_token_expires_in":3600}""","UTF-8")
        val headers = mapOf("host" to "localhost:4794","origin" to "https://account.samsung.com","content-type" to "application/x-www-form-urlencoded")
        fun parse(body: String, h: Map<String,String> = headers, request: String = "POST /signin/callback?dg_state=test-state HTTP/1.1") =
            SamsungOAuthCallback.parse(request,h,body,4794,"test-state")
        assertEquals("a+b=&test",parse("code=$code")?.token)
        assertNull(parse("code=%GG"))
        assertNull(parse("code=$code&state=wrong"))
        assertNull(parse("code=$code&code=other"))
        assertNull(parse("code=$code",headers+("host" to "evil.example")))
        assertNull(parse("code=$code",headers+("origin" to "https://evil.example")))
        assertNull(parse("code=$code",request="POST /signin/callback HTTP/1.1"))
    }
}
