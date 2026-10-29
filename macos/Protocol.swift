import Foundation
import CryptoKit
import Security

enum LCError: Error, CustomStringConvertible {
    case invalid(String)
    var description: String { if case let .invalid(s) = self { return s }; return "Protocol error" }
}
enum Wire {
    static func discoveryID(_ key: Data) -> String { hex(Data(mac(key,"discovery",Data(),Data()).prefix(16))) }
    static func tailnetIP(_ host: String) -> Bool {
        let parts=host.split(separator:".",omittingEmptySubsequences:false)
        guard parts.count==4,parts[0]=="100",parts.allSatisfy({ !$0.isEmpty && $0.count<=3 && $0.allSatisfy({$0.isASCII && $0.isNumber}) && (Int($0) ?? 256)<=255 }),let second=Int(parts[1]) else { return false }
        return (64...127).contains(second)
    }
    static func tailnetBeacon(_ key: Data,nonce: Data,host: String,port: UInt16)throws -> Data {
        guard nonce.count==32,tailnetIP(host),port>=1024 else { throw LCError.invalid("Invalid discovery packet") }
        let endpoint=Data([UInt8(port>>8),UInt8(port&255)])+Data(host.split(separator:".").map{UInt8($0)!})
        return Data("LCD1".utf8)+nonce+endpoint+mac(key,"tailnet-discovery",nonce,endpoint)
    }
    static let magic = Data("LC01".utf8)
    static let maxContent = 8 * 1024 * 1024
    static let maxFrame = maxContent + 17 + 16
    static func random(_ count: Int) throws -> Data {
        var data = Data(count: count)
        let status = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        guard status == errSecSuccess else { throw LCError.invalid("Random generator failed") }
        return data
    }
    static func mac(_ secret: Data, _ label: String, _ server: Data, _ client: Data) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: Data(("LC01/" + label).utf8) + server + client, using: SymmetricKey(data: secret)))
    }
    static func verify(_ proof: Data, secret: Data, label: String, server: Data, client: Data) -> Bool {
        HMAC<SHA256>.isValidAuthenticationCode(proof, authenticating: Data(("LC01/" + label).utf8) + server + client, using: SymmetricKey(data: secret))
    }
    static func uint32(_ value: Int) -> Data { var v = UInt32(value).bigEndian; return Data(bytes: &v, count: 4) }
    static func length(_ data: Data) throws -> Int {
        guard data.count == 4 else { throw LCError.invalid("Invalid frame header") }
        let n = data.reduce(0) { ($0 << 8) | Int($1) }
        guard n >= 33 && n <= maxFrame else { throw LCError.invalid("Frame outside size limit") }
        return n
    }
    static func fingerprint(_ type: UInt8, _ data: Data) -> String { SHA256.hash(data: Data([type]) + data).map { String(format: "%02x", $0) }.joined() }
    static func hex(_ data: Data) -> String { data.map { String(format: "%02x", $0) }.joined() }
    static func unhex(_ text: String) throws -> Data {
        guard text.count == 64, text.allSatisfy({ $0.isASCII && $0.isHexDigit }) else { throw LCError.invalid("Secret must be 64 hex characters") }
        var data = Data(); var i = text.startIndex
        while i < text.endIndex { let j = text.index(i, offsetBy: 2); data.append(UInt8(text[i..<j], radix: 16)!); i = j }
        return data
    }
}
struct ClipMessage {
    let type: UInt8
    let id: Data
    let body: Data
    init(type: UInt8, body: Data = Data(), id: Data? = nil) throws {
        guard (1...4).contains(type), body.count <= Wire.maxContent else { throw LCError.invalid("Unsupported or oversized clipboard") }
        if type == 1 { guard String(data: body, encoding: .utf8) != nil else { throw LCError.invalid("Invalid UTF-8") } }
        if type == 2 { guard body.starts(with: [137,80,78,71,13,10,26,10]) else { throw LCError.invalid("Expected PNG image") } }
        if type >= 3 && !body.isEmpty { throw LCError.invalid("Control message has data") }
        self.type = type; self.body = body; self.id = try id ?? Wire.random(16)
        guard self.id.count == 16 else { throw LCError.invalid("Invalid message ID") }
    }
    init(data: Data) throws {
        guard data.count >= 17 else { throw LCError.invalid("Truncated message") }
        try self.init(type: data[data.startIndex], body: Data(data.dropFirst(17)), id: Data(data.dropFirst().prefix(16)))
    }
    var data: Data { Data([type]) + id + body }
}
final class FrameCipher {
    private let key: SymmetricKey
    private var sequence: UInt64 = 0
    init(key: Data) { self.key = SymmetricKey(data: key) }
    private func nonce() throws -> Data {
        guard sequence < UInt64.max else { throw LCError.invalid("Session exhausted") }
        var n = sequence.bigEndian
        return Data(repeating: 0, count: 4) + Data(bytes: &n, count: 8)
    }
    func seal(_ plain: Data) throws -> Data {
        guard plain.count >= 17 && plain.count <= Wire.maxContent + 17 else { throw LCError.invalid("Invalid payload size") }
        let header = Wire.uint32(plain.count + 16)
        let iv = try nonce()
        let box = try AES.GCM.seal(plain, using: key, nonce: AES.GCM.Nonce(data: iv), authenticating: Wire.magic + header + iv)
        sequence += 1
        return header + box.ciphertext + box.tag
    }
    func open(_ frame: Data) throws -> Data {
        guard frame.count >= 4 else { throw LCError.invalid("Missing frame header") }
        let header = Data(frame.prefix(4)); let n = try Wire.length(header)
        guard frame.count == n + 4 else { throw LCError.invalid("Truncated frame") }
        let iv = try nonce(); let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: frame.dropFirst(4).dropLast(16), tag: frame.suffix(16))
        let plain = try AES.GCM.open(box, using: key, authenticating: Wire.magic + header + iv)
        sequence += 1; return plain
    }
}
