#!/usr/bin/env bash
# One-time setup for reproducible Cloudix Android updates.
# Requires: JDK keytool, GitHub CLI (gh), base64, and authenticated gh auth login.
set -euo pipefail
REPO="1337Detailing/Cloudix"
ALIAS="cloudix"
KEYFILE="${HOME}/cloudix-signing/cloudix-release.p12"
umask 077
if ! command -v keytool >/dev/null || ! command -v gh >/dev/null; then
  echo "Installe JDK (keytool) et GitHub CLI (gh) avant de continuer." >&2
  exit 1
fi
gh auth status >/dev/null
mkdir -p "$(dirname "$KEYFILE")"
if [[ -e "$KEYFILE" ]]; then
  echo "Clé existante détectée : $KEYFILE"
  echo "Ne génère pas de nouvelle clé si tu as déjà installé une APK Cloudix signée."
else
  echo "Création de la clé Android permanente : $KEYFILE"
  keytool -genkeypair -v -keystore "$KEYFILE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 3072 -sigalg SHA256withRSA \
    -validity 12000 -dname "CN=Cloudix, OU=Mobile, O=Cloudix, C=FR"
fi
read -r -s -p "Mot de passe de la clé PKCS12 : " PASSWORD
printf '\n'
if ! keytool -list -keystore "$KEYFILE" -storetype PKCS12 -alias "$ALIAS" -storepass "$PASSWORD" >/dev/null 2>&1; then
  echo "Mot de passe ou alias incorrect : aucun secret GitHub n'a été modifié." >&2
  exit 1
fi
echo "Envoi chiffré des secrets à GitHub Actions pour $REPO..."
base64 < "$KEYFILE" | tr -d '\n' | gh secret set CLOUDIX_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set CLOUDIX_STORE_PASSWORD --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set CLOUDIX_KEY_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set CLOUDIX_KEY_ALIAS --repo "$REPO"
unset PASSWORD
echo "Configuration terminée. Sauvegarde $KEYFILE dans un endroit privé et sûr."
echo "N'ajoute JAMAIS le fichier .p12 à GitHub ou à une conversation."
echo "Lance ensuite une nouvelle compilation depuis GitHub Actions > Android APK > Run workflow."
