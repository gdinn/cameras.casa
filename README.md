# Cameras App

A native Android app for remote monitoring of IP cameras, without relying on any third-party cloud service. The app scans a QR code containing WireGuard VPN credentials, connects directly (peer-to-peer, IPv6) to the home network where the cameras live, and streams the video in real time over WebRTC.

This repository holds the Android client. It's one piece of a larger self-hosted setup:

- **IP cameras** expose their video over **RTSP** on the local network.
- A **Raspberry Pi 4** on that same network ingests the RTSP streams and re-publishes them as **WebRTC** (WHEP), which is far better suited for low-latency, real-time playback on mobile/browsers.
- A **WireGuard VPN over IPv6** provides direct, point-to-point remote access — no cloud relay in the middle of the video traffic.
- This **Android app** reads the VPN credentials from a QR code, brings the tunnel up only while it's in the foreground, and renders the camera streams.

> For a deep, file-level walkthrough of the codebase — layering rules, per-screen MVVM breakdowns, data-flow diagrams and known consistency gaps — see **[docs/architecture.md](docs/architecture.md)**.

## Features

- **QR-code provisioning** of WireGuard credentials (CameraX + ML Kit, fully on-device).
- **Foreground-only VPN tunnel**, raised when the Home screen resumes and dropped when it pauses.
- **Live WebRTC/WHEP playback**, one session per camera, rendered in a grid.
- **Configurable stream set** — add, remove and reset camera URLs in-app.
- **Configurable grid geometry** — columns, rows and a dynamic-rows toggle, stored **independently for portrait and landscape**.
- **Drag-and-drop reordering** of cameras, persisted per orientation.
- **Tap-to-focus** on a single camera without restarting its video session.
- **Paged fixed grids** — when dynamic rows are off, cameras are laid out `rows × columns` per page in a horizontal pager.
- **Encryption at rest** — everything persisted is AES-encrypted with an Android Keystore key.

Recording playback is not yet supported inside the app; for now, the recording history (stored on an external drive attached to the Raspberry Pi and shared via Samba) can only be browsed from a desktop machine connected to the VPN.

## How the app works

The app has five navigation destinations, declared as type-safe `@Serializable` routes in [`NavigationRoute.kt`](app/src/main/java/com/gdisys/cameras/app/navigation/NavigationRoute.kt) and wired together in [`NavigationRoot.kt`](app/src/main/java/com/gdisys/cameras/app/navigation/NavigationRoot.kt). The flow is **gated, not linear**:

1. **Loading (`Init`)** — on launch, [`InitViewModel`](app/src/main/java/com/gdisys/cameras/feature/init/InitViewModel.kt) checks whether valid credentials are already saved. If so, it navigates straight to **Home**; otherwise it sends the user to **Config**.
2. **Config** — a checklist of the four setup steps: grant camera permission, scan the QR code, grant VPN permission, configure stream URLs. It **refuses to leave for Home** until credentials, VPN permission and at least one stream URL are all in place, showing a toast naming the first missing requirement.
3. **QrCode** — a CameraX preview with an ML Kit barcode analyzer. It only reads the payload; decoding, validation and persistence are Config's job, after the raw string travels back through the navigation back stack.
4. **StreamURLs** — edit the camera URL list and the grid geometry. Edits are held in an in-memory draft and written to disk only on an explicit, dialog-confirmed save.
5. **Home** — brings up the WireGuard tunnel using the stored credentials, opens a WHEP session with the Raspberry Pi for each visible camera, and renders the WebRTC video streams in the configured grid.

An important design decision: **the VPN tunnel is only active while the app is in the foreground.** It's raised on the Home route's `ON_RESUME` and torn down on `ON_PAUSE`, with a `VpnLifecycleService` as a safety net if the task is removed. This exists for two reasons:

- to avoid routing the device's traffic through the camera network when the app isn't actively in use;
- because the tunnel is IPv6-only and routes `::/0`, so some apps/services stop working correctly while it's connected — minimizing exposure time limits that side effect.

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

A small Python script that turns `vpn.json` into a scannable QR code image (`qrcode.png`). It is not part of the Android build:

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

MVVM on top of Clean-Architecture-style layering, in a **single `:app` Gradle module** — package structure alone carries the layering.

![Package diagram](docs/images/package-diagram.png)

Four top-level package groups live under `app/src/main/java/com/gdisys/cameras`:

| Group | Role |
|---|---|
| `app/*` | Composition root and navigation: `MainActivity`, `CamerasApp`, `app.navigation` |
| `feature/*` | One package per screen: route + ViewModel + UiState + `components/` + optional `logic/` |
| `core/*` | Everything shared: `storage`, `vpn`, `webrtc`, `permission`, `network`, `utils`, `components` |
| `ui/*` | `ui.theme` — `CamerasTheme`, color and typography |

Inside each `core/<area>` the split is the classic one — `domain` holds abstractions, models and use cases as pure Kotlin; `data` holds the implementations; `di` binds one to the other. Consumers (`feature/*`, `app/*`) depend on `domain` and receive implementations by injection.

Because there is only one module, nothing stops a bad import at compile time. That job belongs to [`LayerDependencyTest`](app/src/test/java/com/gdisys/cameras/architecture/LayerDependencyTest.kt), which uses **Konsist** to fail the build when a feature imports a `.data.` package, when a domain package reaches into `data`, Compose or a feature, or when one feature imports another. It's the fastest way to learn which arrows are allowed.

Pure, JVM-testable logic is deliberately split by reach: shared rules live in `core/<area>/domain` (URL validation, preference reconciliation), while single-screen rules live in `feature/<screen>/logic` (paging math, grid row fitting, grid-input validation).

### Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.3.20, Java 17 |
| UI | Jetpack Compose (BOM 2026.03.01), Material 3 |
| DI | Hilt + KSP |
| Navigation | `navigation-compose`, type-safe routes via `kotlinx.serialization` |
| Async | Coroutines + `Flow` / `StateFlow` / `Channel` |
| Persistence | Jetpack DataStore (typed, custom encrypted serializer) + Android Keystore |
| VPN | `com.wireguard.android:tunnel` (`GoBackend`) |
| Video | `io.getstream:stream-webrtc-android` (WHEP over `HttpURLConnection`) |
| QR scanning | CameraX + ML Kit Barcode Scanning |
| Build | Gradle KTS + version catalog (`gradle/libs.versions.toml`) |
| Tests | JUnit, MockK, Turbine, MockWebServer, Konsist |
| Coverage | JaCoCo, custom `:app:jacocoTestReport` task |

**SDK levels:** `minSdk 26`, `targetSdk 36`, `compileSdk 36`.

### Notable design points

- **Storage is encrypted and self-healing.** Two independent typed DataStore files (user preferences, stream preferences), each with its own Keystore alias. Reads that fail to decrypt or parse fall back to defaults instead of crashing. `streamUrls` is the canonical set; per-orientation orders are reconciled against it on every read.
- **One-shot effects are events, not state.** Navigation, `Intent` launches and toasts travel as `Channel`-backed flows consumed in `LaunchedEffect`, so they can't replay on recomposition. Every ViewModel extends `ToastEventViewModel`.
- **Video sessions survive layout changes.** `HomeScreen` memoizes one `movableContentOf` per stream URL, so moving a camera between the grid and the focused view keeps the same renderer and WHEP session alive.
- **Aspect ratio is discovered, not assumed.** Row heights are computed from the first decoded frame of each stream, so the grid re-measures itself instead of cropping or distorting.
- **The WebRTC contracts sit at the package root**, not under `domain` — `VideoSink` is an SDK type and the sink is the renderer the UI creates, so the contract owns that dependency rather than pretending to be pure domain.

## Building and testing

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest     # unit tests, including the architecture assertions
./gradlew :app:jacocoTestReport      # coverage → app/build/reports/jacoco/jacocoTestReport/html/index.html
```

Two of the unit tests are guardrails rather than feature tests: `LayerDependencyTest` fails on a layering violation, and `StreamHostTest` fails when the `STREAM_HOST` Kotlin constant drifts from the cleartext host declared in `network_security_config.xml`.

The `jacocoTestReport` task narrows coverage to the unit-testable surface — it excludes generated code, DI, Compose UI, bootstrap, theme, and anything backed by a native or hardware stack.

## Documentation

- **[docs/architecture.md](docs/architecture.md)** — full technical reference: route inventory, per-feature MVVM breakdowns, core packages, data flow, external resources and known consistency gaps.
- **`docs/diagrams/`** — Mermaid sources for every diagram, kept in version control so the PNGs in `docs/images/` are reproducible. Regeneration instructions are in [§12 of the architecture doc](docs/architecture.md#12-regenerating-the-diagrams).

## Requirements

- Android device on API 26+ (`minSdk = 26`).
- A running WireGuard peer (e.g. on a router or Raspberry Pi) and a WHEP-compatible WebRTC endpoint on the camera network.
- Python 3 with the `qrcode` package to generate provisioning QR codes.

## License

Licensed under the [Apache License 2.0](LICENSE).
