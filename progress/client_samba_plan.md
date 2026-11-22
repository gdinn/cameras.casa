# Samba for client peers — plan

Client peers (`fd00:10::c:N`) gain read-only Samba access to the recordings (`backup_cams`, group
`smb_viewer`, every camera), the same as admins. Only TCP 445: SSH and HLS stay admin only.

## Decisions

| Topic | Decision |
|---|---|
| What clients gain | TCP 445 only, read only, same share and group as admins |
| Address scope | `fd00:10::c:0/112` (the `vpn-clients` list), not the whole `/64` |
| Login per client batch | Optional: `gen-peers.sh` asks for client batches; admins always get one |
| Generator | Changed only inside `infra_guide.md` (Step 26), no file in `qr-code-gen/` |
| Existing client peers | Not migrated: new peers are issued when needed |
| App | Recordings access depends only on a stored Samba login (already true); "admin" naming renamed to "recordings access" |
| Client without login | Reaches 445 (firewall opens the block) but Samba refuses it (no anonymous session) |

## Steps

1. `infra_guide.md`: address lists, router forward rule, Step 26 generator, Step 30 table, Part 8, Part 9 (ufw).
2. `pi_services_guide.md`: overview, Step 43 (hosts allow), Step 44, Step 45 tests.
3. `nvr-setup/smb.conf` (`hosts allow`) and comments.
4. New `reports/migration_client_samba.md`.
5. App: rename `ObserveIsAdminUseCase`, `RecordingsException.NotAdmin`, `HistoryError.NOT_ADMIN`, strings, KDocs, tests; new fixture `7_validqr_client_samba`; `docs/architecture.md`.

## Outcomes

1. Done. Router: new rule `FORWARD: VPN client -> Rpi (SMB)` (445, `vpn-clients`) before the VPN drop; the admin rule is unchanged. `vpn-clients` comment no longer says "documentation only". Generator: `SMB=yes|no` (asked for client batches only) replaces the `PROFILE = admin` checks; client users are `client-c-N`. Tested the extracted script with a fake `wg` (`bash -n` plus three runs: clients with login, clients without, admins). Step 30: separate rows for 445 (client ✅) and 8888 (client ❌). Part 9: sixth ufw rule; Step 34 also checks 445 from `fd00:10::1` (timeout).
2. Done. Step 45 now also tests from a client tunnel and in the app.
3. Done. `install.sh` had no admin wording: unchanged.
4. Done: `reports/migration_client_samba.md` (steps C1–C6), linked from `infra_guide.md` and `README.md`. The `sed` of C1 was checked (with perl) against the previous `smb.conf`. Not published to the GitHub Pages site (that happens outside this repo).

5. Done. `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin` (run by the user, outside the sandbox): build successful, 583 unit tests, 0 failures; instrumented tests compile. Renames as planned (`ObserveHasRecordingsAccessUseCase`, `RecordingsException.NoSambaLogin`, `HistoryError.NO_SAMBA_LOGIN`, `history_error_no_samba_login`), KDocs say "users with a Samba login". New fixture `7_validqr_client_samba` (`fd00:10::c:20`, `client-c-20`), the other PNGs came out byte identical. New tests: a client case in `ObserveHasRecordingsAccessUseCaseTest`, `QrCodeFlowTest.clientQrWithSambaAccessStoresTheSambaLogin`, `RecordingsFlowTest.aClientWithSambaLoginPlaysAndDownloadsARecording`. `docs/architecture.md` and `docs/diagrams/mvvm-home-route.mmd` updated. `connectedDebugAndroidTest` on a SM-S721B (Android 17): 155 tests, 0 failures, including the two new client flow tests. `docs/images/mvvm-home-route.png` regenerated with mermaid-cli 11.17.0 (run by the user) and checked.
