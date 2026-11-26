import Foundation
import Security
import UIKit

enum GameTranslationError: LocalizedError {
    case missingKey, noText, api(String), invalidImage

    var errorDescription: String? {
        switch self {
        case .missingKey: return "Hãy nhập ít nhất một khóa Gemini trong Cài đặt."
        case .noText: return "Gemini không trả về bản dịch. Hãy thử ảnh rõ hơn."
        case .invalidImage: return "Không đọc được ảnh. Hãy chọn ảnh khác."
        case .api(let message): return message
        }
    }
}

enum SecureKeys {
    private static let service = "com.dichgame.ios.gemini"
    private static let account = "api-keys"

    static func load() -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }

    static func save(_ text: String) {
        let keyQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let data = Data(text.utf8)
        let updated = SecItemUpdate(keyQuery as CFDictionary,
                                    [kSecValueData as String: data] as CFDictionary)
        if updated == errSecItemNotFound {
            var values = keyQuery
            values[kSecValueData as String] = data
            values[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(values as CFDictionary, nil)
        }
    }
}

enum GameImageTranslator {
    static func translate(image: UIImage, game: String, language: String, rawKeys: String) async throws -> String {
        let keys = rawKeys.split(whereSeparator: { ",;\n \t".contains($0) })
            .map(String.init).filter { !$0.isEmpty }
        guard !keys.isEmpty else { throw GameTranslationError.missingKey }
        guard let data = image.jpegData(compressionQuality: 0.7) else {
            throw GameTranslationError.invalidImage
        }
        let prompt = """
        Bạn là biên dịch viên phụ đề game sang tiếng Việt. Tên game: \(game).
        Ngôn ngữ gốc: \(language). Đọc chữ thực sự có trong ảnh; ưu tiên phụ đề hoặc
        hội thoại game và các nhãn menu rõ ràng. Dịch tự nhiên và đúng ngữ cảnh của game.
        Giữ nguyên tên riêng và không sáng tác lời thoại không nhìn thấy.
        Chỉ trả về bản dịch tiếng Việt; nếu ảnh không có chữ đáng dịch,
        hãy trả lời "Không tìm thấy chữ cần dịch".
        """
        let body: [String: Any] = [
            "contents": [["parts": [
                ["text": prompt],
                ["inline_data": ["mime_type": "image/jpeg", "data": data.base64EncodedString()]]
            ]]],
            "generationConfig": ["temperature": 0.15]
        ]
        let payload = try JSONSerialization.data(withJSONObject: body)
        var lastError: Error = GameTranslationError.noText
        for key in keys {
            var request = URLRequest(
                url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent")!
            )
            request.httpMethod = "POST"
            request.httpBody = payload
            request.timeoutInterval = 45
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            do {
                let (responseData, response) = try await URLSession.shared.data(for: request)
                guard let http = response as? HTTPURLResponse else {
                    throw GameTranslationError.api("Máy chủ không trả về HTTP.")
                }
                guard (200..<300).contains(http.statusCode) else {
                    let message = String(data: responseData, encoding: .utf8)?
                        .prefix(240).description ?? "Lỗi không rõ"
                    let error = GameTranslationError.api("Gemini HTTP \(http.statusCode): \(message)")
                    if [401, 403].contains(http.statusCode) {
                        lastError = error
                        continue
                    }
                    throw error
                }
                guard let json = try JSONSerialization.jsonObject(with: responseData) as? [String: Any],
                      let candidates = json["candidates"] as? [[String: Any]],
                      let content = candidates.first?["content"] as? [String: Any],
                      let parts = content["parts"] as? [[String: Any]] else {
                    throw GameTranslationError.noText
                }
                let text = parts.compactMap { $0["text"] as? String }
                    .joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { throw GameTranslationError.noText }
                return text
            } catch {
                // Lỗi mạng không có nghĩa khóa hết lượt; thông báo rõ thay vì xoay khóa vô hạn.
                throw error
            }
        }
        throw lastError
    }
}
