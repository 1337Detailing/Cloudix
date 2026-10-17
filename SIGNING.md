# Cloudix : mises à jour APK signées

Android exige **le même certificat de signature** pour mettre à jour une application sans désinstallation. Le nom de package reste `com.cloudix.app` et `versionCode` augmente automatiquement sur GitHub Actions.

## Important : version déjà installée

Les anciennes APK `Cloudix-debug-apk` générées par GitHub Actions **sans clé stable** utilisent une clé de débogage éphémère. Elles ne peuvent pas être mises à jour par une APK signée avec une nouvelle clé : Android refusera l'installation. **Une seule réinstallation est nécessaire** pour passer à la première version stable. Ensuite, toutes les versions signées avec **la même clé** s'installent par-dessus sans perdre les données de l'application.

Avant la première réinstallation, exporte/sauvegarde les fichiers importants et note tes dossiers sélectionnés. Ne supprime pas l'application serveur sans t'assurer que tes données sont ailleurs.

## Installation guidée (recommandée)

Sur un PC avec **JDK** et **GitHub CLI** connecté à ton compte (`gh auth login`), tu peux exécuter le script `scripts/setup-signing.sh` depuis le dépôt Cloudix. Il crée la clé dans `~/cloudix-signing/cloudix-release.p12`, puis configure automatiquement les quatre secrets du dépôt GitHub via une connexion authentifiée. La clé n'est jamais commitée dans le dépôt.

Après configuration, lance **Actions → Android APK → Run workflow**. Une nouvelle compilation publiera la Release `debug-latest`, contenant l'APK signée et le manifeste de mise à jour. Conserve une copie privée du fichier `.p12` et son mot de passe : sans eux, les prochaines APK ne pourront plus être mises à jour par-dessus.

**Attention :** une build GitHub verte ne prouve pas que la Release est publiée. La publication reste désactivée tant que les secrets ne sont pas configurés.

## Créer une clé privée stable (à conserver)

Sur ton PC, avec JDK 17 ou supérieur :

```bash
keytool -genkeypair -v -keystore cloudix-release.p12 -storetype PKCS12 \
  -alias cloudix -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 12000
```

Choisis un mot de passe long et unique. Garde une copie chiffrée de `cloudix-release.p12` **hors de GitHub**. Ne commite jamais cette clé.

Encode le fichier en base64 sur ton ordinateur :

- Linux : `base64 -w0 cloudix-release.p12`
- macOS : `base64 < cloudix-release.p12 | tr -d '\\n'`
- Windows PowerShell : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cloudix-release.p12"))`

## GitHub Secrets

Ouvre **Settings → Secrets and variables → Actions → New repository secret** dans le dépôt Cloudix. Crée :

| Secret | Valeur |
|---|---|
| `CLOUDIX_KEYSTORE_B64` | contenu base64 du fichier .p12 |
| `CLOUDIX_STORE_PASSWORD` | mot de passe du magasin |
| `CLOUDIX_KEY_PASSWORD` | mot de passe de la clé |
| `CLOUDIX_KEY_ALIAS` | `cloudix` |

Le workflow `.github/workflows/android.yml` reconstruit la clé **sur le runner uniquement**, signe `app-release.apk`, vérifie sa signature avec `apksigner` et publie l'artefact **Cloudix-release-signed**.

**Installe uniquement l'artefact `Cloudix-release-signed` pour conserver la compatibilité des mises à jour.** Les builds `Cloudix-debug-apk` ne sont pas destinées à la distribution.

## Validation

Vérifie la signature de deux versions successives :

```bash
apksigner verify --print-certs --verbose app-release.apk
```

Le SHA-256 du certificat doit rester identique. Si tu perds le fichier .p12 ou le mot de passe, tu ne pourras plus publier de mises à jour compatibles avec la signature actuelle. La signature n'installe pas les mises à jour automatiquement : Android demande toujours à l'utilisateur de confirmer l'installation d'une APK téléchargée.

## Sécurité

- Ne partage jamais la clé privée ou les mots de passe dans une conversation, une issue GitHub ou le dépôt.
- N'active pas la publication automatique des APK signées pour les pull requests de contributeurs externes.
- Les clés de signature Android sont distinctes du certificat TLS utilisé pour appairer les téléphones.
