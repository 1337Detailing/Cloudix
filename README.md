# Cloudix

A private, native Android-to-Android file cloud. One APK, two roles: **Server** (Xiaomi) and **Client** (Vivo). Material 3 + dynamic colors; designed with inspiration from Clint Gecko's flexible UI (no source or assets reused).

## Features (0.2.0)
- One-tap Server / Client onboarding, Material You wallpaper colors and 30 accents × 5 surface intensities, light/dark modes and optional animations.
- Android Storage Access Framework (SAF): select a server root directory and grant only that directory access.
- TLS server with a locally generated P-256 identity encrypted with an Android Keystore AES key, 256-bit pairing secret and certificate pinning.
- Transfer files, browse, search, sort by modification date/size/name, rename and create directories, download, delete (with confirmation). Remote browsing requires explicit pairing.
- QR pairing and pasteable pairing link; no other Android application is required.
- Scheduled and manual uploads from selected client directories (WorkManager, Wi-Fi-only switch, intervals >= 15 minutes, change-detection).
- In-app TLS diagnostics on both phones, last server network error and active requests.
- Optional server boot receiver (subject to HyperOS/Android background-start policies).
- Foreground service with persistent notification. No unrestricted root access needed.

## Limitations
- **Same-network LAN only in v0.2**. Do not port-forward to this server. Cellular/NAT traversal and end-to-end secure relay are *not yet implemented*.
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
[Clint Gecko](https://github.com/jhaiian/ClintGecko) (Material 3, deep UI customization). Cloudix UI is original and independent. Clint's branding and assets are not used.


## Stable signatures and in-place updates (0.2+)

Android permits an in-place APK update only if the **applicationId remains unchanged**, the **signing certificate matches the installed app** and `versionCode` is higher. The original GitHub runner's ephemeral debug key cannot be recovered from the public repository, so anyone using the first v0.2 artifact must uninstall it **once** before installing the first stably signed release. Files stored in the selected external SAF folder are not deleted, but app pairing/settings may be reset.

Generate and retain a single private PKCS#12 Android signing keystore, **never commit it**. Put these four GitHub Actions repository secrets into Settings → Secrets and variables → Actions:

- `CLOUDIX_KEYSTORE_B64`: base64 of the binary PKCS#12 keystore, single line.
- `CLOUDIX_STORE_PASSWORD`: keystore password.
- `CLOUDIX_KEY_ALIAS`: `cloudix`.
- `CLOUDIX_KEY_PASSWORD`: key password (for PKCS#12, generally the same as store password).

When all secrets exist, GitHub Actions signs both debug and release APKs **with the same permanent key**, verifies the release with `apksigner` and uploads `Cloudix-release-signed`. Subsequent release APKs can update in place. The CI run number produces an increasing versionCode. With missing secrets, CI still produces a **temporary debug** APK, but it will **not** support reliable updates between runs.

**Important:** protect the keystore and password; a lost signing key prevents ordinary updates, and a leaked key allows impostor APKs. Store a second offline backup. Never upload your private keystore or base64 to the repository or an issue.

## Debugging connection issues
1. Both phones must be on the same local network (no guest Wi-Fi isolation).
2. Xiaomi: start server, then tap **Tester** on the **Diagnostic réseau** row. Success proves Android TLS handshake works locally. On failure, inspect the last connection error shown below the card.
3. Vivo: use the newest pairing QR, then tap **Tester**. If the local Xiaomi test succeeds but Vivo fails, inspect Wi-Fi IP, client AP isolation and firewall.
4. Never publish a pairing QR/link; rotating the secret revokes all clients.

## TLS repair (0.2.0)
The original 0.1 Android TLS handshake failed with `SSLHandshakeException: connection closed`. We reproduced this on a real Android emulator CI test. Opaque Android Keystore private keys caused Conscrypt's RSA operations to fail, and directly stored EC keys also failed. The current implementation uses an exportable per-device software EC P-256 private key, persisted exclusively as Android Keystore AES-GCM ciphertext. **Pinned TLS handshake succeeded** in emulator workflow [39048975148](https://github.com/1337Detailing/Cloudix/actions/runs/39048975148). This does not substitute for physical Xiaomi/Vivo testing. Re-pair after installing 0.2.0 because the server certificate has changed.

UI inspiration: [Clint Gecko](https://github.com/jhaiian/ClintGecko), Material 3 / Compose customization patterns. No Clint code, artwork or brand assets are copied. The original Cloudix theme contains 30 accents × 5 surface intensities, OS wallpaper color fallback, and optional animations.

## Direct mobile (5G) connection without a box login, VPN provider or rented server

Cloudix v0.3 attempts a direct TLS connection over IPv6 when the Xiaomi's Android Wi-Fi interface has a global IPv6 address. Its pairing link includes both the local IPv4 and public IPv6 address. The Vivo selects the IPv6 address when using cellular data, while keeping direct IPv4 within the same Wi-Fi. The QR retains the certificate pin and random bearer secret.

**Hard limitation:** Public IPv6 does not imply an open, reachable IPv6 port. Home router firewalls and carrier inbound packet filtering commonly block unsolicited traffic. Without opening a router port, a reachable public address, or any third-party coordination/relay, **there is no technically reliable way to guarantee Internet access from two phones**. Cloudix reports whether a global IPv6 address exists, and the Vivo's real 5G test determines reachability. If blocked, the app does not circumvent the firewall. No root privileges are required.

Do not open port 8731 to the wider Internet casually. Network-facing encrypted authentication does not eliminate denial-of-service risks. For reliable no-box 5G use, one must relax at least one constraint (e.g., permit a free VPN signaling/relay service).

## In-app signed GitHub debug updates

On the Settings screen, tap **Rechercher les builds GitHub**. Cloudix reads a SHA-256 manifest from the `debug-latest` GitHub Release, downloads the signed APK and verifies both the digest and the installed Android signing certificate before offering the system installer.

The CI workflow publishes debug releases **only if the same stable signing secrets are configured** and on push to main. Configure `CLOUDIX_KEYSTORE_B64`, `CLOUDIX_STORE_PASSWORD`, `CLOUDIX_KEY_PASSWORD`, and `CLOUDIX_KEY_ALIAS` as detailed in [SIGNING.md](SIGNING.md). Without these secrets the updater will not find a public build. Old ephemeral GitHub-debug signatures cannot update in place and require one final reinstall to the stable signing identity.

## Cloudix with Tailscale (Wi-Fi at home, 5G anywhere, no box access)

**Recommended for blocked inbound IPv6.** Install the **free Tailscale Android app** on both Xiaomi (server) and Vivo (client), sign in with **the same Tailscale account** and activate the VPN on both. You do not need root, Termux, a router configuration or a rented server.

1. Start Cloudix Server on Xiaomi; enable Tailscale.
2. Read the Xiaomi **Tailscale** IPv4 address in its Tailscale app (typically `100.x.y.z`). **Never substitute the Vivo's address.**
3. In Cloudix Xiaomi server home → **Tailscale · Connexion 5G**, let automatic detection fill the address or manually type the address; tap **Enregistrer**. A refreshed Cloudix pairing QR includes the Tailscale endpoint and the existing TLS pin + secret.
4. On Vivo, enable Tailscale and re-pair with that QR. Alternatively, **Cloudix → Client → VPN Tailscale (5G) → Configurer** accepts Xiaomi's `100.x.y.z` address while retaining the existing pairing secret and certificate pin.
5. In Vivo Cloudix, tap **Tester** on 5G, then browse and upload files as normal. Cloudix prefers the Tailscale address when it is saved. Tailscale works over mobile data and switches to an encrypted DERP relay if direct NAT traversal fails.

Tailscale's free Personal plan covers a small private network. Tailscale's control plane and encrypted forwarding relay **are third-party infrastructure**, but file bytes remain stored on Xiaomi and the Cloudix TLS stream stays end-to-end encrypted. This does **not** satisfy a strict no-third-party-network-services requirement. Android allows only one device-level VPN at a time. Disable power restrictions for both Cloudix and Tailscale to improve reliability.

**Safety:** Treat Cloudix pairing links as passwords; only share them privately. No port forwarding and no public IP exposure are required. Do not enable Tailscale Funnel (which exposes services publicly). The `100.64.0.0/10` range is shared address space; an address in this range is **not proof** that the remote device is authenticated—Cloudix continues verifying its pinned server certificate and pairing secret.

Official docs: https://tailscale.com/docs/install/android and https://tailscale.com/kb/1452/connect-to-devices

## Cloudix 0.5 — Live Look & Feel personalization

- Appearance settings use an immutable Compose-observable `AppearanceSnapshot`. Changes persist and *immediately recompose all visible screens*, without restarting Cloudix.
- Material You wallpaper colors on Android 12+, with a graceful fallback on Android devices that do not provide OEM palette resources.
- **3 app themes:** System, Dark and Light.
- **4 surface modes:** Soft Tint, Strong Tint, AMOLED (true black), AMOLED No Tint (pure black, accent controls only). Choosing AMOLED forces dark mode, choosing Light exits AMOLED.
- **36 named accent palettes**, including gold/mustard, red, crimson, ruby, teal and lavender, with icon previews and highlighted selected values.
- **7 individually editable HEX roles:** background, surfaces/cards, text, accent, selection, outlines and scrollbars. Each color can be reset separately and all overrides can be cleared together.
- Animated theme transitions, custom scrollbar thumb, real-time preview, descriptions and visually rich modal selection cards.
- Previously saved theme/accent choices are retained.

Manual testing: Open **Thèmes → Couleur d'accent → Moutarde**; verify the top/bottom navigation and home screen change immediately. Select **AMOLED sans teinte** and navigate without restarting. Enter a custom background HEX and confirm an immediate visual change. Restore system wallpaper via Material You. Test switching colors with animations on and off, and restart to confirm persistence.

Caution: arbitrary user-entered text/background colors can have low contrast. Use reset controls if legibility suffers.
