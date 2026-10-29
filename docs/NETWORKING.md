# Tailscale first and automatic tailnet discovery — LocalClip 0.5.0

Keep both devices signed in to the same tailnet with Tailscale enabled. On the Mac, Start sharing; on Android, Connect to Mac. Existing pairing works without re-pairing. The Mac must stay awake, and Android's connection service must remain running.

## How discovery works

Bonjour does not provide tailnet-wide discovery. While sharing, the Mac reads the installed `/Applications/Tailscale.app/Contents/MacOS/Tailscale status --json`, validates BackendState and Self.TailscaleIPs against this Mac's actual interfaces, and selects its stable MagicDNS name (or verified Tailscale IPv4 address) automatically for pairing.

Every 15 seconds it sends a 74-byte UDP unicast message to port **47390** on up to 64 online peers returned by the local Tailscale client. Only numeric addresses in Tailscale's 100.64.0.0/10 range are used. There is no subnet sweep, guessed address list, Tailscale admin token, embedded VPN, or new LocalClip internet service.

The Android connection service listens for these messages while connected/connecting. A valid message supplies the Mac's current Tailscale address and clipboard port. The phone prioritizes this route and makes the normal authenticated encrypted TCP connection back to the Mac. A reachable tailnet route also replaces an active Wi-Fi fallback. If Tailscale cannot carry TCP, Wi-Fi remains available; promotion retries are throttled while a fallback is active.

After the TCP handshake succeeds, Android remembers the numeric Tailscale endpoint in its existing encrypted pairing record. This does not replace the pairing secret, discard all Wi-Fi backups, or require a fresh pairing code. It can reconnect after an app restart before another discovery message arrives. Cached tailnet routes and configured MagicDNS names are tried ahead of Wi-Fi routes.

The Mac listens for clipboard connections and reports **Phone connected · Tailscale · encrypted** when the authenticated peer uses its Tailscale address. Android's service notification reports the same transport. Discovery itself is never a clipboard delivery confirmation.

## Authentication and privacy

The discovery packet is `LCD1 || nonce[32] || tcpPort[2] || macIPv4[4] || proof[32]`. Proof is HMAC-SHA256(pairing key, `LC01/tailnet-discovery || nonce || tcpPort || macIPv4`). The phone requires an exact-size packet, a tailnet source, a matching source address covered by that proof, and the current pairing key. This prevents another peer from redirecting the phone by relaying a message from its own address. A bounded cache ignores the most recent 128 nonces. Discovery is only a routing hint: a fresh nonce/HMAC TCP handshake and AES-GCM session must succeed before any clipboard data moves or a route is persisted.

Messages contain no clipboard data, pairing secret, Tailscale credentials or peer directory. Visible tailnet peers can observe discovery traffic and its sender address/port. The Mac reads peer metadata locally and does not publish it. Pausing Mac sharing stops the announcer; disconnecting Android closes its UDP listener. Secret rotation invalidates the existing phone's discovery proof until it is paired again.

## Network requirements and limits

Allow **UDP 47390 Mac → phone** for discovery and **TCP 47391 phone → Mac** for clipboard traffic in your tailnet policy. LocalClip never changes ACLs, disables VPN protections or exposes a public Funnel endpoint. Tailscale must include LocalClip in its Android routing; restrictive split-tunneling / incoming-connection settings can prevent access. Discovery currently uses Tailscale IPv4 addresses. Tailnets with more than 64 visible online peers need a configured/cached Mac address if it falls outside the bounded discovery list.

The Mac integration expects Tailscale in Applications. If CLI access or discovery is unavailable, Android can still use a saved Tailscale IP/MagicDNS name via **Change address / use Tailscale**. Tailscale supplies the existing overlay network and may use coordination/DERP infrastructure. LocalClip adds no cloud account or hosted directory.

Bonjour/Android NSD remains available on the same local network and identifies the paired Mac using a domain-separated secret-derived public hint. Its IP suggestions still require the encrypted handshake. AP isolation and multicast filtering can block this fallback. A broken connection can take up to the receive/heartbeat timeout to reconnect. Neither path guarantees delivery while the Mac is asleep, either device is offline, or Android's connection service is stopped.

## Validation

The isolated Android 17 emulator exercised actual UDP reception on synthetic tailnet-shaped loopback aliases, route promotion over an active fallback, encrypted Swift transfer, paired-key/source/duplicate rejection, encrypted route persistence and restart without a new beacon. These aliases are test-only; no real tailnet IPs were added to the host.

On the physical Pixel, the production apps discovered each other using their real Tailscale addresses and authenticated successfully. With Wi-Fi disabled by the user, Android reported a CELLULAR|VPN underlay and continued exchanging authenticated heartbeat responses over the tailnet. See TEST-RESULTS.md for the final evidence and remaining limits.

Sources: [Tailscale CLI](https://tailscale.com/docs/reference/tailscale-cli), [Tailscale connection basics](https://tailscale.com/docs/how-to/connect-to-devices), [Android VPN policy](https://developer.android.com/reference/android/net/VpnService.Builder#allowBypass()).
