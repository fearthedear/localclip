import Foundation
import Darwin

final class DiscoveryAdvertiser: NSObject, NetServiceDelegate {
    static let type="_localclip._tcp."
    private var service: NetService?
    var onStatus: (String)->Void = { _ in }
    func start(secret: Data,port: UInt16) {
        stop()
        let id=Wire.discoveryID(secret)
        let item=NetService(domain:"local.",type:Self.type,name:"LocalClip-"+id,port:Int32(port))
        item.delegate=self
        item.setTXTRecord(NetService.data(fromTXTRecord:["id":Data(id.utf8),"v":Data("1".utf8)]))
        service=item;onStatus("Starting local Wi-Fi discovery…");item.publish()
    }
    func stop() { service?.stop();service?.delegate=nil;service=nil;onStatus("Local discovery paused") }
    func netServiceDidPublish(_ sender: NetService) { onStatus("Discoverable on local Wi-Fi · address changes are handled automatically") }
    func netService(_ sender: NetService,didNotPublish errorDict: [String:NSNumber]) { onStatus("Wi-Fi discovery unavailable · check Local Network permission or use a Tailscale name") }
}

struct TailnetSnapshot {
    let ip: String
    let name: String
    let peers: [String]
}
enum TailscaleAddress {
    static func parse(_ data: Data,local: [String]) throws -> TailnetSnapshot {
        guard data.count<1_048_576,let json=try JSONSerialization.jsonObject(with:data) as? [String:Any],json["BackendState"] as? String=="Running",let me=json["Self"] as? [String:Any],let addresses=me["TailscaleIPs"] as? [String],let ip=addresses.first(where:{Wire.tailnetIP($0)&&local.contains($0)}) else { throw LCError.invalid("Tailscale is not connected on this Mac") }
        let raw=(me["DNSName"] as? String) ?? ""
        let name=raw.hasSuffix(".") ? String(raw.dropLast()) : raw
        let validName=name.hasSuffix(".ts.net") && name.count<=253 && name.range(of:"^[A-Za-z0-9.-]+$",options:.regularExpression) != nil
        var peers=[String]()
        if let entries=json["Peer"] as? [String:Any] {
            for value in entries.values {
                guard let peer=value as? [String:Any],peer["Online"] as? Bool==true,let ips=peer["TailscaleIPs"] as? [String],let address=ips.first(where:Wire.tailnetIP),address != ip else { continue }
                if !peers.contains(address) { peers.append(address) }
            }
        }
        return TailnetSnapshot(ip:ip,name:validName ? name : ip,peers:Array(peers.sorted().prefix(64)))
    }
    static func snapshot(local: [String]) throws -> TailnetSnapshot {
        let cli="/Applications/Tailscale.app/Contents/MacOS/Tailscale"
        guard FileManager.default.isExecutableFile(atPath:cli) else { throw LCError.invalid("Tailscale is not installed in Applications") }
        let process=Process(),pipe=Pipe();process.executableURL=URL(fileURLWithPath:cli);process.arguments=["status","--json"];process.standardOutput=pipe;process.standardError=FileHandle.nullDevice
        try process.run()
        let deadline=DispatchWorkItem { if process.isRunning { process.terminate() } };DispatchQueue.global().asyncAfter(deadline:.now()+5,execute:deadline)
        let data=pipe.fileHandleForReading.readDataToEndOfFile();process.waitUntilExit();deadline.cancel()
        guard process.terminationStatus==0 else { throw LCError.invalid("Could not read local Tailscale status") }
        return try parse(data,local:local)
    }
    static func name(local: [String]) throws -> String { try snapshot(local:local).name }
}

/// Unicast only to bounded, online peers returned by this Mac's local Tailscale client.
final class TailnetAnnouncer {
    private let lock=NSLock()
    private var generation=0
    private var busy=false
    private var timer: Timer?
    var onStatus: (TailnetSnapshot?,String)->Void = { _,_ in }
    func start(secret: Data,port: UInt16,addresses: @escaping ()->[String]) {
        stop();let token=generation
        poll(secret:secret,port:port,token:token,addresses:addresses)
        timer=Timer.scheduledTimer(withTimeInterval:15,repeats:true) { [weak self] _ in self?.poll(secret:secret,port:port,token:token,addresses:addresses) }
    }
    func stop() {
        timer?.invalidate();timer=nil
        lock.lock();generation+=1;busy=false;lock.unlock()
    }
    private func poll(secret: Data,port: UInt16,token: Int,addresses: @escaping ()->[String]) {
        lock.lock();guard generation==token && !busy else { lock.unlock();return };busy=true;lock.unlock()
        DispatchQueue.global(qos:.utility).async { [weak self] in
            guard let self else { return }
            let result=Result { ()throws -> TailnetSnapshot in
                let snapshot=try TailscaleAddress.snapshot(local:addresses())
                let packet=try Wire.tailnetBeacon(secret,nonce:Wire.random(32),host:snapshot.ip,port:port)
                for peer in snapshot.peers {
                    self.lock.lock();guard self.generation==token else { self.lock.unlock();return snapshot }
                    do { try Self.send(packet,source:snapshot.ip,destination:peer) } catch { self.lock.unlock();throw error }
                    self.lock.unlock()
                }
                return snapshot
            }
            DispatchQueue.main.async {
                self.lock.lock();let active=self.generation==token;if active { self.busy=false };self.lock.unlock();guard active else { return }
                switch result {
                case .success(let snapshot): self.onStatus(snapshot,"Tailscale first · discovering paired phone on tailnet")
                case .failure: self.onStatus(nil,"Tailscale discovery unavailable · local Wi-Fi remains available")
                }
            }
        }
    }
    static func send(_ packet: Data,source: String,destination: String,port: UInt16=47390)throws {
        let fd=socket(AF_INET,SOCK_DGRAM,0);guard fd>=0 else { throw LCError.invalid("Cannot open tailnet discovery socket") };defer { close(fd) }
        var local=sockaddr_in(),remote=sockaddr_in();local.sin_len=UInt8(MemoryLayout<sockaddr_in>.size);remote.sin_len=local.sin_len;local.sin_family=sa_family_t(AF_INET);remote.sin_family=local.sin_family;remote.sin_port=port.bigEndian
        guard source.withCString({inet_pton(AF_INET,$0,&local.sin_addr)})==1,destination.withCString({inet_pton(AF_INET,$0,&remote.sin_addr)})==1 else { throw LCError.invalid("Invalid numeric discovery address") }
        let bound=withUnsafePointer(to:&local){$0.withMemoryRebound(to:sockaddr.self,capacity:1){bind(fd,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}}
        guard bound==0 else { throw LCError.invalid("Tailscale address is not available") }
        let sent=packet.withUnsafeBytes { bytes in withUnsafePointer(to:&remote){$0.withMemoryRebound(to:sockaddr.self,capacity:1){sendto(fd,bytes.baseAddress,bytes.count,0,$0,socklen_t(MemoryLayout<sockaddr_in>.size))}} }
        guard sent==packet.count else { throw LCError.invalid("Could not send tailnet discovery message") }
    }
}
