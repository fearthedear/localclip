import Foundation
@main struct AndroidTestServer {
    static func main()throws{
        let s=ClipServer(),key=try Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        s.onStatus={print($0);fflush(stdout)}
        s.onMessage={m in
            do {
                if m.type==4 {print("ANDROID_APPLIED");fflush(stdout);return}
                try s.send(ClipMessage(type:4,id:m.id))
                if m.type==1 {
                    let text=String(data:m.body,encoding:.utf8)!
                    print("ANDROID_TEXT \(text)");fflush(stdout)
                    if text=="test:background" {
                        DispatchQueue.global().asyncAfter(deadline:.now()+2){try? s.send(ClipMessage(type:1,body:Data("mac:background".utf8)))}
                    }else{try s.send(ClipMessage(type:1,body:Data(("mac:"+text).utf8)))}
                }else if m.type==2{print("ANDROID_PNG \(m.body.count)");fflush(stdout);try s.send(m)}
            }catch{print("ERROR \(error)");fflush(stdout)}
        }
        try s.start(secret:key,port:47493);RunLoop.main.run()
    }
}
