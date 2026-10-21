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
