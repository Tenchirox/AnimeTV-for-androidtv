# AnimeTV Beta (fork)

Rebuild désobfusqué et modernisé de l'application **AnimeTV pour Android TV**,
à partir de l'APK `6.6.7-Nightly` (jadx/apktool) — l'original étant
d'[amarullz](https://github.com/amarullz/AnimeTV).

*Deobfuscated & modernized rebuild of the AnimeTV Android TV application,
from the 6.6.7-Nightly APK.*

[![Build APK](https://github.com/Tenchirox/AnimeTV-for-androidtv/actions/workflows/build.yml/badge.svg)](https://github.com/Tenchirox/AnimeTV-for-androidtv/actions/workflows/build.yml)

## Téléchargement

➡️ **[Télécharger la dernière version](https://github.com/Tenchirox/AnimeTV-for-androidtv/releases/latest/download/animetv-latest.apk)**
(ou choisir un asset dans [Releases](https://github.com/Tenchirox/AnimeTV-for-androidtv/releases))

Package : `org.tenchirock.animetv` — **installable à côté de l'application
originale** (label "AnimeTV Beta").

## Différences avec l'upstream

- **Mise à jour in-app via GitHub Releases** de ce fork (avec vérification
  d'intégrité **SHA-256** de l'APK téléchargé)
- Sources mortes retirées de l'UI (AnimeKAI, Anix, Animeflix) ; sources
  actives : **Aniwatch, KickAss, Gojo, Miruro**
- Config distante des domaines sources via [`server.json`](server.json)
  (modification sans recompilation)
- Parser de sous-titres VTT corrigé (numéros de cue / identifiants `_N`
  ne s'affichent plus)
- Signé avec une clé release privée (pas le certificat debug public)
- Stack modernisée : AGP 8.7 / Gradle 8.11, **androidx.media3** (ExoPlayer),
  lambdas Java 8, pool de threads à la place d'AsyncTask
- Chromecast retiré (déjà abandonné upstream en v6)

## Build

```bash
./gradlew assembleRelease   # APK dans app/build/outputs/apk/release/
./gradlew testDebugUnitTest # tests unitaires
```

Requis : JDK 17 + Android SDK (compileSdk 34). `minSdk 22`, `targetSdk 34`.
La CI (`.github/workflows/build.yml`) compile, teste et publie la release à
chaque push sur `master`.

## Fonctionnement des mises à jour

1. La CI publie une release taggée `v<versionName>` avec l'APK + un asset
   `last-nightly` (métadonnées JSON)
2. L'app interroge `releases/latest` au démarrage et compare le tag à sa
   version (`VersionUtils.isNewerVersion`)
3. Si une version plus récente existe : dialogue avec changelog + taille,
   téléchargement de l'asset APK, **vérification SHA-256**, installation

## Crédits

- Application originale : **amarullz** — https://github.com/amarullz/AnimeTV
- Licence : Apache 2.0 (voir [LICENSE](LICENSE))
