import AppKit
let application=NSApplication.shared
application.setActivationPolicy(.regular)
let delegate=AppDelegate(testMode:CommandLine.arguments.contains("--test-mode"))
application.delegate=delegate
application.run()
