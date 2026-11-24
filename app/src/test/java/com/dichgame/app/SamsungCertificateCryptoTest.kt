package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class SamsungCertificateCryptoTest {
    @Test fun duidValidation() {
        assertTrue(SamsungCertificateCrypto.validDuid("2.0#12345678901234567890"))
        assertFalse(SamsungCertificateCrypto.validDuid("bad duid"))
        assertFalse(SamsungCertificateCrypto.validDuid("2.0#12345678<script>"))
    }
    @Test fun generatesDerCsrWithDuid() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val duid = "2.0#12345678901234567890"
        val pem = SamsungCertificateCrypto.csr(pair,"TizenSDK",duid)
        val der = Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
        assertEquals(48, der[0].toInt())
        assertTrue(der.toString(Charsets.ISO_8859_1).contains("URN:tizen:deviceid=$duid"))
        assertTrue(der.toString(Charsets.ISO_8859_1).contains("URN:tizen:packageid="))
    }
}
