import Foundation
import Network
import Combine
import Darwin

/// Foreground, opt-in local HTTP server matching the LG and Samsung receiver protocol.
/// State and connections are owned by a single serial queue.
final class TVSubtitleServer: ObservableObject {
    @Published private(set) var running = false
    @Published private(set) var status = "Chưa bật máy chủ"
    @Published private(set) var addresses: [String] = []
    @Published private(set) var apkLinks: [String] = []
    private var apkData: Data?
    private var apkPath = ""
    private var apkExpiry: TimeInterval = 0
    private var apkGeneration = UUID()
    private let queue = DispatchQueue(label: "dichgame.ios.tvserver")
    private var listener: NWListener?
    private var connections: [UUID: NWConnection] = [:]
    private var apkTransfers = Set<UUID>()
    private struct PairRequest {
        let code: String
        let expiry: TimeInterval
        var token: String?
    }
    private var requests: [String: PairRequest] = [:]
    private let tokenKey = "dichgame.web.tv.tokens"
    private var tokens = Set<String>()
    private var sequence = 0
    private var subtitle = ""
    private var subtitleUntil: TimeInterval = 0

    init() {
        tokens = Set((UserDefaults.standard.stringArray(forKey: tokenKey) ?? []).prefix(16))
    }

    func start() {
        queue.async { [weak self] in
            guard let self, self.listener == nil else { return }
            do {
                let server = try NWListener(using: .tcp, on: 8780)
                self.listener = server
                server.stateUpdateHandler = { [weak self, weak server] state in
                    guard let self, let server, self.listener === server else { return }
                    switch state {
                    case .ready: self.report(true, "Máy chủ đang chạy", addresses: Self.localAddresses()); self.reportAPKLinks()
                    case .failed(let error): self.stopInternal(); self.report(false, error.localizedDescription)
                    case .cancelled: self.stopInternal(); self.report(false, "Đã tắt")
                    default: break
                    }
                }
                server.newConnectionHandler = { [weak self] in self?.accept($0) }
                server.start(queue: self.queue)
            } catch { self.report(false, error.localizedDescription) }
        }
    }

    func stop() {
        queue.async { [weak self] in self?.stopInternal(); self?.report(false, "Đã tắt máy chủ") }
    }

    private func stopInternal() {
        let old = listener
        listener = nil
        old?.cancel()
        connections.values.forEach { $0.cancel() }
        connections.removeAll()
        apkTransfers.removeAll()
        requests.removeAll()
        clearAPKInternal()
        subtitle = ""
        sequence += 1
    }

    /// Share only a document explicitly selected by the user; never install silently.
    func shareAPK(_ data: Data) {
        queue.async { [weak self] in
            guard let self, (1024...32*1024*1024).contains(data.count) else { return }
            self.clearAPKInternal()
            self.apkData = data
            self.apkPath = "/" + Self.secret() + "/DichGame-TV.apk"
            self.apkExpiry = ProcessInfo.processInfo.systemUptime + 600
            self.apkGeneration = UUID()
            let generation = self.apkGeneration
            self.queue.asyncAfter(deadline: .now() + 600) { [weak self] in
                guard let self, self.apkGeneration == generation else { return }
                self.clearAPKInternal()
            }
            if self.listener == nil { self.start() } else { self.reportAPKLinks() }
        }
    }

    func stopAPK() { queue.async { [weak self] in self?.clearAPKInternal() } }

    private func clearAPKInternal() {
        for id in Array(apkTransfers) { close(id) }
        apkData = nil; apkPath = ""; apkExpiry = 0; apkGeneration = UUID()
        DispatchQueue.main.async { [weak self] in self?.apkLinks = [] }
    }

    private func reportAPKLinks() {
        guard apkData != nil, !apkPath.isEmpty else { return }
        let links = Self.localAddresses().map { "http://\($0):8780\(apkPath)" }
        DispatchQueue.main.async { [weak self] in self?.apkLinks = links }
    }

    func publish(_ text: String) {
        queue.async { [weak self] in
            guard let self, self.listener != nil else { return }
            self.subtitle = text
            self.subtitleUntil = ProcessInfo.processInfo.systemUptime + 12
            self.sequence += 1
        }
    }

    func approve(_ code: String) {
        queue.async { [weak self] in
            guard let self else { return }
            self.prune()
            guard let id = self.requests.first(where: { $0.value.code == code && $0.value.token == nil })?.key,
                  self.tokens.count < 16 else {
                self.report(self.listener != nil, "Mã sai, hết hạn hoặc đạt giới hạn TV", addresses: Self.localAddresses()); return
            }
            let token = Self.secret()
            self.tokens.insert(token)
            self.requests[id]?.token = token
            self.persist()
            self.report(self.listener != nil, "Đã ghép TV", addresses: Self.localAddresses())
        }
    }

    func revoke() {
        queue.async { [weak self] in
            guard let self else { return }
            self.tokens.removeAll()
            self.requests.removeAll()
            self.persist()
            self.report(self.listener != nil, "Đã bỏ ghép tất cả TV", addresses: Self.localAddresses())
        }
    }

    private func report(_ active: Bool, _ message: String, addresses: [String] = []) {
        DispatchQueue.main.async { [weak self] in
            self?.running = active
            self?.status = message
            self?.addresses = addresses
        }
    }

    private func persist() { UserDefaults.standard.set(Array(tokens), forKey: tokenKey) }
    private func prune() {
        let time = ProcessInfo.processInfo.systemUptime
        requests = requests.filter { $0.value.expiry > time }
    }
    private static func secret() -> String {
        (UUID().uuidString + UUID().uuidString).replacingOccurrences(of: "-", with: "").lowercased()
    }

    private static func privateHost(_ host: NWEndpoint.Host) -> Bool {
        let raw = String(describing: host)
        if raw.hasPrefix("::ffff:") { return TVLink.privateIPv4(String(raw.dropFirst(7))) }
        return TVLink.privateIPv4(raw)
    }

    private func accept(_ connection: NWConnection) {
        guard listener != nil, connections.count < 16,
              case let .hostPort(host, _) = connection.endpoint,
              Self.privateHost(host) else { connection.cancel(); return }
        let id = UUID()
        connections[id] = connection
        connection.start(queue: queue)
        queue.asyncAfter(deadline: .now() + 8) { [weak self] in
            guard let self, !self.apkTransfers.contains(id) else { return }
            self.close(id)
        }
        receive(id, data: Data())
    }

    private func close(_ id: UUID) { apkTransfers.remove(id); connections.removeValue(forKey: id)?.cancel() }

    private func receive(_ id: UUID, data: Data) {
        guard let conn = connections[id] else { return }
        conn.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] bytes, _, complete, error in
            guard let self, self.connections[id] != nil else { return }
            var buffer = data
            if let bytes { buffer.append(bytes) }
            guard error == nil, buffer.count <= 8192 else { self.close(id); return }
            if let request = String(data: buffer, encoding: .utf8), request.contains("\r\n\r\n") {
                self.handle(id, request: request)
            } else if complete { self.close(id) }
            else { self.receive(id, data: buffer) }
        }
    }

    private func handle(_ id: UUID, request: String) {
        let first = request.components(separatedBy: "\r\n").first ?? ""
        let parts = first.split(separator: " ")
        guard parts.count == 3, (parts[0] == "GET" || parts[0] == "HEAD"), parts[1].hasPrefix("/"),
              let url = URLComponents(string: String(parts[1])) else { reply(id, 400, [:]); return }
        func param(_ name: String) -> String { url.queryItems?.first(where: { $0.name == name })?.value ?? "" }
        if !apkPath.isEmpty && url.path == apkPath && url.query == nil {
            guard let data = apkData, ProcessInfo.processInfo.systemUptime < apkExpiry else { reply(id, 404, [:]); return }
            serveAPK(id, data: data, headOnly: parts[0] == "HEAD")
            return
        }
        guard parts[0] == "GET" else { reply(id, 404, [:]); return }
        prune()
        switch url.path {
        case "/receiver/register":
            guard requests.count < 8 else { reply(id, 429, [:]); return }
            var code = String(Int.random(in: 10000000...99999999))
            while requests.values.contains(where: { $0.code == code }) {
                code = String(Int.random(in: 10000000...99999999))
            }
            let key = Self.secret()
            requests[key] = PairRequest(code: code, expiry: ProcessInfo.processInfo.systemUptime + 120, token: nil)
            reply(id, 200, ["id": key, "code": code])
        case "/receiver/wait":
            guard let item = requests[param("id")] else { reply(id, 404, [:]); return }
            reply(id, 200, ["token": item.token ?? ""])
        case "/receiver/poll":
            let token = param("token")
            guard tokens.contains(token) else { reply(id, 403, [:]); return }
            let since = Int(param("since")) ?? -1
            if since == sequence {
                queue.asyncAfter(deadline: .now() + 1) { [weak self] in self?.frame(id, token: token) }
            } else { frame(id, token: token) }
        default: reply(id, 404, [:])
        }
    }

    private func frame(_ id: UUID, token: String) {
        guard tokens.contains(token) else { reply(id, 403, [:]); return }
        if !subtitle.isEmpty && ProcessInfo.processInfo.systemUptime >= subtitleUntil { subtitle = ""; sequence += 1 }
        reply(id, 200, ["seq": sequence, "text": subtitle, "region": ""])
    }

    private func serveAPK(_ id: UUID, data: Data, headOnly: Bool) {
        guard let conn = connections[id] else { return }
        let header = "HTTP/1.1 200 OK\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: \(data.count)\r\nContent-Disposition: attachment; filename=DichGame-TV.apk\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        guard apkTransfers.count < 4 else { close(id); return }
        apkTransfers.insert(id)
        queue.asyncAfter(deadline: .now() + 120) { [weak self] in self?.close(id) }
        let generation = apkGeneration
        conn.send(content: Data(header.utf8), completion: .contentProcessed { [weak self] error in
            guard let self, error == nil, !headOnly else { self?.close(id); return }
            self.sendAPKChunk(id, data: data, offset: 0, generation: generation)
        })
    }

    private func sendAPKChunk(_ id: UUID, data: Data, offset: Int, generation: UUID) {
        guard let conn = connections[id], generation == apkGeneration,
              ProcessInfo.processInfo.systemUptime < apkExpiry, offset < data.count else { close(id); return }
        let end = min(offset + 65536, data.count)
        conn.send(content: data.subdata(in: offset..<end), completion: .contentProcessed { [weak self] error in
            guard let self, error == nil else { self?.close(id); return }
            self.sendAPKChunk(id, data: data, offset: end, generation: generation)
        })
    }

    private func reply(_ id: UUID, _ status: Int, _ object: [String: Any]) {
        guard let conn = connections[id], let body = try? JSONSerialization.data(withJSONObject: object) else { close(id); return }
        let reason = status == 200 ? "OK" : "Error"
        let header = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: \(body.count)\r\nAccess-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        var data = Data(header.utf8); data.append(body)
        conn.send(content: data, completion: .contentProcessed { [weak self] _ in self?.close(id) })
    }

    static func localAddresses() -> [String] {
        var head: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&head) == 0, let first = head else { return [] }
        defer { freeifaddrs(first) }
        var values = Set<String>()
        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        while let ptr = cursor {
            let item = ptr.pointee
            cursor = item.ifa_next
            guard let addr = item.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET) else { continue }
            var buffer = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            if getnameinfo(addr, socklen_t(addr.pointee.sa_len), &buffer, socklen_t(buffer.count), nil, 0, NI_NUMERICHOST) == 0 {
                let ip = String(cString: buffer)
                if TVLink.privateIPv4(ip) { values.insert(ip) }
            }
        }
        return values.sorted()
    }
}
