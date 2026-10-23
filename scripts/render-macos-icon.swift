import AppKit
import Foundation

// Editable vector artwork. Render every macOS icon size directly, without raster upscaling.
func color(_ r: CGFloat,_ g: CGFloat,_ b: CGFloat,_ a: CGFloat = 1) -> NSColor { NSColor(srgbRed:r/255,green:g/255,blue:b/255,alpha:a) }
func rounded(_ rect: NSRect,_ radius: CGFloat) -> NSBezierPath { NSBezierPath(roundedRect:rect,xRadius:radius,yRadius:radius) }
func fill(_ path: NSBezierPath,_ value: NSColor) { value.setFill();path.fill() }
func arrow(y: CGFloat,right: Bool,tint: NSColor) {
    let p=NSBezierPath();p.lineWidth=46;p.lineCapStyle = .round;p.lineJoinStyle = .round
    let tail: CGFloat=right ? 378:646,tip: CGFloat=right ? 646:378
    p.move(to:NSPoint(x:tail,y:y));p.line(to:NSPoint(x:tip,y:y))
    p.move(to:NSPoint(x:tip+(right ? -68:68),y:y+68));p.line(to:NSPoint(x:tip,y:y));p.line(to:NSPoint(x:tip+(right ? -68:68),y:y-68))
    tint.setStroke();p.stroke()
}
func artwork() {
    NSGraphicsContext.saveGraphicsState()
    let shadow=NSShadow();shadow.shadowColor=color(11,27,68,0.24);shadow.shadowBlurRadius=24;shadow.shadowOffset=NSSize(width:0,height:-12);shadow.set()
    let tile=rounded(NSRect(x:80,y:80,width:864,height:864),190)
    NSGradient(starting:color(58,128,245),ending:color(32,64,184))!.draw(in:tile,angle:-75)
    NSGraphicsContext.restoreGraphicsState()
    let rim=rounded(NSRect(x:81,y:81,width:862,height:862),190);rim.lineWidth=2;color(255,255,255,0.22).setStroke();rim.stroke()

    NSGraphicsContext.saveGraphicsState()
    let paperShadow=NSShadow();paperShadow.shadowColor=color(10,27,80,0.25);paperShadow.shadowBlurRadius=24;paperShadow.shadowOffset=NSSize(width:0,height:-14);paperShadow.set()
    fill(rounded(NSRect(x:292,y:232,width:440,height:566),62),color(246,250,255))
    NSGraphicsContext.restoreGraphicsState()
    let paper=rounded(NSRect(x:293,y:233,width:438,height:564),61)
    NSGradient(starting:color(255,255,255),ending:color(229,240,255))!.draw(in:paper,angle:-90)
    fill(rounded(NSRect(x:406,y:746,width:212,height:88),30),color(21,66,167))
    fill(rounded(NSRect(x:428,y:763,width:168,height:56),20),color(187,220,255))
    fill(rounded(NSRect(x:469,y:793,width:86,height:16),8),color(242,250,255))
    arrow(y:610,right:true,tint:color(34,83,203))
    arrow(y:418,right:false,tint:color(15,158,164))
}
let output=CommandLine.arguments.dropFirst().first ?? ".build/icon"
let folder=URL(fileURLWithPath:output,isDirectory:true)
try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
for (name,pixels) in [("icon_16x16.png",16),("icon_16x16@2x.png",32),("icon_32x32.png",32),("icon_32x32@2x.png",64),("icon_128x128.png",128),("icon_128x128@2x.png",256),("icon_256x256.png",256),("icon_256x256@2x.png",512),("icon_512x512.png",512),("icon_512x512@2x.png",1024)] {
    let bitmap=NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:pixels,pixelsHigh:pixels,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
    NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(bitmapImageRep:bitmap)
    let transform=NSAffineTransform();transform.scale(by:CGFloat(pixels)/1024);transform.concat();artwork()
    NSGraphicsContext.restoreGraphicsState()
    try bitmap.representation(using:.png,properties:[:])!.write(to:folder.appendingPathComponent(name))
}
