# Souris Android

Pointeur de souris virtuel pour Android : un curseur flottant (flèche, main, texte, croix, point),
un petit pavé tactile déplaçable, le glisser-déposer et des outils de dessin. Sans root.

## Bouton rond flottant (toujours au-dessus de tout, déplaçable)

- appui court : souris activée (✕ rouge) / désactivée (M gris, pavé et pointeur masqués)
- appui long (~1 s) : **arrêt d'urgence**, le service s'arrête complètement
- La souris s'éteint toute seule après **2 minutes** sans utilisation (les dessins restent)

## Pavé

- barre grise « Glisser pour déplacer » : fais-la glisser pour placer le pavé où tu veux
- bouton **Forme** : change le pointeur (flèche → main → texte → croix → point)
- bouton **Mode** : change ce que fait un glissement dans la zone claire

| Mode | Effet |
|------|-------|
| Clic | glisser = déplacer le pointeur, toucher = clic, appui long = clic long |
| Glisser | glisser-déposer : appui maintenu à la position du pointeur (~0,5 s), puis déplacement ; en relevant le doigt, l'élément est lâché |
| Dessin | trace un trait à la position du pointeur ; palette de 8 couleurs, épaisseur Fin/Moyen/Épais, Annuler (dernier trait), Effacer (tout) |
| Gomme | efface les traits déjà tracés (épaisseur au choix) |

Les traits sont dessinés sur un calque transparent par-dessus les autres applis. Il ne bloque jamais
les touches. Il est fixe à l'écran : si l'appli en dessous défile, le dessin ne bouge pas.

Pour changer les couleurs, modifier la liste `palette` dans `CursorService.kt`.

## Dernier recours si l'écran est bloqué

Réglages → Accessibilité → Applications installées → Souris Android → désactiver.

## Compiler depuis Termux (sans PC)

```bash
git add . && git commit -m "Mise à jour" && git push
```

Puis sur GitHub : onglet **Actions** → **Build APK** → télécharge l'artefact
`souris-android-debug` (contient `app-debug.apk`).

## Pistes d'amélioration

- Défilement (deux doigts sur le pavé)
- Couleur personnalisée, formes (ligne, rectangle, cercle)
- Boutons retour, accueil, récents (`performGlobalAction`)
