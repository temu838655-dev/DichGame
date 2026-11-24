import Foundation

/// Pure parser for the local Samsung Account callback; reject malformed and conflicting fields.
enum SamsungOAuthCallback {
    struct Account { let token: String; let userID: String; let email: String }
    private static func form(_ text: String) -> [String:String]? {
        var values = [String:String]()
        func decode(_ text: Substring) -> String? {
            String(text).replacingOccurrences(of:"+",with:" ").removingPercentEncoding
        }
        for field in text.split(separator:"&",omittingEmptySubsequences:true) {
            let parts = field.split(separator:"=",maxSplits:1,omittingEmptySubsequences:false)
            guard parts.count == 2,let key = decode(parts[0]),let value = decode(parts[1]),!key.isEmpty else {return nil}
            if let old = values[key],old != value {return nil}
            values[key] = value
        }
        return values
    }
    static func parse(header: [String],body: String,port: UInt16,state: String) -> Account? {
        guard let first = header.first else {return nil}
        let parts = first.split(separator:" ")
        guard parts.count == 3, ["GET","POST"].contains(String(parts[0])), parts[2].hasPrefix("HTTP/1."),
              parts[1].hasPrefix("/") else {return nil}
        let target = parts[1].split(separator:"?",maxSplits:1,omittingEmptySubsequences:false)
        guard target.first == "/signin/callback" else {return nil}
        var headers = [String:String]()
        for line in header.dropFirst() {
            let pair = line.split(separator:":",maxSplits:1,omittingEmptySubsequences:false)
            guard pair.count == 2 else {return nil}
            let name = pair[0].lowercased(),value = pair[1].trimmingCharacters(in:.whitespaces)
            if headers[name] != nil && ["host","origin","content-length","content-type"].contains(name) {return nil}
            headers[name] = value
        }
        guard let host = headers["host"]?.lowercased(),["localhost:\(port)","127.0.0.1:\(port)"].contains(host) else {return nil}
        if let origin = headers["origin"],!["https://account.samsung.com","null"].contains(origin) {return nil}
        if !body.isEmpty {
            guard parts[0] == "POST",headers["content-type"]?.lowercased().hasPrefix("application/x-www-form-urlencoded") == true else {return nil}
        }
        guard var query = form(target.count == 2 ? String(target[1]) : ""),let fields = form(body) else {return nil}
        for (key,value) in fields {
            if let old = query[key],old != value {return nil}
            query[key] = value
        }
        guard query["dg_state"] != nil || query["state"] != nil,
              query["dg_state"].map({$0 == state}) ?? true,
              query["state"].map({$0 == state}) ?? true,
              let code = query["code"],code.utf8.count <= 65536,
              let json = (try? JSONSerialization.jsonObject(with:Data(code.utf8))) as? [String:Any],
              let token = json["access_token"] as? String,!token.isEmpty,token.utf8.count <= 32768,
              let user = json["userId"] as? String,!user.isEmpty,user.utf8.count <= 512,
              (Int(String(describing:json["access_token_expires_in"] ?? 0)) ?? 0)>0 else {return nil}
        return Account(token:token,userID:user,email:String((json["inputEmailID"] as? String ?? "").prefix(320)))
    }
}
