# Cloudix — site vitrine

Site monopage français, sans build : HTML, CSS, JavaScript, Three.js (3D chargée à la demande), GSAP ScrollTrigger et Lenis.

## Site public

**https://1337detailing.github.io/Cloudix/**

Le site public est publié depuis le dépôt GitHub Pages déjà actif : [1337Detailing/1337detailing.github.io](https://github.com/1337Detailing/1337detailing.github.io/tree/main/Cloudix), dossier `Cloudix/`. Le dépôt Android `Cloudix` contient une copie du code dans `website/`, avec validation automatique. Une modification de cette copie ne met pas automatiquement à jour le site public : modifier les fichiers du dossier `Cloudix/` dans le dépôt GitHub Pages pour republier.

## Tester en local

```sh
cd website
python3 -m http.server 8000
```

Ouvrir http://localhost:8000/.

## Ajustements

- Couleur sable : `website/styles.css`, variable `--sand`.
- Textes et liens : `website/index.html`.
- 3D : `website/app.js`, fonction `bootThree`.
- Assets : `website/assets/`.

## Accessibilité et performances

Contenu visible sans JavaScript, visuel CSS si WebGL/CDN échoue, prise en charge de `prefers-reduced-motion`, navigation au clavier, animations et résolution limitées sur mobile. Pas de cookies analytiques.
