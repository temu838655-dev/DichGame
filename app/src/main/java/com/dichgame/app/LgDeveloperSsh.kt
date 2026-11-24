package com.dichgame.app

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import android.util.Base64

/** SSH authentication with explicit host-key approval. Invoke on a worker thread. */
internal object LgDeveloperSsh {
    fun connect(ip: String, passphrase: ByteArray, approve: (String) -> Boolean): Session {
        require(LgDeveloperKey.isPrivateIpv4(ip))
        require(passphrase.size == 6)
        val key = LgDeveloperKey.fetch(ip)
        val jsch = JSch()
        try {
            jsch.addIdentity("lg-developer", key, null, passphrase)
        } finally {
            key.fill(0)
            passphrase.fill(0)
        }
        jsch.setHostKeyRepository(object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val fingerprint = "SHA256:" + Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(key),
                    Base64.NO_WRAP or Base64.NO_PADDING)
                return if (approve(fingerprint)) HostKeyRepository.OK else HostKeyRepository.CHANGED
            }
            override fun add(hostkey: HostKey, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "explicit-user-approval"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        })
        val session = jsch.getSession("prisoner", ip, 9922)
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey")
        try {
            session.connect(10000)
            return session
        } catch (e: Exception) {
            session.disconnect()
            throw e
        }
    }
}
