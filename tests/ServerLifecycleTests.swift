import Foundation
@main struct ServerLifecycleTests {
    static func main()throws{
        let server=ClipServer()
        for _ in 0..<30 { try server.start(secret:Data(repeating:1,count:32),port:47495);server.stop() }
        print("Listener start/pause/restart: 30 cycles passed")
    }
}
