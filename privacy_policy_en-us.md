# Privacy Policy — Cameras App

**Application ID:** `com.gdisys.cameras`
**Last updated:** September 22, 2026

## Summary

Cameras App is a native Android client for watching IP cameras that live on **your own**
private network. It connects to that network through a **WireGuard VPN tunnel you provide**
and plays the video over WebRTC.

**The developer does not operate any server used by this app.** There is no account, no
sign-up, no cloud service in the middle, and no analytics. All data the app handles stays on
your device or travels directly between your device and your own network.

## 1. Who is responsible

This policy covers the Android application "Cameras" distributed under the application ID
`com.gdisys.cameras`.

Contact for privacy questions: `contato@igordebastiani.com`

## 2. Data the app stores on your device

The app stores only what it needs to reconnect to your own network and to render your
cameras the way you configured them:

| Data | Origin | Purpose |
|---|---|---|
| WireGuard credentials — device private key, tunnel address, pre-shared key | Scanned from the QR code you generate | Bringing up the VPN tunnel to your network |
| WireGuard network parameters — server public key, server endpoint (`host:port`), allowed IP ranges, tunnel DNS, MTU, keepalive interval | Scanned from the same QR code | Configuring the tunnel |
| Camera stream URLs | Typed or edited by you in the app | Knowing which streams to play |
| Display preferences — camera order and grid geometry (columns, rows, dynamic-rows toggle), stored separately for portrait and landscape | Set by you in the app | Laying out the camera grid |

**This data never leaves your device.** It is not sent to the developer, and it is not sent to
any third party.

### How it is stored

Everything listed above is persisted in local Jetpack DataStore files that are **AES-encrypted
with a key held in the Android Keystore**. The key is bound to the device and cannot be
extracted from it.

These files are **excluded from Android cloud backup and from device-to-device transfer**
(`backup_rules.xml` and `data_extraction_rules.xml`). Your WireGuard private key therefore
never travels to Google's backup servers or to a new phone — when you switch devices, you scan
the QR code again.

## 3. Data the app does NOT collect

The app does **not** collect, store or transmit:

- Personal identifiers — no name, e-mail address, phone number, or account of any kind.
- Device or advertising identifiers.
- Location data.
- Usage analytics, telemetry, crash reports, or performance metrics.
- Contacts, files, photos, microphone audio, or any other content on your device.

The app contains **no analytics SDK, no advertising SDK and no crash-reporting SDK**.

## 4. Permissions and why they are needed

### Camera (`android.permission.CAMERA`)

Used **exclusively to scan the provisioning QR code** that carries your WireGuard
configuration. Scanning is performed entirely **on-device** by Google ML Kit's bundled barcode
scanner, which requires no network connection.

The camera preview frames are analyzed in memory and discarded. **No image, photo or video is
ever saved to storage or transmitted anywhere.** The camera is active only while the QR-code
screen is open.

### Internet (`android.permission.INTERNET`)

Used for two things, both of which point at infrastructure **you** control:

1. Establishing the WireGuard tunnel to the VPN server endpoint from your QR code.
2. Opening a WHEP/WebRTC session with your own media server (e.g. a Raspberry Pi on your
   network) to receive the camera video.

### VPN (Android VPN service)

The app uses Android's VPN service to raise the WireGuard tunnel. Android will ask for your
explicit consent the first time, through the system dialog.

**The tunnel is foreground-only.** It is raised when the camera screen resumes and torn down
when it pauses, with a lifecycle service as a safety net if the app's task is removed. Your
device's traffic is not routed through the camera network when the app is not actively in use.

## 5. Video streams and network traffic

Camera video travels **directly** between your device and the media server on your own
network, inside the encrypted WireGuard tunnel. It is peer-to-peer with respect to third
parties: **no relay, no cloud, and no developer-operated server is involved in the video path.**

The developer has no ability to see, record or access your camera streams.

Because both the VPN server and the media server are operated by you, any logging they perform
is governed by your own configuration, not by this app.

## 6. Third-party components

The app bundles the following libraries. None of them is used to collect data about you:

- **Google ML Kit Barcode Scanning** — QR-code decoding, bundled and executed on-device.
- **WireGuard for Android (`com.wireguard.android:tunnel`)** — the VPN tunnel implementation.
- **Stream WebRTC Android** — WebRTC video playback.
- **AndroidX / Jetpack (Compose, CameraX, DataStore, Navigation, Lifecycle)** — application
  framework.

The app does not integrate any advertising network, attribution SDK, or social login.

## 7. Data retention and deletion

Because all data is local, you are in full control of it:

- **Reset the stream configuration** from the app's stream-URL screen.
- **Clear the app's data** in Android Settings → Apps → Cameras → Storage → Clear data.
- **Uninstall the app** — this permanently removes the encrypted DataStore files and the
  associated Android Keystore key.

There is no server-side copy for the developer to delete, and no deletion request to file.

## 8. Children's privacy

The app is not directed at children and does not knowingly collect any data from anyone,
including children under 13.

## 9. Security

- Persisted configuration is encrypted at rest (AES, Android Keystore key).
- All remote traffic runs inside a WireGuard tunnel (ChaCha20-Poly1305 encryption).
- Cleartext HTTP is permitted for exactly one host — the media server reachable only inside the
  tunnel — and is blocked everywhere else by the app's network security configuration.
- The provisioning QR code contains secret key material. Treat it like a password: do not share
  it, and generate a distinct one for each device.

No security measure is absolute, but the app is designed so that a compromise of the developer
or of any third party cannot expose your data, because neither ever holds it.

## 10. Changes to this policy

Any change to this policy will be published in the app's repository, with the "Last updated"
date above revised accordingly. Material changes will also be reflected in the Google Play
listing.

## 11. Contact

Questions about this policy: `contato@igordebastiani.com`
