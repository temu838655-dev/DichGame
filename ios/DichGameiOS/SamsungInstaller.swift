import Foundation
import Darwin
import ZIPFoundation

enum SamsungInstallError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let value): return value }
    }
}

private final class SamsungManifest: NSObject, XMLParserDelegate {
    var valid = false
    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        if elementName == "application" && namespaceURI == "http://tizen.org/ns/widgets" {
            valid = attributeDict["id"] == "DichGameTV.Receiver" && attributeDict["package"] == "DichGameTV"
        }
    }
}

/// Developer Mode transport. No AUTH bypass and no certificate issuance.
private final class SamsungSDB {
    private var fd: Int32 = -1
    private var nextID: UInt32 = 1
    private var maxPayload = 4096
    private var deadline = ProcessInfo.processInfo.systemUptime + 15
    private let cnxn: UInt32 = 0x4e584e43, open: UInt32 = 0x4e45504f
    private let okay: UInt32 = 0x59414b4f, wrte: UInt32 = 0x45545257, clse: UInt32 = 0x45534c43
    private let limit = 262144
    private struct Packet { let cmd: UInt32; let a: UInt32; let b: UInt32; let data: Data }

    init(ip: String) throws {
        guard TVLink.privateIPv4(ip) else { throw SamsungInstallError.message("IP TV không hợp lệ") }
        fd = Darwin.socket(AF_INET, SOCK_STREAM, 0)
        guard fd >= 0 else { throw SamsungInstallError.message("Không mở được SDB") }
        do {
            var address = sockaddr_in()
            address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
            address.sin_family = sa_family_t(AF_INET)
            address.sin_port = UInt16(26101).bigEndian
            guard inet_pton(AF_INET, ip, &address.sin_addr) == 1 else { throw SamsungInstallError.message("IP không hợp lệ") }
            var one: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &one, socklen_t(MemoryLayout<Int32>.size))
            let flags = fcntl(fd, F_GETFL, 0)
            guard flags >= 0, fcntl(fd, F_SETFL, flags | O_NONBLOCK) == 0 else { throw SamsungInstallError.message("Không cấu hình được kết nối") }
            let result = withUnsafePointer(to: &address) {
                $0.withMemoryRebound(to: sockaddr.self, capacity: 1) { Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) }
            }
            if result != 0 {
                guard errno == EINPROGRESS else { throw SamsungInstallError.message("TV từ chối kết nối SDB") }
                try ready(Int16(POLLOUT))
                var error: Int32 = 0
                var len = socklen_t(MemoryLayout<Int32>.size)
                guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &len) == 0, error == 0 else {
                    throw SamsungInstallError.message("Không kết nối được TV; kiểm tra Developer Mode và Host PC IP")
                }
            }
            try send(cnxn, 0x00100000, UInt32(limit), Data("host::\u{0}".utf8))
            let p = try read()
            guard p.cmd == cnxn, p.b >= 1024, !p.data.isEmpty else { throw SamsungInstallError.message("TV chưa chấp nhận SDB hoặc yêu cầu xác thực chưa hỗ trợ") }
            maxPayload = min(Int(p.b), limit)
        } catch { close(); throw error }
    }
    deinit { close() }
    func close() { if fd >= 0 { Darwin.close(fd); fd = -1 } }
    private func ready(_ event: Int16) throws {
        while true {
            let ms = (deadline - ProcessInfo.processInfo.systemUptime) * 1000
            guard ms > 0 else { throw SamsungInstallError.message("TV phản hồi quá lâu") }
            var p = pollfd(fd: fd, events: event, revents: 0)
            let n = Darwin.poll(&p, 1, Int32(min(ms, 120000)))
            if n > 0 && p.revents & event != 0 { return }
            if n < 0 && errno == EINTR { continue }
            throw SamsungInstallError.message("Mất kết nối SDB hoặc hết thời gian chờ")
        }
    }
    private static func word(_ n: UInt32) -> Data {
        var value = n.littleEndian
        return withUnsafeBytes(of: &value) { Data($0) }
    }
    private static func uint(_ data: Data, _ offset: Int) -> UInt32 {
        let start = data.startIndex + offset
        return (0..<4).reduce(UInt32(0)) { $0 | (UInt32(data[start + $1]) << ($1 * 8)) }
    }
    private func write(_ data: Data) throws {
        try data.withUnsafeBytes { raw in
            guard let base = raw.baseAddress else { return }
            var pos = 0
            while pos < raw.count {
                try ready(Int16(POLLOUT))
                let n = Darwin.send(fd, base.advanced(by: pos), raw.count - pos, 0)
                if n < 0 && (errno == EAGAIN || errno == EINTR) { continue }
                guard n > 0 else { throw SamsungInstallError.message("Không gửi được SDB") }
                pos += n
            }
        }
    }
    private func receive(_ count: Int) throws -> Data {
        var data = Data(count: count)
        try data.withUnsafeMutableBytes { (raw: UnsafeMutableRawBufferPointer) in
            guard let base = raw.baseAddress else { return }
            var pos = 0
            while pos < count {
                try ready(Int16(POLLIN))
                let n = Darwin.recv(fd, base.advanced(by: pos), count - pos, 0)
                if n < 0 && (errno == EAGAIN || errno == EINTR) { continue }
                guard n > 0 else { throw SamsungInstallError.message("TV đóng kết nối") }
                pos += n
            }
        }
        return data
    }
    private func send(_ cmd: UInt32, _ a: UInt32, _ b: UInt32, _ data: Data = Data()) throws {
        guard data.count <= limit else { throw SamsungInstallError.message("Gói SDB quá lớn") }
        let sum = data.reduce(UInt32(0)) { $0 &+ UInt32($1) }
        var packet = Data()
        for n in [cmd,a,b,UInt32(data.count),sum,~cmd] { packet.append(Self.word(n)) }
        packet.append(data); try write(packet)
    }
    private func read() throws -> Packet {
        let h = try receive(24)
        let cmd = Self.uint(h,0), len = Int(Self.uint(h,12))
        guard Self.uint(h,20) == ~cmd, len <= limit else { throw SamsungInstallError.message("SDB header không hợp lệ") }
        let data = try receive(len)
        guard data.reduce(UInt32(0), { $0 &+ UInt32($1) }) == Self.uint(h,16) else { throw SamsungInstallError.message("SDB checksum sai") }
        return Packet(cmd: cmd, a: Self.uint(h,4), b: Self.uint(h,8), data: data)
    }
    private func start(_ service: String, seconds: Double) throws -> (UInt32,UInt32) {
        deadline = ProcessInfo.processInfo.systemUptime + seconds
        let id = nextID; nextID += 1
        try send(open,id,0,Data((service + "\u{0}").utf8))
        let p = try read()
        guard p.cmd == okay, p.b == id, p.a != 0 else { throw SamsungInstallError.message("TV từ chối dịch vụ SDB") }
        return (id,p.a)
    }
    private func accept(_ p: Packet, _ stream: (UInt32,UInt32), _ data: inout Data) throws -> Bool {
        guard p.b == stream.0, p.a == stream.1 else { throw SamsungInstallError.message("SDB stream sai") }
        if p.cmd == clse { try? send(clse,stream.0,stream.1); return false }
        guard p.cmd == wrte, data.count + p.data.count <= 1024 * 1024 else { throw SamsungInstallError.message("Phản hồi SDB không hợp lệ") }
        data.append(p.data); try send(okay,stream.0,stream.1)
        return true
    }
    func response(_ service: String, seconds: Double, terminal: (String) -> Bool = { _ in false }) throws -> String {
        let s = try start(service, seconds: seconds)
        var data = Data()
        while try accept(read(), s, &data) {
            if terminal(String(decoding: data, as: UTF8.self)) { break }
        }
        return String(decoding: data, as: UTF8.self)
    }
    private func frame(_ tag: String, _ length: UInt32, _ payload: Data = Data()) -> Data {
        var data = Data(tag.utf8); data.append(Self.word(length)); data.append(payload); return data
    }
    func push(_ bytes: Data, remote: String) throws {
        let s = try start("sync:", seconds: 120)
        var response = Data()
        func sendFrame(_ data: Data) throws {
            try send(wrte,s.0,s.1,data)
            while true {
                let p = try read()
                if p.cmd == okay && p.a == s.1 && p.b == s.0 { return }
                guard try accept(p,s,&response) else { throw SamsungInstallError.message("TV đóng luồng truyền WGT") }
            }
        }
        let path = Data((remote + ",33188").utf8)
        try sendFrame(frame("SEND",UInt32(path.count),path))
        var pos = 0
        while pos < bytes.count {
            let end = min(bytes.count,pos + min(65536,maxPayload-8))
            let part = bytes.subdata(in: pos..<end)
            try sendFrame(frame("DATA",UInt32(part.count),part)); pos = end
        }
        try sendFrame(frame("DONE",UInt32(Date().timeIntervalSince1970)))
        while response.count < 8 {
            guard try accept(read(),s,&response) else { throw SamsungInstallError.message("TV chưa xác nhận truyền WGT") }
        }
        guard String(decoding: response.prefix(4), as: UTF8.self) == "OKAY" else {
            throw SamsungInstallError.message("TV từ chối nhận WGT")
        }
    }
}

enum SamsungInstaller {
    static func duid(ip: String) throws -> String {
        let caps = (try? { () throws -> String in
            let s = try SamsungSDB(ip:ip)
            return try s.response("capability:",seconds:5){$0.contains("sdk_toolpath:") && $0.hasSuffix("\n")}
        }()) ?? ""
        var commands = ["0 getduid","0 getduidgadget","/opt/etc/duid-gadget 2.0 2> /dev/null"]
        if caps.contains("product_version:1.0") {commands.swapAt(0,1)}
        for command in commands {
            if let output = try? { () throws -> String in
                let s = try SamsungSDB(ip:ip)
                return try s.response("shell:"+command,seconds:12) { text in
                    text.components(separatedBy:.newlines).contains{SamsungCertificateCrypto.validDUID($0.trimmingCharacters(in:.whitespacesAndNewlines))}
                }
            }() {
                for line in output.components(separatedBy:.newlines) {
                    let value = line.replacingOccurrences(of:"(?i)^duid:\\s*",with:"",options:.regularExpression).trimmingCharacters(in:.whitespacesAndNewlines)
                    if SamsungCertificateCrypto.validDUID(value) {return value}
                }
            }
        }
        throw SamsungInstallError.message("Không đọc được DUID. Nhập DUID chứng chỉ từ TV.")
    }
    private static func validate(_ bytes: Data) throws {
        let archive = try Archive(data: bytes, accessMode: .read)
        guard let config = archive["config.xml"], config.uncompressedSize <= 65536,
              let author = archive["author-signature.xml"], author.uncompressedSize > 0,
              let distributor = archive["signature1.xml"], distributor.uncompressedSize > 0 else {
            throw SamsungInstallError.message("Chọn WGT DichGame đã ký cho TV, không chọn ZIP mã nguồn")
        }
        var xml = Data()
        _ = try archive.extract(config) { xml.append($0) }
        let delegate = SamsungManifest()
        let parser = XMLParser(data: xml)
        parser.shouldProcessNamespaces = true
        parser.shouldResolveExternalEntities = false
        parser.delegate = delegate
        guard parser.parse(), delegate.valid else { throw SamsungInstallError.message("WGT không phải DichGame TV") }
    }
    static func install(ip: String, file: URL, progress: (String) -> Void) throws -> String {
        let accessible = file.startAccessingSecurityScopedResource()
        defer { if accessible { file.stopAccessingSecurityScopedResource() } }
        let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard (1...32*1024*1024).contains(size) else { throw SamsungInstallError.message("WGT rỗng hoặc vượt 32 MB") }
        let bytes = try Data(contentsOf: file)
        guard bytes.count <= 32*1024*1024 else { throw SamsungInstallError.message("WGT quá lớn") }
        return try install(ip:ip,bytes:bytes,progress:progress)
    }
    static func install(ip: String, bytes: Data, progress: (String)->Void) throws -> String {
        guard TVLink.privateIPv4(ip),(1...32*1024*1024).contains(bytes.count) else {throw SamsungInstallError.message("IP hoặc WGT không hợp lệ")}
        try validate(bytes)
        progress("Đang kết nối Developer Mode")
        let probe = try SamsungSDB(ip: ip)
        let caps = (try? probe.response("capability:", seconds: 5) { $0.contains("sdk_toolpath:") && $0.hasSuffix("\n") }) ?? ""
        probe.close()
        let reported = caps.split(separator: "\n").first { $0.hasPrefix("sdk_toolpath:") }.map { String($0.dropFirst(13)).trimmingCharacters(in: .whitespacesAndNewlines) }
        let allowed = ["/home/owner/share/tmp/sdk_tools", "/opt/usr/apps/tmp"]
        let directory = reported.map { $0.hasSuffix("/") ? String($0.dropLast()) : $0 }.flatMap { allowed.contains($0) ? $0 : nil } ?? allowed[caps.contains("platform_version:2.") ? 1 : 0]
        let remote = directory + "/dichgame-" + UUID().uuidString.lowercased() + ".wgt"
        let transfer = try SamsungSDB(ip: ip)
        progress("Đang truyền WGT")
        try transfer.push(bytes,remote: remote); transfer.close()
        progress("Đang cài WGT")
        let install = try SamsungSDB(ip: ip)
        let output = try install.response("shell:0 vd_appinstall DichGameTV " + remote, seconds: 120) {
            $0.localizedCaseInsensitiveContains("install completed") || $0.localizedCaseInsensitiveContains("install failed")
        }
        install.close()
        guard output.localizedCaseInsensitiveContains("install completed"), !output.localizedCaseInsensitiveContains("install failed") else {
            throw SamsungInstallError.message("TV chưa xác nhận cài: " + String(output.prefix(300)))
        }
        progress("Đã cài. Đang mở ứng dụng")
        let opened = (try? { () throws -> String in
            let s = try SamsungSDB(ip: ip)
            return try s.response("shell:0 was_execute DichGameTV.Receiver", seconds: 20) {
                $0.localizedCaseInsensitiveContains("launched") || $0.localizedCaseInsensitiveContains("resumed") || $0.localizedCaseInsensitiveContains("spend time")
            }
        }()) ?? ""
        if !opened.localizedCaseInsensitiveContains("failed") &&
            (opened.localizedCaseInsensitiveContains("launched") || opened.localizedCaseInsensitiveContains("resumed")) {
            return "TV đã xác nhận cài và mở DichGame."
        }
        return "TV đã xác nhận cài. Mở DichGame trong Apps trên TV."
    }
}
