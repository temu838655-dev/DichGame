import Foundation
@main struct SamsungOAuthCallbackTests {
    static func main() {
        let state = "test-state",port: UInt16 = 4794
        let json = "{\"access_token\":\"a+b=&test\",\"userId\":\"test-user\",\"access_token_expires_in\":3600}"
        let encoded = json.addingPercentEncoding(withAllowedCharacters:.alphanumerics)!
        let header = ["POST /signin/callback?dg_state=test-state HTTP/1.1","Host: localhost:4794","Origin: https://account.samsung.com","Content-Type: application/x-www-form-urlencoded"]
        func parse(_ h:[String],_ b:String)->SamsungOAuthCallback.Account? {SamsungOAuthCallback.parse(header:h,body:b,port:port,state:state)}
        precondition(parse(header,"code="+encoded)?.token == "a+b=&test")
        precondition(parse(header,"code=%GG") == nil)
        precondition(parse(header,"code="+encoded+"&state=wrong") == nil)
        precondition(parse(header,"code="+encoded+"&code=other") == nil)
        precondition(parse(header.map{$0.replacingOccurrences(of:"localhost:4794",with:"evil.example")},"code="+encoded) == nil)
        precondition(parse(header.map{$0.replacingOccurrences(of:"https://account.samsung.com",with:"https://evil.example")},"code="+encoded) == nil)
        precondition(parse(["GET /signin/callback?code="+encoded+" HTTP/1.1","Host: localhost:4794"],"") == nil)
        precondition(parse(["GET /signin/callback?dg_state=test-state&code="+encoded+" HTTP/1.1","Host: localhost:4794"],"")?.userID == "test-user")
        print("Samsung OAuth callback tests passed")
    }
}
