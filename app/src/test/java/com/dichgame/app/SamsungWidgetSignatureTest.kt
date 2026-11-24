package com.dichgame.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipInputStream

class SamsungWidgetSignatureTest {
    // Android's compile SDK omits javax.xml.crypto; launch the full JDK's independent verifier.
    private val verifier = """
        import java.io.*;
        import java.nio.file.*;
        import java.security.cert.*;
        import java.util.zip.*;
        import javax.xml.crypto.*;
        import javax.xml.crypto.dsig.*;
        import javax.xml.crypto.dsig.dom.*;
        import javax.xml.parsers.*;
        import org.w3c.dom.*;
        class VerifyWidget {
            public static void main(String[] args) throws Exception {
                try (var zip = new ZipFile(args[0]); var input = Files.newInputStream(Path.of(args[1]))) {
                    var cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    var doc = factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry(args[2])));
                    var ctx = new DOMValidateContext(cert.getPublicKey(), doc.getDocumentElement());
                    var object = (Element) doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Object").item(0);
                    ctx.setIdAttributeNS(object, null, "Id");
                    var signatures = XMLSignatureFactory.getInstance("DOM");
                    ctx.setURIDereferencer((ref, context) -> {
                        if ("#prop".equals(ref.getURI())) return signatures.getURIDereferencer().dereference(ref, context);
                        try {
                            var entry = zip.getEntry(ref.getURI());
                            if (entry == null) throw new URIReferenceException("missing file");
                            return new OctetStreamData(new ByteArrayInputStream(zip.getInputStream(entry).readAllBytes()));
                        } catch (IOException e) { throw new URIReferenceException(e); }
                    });
                    var signature = signatures.unmarshalXMLSignature(ctx);
                    boolean valid = signature.validate(ctx);
                    System.out.println("VALID=" + valid);
                }
            }
        }
    """.trimIndent()

    private fun validate(dir: java.io.File, files: Map<String,ByteArray>, name: String): Boolean {
        val source = dir.resolve("VerifyWidget.java").apply { writeText(verifier) }
        val wgt = dir.resolve("verify.wgt")
        java.util.zip.ZipOutputStream(wgt.outputStream()).use { zip ->
            files.forEach { (file, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(file))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        val java = java.io.File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(java,source.absolutePath,wgt.absolutePath,dir.resolve("cert.pem").absolutePath,name)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output,0,process.waitFor())
        check(output.contains("VALID=")) { output }
        return output.lineSequence().any { it.trim() == "VALID=true" }
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
            assertTrue("Author XML signature",validate(dir,files,"author-signature.xml"))
            assertTrue("Distributor XML signature",validate(dir,files,"signature1.xml"))
            files["index.html"] = "changed".toByteArray()
            assertFalse(validate(dir,files,"author-signature.xml"))
            assertFalse(validate(dir,files,"signature1.xml"))
        } finally { dir.deleteRecursively() }
    }
}
