# Zen Écran

Application Android pour reprendre le contrôle de ton temps d'écran :

- **Limites quotidiennes** par appli : une fois la limite atteinte, l'appli est fermée.
- **Horaires** : plages comme « 22:00 → 07:00, tous les jours » avec une liste d'applis cochées interdites.
- **Contenu** : bloque seulement Reels / Stories / Spotlight / Shorts / Statuts WhatsApp, ou donne un budget (ex. 10 min par jour). Tu peux créer tes propres règles pour n'importe quelle appli.
- **Personnalisation** : couleur d'accent, noir AMOLED, ton propre message sur l'écran de blocage, délai et durée du « + minutes », avertissement avant la limite.
- **Mode concentration** : tout bloquer 30 min, 1 h ou 2 h.
- **Mode engagement** : verrouille tes règles pour ne plus pouvoir les assouplir.
- Aucune permission Internet : rien ne quitte le téléphone.

## Obtenir l'APK (sans Android Studio)

1. Pousse ce projet sur GitHub (branche `main`). Le workflow `.github/workflows/build.yml` démarre tout seul.
2. Attends ~5 minutes (onglet **Actions**).
3. Ouvre l'onglet **Releases** du dépôt : télécharge `ZenEcran.apk` et installe-le.
   (Android te demandera d'autoriser l'installation depuis ton navigateur.)

## Premier lancement

1. **Accès aux statistiques d'utilisation** : accorde-le à Zen Écran.
2. **Service d'accessibilité** : Réglages → Accessibilité → Zen Écran → activer.
   Sur Android 13+, si c'est grisé : Infos de l'appli → menu ⋮ → **Autoriser les paramètres restreints**, puis réessaie.
3. Ajoute tes applis dans **Limites**, crée une plage dans **Horaires**, active tes blocages dans **Contenu**.

## À savoir

- Les repères pour détecter Reels/Spotlight/Shorts dépendent des versions des applis (`data/Presets.kt`). S'ils cassent, active le **Mode diagnostic** dans les réglages et envoie la capture.
- Téléphone, Réglages, l'écran d'accueil et Zen Écran ne sont jamais bloqués.
- Rien n'empêche de désactiver le service d'accessibilité ou de désinstaller l'appli : pour un vrai verrou, fais garder le code de ton téléphone par quelqu'un de confiance.
