import Foundation
import Security
import CryptoKit
import ZIPFoundation

enum SamsungCertificateCrypto {
    static let names = ["config.xml","index.html","pairing.js","receiver.js"]
    static func validDUID(_ s: String) -> Bool {
        (12...160).contains(s.utf8.count) && s.range(of: "^[A-Za-z0-9#.:=\\-]+$",options:.regularExpression) != nil && s.utf8.contains { (48...57).contains($0) }
    }
    private static func der(_ tag: UInt8,_ bytes: Data) -> Data {
        var h = Data([tag])
        if bytes.count < 128 { h.append(UInt8(bytes.count)) }
        else {
            var n = bytes.count, len = [UInt8]()
            while n > 0 { len.insert(UInt8(n & 255),at:0); n >>= 8 }
            h.append(UInt8(128+len.count)); h.append(contentsOf:len)
        }
        h.append(bytes); return h
    }
    private static func seq(_ parts: Data...) -> Data { der(48,parts.reduce(Data()) { $0 + $1 }) }
    private static func oid(_ b: UInt8...) -> Data { der(6,Data(b)) }
    private static func sign(_ key: SecKey,_ data: Data,_ algorithm: SecKeyAlgorithm) throws -> Data {
        var error: Unmanaged<CFError>?
        guard let signed = SecKeyCreateSignature(key,algorithm,data as CFData,&error) else {
            throw SamsungInstallError.message("Không ký được dữ liệu bằng khóa Keychain")
        }
        return signed as Data
    }
    static func key(_ name: String) throws -> SecKey {
        let tag = Data(("com.dichgame.samsung."+name).utf8)
        let query: [String:Any] = [kSecClass as String:kSecClassKey,kSecAttrApplicationTag as String:tag,
            kSecAttrKeyType as String:kSecAttrKeyTypeRSA,kSecAttrKeyClass as String:kSecAttrKeyClassPrivate,kSecReturnRef as String:true]
        var found: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary,&found)
        if status == errSecSuccess, let found { return found as! SecKey }
        guard status == errSecItemNotFound else { throw SamsungInstallError.message("Không đọc được khóa Keychain") }
        let attrs: [String:Any] = [kSecAttrKeyType as String:kSecAttrKeyTypeRSA,kSecAttrKeySizeInBits as String:2048,
            kSecPrivateKeyAttrs as String:[kSecAttrIsPermanent as String:true,kSecAttrApplicationTag as String:tag,
                kSecAttrAccessible as String:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]]
        guard let key = SecKeyCreateRandomKey(attrs as CFDictionary,nil) else { throw SamsungInstallError.message("Không tạo được khóa RSA") }
        return key
    }
    static func csr(_ key: SecKey,identity: String,duid: String? = nil) throws -> String {
        guard (1...80).contains(identity.count), !identity.unicodeScalars.contains(where:{CharacterSet.controlCharacters.contains($0)}),
              duid == nil || validDUID(duid!), let publicKey = SecKeyCopyPublicKey(key),
              let raw = SecKeyCopyExternalRepresentation(publicKey,nil) else { throw SamsungInstallError.message("Tên hoặc DUID không hợp lệ") }
        let name = seq(der(49,seq(oid(0x55,0x04,0x03),der(12,Data(identity.utf8)))))
        let spki = seq(seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,1,1,1),der(5,Data())),der(3,Data([0]) + (raw as Data)))
        var attrs = der(160,Data())
        if let duid {
            let san = seq(der(134,Data("URN:tizen:packageid=".utf8)),der(134,Data("URN:tizen:deviceid=\(duid)".utf8)))
            let extensions = seq(seq(oid(0x55,0x1d,0x11),der(4,san)))
            attrs = der(160,seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,1,9,14),der(49,extensions)))
        }
        let info = seq(der(2,Data([0])),name,spki,attrs)
        let signature = try sign(key,info,.rsaSignatureMessagePKCS1v15SHA512)
        let req = seq(info,seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,1,1,13),der(5,Data())),der(3,Data([0])+signature))
        return "-----BEGIN CERTIFICATE REQUEST-----\n"+req.base64EncodedString(options:[.lineLength64Characters,.endLineWithLineFeed])+"\n-----END CERTIFICATE REQUEST-----\n"
    }
    static func certificate(_ bytes: Data) throws -> SecCertificate {
        let text = String(decoding:bytes,as:UTF8.self)
        var der = bytes
        if text.contains("-----BEGIN CERTIFICATE-----") {
            let lines = text.components(separatedBy:.newlines)
            guard let start = lines.firstIndex(of:"-----BEGIN CERTIFICATE-----"), let end = lines.firstIndex(of:"-----END CERTIFICATE-----"), end > start,
                  let decoded = Data(base64Encoded:lines[(start+1)..<end].joined(),options:.ignoreUnknownCharacters) else { throw SamsungInstallError.message("PEM không hợp lệ") }
            der = decoded
        }
        guard let cert = SecCertificateCreateWithData(nil,der as CFData) else { throw SamsungInstallError.message("Samsung không trả chứng chỉ X.509") }
        return cert
    }
    // Read a DER node without interpreting arbitrary extensions; bound every length.
    private struct Node { let tag: UInt8; let body: Data }
    private static func nodes(_ data: Data) -> [Node]? {
        let b = [UInt8](data); var p = 0, result = [Node]()
        while p < b.count {
            guard p+2 <= b.count, result.count < 128 else { return nil }
            let tag = b[p]; p += 1
            var n = Int(b[p]); p += 1
            if n >= 128 {
                let width = n-128; n = 0
                guard (1...4).contains(width),p+width <= b.count else { return nil }
                for _ in 0..<width { n = n*256+Int(b[p]); p += 1 }
            }
            guard n <= b.count-p else { return nil }
            result.append(Node(tag:tag,body:Data(b[p..<p+n]))); p += n
        }
        return result
    }
    private static func hasDUID(_ cert: SecCertificate,_ duid: String) -> Bool {
        guard let root = nodes(SecCertificateCopyData(cert) as Data)?.first,
              let tbs = nodes(root.body)?.first, let ext = nodes(tbs.body)?.first(where:{$0.tag == 163}),
              let seq = nodes(ext.body)?.first, let extensions = nodes(seq.body) else { return false }
        for item in extensions {
            guard let fields = nodes(item.body), fields.first?.tag == 6, fields.first?.body == Data([0x55,0x1d,0x11]),
                  let octet = fields.last,octet.tag == 4,let san = nodes(octet.body)?.first,let entries = nodes(san.body) else { continue }
            if entries.contains(where:{$0.tag == 134 && String(decoding:$0.body,as:UTF8.self).caseInsensitiveCompare("URN:tizen:deviceid="+duid) == .orderedSame}) { return true }
        }
        return false
    }
    static func checked(_ data: Data,key: SecKey,ca: SecCertificate,duid: String? = nil) throws -> SecCertificate {
        let cert = try certificate(data)
        guard let expected = SecKeyCopyPublicKey(key),let actual = SecCertificateCopyKey(cert),
              let a = SecKeyCopyExternalRepresentation(expected,nil),let b = SecKeyCopyExternalRepresentation(actual,nil),
              (a as Data) == (b as Data) else { throw SamsungInstallError.message("Chứng chỉ không khớp khóa") }
        var trust: SecTrust?
        guard SecTrustCreateWithCertificates([cert,ca] as CFArray,SecPolicyCreateBasicX509(),&trust) == errSecSuccess,let trust,
              SecTrustSetAnchorCertificates(trust,[ca] as CFArray) == errSecSuccess,
              SecTrustSetAnchorCertificatesOnly(trust,true) == errSecSuccess,
              SecTrustSetNetworkFetchAllowed(trust,false) == errSecSuccess,
              SecTrustEvaluateWithError(trust,nil) else { throw SamsungInstallError.message("Chứng chỉ không được CA Samsung xác nhận") }
        if let duid, !hasDUID(cert,duid) { throw SamsungInstallError.message("Chứng chỉ thiếu DUID TV") }
        return cert
    }
    static func hash(_ value: String) -> String { SHA256.hash(data:Data(value.utf8)).map{String(format:"%02x",$0)}.joined() }
    private static func digest(_ data: Data) -> String { Data(SHA256.hash(data:data)).base64EncodedString() }
    private static func xml(_ files: [String:Data],key: SecKey,chain: [SecCertificate],author: Bool) throws -> Data {
        let ds = "http://www.w3.org/2000/09/xmldsig#", dsp = "http://www.w3.org/2009/xmldsig-properties"
        let widget = "http://www.w3.org/ns/widgets-digsig#", c14n = "http://www.w3.org/2001/10/xml-exc-c14n#"
        let c14n11 = "http://www.w3.org/2006/12/xml-c14n11", sha = "http://www.w3.org/2001/04/xmlenc#sha256", rsa = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"
        let id = author ? "AuthorSignature" : "DistributorSignature", role = author ? "author" : "distributor"
        let props = "<SignatureProperties xmlns:dsp=\"\(dsp)\"><SignatureProperty Id=\"profile\" Target=\"#\(id)\"><dsp:Profile URI=\"\(widget)profile\"></dsp:Profile></SignatureProperty><SignatureProperty Id=\"role\" Target=\"#\(id)\"><dsp:Role URI=\"\(widget)role-\(role)\"></dsp:Role></SignatureProperty><SignatureProperty Id=\"identifier\" Target=\"#\(id)\"><dsp:Identifier></dsp:Identifier></SignatureProperty></SignatureProperties>"
        let obj = "<Object Id=\"prop\">\(props)</Object>", canon = "<Object xmlns=\"\(ds)\" Id=\"prop\">\(props)</Object>"
        func ref(_ uri: String,_ hash: String,_ transform: Bool = false) -> String {
            "<Reference URI=\"\(uri)\">"+(transform ? "<Transforms><Transform Algorithm=\"\(c14n11)\"></Transform></Transforms>" : "")+"<DigestMethod Algorithm=\"\(sha)\"></DigestMethod><DigestValue>\(hash)</DigestValue></Reference>"
        }
        let inside = "<CanonicalizationMethod Algorithm=\"\(c14n)\"></CanonicalizationMethod><SignatureMethod Algorithm=\"\(rsa)\"></SignatureMethod>"+files.keys.sorted().map{ref($0,digest(files[$0]!))}.joined()+ref("#prop",digest(Data(canon.utf8)),true)
        let info = Data("<SignedInfo xmlns=\"\(ds)\">\(inside)</SignedInfo>".utf8)
        let signature = try sign(key,info,.rsaSignatureMessagePKCS1v15SHA256)
        guard let pub = SecCertificateCopyKey(chain[0]), SecKeyVerifySignature(pub,.rsaSignatureMessagePKCS1v15SHA256,info as CFData,signature as CFData,nil) else { throw SamsungInstallError.message("Không xác minh được chữ ký WGT") }
        let certs = chain.map{"<X509Certificate>\((SecCertificateCopyData($0) as Data).base64EncodedString())</X509Certificate>"}.joined()
        return Data("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Signature xmlns=\"\(ds)\" Id=\"\(id)\"><SignedInfo>\(inside)</SignedInfo><SignatureValue>\(signature.base64EncodedString())</SignatureValue><KeyInfo><X509Data>\(certs)</X509Data></KeyInfo>\(obj)</Signature>".utf8)
    }
    static func wgt(author: SecKey,authorChain: [SecCertificate],distributor: SecKey,distributorChain: [SecCertificate]) throws -> Data {
        guard let url = Bundle.main.url(forResource:"DichGame-Samsung-source",withExtension:"zip") else { throw SamsungInstallError.message("Thiếu dự án Tizen trong IPA") }
        let source = try Archive(url:url,accessMode:.read)
        var files = [String:Data]()
        for name in names {
            guard let entry = source[name],entry.uncompressedSize <= 1048576 else { throw SamsungInstallError.message("Nguồn WGT không hợp lệ") }
            var bytes = Data()
            _ = try source.extract(entry){bytes.append($0)}
            files[name] = bytes
        }
        files["author-signature.xml"] = try xml(files,key:author,chain:authorChain,author:true)
        files["signature1.xml"] = try xml(files,key:distributor,chain:distributorChain,author:false)
        let archive = try Archive(data:Data(),accessMode:.create)
        for name in files.keys.sorted() {
            let data = files[name]!
            try archive.addEntry(with:name,type:.file,uncompressedSize:Int64(data.count),compressionMethod:.deflate){ pos,size in
                let start = Int(pos); return data.subdata(in:start..<min(start+size,data.count))
            }
        }
        guard let bytes = archive.data else { throw SamsungInstallError.message("Không tạo được WGT") }
        return bytes
    }
}
