package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipInputStream
import javax.xml.crypto.OctetStreamData
import javax.xml.crypto.URIReferenceException
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.parsers.DocumentBuilderFactory

class SamsungWidgetSignatureTest {
    private fun validate(files: Map<String,ByteArray>, name: String, cert: X509Certificate): Boolean {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        }
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(files.getValue(name)))
        val context = DOMValidateContext(cert.publicKey,doc.documentElement)
        val obj = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#","Object").item(0) as org.w3c.dom.Element
        context.setIdAttributeNS(obj,null,"Id")
        val signatures = XMLSignatureFactory.getInstance("DOM")
        context.uriDereferencer = javax.xml.crypto.URIDereferencer { ref, ctx ->
            if (ref.uri == "#prop") signatures.uriDereferencer.dereference(ref,ctx)
            else OctetStreamData(ByteArrayInputStream(files[ref.uri] ?: throw URIReferenceException("missing file")))
        }
        val signature = signatures.unmarshalXMLSignature(context)
        return signature.validate(context)
    }

    @Test fun independentXmlVerifierChecksBothSignaturesAndRejectsTampering() {
        val dir = Files.createTempDirectory("dg-test-sign").toFile()
        try {
            // Synthetic local fixture only; never used for actual TV installation.
            fun openssl(vararg args: String) {
                val process = ProcessBuilder(listOf("openssl")+args).directory(dir).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output,0,process.waitFor())
            }
            openssl("req","-x509","-newkey","rsa:2048","-nodes","-keyout","key.pem","-out","cert.pem","-days","1","-subj","/CN=DichGame test")
            openssl("pkcs8","-topk8","-nocrypt","-in","key.pem","-outform","DER","-out","key.der")
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(dir.resolve("key.der").readBytes()))
            val cert = dir.resolve("cert.pem").inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
            val source = SamsungCertificateCrypto.sourceNames.associateWith { "test $it".toByteArray() }
            val wgt = SamsungCertificateCrypto.wgt(source,key,listOf(cert),key,listOf(cert))
            val files = linkedMapOf<String,ByteArray>()
            ZipInputStream(wgt.inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes(); zip.closeEntry() }
            }
            assertTrue("Author XML signature",validate(files,"author-signature.xml",cert))
            assertTrue("Distributor XML signature",validate(files,"signature1.xml",cert))
            files["index.html"] = "changed".toByteArray()
            assertFalse(validate(files,"author-signature.xml",cert))
            assertFalse(validate(files,"signature1.xml",cert))
        } finally { dir.deleteRecursively() }
    }
}
