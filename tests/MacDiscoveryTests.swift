import Foundation
@main struct DiscoveryTests {
    final class Browser: NSObject,NetServiceBrowserDelegate,NetServiceDelegate {
        let browser=NetServiceBrowser();var services=[NetService]();var resolved: NetService?;var lost=false
        let expected="LocalClip-"+Wire.discoveryID(Data(0..<32))
        override init(){super.init();browser.delegate=self}
        func netServiceBrowser(_ browser: NetServiceBrowser,didFind service: NetService,moreComing: Bool){if service.name.hasPrefix(expected){services.append(service);service.delegate=self;service.resolve(withTimeout:5)}}
        func netServiceDidResolveAddress(_ sender: NetService){resolved=sender}
        func netServiceBrowser(_ browser: NetServiceBrowser,didRemove service: NetService,moreComing: Bool){if service.name.hasPrefix(expected){lost=true;resolved=nil}}
    }
    static var count=0
    static func check(_ condition:Bool,_ label:String){precondition(condition,label);count+=1;print("PASS \(label)")}
    static func wait(_ label:String,_ condition:()->Bool){let end=Date().addingTimeInterval(15);while !condition()&&Date()<end{_ = RunLoop.main.run(mode:.default,before:Date().addingTimeInterval(0.05))};check(condition(),label)}
    static func main()throws{
        let key=Data(0..<32),publisher=DiscoveryAdvertiser(),observer=Browser(),server=ClipServer()
        observer.browser.searchForServices(ofType:DiscoveryAdvertiser.type,inDomain:"local.")
        try server.start(secret:key,port:47496);publisher.start(secret:key,port:47496)
        wait("Bonjour advertisement resolves with the actual listener port"){observer.resolved?.port==47496}
        let txt=NetService.dictionary(fromTXTRecord:observer.resolved!.txtRecordData()!)
        check(txt["id"]==Data(Wire.discoveryID(key).utf8)&&txt["v"]==Data("1".utf8),"discovery identity and version survive DNS-SD")
        check(!txt.values.contains(key) && !txt.values.contains(Data(Wire.hex(key).utf8)),"advertisement contains no pairing secret")
        publisher.stop();server.stop();wait("pausing removes the Bonjour advertisement"){observer.lost}
        observer.resolved=nil;try server.start(secret:key,port:47497);publisher.start(secret:key,port:47497)
        wait("restarting publishes the new port under the same identity"){observer.resolved?.port==47497}
        publisher.stop();server.stop();observer.browser.stop();print("Mac Bonjour integration: \(count) assertions passed")
    }
}
