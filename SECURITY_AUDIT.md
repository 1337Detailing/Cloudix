# Audit Cloudix — 17 octobre 2026 (0.5.2)

**Scope:** Kotlin/Compose Android APK, HTTP/HTTPS storage service, TLS pinning and pairing, SAF storage, Tailscale/IPv6 routing, WorkManager backup, GitHub update pipeline, release signing, Material You/AMOLED UI, manifests, unit tests and emulator instrumentation.

**Method:** Full static review of the current GitHub source, comparison with regression tests and GitHub Actions logs; targeted fixes + new tests. **Not** a penetration test, fuzzing campaign, or a live Xiaomi↔Vivo end-to-end test. Do not interpret a green CI run as proof of perfect data durability.

## Issues fixed during this audit

| ID | Severity | Found in | Issue | Fix |
|---|---|---|---|---|
| F-01 | CRITICAL / data integrity | `CloudixTransport.kt` download | Previously `copyTo()` accepted EOF before the server's Content-Length. | Require precisely Content-Length bytes when provided; throw on truncation. |
| F-02 | HIGH / data integrity | `MainActivity.kt` CreateDocument flow | A failed transfer could leave an incomplete local file with the expected name. | Attempt to remove the new SAF document on failure, and show an error. |
| F-03 | HIGH / silent omissions | `BackupWorker.kt` | The former 4,000-*scanned-item* cap could permanently skip later files while returning success. | Limit only successful uploads per batch, persist progress and retry remaining work; report a failure for too-deep folders or unknown/oversized files. |
| F-04 | MEDIUM / concurrency | `BackupWorker.kt` | Repeated “Sauvegarder maintenant” taps could start overlapping uploads. | Unique WorkManager manual job with KEEP policy. |
| F-05 | MEDIUM / connectivity | `CloudixTransport.kt` | Saved Tailscale address was always preferred, even when VPN disabled and the Xiaomi was reachable on local Wi-Fi. | Select the LAN peer endpoint on Wi-Fi with VPN inactive, while retaining Tailscale routing on VPN/mobile. |
| F-06 | MEDIUM / names | `CloudixService.kt` | Literal `%` in valid filenames was incorrectly rejected. | Allow `%` after URI query decoding; keep path-separator/traversal validation. |
| F-07 | MEDIUM / backup collisions | `CloudixBackupNames.kt` | Unnecessary replacement of punctuation/emoji could collapse distinct filenames, with truncation collisions. | Preserve normal Unicode and punctuation, and append a stable hash to rewritten/truncated names. |
| F-08 | MEDIUM / incorrect status | `BackupWorker.kt`, `CloudixSettings.kt`, `MainActivity.kt` | Backups could fail/partially complete without an understandable indication in the UI. | Persist a bounded error/partial status, refresh the visible backup screen, and display it. |
| F-09 | MEDIUM / UI | `CloudixSettings.kt` | Sort, Wi-Fi-only, backup frequency and automatic boot switches were stored in SharedPreferences without Compose-observable state. | Back them with observable Compose state, preserving SharedPreferences persistence. |

### Regression coverage

- `TransportTest.kt`: exact byte copying and truncation handling.
- `CloudixBackupNamesTest.kt`: Unicode, percent sign, sanitization collision, stable hashing and length limits.
- `AppearanceModelsTest.kt`: palette uniqueness, valid HEX, AMOLED identifiers.
- `AppearanceLiveTest.kt`: accent changes, custom background and AMOLED/system changes without restarting the Activity.
- `SettingsLiveTest.kt`: file sorting and backup controls immediately recompose.
- `CloudixTlsSmokeTest.kt`: TLS handshake/pinned identity and real Android request/response.
- `TailnetAddressTest.kt`: VPN address validation.
- Android Actions: `assembleDebug`, `lintDebug`, `testDebugUnitTest`; separate emulator instrumentation workflow.

## Confirmed limits and remaining bugs / risks

| ID | Priority | Area | Finding / consequence | Proposed follow-up |
|---|---|---|---|---|
| O-01 | HIGH | Android lifecycle | User-triggered file transfers run inside the Activity coroutine scope. Rotation, process kill or background restrictions can interrupt them, and the transfer has no resume. | Foreground transfer service / WorkManager for permitted transfers, persistent job queue, retry with byte ranges and checksums. |
| O-02 | HIGH | Backup reliability | Changed local files are sent to a duplicate-safe upload endpoint. This creates numbered copies rather than updating a canonical latest version, with no retention limit. | Explicit backup versioning, canonical latest pointer, retention/quota rules and recovery UI. Never overwrite the only good version before verifying its replacement. |
| O-03 | HIGH | Backup size | Files greater than 4 GiB (4,294,967,296 bytes) or of unknown SAF length cannot be uploaded, and will now generate an incomplete-backup alert instead of silently skipping. | Chunked/resumable uploads, content hashes, robust provider handling. |
| O-04 | HIGH | Data durability | Server-side and client-side end-to-end cryptographic digest verification is absent for completed uploads; existing byte-count checks catch truncation but not same-length corruption. | SHA-256 transfer verification and atomic publish/rename. |
| O-05 | HIGH | Deployment/signing | GitHub's persistent signing secrets must be configured by repository owner. Without these, debug APK identities change and in-app updates are not guaranteed. | Stable keystore, signing verification and an automated install-over-existing-device test. |
| O-06 | MEDIUM | Server security | Hand-rolled minimal HTTP parser and no authentication rate limiter; four transfer workers and a 16-slot queue can be saturated by hostile traffic. | Hardened HTTP stack, quotas and timeouts, security fuzzing. No public port exposure in the meantime. |
| O-07 | MEDIUM | Huge directories | `DocumentFile.listFiles()` and entire JSON directory response are not paginated; very large folders can cause latency or memory pressure. | Paged listings and incremental UI loading. |
| O-08 | MEDIUM | Backup naming | Two separate selected local trees having the same root folder name could merge in `Backups/<device>/<folder>`; sanitization now avoids illegal-character collisions but not this namespace collision. | Stable per-source directory IDs, discoverable aliases and migration. |
| O-09 | MEDIUM | UI status | Cloudix may show a verified state from a past request although connectivity changes afterwards; a “connected” label is not a live heartbeat. | Network callbacks, live probe and clear state transitions. |
| O-10 | MEDIUM | App restrictions | Server restart and scheduled work are subject to HyperOS/OriginOS battery policy, network/VPN availability, foreground-service restrictions and notification permissions. | Physical-device testing and documented battery/exemption setup. |
| O-11 | MEDIUM | Appearance | Arbitrary custom text/background colors can create inaccessible contrast even though the color editor warns users. | Optional WCAG contrast enforcement and safe-reset accessibility shortcut. |
| O-12 | MEDIUM | VPN privacy | Tailscale's control plane and possible encrypted DERP relays are third-party services; without Tailscale or an accessible public route, direct mobile 5G is not guaranteed. | Clear setup guide, connection status and offline/LAN fallback. |
| O-13 | MEDIUM | SAF provider behavior | Some document providers report zero/unknown sizes or modify display filenames; this can prevent transfer or produce unexpected target names. | Provider-agnostic streaming protocol, explicit actual remote filename, and test matrix across providers. |
| O-14 | LOW | Pairing | One bearer key grants full selected-tree read/write/delete access to any paired client, until global key rotation. | Device-level access permissions and individual revocation. |
| O-15 | LOW | Visual tests | Automated UI tests cover state propagation but not all layouts, animation durations or clipping on OriginOS notch/foldable/small-screen devices. | Screenshot/golden tests and on-device accessibility testing. |

## Threat model and controls

- Files remain in the Android SAF-selected server directory. Root permission is not required for file service.
- Transfer traffic uses TLS; the client pins the Xiaomi certificate fingerprint and requires the random 256-bit bearer pairing secret.
- Server identity and stored pairing credentials are AES-GCM wrapped using Android Keystore keys. A compromised/rooted Android process can still access keys while the app is running.
- Query paths reject absolute paths, `..`, slashes and backslashes inside a segment; remote root deletion is blocked.
- Uploads are streaming, length-limited and delete newly created SAF files on failed input. Existing names are not overwritten by the normal upload endpoint.
- Download length checks and partial document cleanup added in this audit.
- Debug APKs may be debugger-accessible; production deployments must use a privately controlled stable signed build.
- Deep links/QR pairing strings contain full access secrets: treat as passwords and avoid posting screenshots of the decoded strings.
- A personal cloud on one phone **is not a backup** until its contents are independently replicated elsewhere.

## Mandatory physical-device acceptance tests

1. On Xiaomi HyperOS + Vivo OriginOS, install the **same signed** APK, select a server directory, confirm foreground notifications and reconnect across screen-off / reboot.
2. Pair on Wi-Fi. Upload & download 0-byte files, multi-gigabyte videos, a file with `%`/emoji in its name; compute SHA-256 equality on both phones.
3. Interrupt a download halfway (disable network). Verify error shown, partial local file removed, and a retry is possible.
4. Change the server secret. Verify former pairing is rejected and a new QR works.
5. Activate Tailscale on both devices; switch Vivo Wi-Fi→5G→Wi-Fi, then disable Tailscale on Wi-Fi and confirm the LAN fallback.
6. Auto-backup nested source directories with 4,100+ files. Confirm every file eventually appears and incomplete status is visible.
7. Back up two same-named source folders and an edited file; check versioning/collision risks before trusting it with irreplaceable documents.
8. Select AMOLED, then System, then Light; customize HEX background and accent, disable animations, and verify instant update and contrast.
9. Install a newer APK signed with **identical** private key via Cloudix updater, without uninstalling, and verify all app data stays intact.
10. Run long transfer while locking screen, switching app, rotating screen and blocking network to establish real transfer durability.

## Audit conclusion

**Build green does not equal production-safe NAS.** Critical download truncation and silent backup-skip issues have code-level repairs and new/updated tests. Remaining high-priority work: resilient uploads/transfers, backup versioning/retention, cryptographic end-to-end file verification, and physical two-device testing. Keep an independent second copy of important photos and documents.
