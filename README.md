# Cloudix

Cloudix transforme un téléphone Android en serveur de fichiers privé et un autre en client. L'application utilise Kotlin, Jetpack Compose et Material 3, sans accès root.

## Fonctionnalités actuelles

- Choix **Serveur** ou **Client** au premier lancement.
- Stockage limité au dossier autorisé via le sélecteur de documents Android (SAF).
- Appairage QR, authentification et connexion HTTPS avec certificat épinglé.
- Fichiers : parcourir, trier, rechercher dans le dossier ouvert, transférer, renommer, supprimer (avec confirmation).
- Sauvegarde de dossiers sélectionnés avec WorkManager, sur Wi-Fi uniquement ou via réseau mobile/VPN au choix.
- Nom d'appareil personnalisable dans les sauvegardes. Par défaut : `Appareil-XXXXXXXX`, suffixe généré aléatoirement.
- Thèmes Material 3 avec palette système, modes AMOLED et couleurs personnalisées.
- Mises à jour Android depuis une GitHub Release `debug-latest`, après vérification du SHA-256 et du certificat de l'APK.

## Mise en route

1. Installer la même version Cloudix sur deux téléphones Android compatibles (Android 8.0 ou plus récent).
2. Sur le téléphone serveur, choisir un dossier puis démarrer le service.
3. Sur le client, scanner son code QR.
4. Pour un accès distant, connecter **les deux appareils** au même réseau Tailscale privé, puis associer l'adresse VPN du serveur. Tailscale est une application externe ; les connexions peuvent utiliser son infrastructure de relais chiffré.
5. Dans **Backup**, choisir les dossiers à sauvegarder et décider si les données mobiles sont autorisées.

Les appareils, dossiers, adresses et noms d'utilisateur ne sont pas définis dans le code. Cloudix génère les éléments nécessaires à l'appairage sur chaque installation.

## Dossiers de sauvegarde

Les fichiers sauvegardés sont enregistrés sous `Backups/<nom configuré>/<dossier choisi>/`.

Après la mise à jour qui abandonne les noms issus du modèle matériel, un nouveau nom générique est créé pour chaque installation. **Le journal de synchronisation local est réinitialisé**, afin d'éviter de considérer comme envoyés les fichiers qui ne sont pas présents sous le nouveau dossier. Les fichiers de l'ancien dossier restent sur le serveur : Cloudix ne les supprime pas. Prévoir temporairement de l'espace disque supplémentaire.

## Confidentialité et sécurité

- Le fichier de stockage serveur est explicitement autorisé via Android Storage Access Framework.
- Les secrets d'appairage et la clé du certificat TLS sont conservés chiffrés via Android Keystore.
- Ne pas publier les liens ou captures du QR d'appairage : ils donnent accès au stockage partagé.
- Les fichiers ne sont pas supprimés du serveur lorsqu'ils disparaissent du client.
- Un téléphone seul n'est **pas** une sauvegarde suffisante : conserver une autre copie de ses données.
- L'accès VPN est facultatif, mais une liaison 5G directe peut être bloquée par le routeur.
- Cloudix n'envoie pas volontairement le contenu des fichiers à un service de stockage tiers ; le chemin VPN peut néanmoins passer par des relais chiffrés Tailscale.

### Avant de publier pour un public large

La signature debug mise en cache par GitHub Actions convient à une chaîne de tests personnels mais n'est **pas une infrastructure de signature de production**. Pour une distribution publique, utiliser une clé de release protégée et durable, effectuer des tests de reprise/résilience et un audit de sécurité indépendant. Voir [SIGNING.md](SIGNING.md) et [SECURITY_AUDIT.md](SECURITY_AUDIT.md).

## Compilation

JDK 17, Android SDK 35, Gradle 8.13 :

```sh
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Les paramètres du canal de mise à jour sont injectés par `GITHUB_REPOSITORY` lors de la compilation GitHub Actions. Pour une compilation locale avec mises à jour, fournir cette variable à Gradle : aucune identité de compte n'est codée en dur.

## Structure

- `app/src/main/java/com/cloudix/app/` : interface Compose, thème, serveur HTTPS, client, sauvegardes, paramètres.
- `app/src/main/res/values/strings.xml` : libellés français, prêts pour des traductions.
- `app/src/test/` et `app/src/androidTest/` : tests locaux et tests Android.
- `.github/workflows/` : builds Android, signature de test et validation.
- `website/` : modèle du site public statique. Il n'est pas publié automatiquement depuis ce dépôt.

Les catégories globales, aperçus multimédia et index paginé du serveur ne font **pas** partie de cette phase de nettoyage.
