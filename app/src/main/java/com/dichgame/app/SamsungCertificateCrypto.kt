package com.dichgame.app

import java.security.KeyPair
import java.security.Signature
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import javax.security.auth.x500.X500Principal
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

/** PKCS#10 CSR and W3C Widget XML signature for the fixed DichGame receiver file set.
 * Canonical XML is constructed explicitly; no untrusted XML or external references are parsed. */
internal object SamsungCertificateCrypto {
    private val base64 = Base64.getEncoder()
    private fun b64(b: ByteArray) = base64.encodeToString(b)
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val n = bytes.size
        val length = if (n < 128) byteArrayOf(n.toByte()) else {
            val octets = java.nio.ByteBuffer.allocate(4).putInt(n).array().dropWhile { it == 0.toByte() }.toByteArray()
            byteArrayOf((128 + octets.size).toByte()) + octets
        }
        return byteArrayOf(tag.toByte()) + length + bytes
    }
    private fun seq(vararg values: ByteArray) = der(48, values.fold(byteArrayOf()) { a,b -> a+b })
    private fun oid(vararg bytes: Int) = der(6, bytes.map { it.toByte() }.toByteArray())
    private fun sign(key: PrivateKey, bytes: ByteArray, algorithm: String = "SHA512withRSA") = Signature.getInstance(algorithm).run {
        initSign(key); update(bytes); sign()
    }
    fun validDuid(value: String): Boolean = value.length in 12..160 &&
        value.matches(Regex("[A-Za-z0-9#.:=\\-]+")) && value.any(Char::isDigit) &&
        value.none(Char::isWhitespace)

    fun csr(pair: KeyPair, identity: String, duid: String? = null): String {
        require(identity.length in 1..80 && identity.none { it.isISOControl() })
        require(duid == null || validDuid(duid))
        val name = X500Principal("CN=" + escapeDN(identity)).encoded
        val attrs = if (duid == null) der(160, byteArrayOf()) else {
            val san = seq(der(134,"URN:tizen:packageid=".toByteArray(Charsets.US_ASCII)),
                der(134,"URN:tizen:deviceid=$duid".toByteArray(Charsets.US_ASCII)))
            val extensions = seq(seq(oid(0x55,0x1d,0x11),der(4,san)))
            der(160,seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,0x01,0x09,0x0e),der(49,extensions)))
        }
        val info = seq(der(2,byteArrayOf(0)),name,pair.public.encoded,attrs)
        val algorithm = seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,0x01,0x01,0x0d),der(5,byteArrayOf()))
        val request = seq(info,algorithm,der(3,byteArrayOf(0)+sign(pair.private,info)))
        return "-----BEGIN CERTIFICATE REQUEST-----\n" + Base64.getMimeEncoder(64,byteArrayOf(10)).encodeToString(request) + "\n-----END CERTIFICATE REQUEST-----\n"
    }

    private fun escapeDN(value: String): String = value.mapIndexed { i,c ->
        if (c in ",=+<>#;\\\"" || (c == ' ' && (i == 0 || i == value.lastIndex))) "\\$c" else c.toString()
    }.joinToString("")

    private const val DS = "http://www.w3.org/2000/09/xmldsig#"
    private const val DSP = "http://www.w3.org/2009/xmldsig-properties"
    private const val WIDGET = "http://www.w3.org/ns/widgets-digsig#"
    private const val C14N = "http://www.w3.org/2001/10/xml-exc-c14n#"
    private const val C14N11 = "http://www.w3.org/2006/12/xml-c14n11"
    private const val SHA = "http://www.w3.org/2001/04/xmlenc#sha256"
    private const val RSA = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"
    val sourceNames = setOf("config.xml","index.html","receiver.js","pairing.js")
    private fun digest(bytes: ByteArray) = b64(MessageDigest.getInstance("SHA-256").digest(bytes))
    internal fun signatureXml(files: Map<String,ByteArray>, key: PrivateKey, chain: List<X509Certificate>, author: Boolean): ByteArray {
        require(files.keys == if (author) sourceNames else sourceNames + "author-signature.xml")
        require(chain.isNotEmpty())
        val id = if (author) "AuthorSignature" else "DistributorSignature"
        val role = if (author) "author" else "distributor"
        val properties = "<SignatureProperties xmlns:dsp=\"$DSP\">" +
            "<SignatureProperty Id=\"profile\" Target=\"#$id\"><dsp:Profile URI=\"${WIDGET}profile\"></dsp:Profile></SignatureProperty>" +
            "<SignatureProperty Id=\"role\" Target=\"#$id\"><dsp:Role URI=\"${WIDGET}role-$role\"></dsp:Role></SignatureProperty>" +
            "<SignatureProperty Id=\"identifier\" Target=\"#$id\"><dsp:Identifier></dsp:Identifier></SignatureProperty></SignatureProperties>"
        val obj = "<Object Id=\"prop\">$properties</Object>"
        val canonicalObject = "<Object xmlns=\"$DS\" Id=\"prop\">$properties</Object>"
        fun ref(uri: String, hash: String, transform: Boolean = false) =
            "<Reference URI=\"$uri\">" + (if (transform) "<Transforms><Transform Algorithm=\"$C14N11\"></Transform></Transforms>" else "") +
                "<DigestMethod Algorithm=\"$SHA\"></DigestMethod><DigestValue>$hash</DigestValue></Reference>"
        val inside = "<CanonicalizationMethod Algorithm=\"$C14N\"></CanonicalizationMethod><SignatureMethod Algorithm=\"$RSA\"></SignatureMethod>" +
            files.toSortedMap().entries.joinToString("") { ref(it.key,digest(it.value)) } +
            ref("#prop",digest(canonicalObject.toByteArray(Charsets.UTF_8)),true)
        val signedInfo = "<SignedInfo xmlns=\"$DS\">$inside</SignedInfo>"
        val sig = sign(key,signedInfo.toByteArray(Charsets.UTF_8), "SHA256withRSA")
        check(Signature.getInstance("SHA256withRSA").run { initVerify(chain.first()); update(signedInfo.toByteArray()); verify(sig) }) { "Khóa ký không khớp chứng chỉ" }
        val certificates = chain.joinToString("") { "<X509Certificate>${b64(it.encoded)}</X509Certificate>" }
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Signature xmlns=\"$DS\" Id=\"$id\"><SignedInfo>$inside</SignedInfo>" +
            "<SignatureValue>${b64(sig)}</SignatureValue><KeyInfo><X509Data>$certificates</X509Data></KeyInfo>$obj</Signature>").toByteArray(Charsets.UTF_8)
    }

    fun wgt(source: Map<String,ByteArray>, authorKey: PrivateKey, authorChain: List<X509Certificate>, distributorKey: PrivateKey, distributorChain: List<X509Certificate>): ByteArray {
        val files = LinkedHashMap(source)
        files["author-signature.xml"] = signatureXml(files,authorKey,authorChain,true)
        files["signature1.xml"] = signatureXml(files,distributorKey,distributorChain,false)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name,bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
        return out.toByteArray()
    }
}
