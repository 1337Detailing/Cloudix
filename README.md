# Cloudix

A private, native Android-to-Android file cloud. One APK, two roles: **Server** (Xiaomi) and **Client** (Vivo). Material 3 + dynamic colors; designed with inspiration from Clint Browser's flexible UI (no source or assets reused).

## Features (0.1.0)
- One-tap Server / Client onboarding, adjustable accent and appearance.
- Android Storage Access Framework (SAF): select a server root directory and grant only that directory access.
- TLS server with a locally generated Android Keystore identity, 256-bit pairing secret and certificate pinning.
- Transfer files, list folders, create directories, download, delete. Remote browsing requires explicit pairing.
- QR pairing and pasteable pairing link; no other Android application is required.
- Scheduled uploads from selected client directories (WorkManager, Wi-Fi-only switch, intervals >= 15 minutes, change-detection).
- Foreground service with persistent notification. No unrestricted root access needed.

## Limitations
- **Same-network LAN only in v0.1**. Do not port-forward to this server. Cellular/NAT traversal and end-to-end secure relay are *not yet implemented*.
- Android may restrict long-running services or periodic scheduling. Battery exemptions can improve reliability. No automatic start on reboot at this stage.
- Directory-based storage is SAF-mediated; root access is deliberately not used.
- **Not a full backup**: there is no deletion sync, versioning or guarantee against device/storage failures. Keep an independent backup.
- Intelligent file AI, media gallery, resumable transfers and proper external security review remain future work.
- CI artifacts are debug-signed; no release keys shipped.

## Usage
1. Install the same APK on both devices.
2. Server: choose **Serveur**, choose the storage directory and tap **Démarrer**.
3. Server: tap **Partager l'appairage** or display the QR code.
4. Client: choose **Client**, scan the QR or paste the pairing link.
5. Use **Fichiers** for browse, upload and download; **Sauvegardes** to select folders and schedule automatic uploads.

The private server doesn't accept unpaired requests; the certificate SHA-256 fingerprint is transferred within the pairing link. Treat pairing links like passwords, and regenerate the pairing secret if shared by mistake.

## Architecture
`app/src/main/java/com/cloudix/app`: Compose UI, Android foreground file server, pinned HTTPS client, SAF document repository, WorkManager backup scheduling.

## Build
JDK 17, Android SDK 35, Gradle 8.13. Run `gradle :app:assembleDebug`. GitHub Actions builds, lints, tests and attaches the APK as an artifact.

## Inspiration
[Clint Browser](https://github.com/jhaiian/ClintBrowser) (Material 3, deep UI customization). Cloudix UI is original and independent. Clint's branding and assets are not used.
