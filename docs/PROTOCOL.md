# LC01 protocol

Transport is a persistent, bidirectional TCP stream, Mac server / Android client. All integer encodings are big-endian. There is no HTTP endpoint, browser API, public server, or protocol-version fallback.

## Pairing

`localclip1:` followed by standard Base64 of UTF-8 JSON:

```json
{"v":1,"host":"192.168.1.10","port":47391,"key":"64 hexadecimal characters"}
```

The host can be an IP or resolvable hostname. The secret is exactly 32 random bytes. It must be transferred out of band.

## Handshake

- Server → client: ASCII `LC01` (4 bytes), `S` (32 random bytes).
- Client → server: `C` (32 random bytes), `HMAC(secret, ASCII("LC01/client") || S || C)` (32 bytes).
- Server verifies the client's proof using constant-time authentication checking.
- Server → client: `HMAC(secret, ASCII("LC01/server") || S || C)` (32 bytes).
- Client verifies the server's proof using constant-time comparison before accepting any clipboard data.
- Client-to-server key: `HMAC(secret, ASCII("LC01/c2s") || S || C)`.
- Server-to-client key: `HMAC(secret, ASCII("LC01/s2c") || S || C)`.

No secret or clipboard content is sent in clear. Nonces and proofs are public. The random 256-bit secret makes a password-guessing construction unnecessary. HMAC labels are exact ASCII without terminators. Fixed-width nonces make concatenation unambiguous.

## Encrypted frames

Each direction has a separate AES-256-GCM key and a sequence counter starting at zero.

```
uint32 N | ciphertext (N - 16 bytes) | GCM tag (16 bytes)
```

- Nonce: four zero bytes followed by the eight-byte sequence counter.
- AAD: ASCII `LC01` || four-byte N || twelve-byte nonce.
- Counter increments only after successful encryption/decryption. A failed decrypt closes the connection. Keys are never reused across intentionally reconnected sessions; both peers generate fresh nonces. The Java implementation retires the session before the signed-long counter limit.
- `33 <= N <= 8 * 1024 * 1024 + 33`. Length is checked before allocating the frame body.
- Plaintext is one-byte type || 16-byte random message ID || body.
- Type 1: strictly valid UTF-8 text, at most 8 MiB.
- Type 2: PNG bytes, at most 8 MiB. Platforms validate image dimensions before applying; max 16 million pixels and 16384 pixels on either edge.
- Type 3: heartbeat, empty body.
- Type 4: acknowledgement, empty body, the ID of the acknowledged message.

Unknown types, invalid UTF-8, invalid PNG signatures, malformed control bodies, and oversize contents are rejected. Heartbeats are answered by acknowledgements. Clipboard messages are acknowledged after applying the clipboard; a skipped/failed apply is unacknowledged and the sender displays a timeout. This MVP has no structured error-payload type and does not invent a fallback response.

## Operational constraints

10-second heartbeat interval; 45-second idle receive timeout; Android connection/handshake timeout six seconds; Mac handshake timeout five seconds; Mac send timeout five seconds; Android stalled-write watchdog twelve seconds. Android reconnect backoff is 1, 2, 4, 8, then 15 seconds. There is no resumed session, durable queue, compression, or global clipboard ordering. Different copies arriving at the same time can overwrite each other. Message IDs correlate acknowledgements; content fingerprints prevent clipboard loops.

The generic protocol tests intentionally accept PNG-signature payloads independently of image decoding. The platform integration tests separately verify real PNG decoding, dimensions, content URIs, and clipboard application. Protocol test servers use fixed public fixture keys and must never be used for actual clipboard sharing.

## Optional ADB helper channel (0.3.0)

This separate channel listens only on phone loopback `127.0.0.1:47392` while enabled. It uses an independent random 32-byte bridge key, rotated on listener start, and the same LC01 HMAC handshake, key derivation, AES-GCM framing and sequence enforcement. The debugging launcher supplies the app-private bridge key to the shell helper over stdin; it is not the Mac pairing secret. No plaintext clipboard frame is accepted.

The local decrypted payload begins with one type byte and 16 reserved/random ID bytes, followed by a bounded body. Local types differ from Mac messages: `6` is the first helper frame, with a four-byte big-endian Android SDK integer; `1` carries UTF-8 text; `2` carries source image bytes for app-side validation/PNG conversion; `3` is an empty heartbeat; `5` is an empty source-read/size failure notice. The app returns encrypted type-4 empty control acknowledgements for processed frames. These local acknowledgements confirm processing of a helper event, not Mac delivery; the Mac connection's own acknowledgements update the Last transfer card.

Source bodies are at most 8 MiB. Android image decoding also applies the existing 16-megapixel bound. A wrong key, authentication failure, malformed frame or heartbeat timeout closes the helper connection. The helper reads no preexisting clip at startup. See [helper setup and security](ADB-CLIPBOARD.md).

## Local discovery (0.4.0)

The clipboard TCP protocol is unchanged. Bonjour advertises `_localclip._tcp.` with service name `LocalClip-<id>` (subject to OS conflict suffixes), TXT `id=<id>`, and `v=1`. ID = lowercase hex of the first 16 bytes of HMAC-SHA256(secret, ASCII `LC01/discovery`). Advertised ports must be 1024–65535; Android verifies the expected ID and TXT version before treating the result as a candidate, then authenticates the TCP peer normally. The v1 pairing JSON optionally carries `hosts`, up to six explicit route strings; required `host`, `port`, `key` and `v` fields remain unchanged. Discovery never supplies or rotates a secret.

## Tailnet discovery (0.5.0)

UDP destination 47390. Exact 74-byte packet: ASCII `LCD1`, 32-byte random nonce, unsigned big-endian 16-bit clipboard TCP port, 4-byte Mac Tailscale IPv4, 32-byte proof. Proof is HMAC-SHA256(secret, ASCII `LC01/tailnet-discovery` || nonce || port || IPv4). The receiver rejects non-tailnet sources, mismatched source addresses, invalid sizes/ports, unpaired proofs and recent duplicate nonces. No clipboard data or secret is carried. A normal fresh TCP handshake is still mandatory before data or persistent route changes. See NETWORKING.md for lifecycle, limits and tailnet policy.

Android image preparation may read up to 32 MiB from an explicitly shared image provider; the decoded 16-megapixel and transmitted 8 MiB bounds remain. Resizing metadata is sender-local UI state and is not part of the clipboard wire format. The optional ADB helper's own input bound remains 8 MiB.
