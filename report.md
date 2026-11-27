# Résultats des tests émulateur

## Limite quotidienne (1 min sur Faux Instagram)
- ✅ bloqué après 58 s d'utilisation
- ✅ retour à l'accueil
    11-27 20:36:06.370  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:11.385  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:16.400  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:21.416  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:26.430  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:31.445  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:36.459  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    11-27 20:36:41.476  6315  6315 I ZenEcran: FG fenetre=com.instagram.android journal=com.instagram.android evenement=com.instagram.android
    services d'accessibilité activés : com.zenecran/com.zenecran.service.GuardService
    11-27 20:36:33.454  6315  6344 I ZenEcran: USAGE pkg=com.instagram.android journal=53210 interne=49158
    11-27 20:36:37.464  6315  6344 I ZenEcran: USAGE pkg=com.instagram.android journal=57221 interne=53169
    11-27 20:36:41.478  6315  6344 I ZenEcran: USAGE pkg=com.instagram.android journal=61234 interne=57180

## Plage horaire (toute la journée)
- ✅ bloqué par la plage horaire
- ✅ retour à l'accueil
- ✅ écran de blocage affiché
- ✅ pas de blocage hors de la plage

## Mode concentration
- ✅ Faux Facebook bloqué pendant la concentration

## Contenus (Reels, Stories)
- ✅ Reels Instagram détecté et bloqué
- ✅ Reels Instagram : action « redirect » (reste dans l'appli)
- ✅ Reels Instagram : toujours dans l'appli
- ✅ Reels Instagram fermé
- ✅ Stories Instagram détecté et bloqué
- ✅ Stories Instagram : action « back » (reste dans l'appli)
- ✅ Stories Instagram : toujours dans l'appli
- ✅ Stories Instagram fermé
- ✅ Stories Facebook détecté et bloqué
- ✅ Stories Facebook : action « back » (reste dans l'appli)
- ✅ Stories Facebook : toujours dans l'appli
- ✅ Stories Facebook fermé
- ✅ Onglet Reels Facebook détecté et bloqué
- ✅ Onglet Reels Facebook : action « redirect » (reste dans l'appli)
- ✅ Onglet Reels Facebook : toujours dans l'appli
- ✅ Onglet Reels Facebook fermé
- ✅ Lecteur Reels Facebook (structure) détecté et bloqué
- ✅ Lecteur Reels Facebook (structure) : action « back » (reste dans l'appli)
- ✅ Lecteur Reels Facebook (structure) : toujours dans l'appli
- ✅ Lecteur Reels Facebook (structure) fermé

## Snapchat : onglets Stories et Spotlight renvoyés vers le Chat
- ✅ l'onglet Chat reste utilisable
- ✅ onglet Stories détecté, renvoi vers le Chat
- ✅ Faux Snapchat est revenu sur le Chat
- ✅ appui sur Spotlight renvoyé vers le Chat
- ✅ revenu sur le Chat après Spotlight

## Pas de faux positif
- ✅ les accueils Instagram et Facebook restent utilisables

## Budget (1 min de Reels par jour)
- ✅ Reels bloqués après 61 s (budget respecté)

## Interface (captures)
- ✅ l'interface s'ouvre et navigue sans planter

## Plantages
- ✅ aucun plantage de Zen Écran

**Total : 36 réussis, 0 échoués**
