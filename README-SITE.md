# Site vitrine Cloudix

Le code du site statique se trouve dans `website/` (HTML, CSS, JavaScript).
La validation automatique s'exécute sur chaque modification du dossier ; **elle ne publie pas le site**.

Pour le déployer, choisir librement un hébergeur de pages statiques (GitHub Pages, serveur web ou équivalent). Aucun domaine personnel n'est requis ni enregistré dans le code. Lorsqu'il est hébergé sous `<compte>.github.io/Cloudix/`, le lien « GitHub » est déterminé par le compte de l'hébergement.

Tester en local :

```sh
cd website
python3 -m http.server 8000
```

La page ne demande pas de comptes utilisateur, cookies analytiques ni clés API.
