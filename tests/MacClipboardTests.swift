import AppKit
import Foundation
@main struct MacClipboardTests {
    static func main()throws{
        _ = NSApplication.shared
        let app=AppDelegate(testMode:true)
        app.running=true;app.lastChange=app.board.changeCount
        let key=try Wire.unhex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        let bitmap=NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:2,pixelsHigh:2,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
        for x in 0..<2 {for y in 0..<2{bitmap.setColor(NSColor(deviceRed:0,green:0.4,blue:1,alpha:1),atX:x,y:y)}}
        let png=bitmap.representation(using:.png,properties:[:])!
        // Local pasteboard paths use an isolated named pasteboard, preserving the user's clipboard.
        app.board.clearContents();app.board.setString("private-test",forType:.string)
        app.board.setData(Data(),forType:NSPasteboard.PasteboardType("org.nspasteboard.ConcealedType"))
        do {try app.sendClipboard(explicit:true);fatalError("Sensitive content was sent")}catch{print("PASS concealed pasteboard skipped");fflush(stdout)}
        app.server.onMessage={m in DispatchQueue.main.async {
            app.receive(m)
            if m.type==1 {
                precondition(app.board.string(forType:.string)==String(data:m.body,encoding:.utf8),"Incoming text was not applied")
                print("PASS Mac text pasteboard applied");fflush(stdout)
                app.board.clearContents();app.board.setString("mac:"+String(data:m.body,encoding:.utf8)!,forType:.string);app.tick()
            } else if m.type==2 {
                precondition(app.board.data(forType:.png)==m.body,"Incoming PNG was not applied")
                print("PASS Mac PNG pasteboard applied");fflush(stdout)
                app.board.clearContents();app.board.setData(png,forType:.png);app.tick()
            }
        }}
        try app.server.start(secret:key,port:47494)
        print("READY Mac clipboard test");fflush(stdout);RunLoop.main.run()
    }
}
