import Foundation
import Darwin
@main struct TailnetTests {
    static var count=0
    static func check(_ ok:Bool,_ label:String){precondition(ok,label);count+=1;print("PASS \(label)")}
    static func main()throws{
        let data=Data(#"{"BackendState":"Running","Self":{"DNSName":"mac.tail123.ts.net.","TailscaleIPs":["100.90.0.1"]},"Peer":{"a":{"Online":true,"TailscaleIPs":["100.90.0.2"]},"b":{"Online":false,"TailscaleIPs":["100.90.0.3"]},"c":{"Online":true,"TailscaleIPs":["192.168.1.10"]}}}"#.utf8)
        let snapshot=try TailscaleAddress.parse(data,local:["100.90.0.1"])
        check(snapshot.name=="mac.tail123.ts.net" && snapshot.ip=="100.90.0.1","verified self supplies stable Tailscale pairing address")
        check(snapshot.peers==["100.90.0.2"],"discovery targets only online numeric tailnet peers")
        var rejected=false;do{_ = try TailscaleAddress.parse(data,local:["192.168.1.10"])}catch{rejected=true};check(rejected,"unbound or stale self address rejected")
        rejected=false;do{_ = try TailscaleAddress.parse(Data("{}".utf8),local:[])}catch{rejected=true};check(rejected,"unexpected status response rejected")
        let fd=socket(AF_INET,SOCK_DGRAM,0);precondition(fd>=0);defer{close(fd)}
        var address=sockaddr_in();address.sin_len=UInt8(MemoryLayout<sockaddr_in>.size);address.sin_family=sa_family_t(AF_INET);address.sin_addr.s_addr=inet_addr("127.0.0.1")
        let bound=withUnsafePointer(to:&address){$0.withMemoryRebound(to:sockaddr.self,capacity:1){bind(fd,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}};precondition(bound==0)
        var length=socklen_t(MemoryLayout<sockaddr_in>.size);_ = withUnsafeMutablePointer(to:&address){$0.withMemoryRebound(to:sockaddr.self,capacity:1){getsockname(fd,$0,&length)}}
        var timeout=timeval(tv_sec:2,tv_usec:0);setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,socklen_t(MemoryLayout<timeval>.size))
        let packet=try Wire.tailnetBeacon(Data(0..<32),nonce:Data(32..<64),host:"100.90.0.1",port:47391)
        try TailnetAnnouncer.send(packet,source:"127.0.0.1",destination:"127.0.0.1",port:UInt16(bigEndian:address.sin_port))
        var received=Data(count:75);let size=received.withUnsafeMutableBytes{recv(fd,$0.baseAddress,75,0)}
        check(size==74 && Data(received.prefix(size))==packet,"native UDP sender transmits exact authenticated discovery bytes")
        print("Mac tailnet discovery: \(count) assertions passed")
    }
}
