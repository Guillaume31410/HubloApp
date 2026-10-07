# Hublot

Calcule le départ différé à régler sur le lave-linge pour que le cycle finisse à
l'heure voulue, en option en maximisant le temps passé en heures creuses.

## Récupérer l'APK

Chaque push sur `main` lance les tests, compile l'APK debug et publie une
release GitHub.

- Lien direct vers le dernier APK (à ouvrir depuis le téléphone) :
  `https://github.com/<utilisateur>/<dépôt>/releases/latest/download/hublot.apk`
- Ou : page du dépôt › **Releases** › dernière release › `hublot.apk`.

Le workflow peut aussi être lancé à la main : onglet **Actions** › **Build** ›
**Run workflow**.

## Installer

1. Ouvrir `hublot.apk` sur le téléphone.
2. Autoriser l'installation depuis le navigateur ou le gestionnaire de fichiers
   quand Android le demande (« Sources inconnues »).
3. Les mises à jour s'installent par-dessus la version précédente : l'APK est
   toujours signé avec la même clé de debug (`keystore/debug.keystore`), donc les
   paramètres sont conservés.

Au premier lancement, autoriser les notifications pour recevoir les rappels.

## Structure

- `app/src/main/java/fr/perso/hublot/calcul/` : logique de calcul pure (sans
  Android), testée par `app/src/test/`.
- `app/src/main/java/fr/perso/hublot/` : écrans, notifications, alarmes,
  icône jour / nuit.
- `.github/workflows/build.yml` : tests, APK, release.

Lancer les tests en local (Gradle 9.6+ et SDK Android) : `gradle testDebugUnitTest`.
