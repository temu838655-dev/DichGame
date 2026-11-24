package com.dichgame.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.zip.ZipInputStream

/** Calls Samsung online CA directly with the account's explicitly authorized token.
 * Uses normal platform TLS validation; no redirects or untrusted certificate acceptance. */
internal object SamsungCertificateAuthority {
    internal data class Account(val token: String, val userId: String, val email: String)
    private const val BASE = "https://svdca.samsungqbe.com"
    private const val AUTHOR = "dichgame-samsung-author-v1"
    private fun certificate(bytes: ByteArray) = CertificateFactory.getInstance("X.509")
        .generateCertificate(bytes.inputStream()) as X509Certificate
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun keys(alias: String): KeyPair {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(alias)) return KeyPair(ks.getCertificate(alias).publicKey,ks.getKey(alias,null) as PrivateKey)
        return KeyPairGenerator.getInstance("RSA","AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build())
        }.generateKeyPair()
    }

    private fun request(path: String, account: Account, csr: String, distributor: Boolean, platform: Boolean): ByteArray {
        require(path in listOf("/apis/v3/authors","/apis/v1/distributors","/apis/v3/distributors"))
        val boundary = "DichGame-" + UUID.randomUUID()
        val body = ByteArrayOutputStream()
        fun append(value: String) { body.write(value.toByteArray(Charsets.UTF_8)) }
        fun field(name: String,value: String) {
            require(value.length <= 32768 && !value.contains('\r') && !value.contains('\n'))
            append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        }
        field("access_token",account.token); field("user_id",account.userId)
        if (distributor) { field("privilege_level","Public"); field("developer_type","Individual") }
        if (platform) field("platform","VD")
        val name = if (distributor) "distributor.csr" else "author.csr"
        append("--$boundary\r\nContent-Disposition: form-data; name=\"csr\"; filename=\"$name\"\r\nContent-Type: application/octet-stream\r\n\r\n$csr\r\n--$boundary--\r\n")
        val c = URL(BASE+path).openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects = false; c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 15000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
            c.setFixedLengthStreamingMode(body.size()); c.outputStream.use { body.writeTo(it) }
            check(c.responseCode == 200) { "Samsung CA trả HTTP ${c.responseCode}. Kiểm tra đăng nhập hoặc thử lại sau." }
            val result = ByteArrayOutputStream()
            c.inputStream.use { stream ->
                val buf = ByteArray(4096)
                while (true) {
                    val n = stream.read(buf); if (n < 0) break
                    check(result.size()+n <= 262144) { "Phản hồi CA quá lớn" }
                    result.write(buf,0,n)
                }
            }
            check(result.size()>0) { "Samsung CA không trả chứng chỉ" }
            return result.toByteArray()
        } finally { c.disconnect() }
    }

    private fun checked(bytes: ByteArray, pair: KeyPair, ca: X509Certificate, duid: String? = null): X509Certificate {
        val cert = certificate(bytes)
        cert.checkValidity(); ca.checkValidity()
        check(cert.publicKey == pair.public) { "Chứng chỉ không khớp khóa thiết bị" }
        cert.verify(ca.publicKey)
        if (duid != null) {
            val names = cert.subjectAlternativeNames.orEmpty()
            check(names.any { it.size >= 2 && it[0] == 6 && it[1].toString().equals("URN:tizen:deviceid=$duid",true) }) {
                "Chứng chỉ Samsung chưa chứa DUID TV"
            }
        }
        return cert
    }

    fun hasCertificates(context: Context,duid: String): Boolean {
        if (!SamsungCertificateCrypto.validDuid(duid)) return false
        val dir = File(context.noBackupFilesDir,"samsung-certificates")
        return File(dir,"author.cer").isFile && File(dir,hash(duid)+".cer").isFile
    }

    @Synchronized fun signReceiver(context: Context,duid: String,identity: String,account: Account?,progress:(String)->Unit): ByteArray {
        require(SamsungCertificateCrypto.validDuid(duid))
        val dir = File(context.noBackupFilesDir,"samsung-certificates").apply { mkdirs() }
        val authorPair = keys(AUTHOR)
        val distributorAlias = "dichgame-samsung-distributor-" + hash(duid)
        val distributorPair = keys(distributorAlias)
        val authorCA = context.assets.open("tv/samsung-ca/vd_tizen_dev_author_ca.cer").use { certificate(it.readBytes()) }
        val distributorCA = context.assets.open("tv/samsung-ca/vd_tizen_dev_public2.crt").use { certificate(it.readBytes()) }
        val authorFile = File(dir,"author.cer")
        val distFile = File(dir,hash(duid)+".cer")
        fun save(file: File,cert: X509Certificate) {
            val tmp = File(file.path+".tmp"); tmp.writeBytes(cert.encoded)
            check(tmp.renameTo(file)) { "Không lưu được chứng chỉ" }
        }
        val author = runCatching { checked(authorFile.readBytes(),authorPair,authorCA) }.getOrNull() ?: run {
            check(account != null) { "Cần đăng nhập Samsung để cấp chứng chỉ tác giả" }
            progress("Đang yêu cầu chứng chỉ tác giả từ Samsung")
            checked(request("/apis/v3/authors",account,SamsungCertificateCrypto.csr(authorPair,identity),false,true),authorPair,authorCA).also { save(authorFile,it) }
        }
        val distributor = runCatching { checked(distFile.readBytes(),distributorPair,distributorCA,duid) }.getOrNull() ?: run {
            check(account != null) { "Cần đăng nhập Samsung để cấp chứng chỉ cho TV" }
            progress("Đang yêu cầu chứng chỉ phân phối cho DUID TV")
            val csr = SamsungCertificateCrypto.csr(distributorPair,"TizenSDK",duid)
            request("/apis/v1/distributors",account,csr,true,true)
            checked(request("/apis/v3/distributors",account,csr,true,false),distributorPair,distributorCA,duid).also { save(distFile,it) }
        }
        progress("Đang ký WGT DichGame trên điện thoại")
        val source = linkedMapOf<String,ByteArray>()
        context.assets.open("tv/DichGame-Samsung-source.zip").use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name in SamsungCertificateCrypto.sourceNames) {
                        check(!source.containsKey(e.name))
                        val out = ByteArrayOutputStream(); val buf = ByteArray(4096)
                        while (true) { val n = zip.read(buf); if (n < 0) break; check(out.size()+n <= 1048576); out.write(buf,0,n) }
                        source[e.name] = out.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
        }
        check(source.keys == SamsungCertificateCrypto.sourceNames) { "Thiếu mã nguồn Tizen trong APK" }
        return SamsungCertificateCrypto.wgt(source,authorPair.private,listOf(author,authorCA),distributorPair.private,listOf(distributor,distributorCA))
    }

    fun duid(ip: String): String {
        val caps = runCatching { SamsungSdb(ip).use { it.connect(); it.capabilities() } }.getOrDefault("")
        val fields = caps.lineSequence().mapNotNull { line ->
            val i = line.indexOf(':'); if (i < 1) null else line.substring(0,i).trim().lowercase() to line.substring(i+1).trim()
        }.toMap()
        val secure = fields["secure_protocol"]?.lowercase() in listOf("true","yes","1","enabled")
        val commands = if (secure) listOf(if (fields["product_version"] == "1.0") "0 getduidgadget" else "0 getduid")
            else listOf("/opt/etc/duid-gadget 2.0 2> /dev/null","0 getduid")
        for (command in commands) {
            val output = runCatching { SamsungSdb(ip).use { s ->
                s.connect(); s.shell(command,15000) { text -> text.lineSequence().any { line -> SamsungCertificateCrypto.validDuid(line.trim().removePrefix("duid:").trim()) } }
            } }.getOrDefault("")
            val value = output.lineSequence().map { it.trim().replace(Regex("(?i)^duid:\\s*"),"").trim('"','\'') }
                .firstOrNull { SamsungCertificateCrypto.validDuid(it) }
            if (value != null) return value
        }
        error("Không đọc được DUID; nhập mã từ màn hình Thông tin TV hoặc Certificate Manager.")
    }
}
