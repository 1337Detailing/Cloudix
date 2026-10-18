# Cloudix — site vitrine

Site français monopage, sans compilation ni framework. HTML / CSS / JavaScript, WebGL Three.js chargé uniquement lorsque le visuel 3D est visible, GSAP ScrollTrigger et Lenis en améliorations progressives.

## Tester en local

Depuis le dépôt :

```sh
cd website
python3 -m http.server 8000
```

Ouvrir http://localhost:8000/ (le site et ses liens marchent sans construire de bundle ; l'animation Three.js dépend d'une connexion aux CDN).

## Personnaliser

- Couleur sable : `website/styles.css` variable `--sand`.
- Textes et liens : `website/index.html`.
- Visuel 3D : `website/app.js`, fonction `bootThree`.
- Favicon et aperçu : `website/assets/`.

## GitHub Pages

Le workflow `.github/workflows/cloudix-pages.yml` publie automatiquement `website/` lors des modifications de `main`, et peut être lancé manuellement. Dans **Settings → Pages → Build and deployment**, la source doit être **GitHub Actions**. L'adresse publique attendue est `https://1337detailing.github.io/Cloudix/`. L'activation initiale de GitHub Pages dépend des paramètres du dépôt.

## Accessibilité et performances

Le contenu s'affiche avant les animations. Un visuel CSS est présent si WebGL/CDN est indisponible. Le site respecte `prefers-reduced-motion`, le clavier, et limite la résolution et le nombre de FPS sur mobile. Aucun compte ni traqueur n'est nécessaire.