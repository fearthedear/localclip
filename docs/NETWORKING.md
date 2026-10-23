# Finding the paired Mac — LocalClip 0.4.0

A Wi-Fi address is a route, not a device identity. A Mac moving from `.34` to `.37` should not require pairing again. Version 0.4.0 separates those concerns.

## Upgrade and reconnect

Install the 0.4.0 APK over the existing APK, update the Mac app, leave Mac sharing started and tap Connect on Android. The package ID, Android development signing certificate, Keystore alias, Mac Keychain item and clipboard encryption protocol are unchanged. Existing pairing codes without the new optional address list remain valid. Local discovery derives its identity hint from the existing secret, so an old stored IP can recover without a new pairing code.

The Android connection card shows **Using host:port** after authentication, rather than displaying a stale saved address as though it were the active connection. **Change address / use Tailscale** edits the endpoint while retaining the same pairing key and reconnects the service. This does not stop a running ADB clipboard helper.

## Same Wi-Fi: automatic discovery

While sharing is started, macOS advertises `_localclip._tcp.` using Bonjour. Android's native Network Service Discovery finds matching advertisements and resolves current addresses and ports. Android 14+ uses service-info updates to track changes; Android 10–13 uses the older resolution API. Pausing Mac sharing withdraws the advertisement; disconnecting Android stops its discovery listener.

The advertised ID is the first 16 bytes of HMAC-SHA256(pairing key, `LC01/discovery`), encoded as 32 hex characters. It is a public, stable discovery hint, not the pairing key. TXT records contain only `id` and protocol version `v=1`. Another device can observe or copy that hint, so an advertisement is never trusted as proof of identity: the normal nonce/HMAC handshake and AES-GCM transport must succeed before clipboard data is accepted.

Discovered endpoints are tried before saved endpoints, with bounded connection attempts. They are not written into the encrypted pairing record as trusted identities. The source uses Android's resolved Network for local routes, and preserves the normal default network for configured DNS/Tailscale destinations. No subnet scanning, IP guessing, hosted directory or new cloud service is used.

Bonjour/mDNS generally stays on one local network. Guest Wi-Fi, AP isolation, multicast filtering, VPN policies and denied Local Network permission can prevent discovery or connections. The saved address/name remains usable when direct routing works. Existing connections can still take up to the existing receive/heartbeat timeout to notice a broken network path; discovery does not promise uninterrupted delivery during a network change.

## Across networks: Tailscale MagicDNS

Connect both devices to the same tailnet and permit TCP 47391 between them. In the Mac app, choose **Use this Mac’s Tailscale name**. Then either enter the displayed full name on Android through **Change address / use Tailscale**, or use the Mac's newly copied pairing code during initial setup.

The Mac button reads the installed Tailscale app's `status --json` output, verifies that its Self addresses belong to this Mac's active interfaces, and selects its full MagicDNS name. It does not change the Tailscale account, permissions or ACLs. If the CLI is unavailable or inactive, paste the name manually. The selected name is remembered across Mac launches.

[Tailscale MagicDNS](https://tailscale.com/docs/features/magicdns) supplies the hostname; [Tailscale addresses](https://tailscale.com/kb/1033/ip-and-dns-addresses/) remain stable as a device moves between underlying networks. Both devices must have Tailscale enabled. Bonjour discovery is not treated as a tailnet-wide discovery protocol. Changing tailnets, removing/recreating a Tailscale device or renaming it can require updating the saved name.

LocalClip keeps its own authenticated encryption over the tailnet. Tailscale may use its coordination/DERP infrastructure; this feature adds no LocalClip internet service. Local Wi-Fi discovery works without Tailscale.

## Pairing format and privacy

The v1 JSON pairing payload still requires `v`, `host`, `port` and `key`. New codes optionally include `hosts`, with up to six explicit addresses/names. Old APKs ignore this optional field and continue using `host`; upgrading both sides enables discovery and alternate routes. A Mac only advertises while sharing is enabled. Secret rotation also changes its discovery ID. The public ID can link advertisements to the same paired installation until rotation.

Sources: [Android NSD guide](https://developer.android.com/develop/connectivity/wifi/use-nsd), [NsdManager API](https://developer.android.com/reference/android/net/nsd/NsdManager), [Apple NetService](https://developer.apple.com/documentation/foundation/netservice).

## Validation and limits

Mac tests exercised real Bonjour publication, resolution, TXT records, withdrawal and restart at another port. Android 17 tests used real NSD registration/resolution and a test-only TCP proxy to the actual Swift encrypted peer: a stale address recovered automatically and a changed advertised endpoint reconnected without replacing the pairing key. Test routes are synthetic; this does not certify multicast forwarding between the Mac and a physical Pixel, which needs a device check. Tailscale setup on the physical phone and cross-network delivery remain unverified.
