import Foundation
import Darwin

final class Peer {
    let fd: Int32
    let tx: FrameCipher
    let rx: FrameCipher
    private let writeLock = NSLock()
    private var ended = false
    init(fd: Int32, tx: FrameCipher, rx: FrameCipher) { self.fd=fd; self.tx=tx; self.rx=rx }
    func send(_ message: ClipMessage) throws {
        writeLock.lock(); defer { writeLock.unlock() }
        guard !ended else { throw LCError.invalid("Disconnected") }
        try SocketIO.write(fd, try tx.seal(message.data))
    }
    func stop() { writeLock.lock(); defer { writeLock.unlock() }; if !ended { ended=true; shutdown(fd, SHUT_RDWR) } }
}
enum SocketIO {
    static func timeout(_ fd: Int32, _ seconds: Int) {
        var t = timeval(tv_sec: seconds, tv_usec: 0)
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &t, socklen_t(MemoryLayout<timeval>.size))
        var sendTimeout=timeval(tv_sec: 5, tv_usec: 0)
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &sendTimeout, socklen_t(MemoryLayout<timeval>.size))
        var yes: Int32=1; setsockopt(fd,SOL_SOCKET,SO_NOSIGPIPE,&yes,4); setsockopt(fd,IPPROTO_TCP,TCP_NODELAY,&yes,4)
    }
    static func read(_ fd: Int32, _ count: Int) throws -> Data {
        var data = Data(count: count); var offset=0
        try data.withUnsafeMutableBytes { ptr in
            while offset<count { let n=Darwin.recv(fd,ptr.baseAddress!.advanced(by: offset),count-offset,0)
                if n<0 && errno==EINTR { continue }
                guard n>0 else { throw LCError.invalid("Connection ended or timed out") }; offset+=n
            }
        }; return data
    }
    static func write(_ fd: Int32, _ data: Data) throws {
        var offset=0
        try data.withUnsafeBytes { ptr in
            while offset<data.count { let n=Darwin.send(fd,ptr.baseAddress!.advanced(by: offset),data.count-offset,0)
                if n<0 && errno==EINTR { continue }
                guard n>0 else { throw LCError.invalid("Send failed") }; offset+=n
            }
        }
    }
}
final class ClipServer {
    var onStatus: (String)->Void = { _ in }
    var onMessage: (ClipMessage)->Void = { _ in }
    private let lock=NSLock()
    private var listener: Int32 = -1
    private var peer: Peer?
    private var generation=0
    private var handshakes=0
    private var listenerDone: DispatchGroup?
    private var timer: DispatchSourceTimer?
    var connected: Bool { lock.lock(); defer{ lock.unlock() }; return peer != nil }
    func start(secret: Data, port: UInt16) throws {
        stop()
        let fd=socket(AF_INET6,SOCK_STREAM,0); guard fd>=0 else { throw LCError.invalid("Cannot create listener") }
        var yes: Int32=1, no: Int32=0
        setsockopt(fd,SOL_SOCKET,SO_REUSEADDR,&yes,4); setsockopt(fd,IPPROTO_IPV6,IPV6_V6ONLY,&no,4)
        var address=sockaddr_in6(); address.sin6_len=UInt8(MemoryLayout<sockaddr_in6>.size); address.sin6_family=sa_family_t(AF_INET6); address.sin6_port=port.bigEndian
        let result=withUnsafePointer(to:&address) { $0.withMemoryRebound(to:sockaddr.self,capacity:1) { bind(fd,$0,socklen_t(MemoryLayout<sockaddr_in6>.size)) } }
        guard result==0 && listen(fd,4)==0 else { close(fd); throw LCError.invalid("Port \(port) unavailable") }
        _ = fcntl(fd,F_SETFL,O_NONBLOCK)
        let done=DispatchGroup(); done.enter()
        lock.lock(); listener=fd; listenerDone=done; generation+=1; let gen=generation; lock.unlock()
        onStatus("Listening on TCP \(port) — waiting for phone")
        DispatchQueue.global(qos:.utility).async { [weak self] in
            while true {
                guard let self else { break }
                self.lock.lock(); let listening=self.generation==gen; self.lock.unlock()
                if !listening { break }
                var event=pollfd(fd:fd,events:Int16(POLLIN),revents:0)
                let ready=poll(&event,1,100)
                if ready==0 { continue }; if ready<0 { if errno==EINTR { continue }; break }
                let client=accept(fd,nil,nil); if client<0 { if errno==EAGAIN || errno==EINTR { continue }; break }
                _ = fcntl(client,F_SETFL,0)
                self.lock.lock(); let allowed=self.generation==gen && self.handshakes<4; if allowed { self.handshakes+=1 }; self.lock.unlock()
                if !allowed { close(client); continue }
                DispatchQueue.global(qos:.userInitiated).async { self.handle(client,secret:secret,generation:gen) }
            }
            close(fd); done.leave()
        }
        let t=DispatchSource.makeTimerSource(queue:DispatchQueue.global(qos:.utility))
        t.schedule(deadline:.now()+10,repeating:10)
        t.setEventHandler { [weak self] in if let self, self.connected { try? self.send(ClipMessage(type:3)) } }; t.resume(); timer=t
    }
    func stop() {
        timer?.cancel(); timer=nil
        lock.lock(); generation+=1; let old=listener; listener = -1; let p=peer; peer=nil; let done=listenerDone; listenerDone=nil; lock.unlock()
        if old>=0 { shutdown(old,SHUT_RDWR); done?.wait() }; p?.stop()
    }
    func send(_ message: ClipMessage) throws {
        lock.lock(); let p=peer; lock.unlock()
        guard let p else { throw LCError.invalid("Phone offline — reconnect and send again") }
        do { try p.send(message) } catch { p.stop(); throw error }
    }
    private func handle(_ fd: Int32, secret: Data, generation gen: Int) {
        var current: Peer?; var counted=true
        defer {
            current?.stop(); close(fd)
            lock.lock(); if counted { handshakes-=1 }; let active=peer === current && current != nil; if active { peer=nil }; let valid=generation==gen; lock.unlock()
            if active && valid { onStatus("Phone disconnected — waiting to reconnect") }
        }
        do {
            SocketIO.timeout(fd,5)
            let server=try Wire.random(32)
            try SocketIO.write(fd,Wire.magic+server)
            let response=try SocketIO.read(fd,64); let client=Data(response.prefix(32))
            guard Wire.verify(Data(response.suffix(32)),secret:secret,label:"client",server:server,client:client) else { throw LCError.invalid("Peer authentication failed") }
            try SocketIO.write(fd,Wire.mac(secret,"server",server,client))
            let p=Peer(fd:fd,tx:FrameCipher(key:Wire.mac(secret,"s2c",server,client)),rx:FrameCipher(key:Wire.mac(secret,"c2s",server,client)))
            current=p
            lock.lock(); handshakes-=1; counted=false
            guard generation==gen else { lock.unlock(); return }
            let previous=peer; peer=p; lock.unlock(); previous?.stop()
            SocketIO.timeout(fd,45); onStatus("Phone connected · encrypted")
            while true {
                let header=try SocketIO.read(fd,4); let payload=try SocketIO.read(fd,Wire.length(header))
                let message=try ClipMessage(data:p.rx.open(header+payload))
                lock.lock(); let live=generation==gen && peer === p; lock.unlock()
                guard live else { break }
                if message.type==3 { try p.send(ClipMessage(type:4,id:message.id)) }
                else { onMessage(message) }
            }
        } catch {
            // Never log clipboard contents, pairing secrets, or handshake material.
        }
    }
}
