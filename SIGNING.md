# Signature des mises à jour Cloudix — même principe que une application Android de référence

**Aucune configuration manuelle, aucun secret GitHub à créer.** Cloudix utilise la même stratégie que le workflow officiel une application Android de référence : une clé de signature debug Android générée une seule fois par GitHub Actions, conservée dans le cache `cloudix-debug-signing-v1` et restaurée pour chaque build suivante.

À chaque push sur `main`, la CI :
1. réutilise la même clé de signature ;
2. compile `app-debug.apk`, exécute tests et Android Lint ;
3. vérifie le certificat APK et la progression du `versionCode` ;
4. refuse de publier si l'empreinte du certificat ne correspond pas à la dernière APK publiée ;
5. publie `cloudix-debug.apk` et `update.json` dans la release GitHub publique [debug-latest](https://github.com/<propriétaire>/<dépôt>/releases/tag/debug-latest).

Cloudix télécharge la nouvelle APK, vérifie son SHA-256 **et le certificat de signature Android correspondant à l'installation actuelle**, puis ouvre l'installateur système. La validation par l'utilisateur reste obligatoire sur Android.

## Transition depuis une ancienne APK Cloudix

La signature d'une ancienne build GitHub Actions construite avant ce changement de cache n'a pas été conservée. Android **ne permet pas de la remplacer par une APK portant une autre signature**. Si l'installateur affiche un conflit de package, une unique désinstallation de l'ancienne Cloudix pourra être nécessaire. Ensuite, les builds de la nouvelle chaîne conservent la même signature.

**Avant toute désinstallation**, sauvegarde les dossiers et paramètres importants ; les données internes de l'application seront effacées. Ne supprime jamais tes dossiers de fichiers partagés.

## Limitation du cache (important)

Cette approche **de bêta personnelle** évite toute configuration, mais sa clé stockée en cache GitHub n'a pas la durabilité ni l'isolation d'une vraie clé privée de production. Une éviction du cache pourrait faire perdre la clé. GitHub Actions est donc programmé deux fois par semaine pour maintenir le cache actif. Si le cache disparaît alors qu'une release existe, la CI **échoue volontairement**, au lieu de créer silencieusement une clé incompatible.

Ne te sers pas de ce système pour une distribution publique hautement sensible ou sur Play Store. Une clé de production stockée dans un secret privé reste préférable pour cette utilisation. Le certificat APK de mise à jour est distinct de la clé TLS utilisée pour protéger les fichiers Cloudix.


**Publication publique :** le cache GitHub Actions n'est pas un coffre à clés durable. Prévoir une clé de release privée et un mécanisme de rotation contrôlé avant diffusion large. Le dépôt ne contient aucune clé privée.
