import Foundation
@main struct TestServer {
    static func main()throws{
        let server=ClipServer(), secret=try Wire.unhex(CommandLine.arguments[2])
        server.onStatus={ print($0);fflush(stdout) }
        server.onMessage={ m in
            if m.type<=2 { do { try server.send(ClipMessage(type:4,id:m.id));try server.send(m) }catch{} }
        }
        try server.start(secret:secret,port:UInt16(CommandLine.arguments[1])!)
        RunLoop.main.run()
    }
}
