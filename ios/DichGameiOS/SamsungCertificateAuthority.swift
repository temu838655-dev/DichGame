import Foundation
import Security

enum SamsungCertificateAuthority {
    struct Account { let token: String; let userID: String; let email: String }
    private final class NoRedirect: NSObject, URLSessionTaskDelegate {
        func urlSession(_ session: URLSession,task: URLSessionTask,willPerformHTTPRedirection response: HTTPURLResponse,newRequest request: URLRequest,completionHandler: @escaping (URLRequest?)->Void) { completionHandler(nil) }
    }
    private static func request(_ path: String,account: Account,csr: String,distributor: Bool,platform: Bool) async throws -> Data {
        let boundary = "DichGame-"+UUID().uuidString
        var body = Data()
        func append(_ s: String) { body.append(contentsOf:s.utf8) }
        func field(_ key: String,_ value: String) throws {
            guard value.utf8.count <= 32768,!value.contains("\r"),!value.contains("\n") else { throw SamsungInstallError.message("Dữ liệu Samsung không hợp lệ") }
            append("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(key)\"\r\n\r\n\(value)\r\n")
        }
        try field("access_token",account.token);try field("user_id",account.userID)
        if distributor { try field("privilege_level","Public");try field("developer_type","Individual") }
        if platform { try field("platform","VD") }
        let name = distributor ? "distributor.csr" : "author.csr"
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"csr\"; filename=\"\(name)\"\r\nContent-Type: application/octet-stream\r\n\r\n\(csr)\r\n--\(boundary)--\r\n")
        var req = URLRequest(url:URL(string:"https://svdca.samsungqbe.com"+path)!)
        req.httpMethod = "POST";req.httpBody = body;req.setValue("multipart/form-data; boundary="+boundary,forHTTPHeaderField:"Content-Type")
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 60;config.timeoutIntervalForResource = 90
        let session = URLSession(configuration:config,delegate:NoRedirect(),delegateQueue:nil)
        defer{session.invalidateAndCancel()}
        let (bytes,response) = try await session.bytes(for:req)
        guard let status = (response as? HTTPURLResponse)?.statusCode,status == 200 else {
            throw SamsungInstallError.message("Samsung CA từ chối yêu cầu HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0)")
        }
        var result = Data()
        for try await byte in bytes { guard result.count < 262144 else {throw SamsungInstallError.message("Phản hồi CA quá lớn")};result.append(byte) }
        guard !result.isEmpty else {throw SamsungInstallError.message("CA không trả chứng chỉ")}
        return result
    }
    private static func ca(_ name: String,_ ext: String) throws -> SecCertificate {
        guard let url = Bundle.main.url(forResource:name,withExtension:ext) else {throw SamsungInstallError.message("Thiếu Samsung CA")}
        return try SamsungCertificateCrypto.certificate(Data(contentsOf:url))
    }
    private static func store(_ name: String,_ data: Data) throws {
        let query: [String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"com.dichgame.samsung.certs",kSecAttrAccount as String:name]
        let status = SecItemUpdate(query as CFDictionary,[kSecValueData as String:data] as CFDictionary)
        if status == errSecItemNotFound {
            var create = query;create[kSecValueData as String] = data;create[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(create as CFDictionary,nil) == errSecSuccess else {throw SamsungInstallError.message("Không lưu được chứng chỉ")}
        } else if status != errSecSuccess {throw SamsungInstallError.message("Không cập nhật được chứng chỉ")}
    }
    private static func load(_ name: String) -> Data? {
        let query: [String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"com.dichgame.samsung.certs",kSecAttrAccount as String:name,kSecReturnData as String:true]
        var item: CFTypeRef?
        return SecItemCopyMatching(query as CFDictionary,&item) == errSecSuccess ? item as? Data : nil
    }
    static func hasCertificates(_ duid: String) -> Bool {load("author") != nil && load(SamsungCertificateCrypto.hash(duid)) != nil}
    static func sign(duid: String,identity: String,account: Account?,progress: @escaping (String)->Void) async throws -> Data {
        guard SamsungCertificateCrypto.validDUID(duid) else {throw SamsungInstallError.message("DUID không hợp lệ")}
        let hash = SamsungCertificateCrypto.hash(duid)
        let author = try SamsungCertificateCrypto.key("author"),distributor = try SamsungCertificateCrypto.key("dist."+hash)
        let aCA = try ca("vd_tizen_dev_author_ca","cer"),dCA = try ca("vd_tizen_dev_public2","crt")
        let a: SecCertificate
        if let data = load("author"),let existing = try? SamsungCertificateCrypto.checked(data,key:author,ca:aCA) {a = existing}
        else {
            guard let account else {throw SamsungInstallError.message("Cần đăng nhập Samsung")}
            progress("Đang cấp chứng chỉ tác giả")
            a = try SamsungCertificateCrypto.checked(await request("/apis/v3/authors",account:account,csr:SamsungCertificateCrypto.csr(author,identity:identity),distributor:false,platform:true),key:author,ca:aCA)
            try store("author",SecCertificateCopyData(a) as Data)
        }
        let d: SecCertificate
        if let data = load(hash),let existing = try? SamsungCertificateCrypto.checked(data,key:distributor,ca:dCA,duid:duid) {d = existing}
        else {
            guard let account else {throw SamsungInstallError.message("Cần đăng nhập Samsung")}
            progress("Đang cấp chứng chỉ phân phối cho TV")
            let csr = try SamsungCertificateCrypto.csr(distributor,identity:"TizenSDK",duid:duid)
            _ = try await request("/apis/v1/distributors",account:account,csr:csr,distributor:true,platform:true)
            d = try SamsungCertificateCrypto.checked(await request("/apis/v3/distributors",account:account,csr:csr,distributor:true,platform:false),key:distributor,ca:dCA,duid:duid)
            try store(hash,SecCertificateCopyData(d) as Data)
        }
        progress("Đang ký WGT")
        return try SamsungCertificateCrypto.wgt(author:author,authorChain:[a,aCA],distributor:distributor,distributorChain:[d,dCA])
    }
}
