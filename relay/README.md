# Cloudix Relay — Internet/5G, without another Android app

The Cloudix Android APK opens **outbound WSS** sessions to a user-hosted Cloudflare Worker + Durable Object. No router port forwarding, PC, VPN app, root or Termux required.

**Required external setup:** one Cloudflare account / deployed Worker URL. No hosted instance is bundled in Cloudix. Deploying the Worker is a one-time manual action.

## Deployment

1. Sign in to Cloudflare Workers; create a Workers subdomain.
2. On a computer with Node.js >= 20, run:
   ```sh
   cd relay
   npm install
   npx wrangler login
   npm run deploy
   ```
3. The output gives a URL like `https://cloudix-relay.YOUR-SUBDOMAIN.workers.dev`.
4. Paste that URL in **Cloudix → Xiaomi Serveur → Options → Relais Internet**, save, start/restart server, and re-pair client with new QR.
5. Install the same APK on the Vivo. In 5G it will automatically use the WSS relay; in the same local Wi-Fi it uses direct TLS.

## Architecture and security

- A Durable Object only forwards binary frames between the server's WebSocket and up to 16 client sessions.
- The actual TLS session runs **end to end** from the Android client to the Xiaomi's already running local TLS server.
- The relay never terminates the inner TLS and does not persist file bytes.
- Room IDs and authentication tokens are derived from a random 256-bit pairing secret using separate SHA-256 domain labels.
- Relay verifies a room's authentication token hash. Pairing QR/link remains a sensitive full-access credential; never share publicly.
- Cloudflare can still observe transfer timing, sizes and IPs (not plaintext).
- Sessions reconnect when mobile/Wi-Fi networks flap. Long transfers restart after a disconnect (resume not implemented).
- Cloudflare Workers and Durable Objects have free quotas/limits; high-volume transfers can incur charges or fail when quotas are exhausted.
- Keeping the Xiaomi background foreground service alive still depends on Android/HyperOS power policies. Disable battery restrictions.
- Never configure an HTTP relay URL; app requires HTTPS/WSS.
- Update the code/redeploy when vulnerabilities are fixed. Only use your own deployed relay.

## Tests

`npm test` for framing and control separation. GitHub Actions runs these tests. Real two-device 5G testing and threat modelling are still necessary.
