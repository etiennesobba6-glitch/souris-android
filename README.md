# Souris Android

Pointeur de souris virtuel pour Android : un curseur flottant (flèche, main, texte, croix, point)
et un petit pavé tactile pour le déplacer et cliquer, sans root.

## Utilisation

- **Bouton rond flottant** (toujours au-dessus de tout, déplaçable en le faisant glisser)
  - appui court : souris activée (✕ rouge) / désactivée (M gris, rien n'est affiché)
  - appui long (~1 s) : **arrêt d'urgence**, le service s'arrête complètement
- **Pavé** (40 % de la largeur, 25 % de la hauteur, en bas à droite)
  - glisser : déplace le pointeur
  - toucher : clic ; appui long : clic long
  - bouton « Pointeur » : change de variante (flèche → main → texte → croix → point)
- La souris s'éteint toute seule après **2 minutes** sans utilisation
- L'appli contient aussi un bouton « Arrêter la souris »

Les touches en dehors du pavé et du bouton passent normalement aux applis dessous,
clavier compris. Le pointeur lui-même n'intercepte jamais les touches.

## Dernier recours si l'écran est bloqué

Réglages → Accessibilité → Applications installées → Souris Android → désactiver.

## Compiler depuis Termux (sans PC)

```bash
git add . && git commit -m "Mise à jour" && git push
```

Puis sur GitHub : onglet **Actions** → **Build APK** → télécharge l'artefact
`souris-android-debug` (contient `app-debug.apk`).

## Pistes d'amélioration

- Glisser-déposer (clic maintenu + déplacement)
- Défilement (deux doigts sur le pavé)
- Pavé redimensionnable / déplaçable
- Boutons retour, accueil, récents (`performGlobalAction`)
