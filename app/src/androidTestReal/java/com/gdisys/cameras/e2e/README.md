# Real-environment smoke (level C)

`RealEnvironmentSmokeTest` automates the release smoke: the real QR → the real WireGuard tunnel →
a camera stream playing. It runs on the production app (`CamerasApp`, its Hilt graph, `GoBackend`,
WHEP), never on CI, and only by hand on a phone that can reach the home endpoint over IPv6.

It runs on `e2eRelease`: the release build as R8 makes it, signed with the debug key and installed as
`com.gdisys.cameras.e2e`. R8 mistakes in this stack (a missing keep rule for a serializer, a JNI
entry point) only show at runtime, so this is the run that catches them.

It skips the camera only. The QR JSON goes through the same `ParseUserPreferencesFromQrCodeUseCase`
the scanner feeds, and the production use cases save it.

## What you need

- **A dedicated test peer** and its QR JSON in a local file outside git. `qr-code-gen/.gitignore`
  already ignores `vpn*.json`. The file is one peer object, not the list in `vpn.json`. Never use a
  real phone's key: two clients with the same key fight over the endpoint, and the test would knock
  that phone off the tunnel.
- **A physical phone** with USB debugging, the screen unlocked and animations off. The emulator's
  IPv6 and VPN support are not reliable enough.
- **The Pi streaming video** on the path you pass.
- **No other VPN active on the phone.** Android runs one VPN at a time, so the tested app's tunnel
  replaces any other, including the release app's.

## Running it

```bash
CAMERAS_E2E_QR_FILE=~/path/to/test-peer.json \
CAMERAS_E2E_STREAM_PATH=8889/cam_160 \
ANDROID_SERIAL=<phone serial from `adb devices`> \
./gradlew :app:connectedE2eReleaseAndroidTest -PrealEnvironment
```

- `-PrealEnvironment` swaps the runner for the plain `AndroidJUnitRunner`, so the test runs on the
  production app instead of `HiltTestApplication`. It also runs only `@RealEnvironment` tests, and
  compiles only this directory (`src/androidTestReal/`). Without it, every run excludes them.
- `-PrealEnvironment=debug` with `connectedDebugAndroidTest` runs the same test on the debug build.
  If the minified run fails and this one passes, the problem is R8, not the tunnel or the camera.
- `CAMERAS_E2E_STREAM_PATH` is the port and path after `http://[fd00:20::cafe]:`.
- `ANDROID_SERIAL` is needed only with more than one device attached.

## What it does

1. `pushRealQrPayload` pushes the QR file to `/data/local/tmp/cameras-e2e-qr.json`.
2. The test reads it through the shell and saves the parsed credentials and the stream URL.
3. It grants the VPN consent with `appops set <package> ACTIVATE_VPN allow`. The consent stays
   granted for the debug app afterwards.
4. It opens `MainActivity` and waits up to 90 s for a card whose `StreamPlaybackState` is
   `PLAYING`, which means a frame was drawn. A tunnel that is merely up is not enough.
5. The test deletes the file, and `removeRealQrPayload` deletes it again, even when a test fails.

On a timeout, the failure names the last step reached:

| Message | Where to look |
|---|---|
| The app opened Config | The stored credentials were rejected: the QR is not a valid peer |
| Home is still waiting for the tunnel | Endpoint, IPv6 reachability, the test peer on the MikroTik |
| The WHEP negotiation failed | The stream path, MediaMTX on the Pi |
| No frame arrived | The camera is connected but not sending video |

## Secrets

The QR holds the test peer's private key. It never appears on a command line (it travels as a
pushed file) and never in a log or failure message. A parse failure reports only the exception
type, because kotlinx.serialization quotes the JSON in its messages.

## R8 and the test APK

The test calls a few app classes directly (the QR parser, the storage use cases and repositories,
the playback state). `app/proguard-e2e.pro` keeps exactly those in `e2eRelease`, and nothing else
differs from release. The libraries the app also depends on are left out of the test APK, so the
test framework runs on the app's shrunk copy of them: `proguard-e2e.pro` keeps the ones it needs too
(`androidx.tracing`, used by AndroidX Test; the Kotlin standard library and kotlinx.coroutines;
the Compose runtime and UI layer, which Compose UI Test drives, with `androidx.lifecycle`,
`androidx.core` and `androidx.concurrent.futures`). Foundation and Material 3 stay shrunk. To list
what the test APK still lacks without a device, run R8's `TraceReferences --check` with the test
APK as `--source` and the app APK as `--target` (both zipped dex), and drop from its output the
classes the app's dex defines (`dexdump`): the tool reports those as missing too.. If R8 removes something the test calls, the test APK fails to build with a
missing class, or the test fails with `NoSuchMethodError`: add that class to `proguard-e2e.pro`,
never a broader rule, since a wider keep hides the breakage this run is for.
