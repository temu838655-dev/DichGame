package com.dichgame.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Dò máy PS4/PS5 trong mạng gia đình cho Android: gắn ổ UDP vào đúng mạng Wi-Fi/LAN (không đi nhầm sang 4G hay VPN),
 * giữ khóa multicast của Wi-Fi, rồi giao cho [PsScanner] gửi gói hỏi.
 *
 * [onChange] được gọi từ luồng nền với danh sách máy hiện có.
 */
class PsFinder(
    private val ctx: Context,
    private val extraTargets: List<String>,
    private val onChange: (List<PsHost>) -> Unit,
) {
    private var scanner: PsScanner? = null
    private var lock: WifiManager.MulticastLock? = null
    private val sockets = ArrayList<java.net.DatagramSocket>()

    /** Mô tả mạng đang quét, hoặc lý do không quét được (hiện cho người dùng). */
    @Volatile var info: String = "Đang chuẩn bị dò mạng…"
        private set
    @Volatile var failed = false
        private set

    val rounds: Int get() = scanner?.rounds ?: 0
    val sent: Int get() = scanner?.sent ?: 0

    fun start() {
        if (scanner != null) return
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            lock = wm?.createMulticastLock("dg-ps-discover")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {
            lock = null
        }
        val legs = openLegs(ctx)
        if (legs.isEmpty()) {
            failed = true
            info = "Chưa thấy Wi-Fi/LAN: hãy kết nối điện thoại vào cùng Wi-Fi với máy chơi game"
            return
        }
        for (l in legs) sockets.add(l.socket)
        info = "Đang quét " + legs.joinToString(" · ") { it.label + " (" + (it.sweep.size) + " địa chỉ)" }
        val sc = PsScanner(legs, extraTargets.filter { it.isNotBlank() && PsDiscoverProto.ipToInt(it) != null }, onChange)
        scanner = sc
        sc.start()
    }

    fun stop() {
        try { scanner?.stop() } catch (_: Throwable) {}
        scanner = null
        for (s in sockets) try { s.close() } catch (_: Throwable) {}
        sockets.clear()
        try { lock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        lock = null
    }

    companion object {
        private class Iface(val ip: Int, val prefix: Int, val label: String, val net: Network?)


        /**
         * Mở sẵn các "chân" dò cho mọi mạng Wi-Fi/LAN của điện thoại (ổ UDP đã gắn vào đúng mạng, địa chỉ broadcast, dải địa chỉ).
         * Người gọi phải đóng các ổ [PsLeg.socket] khi xong.
         */
        fun openLegs(ctx: Context): List<PsLeg> {
            val legs = ArrayList<PsLeg>()
            for (f in try { interfaces(ctx) } catch (_: Throwable) { emptyList() }) {
                try {
                    val s = PsScanner.newSocket()
                    try { f.net?.bindSocket(s) } catch (_: Throwable) {}
                    legs.add(PsLeg(
                        s,
                        listOf(PsDiscoverProto.intToIp(PsDiscoverProto.broadcastOf(f.ip, f.prefix))),
                        PsDiscoverProto.sweepTargets(f.ip, f.prefix),
                        f.label + " " + PsDiscoverProto.intToIp(f.ip) + "/" + f.prefix,
                    ))
                } catch (_: Throwable) {
                }
            }
            return legs
        }

        private fun interfaces(ctx: Context): List<Iface> {
            val out = ArrayList<Iface>()
            val seen = HashSet<Int>()
            // 1) Mạng do hệ thống báo: ưu tiên Wi-Fi và LAN, bỏ 4G/VPN.
            try {
                val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                @Suppress("DEPRECATION")
                for (n in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(n) ?: continue
                    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val eth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    if (!wifi && !eth) continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                    val lp = cm.getLinkProperties(n) ?: continue
                    for (la in lp.linkAddresses) {
                        val a = la.address
                        if (a !is Inet4Address || a.isLoopbackAddress) continue
                        val ip = PsDiscoverProto.ipToInt(a.hostAddress ?: "") ?: continue
                        if (la.prefixLength !in 8..30) continue
                        if (seen.add(ip)) out.add(Iface(ip, la.prefixLength, if (wifi) "Wi-Fi" else "LAN", n))
                    }
                }
            } catch (_: Throwable) {
            }
            if (out.isNotEmpty()) return out
            // 2) Dự phòng: đọc thẳng các giao diện mạng (bỏ 4G: rmnet/ccmni/...).
            try {
                val list = NetworkInterface.getNetworkInterfaces()
                while (list != null && list.hasMoreElements()) {
                    val ni = list.nextElement()
                    if (!ni.isUp || ni.isLoopback || ni.isPointToPoint) continue
                    val n = ni.name.lowercase()
                    if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") || n.startsWith("tun") || n.startsWith("dummy")) continue
                    for (ia in ni.interfaceAddresses) {
                        val a = ia.address
                        if (a !is Inet4Address || a.isLoopbackAddress) continue
                        val ip = PsDiscoverProto.ipToInt(a.hostAddress ?: "") ?: continue
                        val pre = ia.networkPrefixLength.toInt()
                        if (pre !in 8..30) continue
                        if (seen.add(ip)) out.add(Iface(ip, pre, if (n.startsWith("eth")) "LAN" else "Wi-Fi", null))
                    }
                }
            } catch (_: Throwable) {
            }
            return out
        }
    }
}
