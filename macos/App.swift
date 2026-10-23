import AppKit
import Foundation
import Security
import ImageIO

final class SecretStore {
    static func query() -> [String: Any] { [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"localclip.peer",kSecAttrAccount as String:"pairing-key"] }
    static func load() throws -> Data {
        var q=query(); q[kSecReturnData as String]=true; var result: CFTypeRef?
        let status=SecItemCopyMatching(q as CFDictionary,&result)
        if status==errSecSuccess, let data=result as? Data, data.count==32 { return data }
        guard status==errSecItemNotFound else { throw LCError.invalid("Cannot unlock pairing key in Keychain (\(status))") }
        return try rotate()
    }
    static func rotate() throws -> Data {
        let data=try Wire.random(32); let status=SecItemUpdate(query() as CFDictionary,[kSecValueData as String:data] as CFDictionary)
        if status==errSecItemNotFound { var q=query(); q[kSecValueData as String]=data; q[kSecAttrAccessible as String]=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(q as CFDictionary,nil)==errSecSuccess else { throw LCError.invalid("Cannot save pairing key") }
        } else if status != errSecSuccess { throw LCError.invalid("Cannot update pairing key") }
        return data
    }
}
enum ImageCheck {
    static func png(_ data: Data) throws {
        guard data.count<=Wire.maxContent, let source=CGImageSourceCreateWithData(data as CFData,nil), CGImageSourceGetType(source) as String? == "public.png",
              let props=CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [String:Any], let w=props[kCGImagePropertyPixelWidth as String] as? Int, let h=props[kCGImagePropertyPixelHeight as String] as? Int,
              w>0, h>0, w<=16384, h<=16384, w*h<=16_000_000 else { throw LCError.invalid("Image must be PNG, at most 16 megapixels / 8 MiB") }
    }
}
final class AppDelegate: NSObject, NSApplicationDelegate {
    let server=ClipServer()
    let discovery=DiscoveryAdvertiser()
    let discoveryStatus=NSTextField(wrappingLabelWithString:"Local discovery paused")
    var window: NSWindow!
    var statusItem: NSStatusItem!
    let status=NSTextField(labelWithString:"Sharing stopped")
    let detail=NSTextField(wrappingLabelWithString:"Nothing transferred yet")
    let host=NSTextField(string:"")
    let pairCode=NSTextField(wrappingLabelWithString:"")
    var secret=Data()
    var timer: Timer?
    var running=false
    var runID=0
    var lastChange=0
    var lastDigest=""
    var pending: [String:Date]=[:]
    let board: NSPasteboard
    let testMode: Bool
    init(testMode: Bool=false) { self.testMode=testMode; board=testMode ? NSPasteboard(name:NSPasteboard.Name("localclip.tests")) : .general; super.init() }
    func applicationDidFinishLaunching(_ notification: Notification) {
        do { secret = testMode ? try Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f") : try SecretStore.load() } catch { showError(error); NSApp.terminate(nil); return }
        let mainMenu=NSMenu(), applicationItem=NSMenuItem(), applicationMenu=NSMenu()
        applicationMenu.addItem(withTitle:"Open LocalClip",action:#selector(show),keyEquivalent:"").target=self
        applicationMenu.addItem(withTitle:"Quit LocalClip",action:#selector(quit),keyEquivalent:"q").target=self
        applicationItem.submenu=applicationMenu; mainMenu.addItem(applicationItem)
        let editItem=NSMenuItem(title:"Edit",action:nil,keyEquivalent:""),editMenu=NSMenu(title:"Edit")
        for (title,selector,key) in [("Cut","cut:","x"),("Copy","copy:","c"),("Paste","paste:","v"),("Select All","selectAll:","a")] { editMenu.addItem(withTitle:title,action:NSSelectorFromString(selector),keyEquivalent:key) }
        editItem.submenu=editMenu;mainMenu.addItem(editItem);NSApp.mainMenu=mainMenu
        window=NSWindow(contentRect:NSRect(x:0,y:0,width:640,height:790),styleMask:[.titled,.closable,.miniaturizable],backing:.buffered,defer:false)
        window.title="LocalClip"; window.center(); window.isReleasedWhenClosed=false
        let root=NSStackView(); root.orientation = .vertical; root.alignment = .leading; root.spacing=12; root.translatesAutoresizingMaskIntoConstraints=false
        window.contentView!.addSubview(root)
        NSLayoutConstraint.activate([root.leadingAnchor.constraint(equalTo:window.contentView!.leadingAnchor,constant:24),root.trailingAnchor.constraint(equalTo:window.contentView!.trailingAnchor,constant:-24),root.topAnchor.constraint(equalTo:window.contentView!.topAnchor,constant:22)])
        let title=NSTextField(labelWithString:"LocalClip"); title.font = .systemFont(ofSize:28,weight:.semibold); root.addArrangedSubview(title)
        root.addArrangedSubview(NSTextField(wrappingLabelWithString:"Private clipboard between this Mac and your Android. Text and PNG images, directly between your devices."))
        status.font = .systemFont(ofSize:14,weight:.semibold); root.addArrangedSubview(status)
        root.addArrangedSubview(NSTextField(labelWithString:"Mac address (choose a Wi-Fi IP or Tailscale address):"))
        host.stringValue=UserDefaults.standard.string(forKey:"preferredHost") ?? localAddresses().first ?? ""; root.addArrangedSubview(host); host.widthAnchor.constraint(equalTo:root.widthAnchor).isActive=true
        root.addArrangedSubview(NSTextField(wrappingLabelWithString:"Available: " + localAddresses().joined(separator:", ")))
        let actions=NSStackView(); actions.spacing=10
        for (title,action) in [("Start sharing",#selector(start)),("Pause",#selector(pause)),("Send current clipboard",#selector(sendCurrent))] { actions.addArrangedSubview(NSButton(title:title,target:self,action:action)) }; root.addArrangedSubview(actions)
        let pairActions=NSStackView(); pairActions.spacing=10
        pairActions.addArrangedSubview(NSButton(title:"Copy pairing code",target:self,action:#selector(copyPair)))
        pairActions.addArrangedSubview(NSButton(title:"Forget phone / rotate secret",target:self,action:#selector(rotate)))
        root.addArrangedSubview(pairActions)
        root.addArrangedSubview(NSTextField(wrappingLabelWithString:"Paste the pairing code into LocalClip on Android. Treat it like a password. Port: 47391. Only one paired phone is supported."))
        let tailButton=NSButton(title:"Use this Mac’s Tailscale name",target:self,action:#selector(useTailscale))
        root.addArrangedSubview(tailButton);root.addArrangedSubview(discoveryStatus)
        discovery.onStatus={ [weak self] text in self?.discoveryStatus.stringValue=text }
        root.addArrangedSubview(detail)
        root.addArrangedSubview(NSTextField(wrappingLabelWithString:"Keep Gboard on Android. For automatic copying, enable the optional ADB helper in Android LocalClip and run the supplied Mac launcher. Without it: Share → LocalClip, or open LocalClip and tap Send clipboard.\n\nMac → phone is automatic while sharing and the Android connection service are running and the phone is unlocked. Neither device keeps a clipboard history. The Mac must stay awake. Allow clipboard access and local network access when macOS asks."))
        for view in root.arrangedSubviews { if let field = view as? NSTextField, field !== host { field.maximumNumberOfLines=0; field.lineBreakMode = .byWordWrapping; field.widthAnchor.constraint(equalTo:root.widthAnchor).isActive=true } }
        statusItem=NSStatusBar.system.statusItem(withLength:NSStatusItem.variableLength); statusItem.button?.title="⇄ Clip"
        let menu=NSMenu(); menu.addItem(withTitle:"Open LocalClip",action:#selector(show),keyEquivalent:"").target=self
        menu.addItem(withTitle:"Pause sharing",action:#selector(pause),keyEquivalent:"").target=self
        menu.addItem(NSMenuItem.separator()); menu.addItem(withTitle:"Quit LocalClip",action:#selector(quit),keyEquivalent:"q").target=self; statusItem.menu=menu
        server.onStatus={ [weak self] s in DispatchQueue.main.async { self?.status.stringValue=s } }
        server.onMessage={ [weak self] message in DispatchQueue.main.async { self?.receive(message) } }
        timer=Timer.scheduledTimer(withTimeInterval:0.35,repeats:true) { [weak self] _ in self?.tick() }
        show()
        if testMode { start() }
    }
    @objc func show() { window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps:true) }
    @objc func start() {
        guard !running else { return }
        do { runID+=1; let id=runID
            server.onMessage={ [weak self] message in DispatchQueue.main.async { guard let self, self.runID==id else { return }; self.receive(message) } }
            lastChange=board.changeCount; try server.start(secret:secret,port:47391); running=true; if !testMode { discovery.start(secret:secret,port:47391) }; statusItem.button?.title="⇄ Clip •" } catch { showError(error) }
    }
    @objc func pause() { runID+=1; server.stop(); discovery.stop(); running=false; pending.removeAll(); status.stringValue="Sharing paused"; statusItem.button?.title="⇄ Clip" }
    @objc func quit() { NSApp.terminate(nil) }
    @objc func rotate() {
        pause()
        do { secret=try SecretStore.rotate(); detail.stringValue="Old pairing revoked. Copy a new pairing code and pair again." } catch { showError(error) }
    }
    @objc func useTailscale() {
        detail.stringValue="Looking up this Mac’s Tailscale name…"
        let addresses=localAddresses()
        DispatchQueue.global(qos:.utility).async { [weak self] in
            let result=Result { try TailscaleAddress.name(local:addresses) }
            DispatchQueue.main.async {
                guard let self else { return }
                switch result {
                case .success(let name): self.host.stringValue=name;UserDefaults.standard.set(name,forKey:"preferredHost");self.detail.stringValue="Tailscale name selected. Both devices must use the same tailnet. Copy the pairing code, or enter this name on Android."
                case .failure(let error): self.detail.stringValue=String(describing:error)
                }
            }
        }
    }
    @objc func copyPair() {
        do {
            let address=host.stringValue.trimmingCharacters(in:.whitespacesAndNewlines)
            guard !address.isEmpty, address.count<=253, !address.contains("/"), !address.contains(where:{$0.isWhitespace}) else { throw LCError.invalid("Enter a valid Mac IP or hostname") }
            UserDefaults.standard.set(address,forKey:"preferredHost")
            let hosts=Array(([address]+localAddresses()).reduce(into:[String]()){ if !$0.contains($1) && $0.count<6 { $0.append($1) } })
            let data=try JSONSerialization.data(withJSONObject:["v":1,"host":address,"hosts":hosts,"port":47391,"key":Wire.hex(secret)],options:[.sortedKeys])
            let code="localclip1:"+data.base64EncodedString()
            NSPasteboard.general.clearContents(); NSPasteboard.general.setString(code,forType:.string)
            NSPasteboard.general.setData(Data(),forType:NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
            lastChange=board.changeCount; detail.stringValue="Pairing code copied. Paste it into the Android app, then replace your clipboard."
        } catch { showError(error) }
    }
    @objc func sendCurrent() { do { try sendClipboard(explicit:true) } catch { detail.stringValue=String(describing:error) } }
    func tick() {
        let expired=pending.filter { Date().timeIntervalSince($0.value)>12 }.map(\.key)
        for id in expired { pending.removeValue(forKey:id) }
        if !expired.isEmpty { detail.stringValue="No delivery confirmation. Unlock/open the phone and send again." }
        guard running, board.changeCount != lastChange else { return }
        lastChange=board.changeCount
        do { try sendClipboard(explicit:false) } catch { detail.stringValue=String(describing:error) }
    }
    func sendClipboard(explicit: Bool) throws {
        guard running else { throw LCError.invalid("Start sharing first") }
        guard !(board.types ?? []).contains(NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType")) else { throw LCError.invalid("Sensitive clipboard skipped") }
        var type: UInt8=1; var data=Data()
        if let png=board.data(forType:.png) { type=2; data=png; try ImageCheck.png(data) }
        else if let tiff=board.data(forType:.tiff), let rep=NSBitmapImageRep(data:tiff) {
            guard rep.pixelsWide*rep.pixelsHigh<=16_000_000, let png=rep.representation(using:.png,properties:[:]) else { throw LCError.invalid("Image too large") }
            type=2; data=png; try ImageCheck.png(data)
        } else if let text=board.string(forType:.string) { data=Data(text.utf8) }
        else { if explicit { throw LCError.invalid("Copy text or an image first") }; return }
        let digest=Wire.fingerprint(type,data); if !explicit && digest==lastDigest { return }
        let message=try ClipMessage(type:type,body:data)
        try server.send(message); lastDigest=digest; pending[Wire.hex(message.id)]=Date(); detail.stringValue="Sent \(type==1 ? "text" : "image") · waiting for phone confirmation"
    }
    func receive(_ message: ClipMessage) {
        guard running else { return }
        if message.type==4 {
            if pending.removeValue(forKey:Wire.hex(message.id)) != nil { detail.stringValue="Phone clipboard updated · \(DateFormatter.localizedString(from:Date(),dateStyle:.none,timeStyle:.medium))" }; return
        }
        do {
            if message.type==2 { try ImageCheck.png(message.body) }
            board.clearContents()
            if message.type==1 { guard board.setString(String(data:message.body,encoding:.utf8)!,forType:.string) else { throw LCError.invalid("macOS rejected clipboard write") } }
            else if message.type==2 { guard board.setData(message.body,forType:.png) else { throw LCError.invalid("macOS rejected image clipboard write") } }
            lastChange=board.changeCount; lastDigest=Wire.fingerprint(message.type,message.body)
            try server.send(ClipMessage(type:4,id:message.id)); detail.stringValue="Received \(message.type==1 ? "text" : "image") from phone · ready to paste"
        } catch { detail.stringValue="Could not apply clipboard: \(error)" }
    }
    func showError(_ error: Error) { let a=NSAlert(); a.messageText="LocalClip"; a.informativeText=String(describing:error); a.runModal() }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender:NSApplication)->Bool { false }
    func applicationWillTerminate(_ notification: Notification) { server.stop();discovery.stop() }
}
func localAddresses() -> [String] {
    var list: UnsafeMutablePointer<ifaddrs>?; guard getifaddrs(&list)==0 else { return [] }; defer{ freeifaddrs(list) }
    var addresses: [String]=[]; var p=list
    while let item=p {
        defer{p=item.pointee.ifa_next}
        guard let addr=item.pointee.ifa_addr, Int32(addr.pointee.sa_family)==AF_INET, (item.pointee.ifa_flags & UInt32(IFF_LOOPBACK))==0 else { continue }
        var buffer=[CChar](repeating:0,count:Int(NI_MAXHOST))
        if getnameinfo(addr,socklen_t(addr.pointee.sa_len),&buffer,socklen_t(buffer.count),nil,0,NI_NUMERICHOST)==0 { addresses.append(String(cString:buffer)) }
    }; return addresses
}
