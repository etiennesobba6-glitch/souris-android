# Souris Android

Pointeur de souris virtuel pour Android : un curseur flottant (flèche, main, texte, croix)
et un pavé tactile pour le déplacer et cliquer, sans root.

## Utilisation

- Glisser sur le pavé : déplace le pointeur
- Toucher : clic
- Appui long : clic long
- Bouton « Pointeur » : change de variante (flèche → main → texte → croix)

## Compiler depuis Termux (sans PC)

```bash
pkg install git gh
gh auth login
git init && git add . && git commit -m "Premier commit"
git branch -M main
gh repo create souris-android --public --source=. --push
```

Ensuite, sur GitHub : onglet **Actions** → **Build APK** → télécharge l'artefact
`souris-android-debug` (contient `app-debug.apk`).

## Installer

1. Installe l'APK (autorise les sources inconnues si Android le demande).
2. Ouvre l'appli, puis Réglages d'accessibilité → Souris Android → activer.

## Pistes d'amélioration

- Glisser-déposer (clic maintenu + déplacement)
- Défilement (deux doigts sur le pavé → `swipe`)
- Pavé redimensionnable / déplaçable
- Boutons retour, accueil, récents (`performGlobalAction`)
- Variantes de pointeur automatiques selon l'élément sous le curseur
