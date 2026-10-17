# Cloudix 0.1 security / functionality audit

Audit date: 2026-10-17. **Static code review and Android CI build + lint, not a third-party penetration test or on-device QA**.

## Controls reviewed
- The server requires an explicit Android SAF document-tree grant; does not request broad root/storage permissions.
- TLS server identity is created in the Android Keystore. The client pins the SHA-256 of the pairing certificate. Hostname verification is replaced by exact cert pinning, not by unauthenticated TLS.
- Every file API checks a 256-bit random bearer secret; token is verified using constant-time byte comparison.
- The file path resolver is relative to the SAF root and rejects `..`, absolute paths, path separators in individual names, and attempts to delete the root.
- Request headers, request path and upload lengths have limits; uploads stream to SAF without buffering the file into RAM.
- File creation picks a non-overwriting filename when a conflict occurs; partial uploads are deleted on failure.
- Server uses a bounded worker pool and a foreground notification. File deletions require confirmation in the client.
- `android:allowBackup=false` and `android:usesCleartextTraffic=false`; no analytics, no Internet relay, no open inbound router ports.

## Verified by CI
- `assembleDebug`: compile + package APK.
- `lintDebug`: static Android lint.
- `testDebugUnitTest`: tests of bounded copy semantics and truncated uploads.
- APK artifact uploaded automatically on green GitHub Actions build.

## Known limitations / residual risks
1. **Local network only**. Cellular/NAT traversal not implemented. Never open port 8731 to the public Internet.
2. The pairing link is a bearer credential. Anyone who scans or obtains it can access all selected files; do not screenshot/share publicly. Revoking regenerates a secret and invalidates previous pairings.
3. The secret and paired link are stored in private Android preferences, not hardware-encrypted; root compromise or physical compromise exposes them. A future iteration should wrap them using AES-GCM Android Keystore.
4. Android power policies may stop background service/workers. WorkManager periodic runs are inexact. Foreground service requires manual start and is not automatically restored on reboot.
5. No resumable transfer, hashes after upload, remote version history, conflict resolution across devices, or disk-space quota: keep a separate verified backup.
6. No upload/download progress in the first release; uploads started from the UI can be interrupted if Android kills the process.
7. Network parser is intentionally minimal. Before exposure beyond a trusted LAN, replace it with a hardened HTTP implementation; add rate limiting, extensive fuzz testing, resource quotas and transport integration tests.
8. First-run TLS handshake, HyperOS/OriginOS background restrictions, SAF providers and QR scanner must be exercised on **physical devices**. A passing CI build cannot validate those behaviours.
9. The selected directory is the entire security scope. Do not choose a folder containing secrets you do not want to share.
10. No AI feature is shipped in v0.1; that is a separate module and requires performance/security assessment.

## Manual acceptance plan
1. Install same APK on server and client devices; grant server directory; start service; verify notification and green status.
2. Pair via QR, verify successful browse only when both devices are on the same LAN.
3. Confirm unpaired requests fail, wrong pinned certificate is rejected, and previous pairing stops working after secret rotation.
4. Upload a photo and PDF; download; byte-compare the originals and copies.
5. Create a nested directory, rename local test file, delete a remote test file after confirmation.
6. Schedule a folder backup, modify one file, and verify only modified file is uploaded later.
7. Turn off Xiaomi screen and repeat transfers, run Wi-Fi reconnect test, then restart the app/device.
8. Test with denied notification or revoked SAF permission; surface actionable failures.
