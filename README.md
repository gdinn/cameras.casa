# Cameras App

A native Android app for remote monitoring of IP cameras, without relying on any third-party cloud service. The app scans a QR code containing WireGuard VPN credentials, connects directly (peer-to-peer, IPv6) to the home network where the cameras live, and streams the video in real time over WebRTC.

This repository holds the Android client. It's one piece of a larger self-hosted setup:

- **IP cameras** expose their video over **RTSP** on the local network.
- A **Raspberry Pi 4** on that same network ingests the RTSP streams and transcodes them to **WebRTC** (WHEP), which is far better suited for low-latency, real-time playback on mobile/browsers.
- A **WireGuard VPN over IPv6** provides direct, point-to-point remote access — no cloud relay in the middle of the video traffic.
- This **Android app** reads the VPN credentials from a QR code, brings the tunnel up only while it's in the foreground, and renders the camera streams.

## How the app works

Functionally, the app does three things: reads a QR code, connects to the VPN, and displays the video streams. Navigation is a simple linear flow driven by whether valid VPN credentials are already stored on the device (see [`NavigationRoot.kt`](app/src/main/java/com/gdisys/cameras/app/navigation/NavigationRoot.kt)):

1. **Loading (`Init`)** — on launch, [`InitViewModel`](app/src/main/java/com/gdisys/cameras/feature/init/InitViewModel.kt) checks whether valid credentials are already saved. If so, it navigates straight to **Home**; otherwise it sends the user to **Config**.
2. **Config** — the camera-based QR scanner (backed by CameraX + ML Kit Barcode Scanning) reads a QR code, the payload is parsed and validated, and — if valid — persisted to encrypted local storage (DataStore + Android Keystore-backed crypto). The user is then taken to **Home**.
3. **Home** — the app requests VPN permission, brings up the WireGuard tunnel using the stored credentials, opens a WHEP session with the Raspberry Pi for each camera, and renders the WebRTC video streams in a grid, with a focused single-stream view available.

An important design decision: **the VPN tunnel is only active while the app is in the foreground.** It's torn down as soon as the app moves to the background. This exists for two reasons:

- to avoid routing the device's traffic through the camera network when the app isn't actively in use;
- because the tunnel is IPv6-only, so some apps/services stop working correctly while it's connected — minimizing exposure time limits that side effect.

Recording playback is not yet supported inside the app; for now, the recording history (stored on an external drive attached to the Raspberry Pi and shared via Samba) can only be browsed from a desktop machine connected to the VPN.

## The `vpn.json` file and QR code provisioning

Instead of typing WireGuard configuration by hand, the app is provisioned by scanning a single QR code. That QR code simply encodes a JSON document — the same one produced from `qr-code-gen/vpn.json` — with two sections that mirror the `[Interface]` / `[Peer]` blocks of a standard WireGuard config file:

```json
{
  "vpnConfigDefaults": {
    "iDns": "fd00:10::1",
    "iMtu": "1420",
    "pPuk": "<server / peer public key>",
    "pAllowedips": "::/0, 0.0.0.0/0",
    "pEndpoint": "example.com:4567",
    "pPersistentKeepAlive": "25"
  },
  "vpnConfigTokens": {
    "iPrk": "<this device's private key>",
    "iAddr": "fd00:10::20/128",
    "pPsk": "<pre-shared key for this device>"
  }
}
```

- **`vpnConfigDefaults`** — parameters shared by every device that connects to this VPN: the tunnel DNS server, the interface MTU, the server's public key, the allowed IP ranges to route through the tunnel, the server endpoint (`host:port`), and the keepalive interval. These come from the `[Interface]`/`[Peer]` sections of the server-side WireGuard config and stay the same across QR codes.
- **`vpnConfigTokens`** — the credentials unique to a single device: its own WireGuard private key, the tunnel IP address assigned to it, and the pre-shared key negotiated with the peer. Each device you provision needs its own `vpn.json` (and therefore its own QR code) with a distinct `iPrk`/`iAddr`/`pPsk` combination — never reuse tokens across devices.

On the app side, [`ParseUserPreferencesFromQrCodeUseCase`](app/src/main/java/com/gdisys/cameras/core/storage/domain/usecase/ParseUserPreferencesFromQrCodeUseCase.kt) decodes the scanned string into this same structure, validates that every field is present, and only then saves it. From there, [`ConnectVpnUseCase`](app/src/main/java/com/gdisys/cameras/core/vpn/domain/usecase/ConnectVpnUseCase.kt) maps it into a [`VpnConfig`](app/src/main/java/com/gdisys/cameras/core/vpn/domain/model/VpnConfig.kt) and hands it to the WireGuard tunnel implementation.

**`vpn.json` (and the QR codes it produces) contain secret key material — treat it like a password and never commit real values to version control.** The file checked into `qr-code-gen/vpn.json` is meant to be edited locally with your own server/device keys before generating a QR code; the `examples/` folder only contains bogus/sample data for reference.

## The QR code generator (`qr-code-gen/main.py`)

A small Python script that turns `vpn.json` into a scannable QR code image (`qrcode.png`):

```python
with open('vpn.json', 'r', encoding='utf-8') as arquivo:
    vpn_data = json.load(arquivo)

criar_qrcode(json.dumps(vpn_data, ensure_ascii=False), "qrcode.png")
```

It loads `vpn.json`, re-serializes it to a compact JSON string, and feeds that string into the [`qrcode`](https://pypi.org/project/qrcode/) library to render a PNG. The QR is generated with error correction level `H` (recovers up to ~30% of the code even if partially damaged/obscured), which gives some headroom for the fairly large payload the credentials JSON produces.

### Usage

```bash
cd qr-code-gen
python -m venv env_python
source env_python/bin/activate
pip install qrcode[pil]
```

1. Edit `vpn.json` with the real `vpnConfigDefaults` (shared server/peer settings) and a fresh `vpnConfigTokens` block for the device you're provisioning.
2. Run the script:

```bash
python main.py
```

3. `qrcode.png` is generated in the same folder. Open it and scan it with the app's **Config** screen (or display it on another screen/printout) to provision that device's VPN credentials.

Generate a new, unique `vpn.json` (with its own `iPrk`/`iAddr`/`pPsk`) for every device you want to grant access to.

## Architecture

The project is split into two main areas under `app/src/main/java/com/gdisys/cameras`:

- **`core`** — integrations with external libraries and system capabilities (VPN/WireGuard, WebRTC/WHEP, encrypted local storage, camera permissions). This layer follows **Clean Architecture** principles, with a clear split between `domain` (interfaces and use cases) and `data` (concrete implementations). For example, `core/vpn/domain/VpnRepository.kt` defines the contract, and `core/vpn/data/VpnRepositoryImpl.kt` implements it on top of the WireGuard Android library — the rest of the app only ever depends on the interface and its use cases (e.g. `ConnectVpnUseCase`, `DisconnectVpnUseCase`), which keeps the code testable and decoupled from the concrete VPN/WebRTC libraries. This abstraction is not fully applied to WebRTC, since some particulars of the native video streaming stack made a full interface boundary impractical.
- **`feature`** — the app's screens (`init`, `config`, `cameras`), built with **Jetpack Compose** following **MVVM**: Composables stay as thin as possible, with the actual logic living in ViewModels, which lean on the `core` use cases to avoid duplicating logic across features.

Other notable pieces:

- **Dependency injection**: Hilt.
- **Persistence**: Jetpack DataStore, with credentials encrypted via Android Keystore-backed crypto (see `core/storage`).
- **QR scanning**: CameraX + ML Kit Barcode Scanning (see `core/utils/QrCodeAnalyzer.kt`).
- **VPN tunnel**: WireGuard for Android (`core/vpn`), started/stopped by a foreground `VpnLifecycleService` tied to the app's lifecycle.
- **Video streaming**: Stream's WebRTC Android SDK, speaking the WHEP protocol to the Raspberry Pi transcoder (`core/webrtc`).
- **Build**: Gradle with Kotlin DSL (`.kts`) and a version catalog (`gradle/libs.versions.toml`) to centralize dependency versions.
- **Testing**: JUnit, MockK, Turbine and MockWebServer for unit tests, with a JaCoCo task (`./gradlew :app:jacocoTestReport`) scoped to the unit-testable layers (use cases, repositories, ViewModels — excluding Compose UI, DI wiring, and native/hardware-backed code).

## Requirements

- Android device on API 26+ (`minSdk = 26`).
- A running VPN peer (e.g. a WireGuard server on a router/Raspberry Pi) and a WHEP-compatible WebRTC endpoint on the camera network.
- Python 3 with the `qrcode` package to generate provisioning QR codes.
