import Foundation
import ZIPFoundation

enum TVAPKDocument {
    static func read(_ url: URL) throws -> Data {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard url.pathExtension.lowercased() == "apk", (1024...32*1024*1024).contains(size) else {
            throw SamsungInstallError.message("Chọn DichGame-TV.apk từ bản phát hành chính thức (tối đa 32 MB).")
        }
        let data = try Data(contentsOf: url)
        guard (1024...32*1024*1024).contains(data.count) else {
            throw SamsungInstallError.message("Kích thước APK không hợp lệ.")
        }
        let archive = try Archive(data: data, accessMode: .read)
        guard archive["AndroidManifest.xml"] != nil, archive["classes.dex"] != nil else {
            throw SamsungInstallError.message("Tệp không có cấu trúc APK hợp lệ.")
        }
        // Structural validation only; Android's package installer verifies signature and update identity.
        return data
    }
}
