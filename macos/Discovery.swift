import Foundation

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

enum TailscaleAddress {
    static func name(local: [String]) throws -> String {
        let cli="/Applications/Tailscale.app/Contents/MacOS/Tailscale"
        guard FileManager.default.isExecutableFile(atPath:cli) else { throw LCError.invalid("Tailscale is not installed in Applications. You can paste its MagicDNS name instead.") }
        let process=Process(),pipe=Pipe();process.executableURL=URL(fileURLWithPath:cli);process.arguments=["status","--json"];process.standardOutput=pipe;process.standardError=FileHandle.nullDevice
        try process.run()
        let deadline=DispatchWorkItem { if process.isRunning { process.terminate() } };DispatchQueue.global().asyncAfter(deadline:.now()+5,execute:deadline)
        let data=pipe.fileHandleForReading.readDataToEndOfFile();process.waitUntilExit();deadline.cancel()
        guard process.terminationStatus==0,data.count<1_048_576,let json=try JSONSerialization.jsonObject(with:data) as? [String:Any],json["BackendState"] as? String=="Running",let me=json["Self"] as? [String:Any],let addresses=me["TailscaleIPs"] as? [String],!Set(addresses).isDisjoint(with:local),let raw=me["DNSName"] as? String else { throw LCError.invalid("Could not verify this Mac's active Tailscale address. Connect Tailscale, or paste its MagicDNS name.") }
        let name=raw.hasSuffix(".") ? String(raw.dropLast()) : raw
        guard name.hasSuffix(".ts.net"),name.count<=253,name.range(of:"^[A-Za-z0-9.-]+$",options:.regularExpression) != nil else { throw LCError.invalid("No valid MagicDNS name is available") }
        return name
    }
}
