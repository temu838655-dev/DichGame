import Foundation
import Darwin
import CryptoKit
import CSSH2

enum LGInstallError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let text): return text }
    }
}

private final class LGNoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

/// One SSH session, used by a worker thread only. No password fallback or host key bypass.
private final class LGSSH {
    private var fd: Int32 = -1
    private var session: OpaquePointer?
    private static let initialized = libssh2_init(0)
    init(ip: String, key: Data, passphrase: String, approve: (String) -> Bool) throws {
        guard Self.initialized == 0, TVLink.privateIPv4(ip), passphrase.utf8.count == 6 else {
            throw LGInstallError.message("Kiểm tra IP và passphrase 6 ký tự")
        }
        do {
            fd = Darwin.socket(AF_INET, SOCK_STREAM, 0)
            guard fd >= 0 else { throw LGInstallError.message("Không mở được SSH") }
            var address = sockaddr_in()
            address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            address.sin_family = sa_family_t(AF_INET)
            address.sin_port = UInt16(9922).bigEndian
            guard inet_pton(AF_INET, ip, &address.sin_addr) == 1 else { throw LGInstallError.message("IP sai") }
            var one: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
            let flags = fcntl(fd, F_GETFL, 0)
            guard flags >= 0, fcntl(fd,F_SETFL,flags | O_NONBLOCK) == 0 else { throw LGInstallError.message("Lỗi socket") }
            let rc = withUnsafePointer(to: &address) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.connect(fd,$0,socklen_t(MemoryLayout<sockaddr_in>.size)) }
            }
            if rc != 0 {
                guard errno == EINPROGRESS else { throw LGInstallError.message("TV từ chối kết nối SSH") }
                var descriptor = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
                guard Darwin.poll(&descriptor,1,10000) > 0 else { throw LGInstallError.message("SSH hết thời gian chờ") }
                var err: Int32 = 0
                var len = socklen_t(MemoryLayout<Int32>.size)
                guard getsockopt(fd,SOL_SOCKET,SO_ERROR,&err,&len) == 0, err == 0 else {
                    throw LGInstallError.message("Không kết nối được Developer Mode")
                }
            }
            guard fcntl(fd,F_SETFL,flags) == 0, let s = libssh2_session_init_ex(nil,nil,nil,nil) else {
                throw LGInstallError.message("Không tạo được phiên SSH")
            }
            session = s
            libssh2_session_set_blocking(s,1)
            libssh2_session_set_timeout(s,15000)
            guard libssh2_session_handshake(s,fd) == 0 else { throw LGInstallError.message("Bắt tay SSH thất bại") }
            var length = 0
            var type: Int32 = 0
            guard let raw = libssh2_session_hostkey(s,&length,&type), length > 0, length <= 32768 else {
                throw LGInstallError.message("Không lấy được fingerprint TV")
            }
            let digest = Data(SHA256.hash(data: Data(bytes: raw, count: length)))
                .base64EncodedString().replacingOccurrences(of: "=",with: "")
            guard approve("SHA256:" + digest) else { throw LGInstallError.message("Chưa chấp nhận khóa TV") }
            let auth = key.withUnsafeBytes { bytes in
                libssh2_userauth_publickey_frommemory(s,"prisoner",8,nil,0,
                    bytes.bindMemory(to: CChar.self).baseAddress,bytes.count,passphrase)
            }
            guard auth == 0 else { throw LGInstallError.message("Xác thực Developer Mode thất bại; kiểm tra passphrase") }
        } catch { close(); throw error }
    }
    deinit { close() }
    private func close() {
        if let s = session {
            libssh2_session_set_timeout(s,2000)
            _ = libssh2_session_disconnect_ex(s,11,"DichGame","")
            _ = libssh2_session_free(s)
            session = nil
        }
        if fd >= 0 { Darwin.close(fd); fd = -1 }
    }
    func command(_ command: String, input: Data? = nil, seconds: Int = 30,
                 terminal: ((String) throws -> Bool)? = nil) throws -> (Int32,String) {
        guard let s = session else { throw LGInstallError.message("Chưa có phiên SSH") }
        libssh2_session_set_timeout(s,Int(seconds * 1000))
        guard let ch = libssh2_channel_open_ex(s,"session",7,2*1024*1024,32768,nil,0) else {
            throw LGInstallError.message("Không mở được kênh SSH")
        }
        defer {
            libssh2_session_set_timeout(s,2000)
            _ = libssh2_channel_close(ch)
            _ = libssh2_channel_free(ch)
        }
        // Discard stderr instead of contaminating the JSON subscription on stdout.
        _ = libssh2_channel_handle_extended_data2(ch,1)
        guard libssh2_channel_process_startup(ch,"exec",4,command,UInt32(command.utf8.count)) == 0 else {
            throw LGInstallError.message("TV từ chối lệnh SSH")
        }
        let deadline = ProcessInfo.processInfo.systemUptime + Double(seconds)
        func remaining() throws {
            let ms = Int((deadline - ProcessInfo.processInfo.systemUptime)*1000)
            guard ms > 0 else { throw LGInstallError.message("TV phản hồi quá lâu") }
            libssh2_session_set_timeout(s,ms)
        }
        if let input {
            try input.withUnsafeBytes { raw in
                guard let start = raw.bindMemory(to: CChar.self).baseAddress else { return }
                var pos = 0
                while pos < input.count {
                    try remaining()
                    let n = libssh2_channel_write_ex(ch,0,start.advanced(by: pos),min(32768,input.count-pos))
                    guard n > 0 else { throw LGInstallError.message("Truyền IPK thất bại") }
                    pos += n
                }
            }
        }
        try remaining()
        if terminal == nil || input != nil {
            guard libssh2_channel_send_eof(ch) == 0 else { throw LGInstallError.message("Không kết thúc được luồng SSH") }
        } // Interactive Luna subscription must keep stdin open until the final reply.
        var output = Data(), buffer = [CChar](repeating: 0,count:4096)
        while true {
            try remaining()
            let n = buffer.withUnsafeMutableBufferPointer { libssh2_channel_read_ex(ch,0,$0.baseAddress,$0.count) }
            guard n >= 0 else { throw LGInstallError.message("Đọc phản hồi SSH thất bại") }
            if n == 0 {
                if libssh2_channel_eof(ch) != 0 { break }
                Thread.sleep(forTimeInterval:0.02); continue
            }
            guard output.count + n <= 262144 else { throw LGInstallError.message("Phản hồi TV quá lớn") }
            output.append(contentsOf: buffer.prefix(n).map { UInt8(bitPattern:$0) })
            if try terminal?(String(decoding:output,as:UTF8.self)) == true { return (0,String(decoding:output,as:UTF8.self)) }
        }
        return (libssh2_channel_get_exit_status(ch),String(decoding:output,as:UTF8.self))
    }
}

enum LGInstaller {
    private static let app = "com.dichgame.subtitles"
    static func key(ip: String) async throws -> Data {
        guard TVLink.privateIPv4(ip), let url = URL(string:"http://\(ip):9991/webos_rsa") else {
            throw LGInstallError.message("IP TV không hợp lệ")
        }
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 5
        config.timeoutIntervalForResource = 10
        let session = URLSession(configuration:config,delegate:LGNoRedirect(),delegateQueue:nil)
        defer { session.invalidateAndCancel() }
        let (bytes,response) = try await session.bytes(from:url)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw LGInstallError.message("Bật Key Server trong Developer Mode trên LG")
        }
        var key = Data()
        for try await b in bytes {
            guard key.count < 32768 else { throw LGInstallError.message("Khóa TV quá lớn") }
            key.append(b)
        }
        let text = String(decoding:key,as:UTF8.self)
        guard text.hasPrefix("-----BEGIN "), text.contains("PRIVATE KEY-----") else {
            throw LGInstallError.message("Khóa Developer Mode không hợp lệ")
        }
        return key
    }
    private static func json(_ object: [String:Any]) throws -> String {
        let data = try JSONSerialization.data(withJSONObject:object)
        return String(decoding:data,as:UTF8.self)
    }
    private static func replies(_ output: String) -> [[String:Any]] {
        output.split(separator:"\n").compactMap { line in
            (try? JSONSerialization.jsonObject(with:Data(line.utf8))) as? [String:Any]
        }
    }
    static func install(ip: String, key: Data, passphrase: String, phoneIP: String?,
                        approve: (String)->Bool, progress: (String)->Void) throws -> String {
        guard let url = Bundle.main.url(forResource:"dichgame-lg",withExtension:"ipk") else {
            throw LGInstallError.message("Bản iPhone này chưa nhúng IPK LG")
        }
        let pkg = try Data(contentsOf:url)
        guard !pkg.isEmpty, pkg.count <= 16*1024*1024 else { throw LGInstallError.message("IPK không hợp lệ") }
        progress("Đang xác thực SSH")
        let ssh = try LGSSH(ip:ip,key:key,passphrase:passphrase,approve:approve)
        let remote = "/media/developer/temp/dichgame-" + UUID().uuidString.lowercased() + ".ipk"
        guard try ssh.command("/usr/bin/test -d /media/developer/temp || /bin/mkdir -p /media/developer/temp").0 == 0 else {
            throw LGInstallError.message("Không tạo được thư mục IPK")
        }
        defer { _ = try? ssh.command("/bin/rm -f '\(remote)'",seconds:5) }
        progress("Đang truyền IPK")
        guard try ssh.command("/bin/cat > '\(remote)'",input:pkg,seconds:60).0 == 0 else { throw LGInstallError.message("Không truyền được IPK") }
        let size = try ssh.command("/usr/bin/wc -c < '\(remote)'",seconds:10)
        guard size.0 == 0, Int(size.1.trimmingCharacters(in:.whitespacesAndNewlines)) == pkg.count else { throw LGInstallError.message("Kích thước IPK không khớp") }
        progress("Đang cài IPK")
        let payload = try json(["id":"com.ares.defaultName","ipkUrl":remote,"subscribe":true])
        var installed = false
        _ = try ssh.command("/usr/bin/luna-send-pub -i luna://com.webos.appInstallService/dev/install '\(payload)'",seconds:120) { output in
            for reply in replies(output) {
                let details = reply["details"] as? [String:Any]
                let state = (details?["state"] as? String ?? "").uppercased()
                if reply["returnValue"] as? Bool == false || state.contains("FAILED") {
                    throw LGInstallError.message(details?["reason"] as? String ?? reply["errorText"] as? String ?? "TV từ chối cài IPK")
                }
                if state == "INSTALLED" || state.hasPrefix("SUCCESS") { installed = true; return true }
            }
            return false
        }
        guard installed else { throw LGInstallError.message("TV chưa xác nhận cài thành công") }
        progress("Đã cài. Đang mở app")
        var launch: [String:Any] = ["id":app,"subscribe":false]
        if let phoneIP, TVLink.privateIPv4(phoneIP) { launch["params"] = ["server":"\(phoneIP):8780"] }
        let launchJSON = try json(launch)
        let result = try? ssh.command("/usr/bin/luna-send-pub -n 1 luna://com.webos.applicationManager/launch '\(launchJSON)'",seconds:15)
        if let result, result.0 == 0, replies(result.1).contains(where:{ $0["returnValue"] as? Bool == true }) {
            return "TV xác nhận đã cài và mở DichGame."
        }
        return "TV xác nhận đã cài. Hãy mở DichGame trên TV."
    }
}
