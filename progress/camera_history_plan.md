# Camera history — plan

Admins browse, play and download the recordings that the Pi's recorder keeps on the Samba share,
from the camera's focused view. Non-admins never see the feature.

## 0. Decisions (agreed before planning)

### Source of the recordings

- Share `backup_cams` on the stream host (`STREAM_HOST`, `fd00:20::cafe`), SMB3, read only
  (`nvr-setup/smb.conf`). Layout: `cam_xx/YYYY/MM/DD/<file>.mp4`.
- File names: the recorder writes `cam_10_2026-11-10_15-00-00.mp4`
  (`nvr-setup/bin/nvr-record:113`). The parser also accepts `cam_10_2026_11_10_15-00-00.mp4`.
  Seconds are always ignored.
- Files are 15 minutes long and start on the clock (`-segment_atclocktime 1`): a file never crosses
  a `:00/:15/:30/:45` boundary. After a reconnect at 15:10, the file `15-10-00` ends at 15:15.
- The file being written is not playable (`+faststart` writes the index when it closes). It is
  hidden, and does not count when deciding whether a day has recordings.
- Times are shown as they appear in the file name (Pi and phone share a time zone).
- `cam_xx` is the same name in the stream URL path (`.../cam_10/whep`) and on the share.

### Who is an admin

- The QR payload gets an optional top-level object. Old QR codes stay valid and mean "not admin":
  ```json
  { "vpnConfigDefaults": {...}, "vpnConfigTokens": {...},
    "smbCredentials": { "smbUser": "admin-a-7", "smbPassword": "..." } }
  ```
- Admin = both fields present and non-blank. Host and share are fixed (`STREAM_HOST`,
  `backup_cams`). Stored in the existing encrypted `user-preferences` DataStore.
- Hiding the screen is UX only. The real enforcement is server side: router + ufw (admin block
  `fd00:10::a:0/112`), Samba `hosts allow` and the Samba password.

### Navigation and screens

- Home → focused view (tap a camera, existing `FocusedStreamView`) → history button in a corner,
  shown only to admins → **History** route for that `cam_xx` (a separate destination).
- History: months as an expandable list, newest first. Expanding a month shows only the days that
  have at least one playable file.
- Tapping a day opens the time picker: 96 slots of 15 minutes, with the slots that have a
  recording highlighted.
- **Nearest recording:** only within the chosen day. Pick the file whose start (HH:mm) is closest
  to the chosen slot, looking forwards and backwards. On a tie, pick the later file. When the start
  differs from the slot, tell the user ("The nearest recording starts at 15:10").
- Player: previous/next file buttons, automatic advance at the end of a file. Both cross midnight
  (and skip days with no recordings).

### Downloads

- Requested one video at a time, from the player. Several can run at once.
- Saved to `Downloads/Cameras`. If the name exists, add a suffix: `_1`, `_2`, ...
- A request for a video that is already downloading is ignored.
- Runs in WorkManager as a long-running foreground worker (`dataSync`), with a progress notification
  and a cancel action.
- Permissions are requested on the first download: `POST_NOTIFICATIONS` (Android 13+; if denied, the
  download still runs) and `WRITE_EXTERNAL_STORAGE` (Android 8–9 only; Android 10+ uses MediaStore).

### Tunnel rules

| Situation | Tunnel | Downloads |
|---|---|---|
| Home or History on screen | up | run |
| App in background or screen locked, downloads running | stays up | continue |
| App in background, last download finishes | drops | — |
| App in background, no download | drops (current behavior) | — |
| App swiped from recents | drops | cancelled, reported as interrupted |
| Process dies | drops with it | reported as interrupted |

- A download worker **never raises the tunnel**. It only holds a tunnel that is already up.
- **Interrupted downloads:** the next time Home opens, a dialog lists them.
  - "Resume" deletes the partial files and enqueues the downloads again.
  - "Discard" deletes the partial files only.
- **Assumption (not discussed explicitly):** a swipe from recents is reported the same way as a
  process death. Downloads that fail for other reasons (SMB error, disk full) show a failure
  notification and are listed in the same dialog.

## 1. Design

### 1.1 Tunnel demand (replaces "Home owns the tunnel")

Today `HomeViewModel` connects on resume and disconnects on pause (`tunnelWanted` + a single
collector). That model has a single owner and cannot serve History or the downloads.

New `TunnelCoordinator` in `core/vpn`:

- A domain contract with a process-wide, singleton implementation, running in a new
  `@ApplicationScope CoroutineScope` (`core/coroutines`).
- `request(holder)` / `release(holder)` keep a set of holders: `Screen(name)` and
  `Download(workId)`.
- `holdIfUp(holder): Boolean` adds a download holder only while the tunnel is `CONNECTED`. This is
  what makes "the worker never raises the tunnel" true.
- **A single collector applies the decision**: tunnel wanted = holder set not empty. It keeps the
  guarantees of the current collector: no overlapping connect/disconnect, the last state wins,
  `NonCancellable` operations.
- **Grace period on release (~1.5 s):** going from Home to History pauses Home before History
  resumes. Without the grace period the tunnel would drop and come back up during the switch. A new
  request within the grace period cancels the pending disconnect.
- `HomeViewModel` keeps its rule "resumed *and* has streams", but turns it into
  `request`/`release`. `HistoryViewModel` does the same on its own resume/pause.
- `VpnLifecycleService.onTaskRemoved` cancels all download work (tag), clears the coordinator and
  tears the tunnel down (current behavior).

**Download holders live in memory, on purpose.** The ViewModel takes the holder when it enqueues
the work, keyed by the work id, while the tunnel is up (History is on screen). The worker checks
that its holder exists. If it does not, the process that enqueued the work is gone, so this run is
a WorkManager re-run after a process death: the worker marks the download as interrupted and exits
without network. The coordinator releases the holder when the work reaches a terminal state.

### 1.2 `core/recordings`

| Layer | Contents |
|---|---|
| `domain` | `Recording` (camera, start `LocalDateTime` without seconds, remote path), `RecordingFileName` parser (both formats), `RecordingsRepository` (months with recordings, days of a month with playable files, files of a day, file after/before a given one across days), `SmbCredentials` access through storage |
| `data` | smbj-based implementation. One `SMBClient` per process, sessions opened on demand and closed when idle. Listing goes through `DiskShare.list`. Detection of the file being written |
| `di` | Hilt bindings |

Single-screen rules go to `feature/history/logic`: the nearest-file selection, the 96-slot model and
the highlighting.

### 1.3 Player

- Media3 ExoPlayer with a custom `DataSource` that reads the file from the share through smbj with
  random access. The file is not downloaded first, and seeking works because of `+faststart`.
- The feature receives the player through a swappable abstraction, like `LocalEglBase` /
  `LocalQrCodeScanner`, so instrumented tests can use a fake and the feature never imports `data`.
- Previous/next/auto-advance ask `RecordingsRepository` for the neighbor file.
- Caveat to state in the UI and docs: playback depends on the home connection's upload being
  higher than the camera's bitrate.

### 1.4 Downloads (`core/downloads`)

- `DownloadRepository` (domain) enqueues, observes and cancels downloads. It is backed by
  WorkManager, with unique work name = remote path and `ExistingWorkPolicy.KEEP` (duplicate
  requests are ignored).
- `RecordingDownloadWorker` (`CoroutineWorker` + `@HiltWorker`):
  - checks its tunnel holder (§1.1) and calls `setForeground` with type `dataSync`;
  - copies SMB → output in chunks and publishes progress;
  - on Android 10+ the output is a MediaStore `Downloads` entry with `RELATIVE_PATH=Download/Cameras`
    and `IS_PENDING=1`; on 8–9 it is a `.part` file in `Download/Cameras`, renamed at the end;
  - the `_n` suffix is resolved at the end, when the name becomes final.
- The copy logic is separate from the worker class, so it can be tested on the JVM.
- Download records persist in a small encrypted DataStore (`download-records`): remote path, output
  reference, state (`RUNNING`, `INTERRUPTED`, `FAILED`).
  - A record still `RUNNING` with no holder in this process is `INTERRUPTED`.
  - The worker removes the record on success and on cancellation by the user.
- The interrupted/failed dialog is shown by Home (only to admins) and applies Resume/Discard
  through `DownloadRepository`.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`,
  `WRITE_EXTERNAL_STORAGE maxSdkVersion=28`, and the `dataSync` type on WorkManager's
  `SystemForegroundService`. WorkManager is initialized on demand (Hilt `Configuration.Provider`).

### 1.5 New dependencies (versions verified when added)

| Dependency | Why |
|---|---|
| `com.hierynomus:smbj` | SMB2/3 client (the Pi requires SMB3) |
| `androidx.media3:media3-exoplayer`, `media3-ui` (or `media3-ui-compose`) | Playback |
| `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work` (+ compiler) | Downloads |

smbj pulls in BouncyCastle and an event bus that uses reflection, so R8 keep rules are likely
needed. Validate them on the `e2eRelease`/release build on a device, as the existing keep rules
were.

## 2. Steps

Each step ends with unit tests, Konsist green and the 90% LINE gate. Record the outcome here when
the step is done.

### Step 1 — Spike: smbj + Media3 against the real Pi

Before building on them, prove the two risky pieces on a device with an admin tunnel:

- smbj over IPv6 literal `fd00:20::cafe`, SMB3 dialect, encryption/signing as the server
  negotiates them;
- listing `cam_xx/YYYY/MM/DD`;
- an ExoPlayer `DataSource` that plays and seeks a file through the tunnel (MTU 1280);
- a release build with minification.

Throwaway code; only the findings are kept here.

**Spike code (temporary, delete when the step closes):**

| File | What it is |
|---|---|
| `app/src/main/java/com/gdisys/cameras/spike/RecordingsSpike.kt` | The probe |
| `app/src/androidTestReal/java/com/gdisys/cameras/e2e/RecordingsSpikeTest.kt` | The `@RealEnvironment` test that drives it |
| `app/build.gradle.kts` | `CAMERAS_E2E_SMB_FILE` push task and the JaCoCo exclusion of `spike/**` |
| `app/proguard-e2e.pro` | Keeps only `RecordingsSpike`'s entry point |

The test brings the tunnel up the way the smoke does. The probe then:

- connects to the share with smbj (`BCSecurityProvider`) and reports the dialect, max read size,
  signing and encryption;
- lists `cam_xx/YYYY/MM/DD`, times the listing and checks the file names against the parser regex;
- times sequential reads with 64 KB and 1 MB chunks, and one random read near the end of the file;
- plays the second-newest file through ExoPlayer with a 1 MB read-ahead SMB `DataSource`: time to
  ready, playback rate over 10 s, a seek to 2/3, buffering events.

The dependencies stay after the spike: smbj 0.15.0 (pulls in bcprov, mbassador, slf4j-api,
asn-one) and media3-exoplayer 1.11.1.

**Status (2026-11-11).**

- Debug app and test sources compile. Gradle runs on the user's side, because it cannot start
  inside the agent's sandbox (no local socket binding).
- `assembleE2eRelease` first failed on R8 missing classes: `javax.el.*` (mbassador's EL filters)
  and `org.ietf.jgss.*` (smbj's Kerberos/SPNEGO login).
- Fixed with a new smbj block in `proguard-rules.pro`:
  - `-dontwarn` for both families;
  - mbassador kept whole, plus the `@Handler` methods, because smbj's internal events are
    dispatched by reflection and would otherwise be lost silently.
- `assembleE2eRelease` is now green. Waiting for the run on a phone with an **admin** peer.

**Outcome (2026-11-11). Both pieces work.** The run used `e2eRelease` (minified) on an SM-S721B
(Android 17), admin peer `a-2`, against `cam_10`. Findings:

**Connection**

| | Result |
|---|---|
| IPv6 literal | smbj connects to `fd00:20::cafe` with no workaround |
| Dialect | SMB 3.1.1 |
| Connect | 59 ms |
| NTLM login | 62 ms |
| Max read size | 8 MiB |
| Signing / encryption | Neither required. Acceptable: the traffic only travels inside WireGuard |
| R8 at runtime | The keep rules hold: BouncyCastle and mbassador work minified |

**Listing**

| | Result |
|---|---|
| All 22 day folders of a camera | 284 ms |
| One day folder | 73–96 ms |
| File names | All 46 of the day match the parser |

- **Seconds are not always `00`.** The probe file is `cam_10_2026-11-11_11-00-01.mp4`, which
  confirms the "ignore seconds" rule.
- **Tomorrow's empty folder is real.** `2026/11/12` existed with 0 files, so days must be filtered
  by content.
- **The newest file is the one being written.** At 11:15:07, `11-15-00` held 1.5 MB.

**Reads**

- A 15-minute 1080p file is about 145 MB, so the camera records at ≈1.3 Mbit/s.
- Sequential reads:

  | Chunk | Throughput |
  |---|---|
  | 64 KB | 12 Mbit/s |
  | 1 MB | 33 Mbit/s |

  Each SMB read is one round trip through the tunnel, so the chunk size matters.
- A random read near the end of the file took 37 ms.

**Playback** (ExoPlayer + SMB `DataSource` with a 1 MB read-ahead)

- Ready in 838 ms; duration 898 s.
- Real time: 10.0 s of video in 10 s of wall time.
- Seek to 2/3: ready in 529 ms.
- No errors.

**Consequences for the next steps**

- **Step 5 — expanding a month:** 30 sequential day listings would take ~2.5 s. List the days in
  parallel on the same share, or accept a spinner.
- **Step 8 — player:** keep a large read-ahead (≥ 1 MB) in the SMB `DataSource`. ExoPlayer's
  small reads would otherwise be latency-bound.
- **Step 9 — downloads:**
  - read in large chunks (try 4–8 MB, up to the negotiated max read size);
  - at ~33 Mbit/s, a file takes ~35 s on this link.
- **Kept from the spike:** the smbj/Media3 dependencies, the smbj block in `proguard-rules.pro`
  and the `BCSecurityProvider` configuration.
- **Removed:** the spike code (`spike/`, `RecordingsSpikeTest`, the `CAMERAS_E2E_SMB_FILE` push
  task, its keep rule and its JaCoCo exclusion).

### Step 2 — Admin credentials in the QR payload

- `SmbCredentials` model and `UserPreferences.smbCredentials` (nullable, default null).
- Parsing in `ParseUserPreferencesFromQrCodeUseCase`. Proposed rule: an `smbCredentials` object
  with a blank or missing field makes the QR invalid; an absent object means "not admin".
- `ObserveIsAdminUseCase`. QR fixtures for admin/non-admin.
- The payload is never logged; the same rule now covers the Samba password.

**Outcome.** _pending_

### Step 3 — Generator and Pi guide

- `reports/infra_guide.md` Step 26 (`gen-peers.sh`), for the admin profile:
  - one Samba user per peer, named after the peer (`admin-a-7`), with a random password
    (`openssl rand`);
  - `smbCredentials` added to that peer's `vpn.json` entry;
  - a new `peers/pi-smb-users.sh` with one block per peer:
    `useradd --no-create-home --shell /usr/sbin/nologin -G smb_viewer <name>`, then
    `smbpasswd -s -a <name>` with the password on stdin;
  - the revocation commands, commented out.
- Step 28 (QR rendering): confirm that `main.py` encodes the whole entry, including the new object.
- New step in the Part 8 flow: copy and run `pi-smb-users.sh` on the Pi, then delete it.
- `reports/pi_services_guide.md` Step 44 links to it (manual creation stays as the alternative).
- `.gitignore` note: `pi-smb-users.sh` holds passwords, like the rest of `peers/`.
- Fix the stale admin warning in the script ("SSH, Samba and internet": admins have no internet
  through the tunnel since the split tunnel).

**Outcome.** _pending_

### Step 4 — Tunnel coordinator

- `@ApplicationScope` scope, `TunnelCoordinator` contract + implementation, grace period.
- `HomeViewModel` migrates to it, with no behavior change for Home.
- `VpnLifecycleService.onTaskRemoved` also clears the coordinator.
- Unit tests: ordering, last state wins, grace period, `holdIfUp` refused while down.
- Adjust `VpnLifecycleFlowTest`/`VpnFailureFlowTest` to the grace period.

**Outcome.** _pending_

### Step 5 — `core/recordings`

- File name parser (both formats, invalid names ignored).
- Repository over smbj: months, days with playable files (one listing per day when a month is
  expanded), files of a day, neighbor file across days.
- Detection of the file being written: the newest file whose slot end is still in the future.
- Fake repository for instrumented tests.
- JaCoCo exclusions for the smbj-backed classes only (the guard test keeps them honest).

**Outcome.** _pending_

### Step 6 — History route: months and days

- `NavigationRoute.History(camera: String)`.
- `cam_xx` extracted from the focused stream URL in `feature/cameras` and passed through navigation.
- History button on the focused view, admins only.
- `HistoryViewModel` takes a tunnel holder on resume and releases it on pause.
- States: loading / list / empty / error (SMB login failed: bad credentials; host unreachable).
- Expandable months, newest first.

**Outcome.** _pending_

### Step 7 — Time picker and nearest recording

- 96-slot picker with highlighted slots.
- Nearest-file rule in `feature/history/logic`, with exhaustive unit tests: exact match,
  forwards-only, backwards-only, tie → later, empty day, ignored seconds, in-progress file excluded.
- Message when the chosen file does not start at the slot.

**Outcome.** _pending_

### Step 8 — Player

- SMB `DataSource` + ExoPlayer behind the swappable player abstraction.
- Player screen: previous/next, auto-advance, crossing midnight, end of the list, read errors.
- Released on pause and recreated on resume, with the position kept.

**Outcome.** _pending_

### Step 9 — Downloads

- Worker, records store, MediaStore / legacy output, `_n` suffix, duplicate rule, progress
  notification with cancel, permissions on the first download.
- Tunnel holder taken at enqueue and released at the terminal state.
- Download button on the player, plus the state of the current file (downloading / progress).

**Outcome.** _pending_

### Step 10 — Interruptions

- Swipe from recents: cancel work by tag, mark records `INTERRUPTED`, tunnel down.
- Process death: a WorkManager re-run without a holder marks the record `INTERRUPTED`, and records
  left `RUNNING` are reconciled at startup.
- Home dialog with Resume / Discard; partial files deleted in both cases.
- Manual device check: background + lock during a download (tunnel stays up and drops at the end);
  swipe during a download; `adb shell am kill` during a download.

**Outcome.** _pending_

### Step 11 — Instrumented flows, release check, docs

- Flow tests with the fakes:
  - an admin sees the button and a non-admin does not;
  - month → day → slot → player;
  - the nearest-recording message;
  - a download request;
  - the interrupted dialog.
- Release build with the new keep rules, smoke-tested on a device against the real Pi: list, play,
  download (real-VPN tests run by hand, as today).
- `docs/architecture.md`:
  - new route and features (§4, §5);
  - `core/recordings` and `core/downloads` (§6);
  - tunnel coordinator replacing "Home owns the tunnel" (§1, §6.2, §7);
  - dependencies (§8.2), R8 keep rules (§2), testing (§10);
  - diagrams if affected (§12).

**Outcome.** _pending_
