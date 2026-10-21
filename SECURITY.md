# Security model

## Trust and transport

The Mac generates a 32-byte random pairing secret with the OS CSPRNG. The pairing code contains the Mac host, port, version, and full secret. Anyone with this code can authenticate as the phone or impersonate the Mac. Copy it only through a trusted channel. It is a high-entropy secret, not a human password.

The TCP handshake uses independent fresh 32-byte nonces and role-separated HMAC-SHA256 proofs. Direction-separated keys are derived by HMAC-SHA256 from the secret and both nonces. Clipboard frames use AES-256-GCM with a 128-bit authentication tag, monotonically increasing sequence nonces, and authenticated framing metadata. Invalid authentication, replay within a session, out-of-order frames, malformed protocol messages, and oversized frames cause rejection. There is no plaintext clipboard mode or TLS validation bypass. This is an application-layer encrypted protocol, **not TLS**. It uses OS crypto implementations (CryptoKit/JCE), but the protocol and implementation are not independently audited.

There is **no forward secrecy**: possession of the pairing secret plus a previously captured handshake permits deriving that session's keys. Rotation revokes future connections; it cannot retract data previously copied or prevent decrypting old captures if the old secret was compromised. Traffic lengths, endpoints, and timing are observable. Device compromise is outside this threat model.

The listener binds all Mac IPv4/IPv6 interfaces on TCP 47391 while sharing is active, to support Wi-Fi and optional Tailscale. It caps simultaneous unauthenticated handshakes at four and gives the handshake a five-second receive timeout. An authenticated peer replaces the previous peer. This is a one-phone design. Do not expose the port through a public router. Pairing protects clipboard contents, not availability against a network attacker; denial of service remains possible.

## Secret storage

- Mac: Keychain generic password, service `localclip.peer`, account `pairing-key`, device-only after-first-unlock accessibility.
- Android: a non-exportable Android Keystore AES-GCM wrapping key encrypts the stored pairing code in private preferences. App backups are disabled. Root/debugging access and an unlocked or compromised device are outside the secret-storage guarantee.
- The key is in process memory while needed. Pairing input is password-masked, not autofill-enabled, and screenshots of the Android app are allowed.
- Production code does not log clip contents, secrets, nonces, or authentication proofs. Test fixtures deliberately log their known synthetic data.

## Clipboard behavior

- Mac source clips marked `org.nspasteboard.ConcealedType` are excluded, including the copied pairing code.
- Android source clips marked `android.content.extra.IS_SENSITIVE` are excluded. Pairing-code text is always excluded from sync.
- This relies on source-app metadata. Private text without a sensitive marker will still sync when the user enables automatic sending.
- The phone skips received clipboard updates while `KeyguardManager.isDeviceLocked()` reports locked. The Mac will not receive an apply acknowledgement; unlock and send again. Pending content is not saved for later unlock.
- Acknowledgements are sent after the clipboard setter succeeds/returns. They do not mean a destination app pasted or displayed the content.
- The apps suppress echo using content fingerprints; Android automatic sending also excludes clips labeled as locally received LocalClip content. Explicit re-send remains available.
- Offline automatic changes and failed sends are not placed in a durable queue. A transparent ShareActivity reads/converts source URI data while its temporary grant is valid, then hands bounded clipboard bytes to the foreground service and closes. One explicitly shared item can wait in service memory for up to 30 seconds; newer pending shares replace it. In-app clipboard/photo actions can instead wait in the main Activity for up to 30 seconds and send while that screen has focus. Content involved in an in-progress send can remain briefly in memory. There is no clipboard-history database.
- Up to four PNGs remain in Android's private cache so clipboard URI recipients can read them. Android can evict the cache. Overwriting the cache can make an older image clipboard URI unavailable. Clearing app storage/uninstalling removes this cache; ordinary Disconnect does not purge it. Other apps that already pasted/cached an image retain their own copies.
- `ImageProvider` is non-exported with per-URI clipboard grants, read-only, and accepts only fixed-format filenames. It does not expose arbitrary filesystem paths.
- Images are limited to 8 MiB encoded and 16 megapixels; PNG transport strips original non-PNG formatting/animation. No remote URLs or arbitrary file URIs are fetched.

## MVP limits

No external security audit, multi-peer identity management, perfect delivery under process death, durable delivery queue, remote wipe, or encrypted clipboard history. The Mac must be awake, and its global clipboard can be changed while the Mac app is running, including while the Mac is locked; only the Android lock state has an explicit receive gate. Network backoff and OS sleep restrictions may delay or interrupt sync. Consider whether copying sensitive text on either device is appropriate while automatic sharing is enabled.

The optional production helper in `adb-bridge` runs as ADB shell UID 2000. A fresh random bridge key in an app-private file bootstraps a mutually authenticated AES-GCM session on 127.0.0.1:47392; the authorized ADB launcher pipes that file through `run-as` on the debuggable APK. The Mac pairing key is never given to the helper. Other ordinary apps cannot read the file. Closing the channel deletes its bootstrap file; a restart rotates the key. Loopback authentication, encryption and bounds reuse the wire primitives, but local payload types differ to permit source-image conversion. No localhost listener is open unless the option and connection service are running. Availability against another local app repeatedly connecting is not guaranteed.

The helper reads new copies only, honors sensitive/pairing exclusions, drops locked/offline copies, and ignores incoming LocalClip labels. Disabling the option or stopping sharing closes its session; the helper exits. Debugging authorization must be granted explicitly outside LocalClip and is privileged device access. The launcher requires this debug build's `run-as`; it will not work unchanged with a non-debuggable release. See [helper setup and limitations](docs/ADB-CLIPBOARD.md). The older read-only API 34 experiment remains separate and is not the shipped helper.
