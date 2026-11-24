import Foundation

enum TVLink {
    enum Failure: LocalizedError {
        case invalidIP, invalidCode, rejected(Int), invalidToken
        var errorDescription: String? {
            switch self {
            case .invalidIP: return "Nhập IPv4 nội bộ của TV."
            case .invalidCode: return "Mã ghép gồm 8 chữ số."
            case .rejected(let status): return "TV trả mã \(status). Kiểm tra kết nối hoặc ghép lại."
            case .invalidToken: return "TV không trả mã xác thực hợp lệ."
            }
        }
    }

    static func privateIPv4(_ ip: String) -> Bool {
        let parts = ip.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return false }
        let numbers = parts.compactMap { part -> Int? in
            guard !part.isEmpty, part.utf8.allSatisfy({ $0 >= 48 && $0 <= 57 }),
                  let n = Int(part), (0...255).contains(n), String(n) == String(part)
            else { return nil }
            return n
        }
        guard numbers.count == 4 else { return false }
        return numbers[0] == 10 || (numbers[0] == 192 && numbers[1] == 168)
            || (numbers[0] == 172 && (16...31).contains(numbers[1]))
    }

    static func validToken(_ token: String) -> Bool {
        token.utf8.count == 32 && token.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) }
    }

    private static func post(ip: String, path: String, data: Data, token: String? = nil) async throws -> Data {
        guard privateIPv4(ip), let url = URL(string: "http://\(ip):8765\(path)") else { throw Failure.invalidIP }
        var request = URLRequest(url: url, timeoutInterval: 5)
        request.httpMethod = "POST"
        request.httpBody = data
        request.setValue(token == nil ? "text/plain" : "application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue(token, forHTTPHeaderField: "X-Token") }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 8
        let session = URLSession(configuration: configuration, delegate: NoRedirect(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let (body, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1
        guard status == 200 else { throw Failure.rejected(status) }
        return body
    }

    static func pair(ip: String, code: String) async throws -> String {
        guard code.utf8.count == 8, code.utf8.allSatisfy({ (48...57).contains($0) }) else { throw Failure.invalidCode }
        let response = try await post(ip: ip, path: "/pair", data: Data(code.utf8))
        let token = String(decoding: response, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
        guard validToken(token) else { throw Failure.invalidToken }
        return token
    }

    static func send(ip: String, token: String, text: String, size: Int, speak: Bool, clear: Bool = false) async throws {
        guard validToken(token) else { throw Failure.invalidToken }
        let body: [String: Any] = ["kind": clear ? "clear" : "sub", "text": clear ? "" : text,
            "size": min(80, max(14, size)), "speak": speak && !clear,
            "fg": -1, "bg": Int32(bitPattern: 0xB3000000)]
        _ = try await post(ip: ip, path: "/sub", data: JSONSerialization.data(withJSONObject: body), token: token)
    }
}

private final class NoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
