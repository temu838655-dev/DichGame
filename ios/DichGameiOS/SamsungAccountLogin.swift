import Foundation
import Network
import SafariServices
import SwiftUI

/// An explicit foreground browser login. Accept only a loopback callback with the
/// randomly generated request state; never store the Samsung password or token.
final class SamsungAccountLogin {
    private let queue = DispatchQueue(label:"dichgame.samsung.login")
    private let state = UUID().uuidString + UUID().uuidString
    private var listener: NWListener?
    private var connections: [UUID:NWConnection] = [:]
    private var finished = false
    private var callbackPort: UInt16 = 0
    private let completion: (Result<SamsungCertificateAuthority.Account,Error>)->Void
    init(completion: @escaping (Result<SamsungCertificateAuthority.Account,Error>)->Void) {self.completion = completion}
    func start(open: @escaping (URL)->Void) {
        queue.async {
            guard !self.finished,self.listener == nil else {return}
            do {
                let port = NWEndpoint.Port(rawValue:UInt16.random(in:4794...4813))!
                self.callbackPort = port.rawValue
                let parameters = NWParameters.tcp
                parameters.requiredLocalEndpoint = .hostPort(host:"127.0.0.1",port:port)
                let listener = try NWListener(using:parameters,on:port)
                self.listener = listener
                listener.stateUpdateHandler = { [weak self] status in
                    guard let self,!self.finished else {return}
                    switch status {
                    case .ready:
                        var url = URLComponents(string:"https://account.samsung.com/mobile/account/check.do")!
                        url.queryItems = [URLQueryItem(name:"serviceID",value:"v285zxnl3h"),URLQueryItem(name:"actionID",value:"StartOAuth2"),
                            URLQueryItem(name:"accessToken",value:"Y"),
                            URLQueryItem(name:"redirect_uri",value:"http://localhost:\(port.rawValue)/signin/callback?dg_state=\(self.state)"),
                            URLQueryItem(name:"state",value:self.state)]
                        if let target = url.url {DispatchQueue.main.async {open(target)}}
                    case .failed(let error):self.finish(.failure(error))
                    default:break
                    }
                }
                listener.newConnectionHandler = { [weak self] conn in self?.accept(conn) }
                listener.start(queue:self.queue)
                self.queue.asyncAfter(deadline:.now()+300) { [weak self] in self?.finish(.failure(SamsungInstallError.message("Đăng nhập Samsung hết hạn"))) }
            } catch {self.finish(.failure(error))}
        }
    }
    func cancel() {queue.async {self.finish(.failure(SamsungInstallError.message("Đã hủy đăng nhập Samsung")))}}
    private func finish(_ result: Result<SamsungCertificateAuthority.Account,Error>) {
        guard !finished else {return}
        finished = true;listener?.cancel();listener = nil
        connections.values.forEach{$0.cancel()};connections.removeAll()
        DispatchQueue.main.async {self.completion(result)}
    }
    private func close(_ id: UUID) {connections.removeValue(forKey:id)?.cancel()}
    private func accept(_ conn: NWConnection) {
        guard !finished,connections.count < 4,case let .hostPort(host,_) = conn.endpoint,
              ["127.0.0.1","::ffff:127.0.0.1","::1"].contains(String(describing:host)) else {conn.cancel();return}
        let id = UUID();connections[id] = conn;conn.start(queue:queue)
        queue.asyncAfter(deadline:.now()+8){[weak self] in self?.close(id)}
        receive(id,buffer:Data())
    }
    private func receive(_ id: UUID,buffer: Data) {
        guard let conn = connections[id] else {return}
        conn.receive(minimumIncompleteLength:1,maximumLength:4096){[weak self] bytes,_,done,error in
            guard let self,self.connections[id] != nil else {return}
            var data = buffer;if let bytes {data.append(bytes)}
            guard error == nil,data.count <= 73728 else {self.close(id);return}
            if let split = data.range(of:Data("\r\n\r\n".utf8)) {
                let header = String(decoding:data[..<split.lowerBound],as:UTF8.self)
                let lines = header.components(separatedBy:"\r\n")
                let lengthLine = lines.first{$0.lowercased().hasPrefix("content-length:")}
                let length = lengthLine.flatMap{Int($0.split(separator:":",maxSplits:1).last?.trimmingCharacters(in:.whitespaces) ?? "")} ?? 0
                guard (0...65536).contains(length),!lines.contains(where:{$0.lowercased().hasPrefix("transfer-encoding:")}) else {self.close(id);return}
                let body = data[split.upperBound...]
                if body.count >= length {
                    self.handle(id,header:lines,body:String(decoding:body.prefix(length),as:UTF8.self));return
                }
            }
            if done {self.close(id)} else {self.receive(id,buffer:data)}
        }
    }
    private func handle(_ id: UUID,header: [String],body: String) {
        guard let conn = connections[id] else {return}
        let account = SamsungOAuthCallback.parse(header:header,body:body,port:callbackPort,state:state)
            .map { SamsungCertificateAuthority.Account(token:$0.token,userID:$0.userID,email:$0.email) }
        let message = account == nil ? "Invalid Samsung callback. Retry in DichGame." : "Samsung login received. Return to DichGame."
        let body = Data(message.utf8)
        let status = account == nil ? "400 Bad Request" : "200 OK"
        let header = "HTTP/1.1 \(status)\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n"
        conn.send(content:Data(header.utf8)+body,completion:.contentProcessed{[weak self] _ in
            self?.close(id)
            if let account {self?.finish(.success(account))}
        })
    }
}

struct SamsungLoginPage: Identifiable {let id = UUID();let url: URL}
struct SamsungLoginBrowser: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context:Context)->SFSafariViewController {SFSafariViewController(url:url)}
    func updateUIViewController(_ controller:SFSafariViewController,context:Context) {}
}
