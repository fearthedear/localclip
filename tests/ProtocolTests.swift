import Foundation
@main struct Tests {
    static var count=0
    static func check(_ b: Bool,_ label: String) { precondition(b,label); count+=1 }
    static func reject(_ label: String,_ action: ()throws->Void) { var failed=false;do{try action()}catch{failed=true};check(failed,label) }
    static func main()throws{
        let key=Data(0..<32),s=Data(32..<64),c=Data(64..<96)
        check(Wire.hex(Wire.mac(key,"client",s,c))==CommandLine.arguments[1],"HMAC vector")
        check(Wire.discoveryID(key)=="4ce9b50ef959bd12074a57de1566bab0","stable domain-separated discovery ID")
        let msg=try ClipMessage(type:1,body:Data("hello 🌏\ntext".utf8)).data
        let tx=FrameCipher(key:key),rx=FrameCipher(key:key);let frame=try tx.seal(msg)
        check(try rx.open(frame)==msg,"AES roundtrip")
        reject("replay rejected"){_ = try rx.open(frame)}
        var altered=frame;altered[altered.count-1]^=1
        reject("tamper rejected"){_ = try FrameCipher(key:key).open(altered)}
        reject("wrong key"){_ = try FrameCipher(key:Data(repeating:255,count:32)).open(frame)}
        reject("size limit"){_ = try Wire.length(Data([127,255,255,255]))}
        reject("short header"){_ = try Wire.length(Data([0,0,0,32]))}
        reject("truncated"){_ = try ClipMessage(data:Data(repeating:0,count:16))}
        reject("unknown type"){_ = try ClipMessage(type:9)}
        reject("invalid utf8"){_ = try ClipMessage(type:1,body:Data([255]))}
        reject("invalid PNG"){_ = try ClipMessage(type:2,body:Data(repeating:0,count:12))}
        reject("invalid ping"){_ = try ClipMessage(type:3,body:Data([1]))}
        reject("content limit"){_ = try ClipMessage(type:1,body:Data(repeating:0,count:Wire.maxContent+1))}
        reject("invalid hex"){_ = try Wire.unhex("abc")}
        check(try Wire.unhex(Wire.hex(key))==key,"hex roundtrip")
        let next=try tx.seal(msg);reject("out of order"){_ = try FrameCipher(key:key).open(next)}
        let fresh=FrameCipher(key:key);_ = try fresh.open(frame);check(try fresh.open(next)==msg,"ordered sequence")
        check(Wire.mac(key,"c2s",s,c) != Wire.mac(key,"s2c",s,c),"direction keys distinct")
        print("Swift protocol tests: \(count) passed")
    }
}
