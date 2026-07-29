# AnimeTV Beta (fork)

Rebuild désobfusqué et modernisé de l'application **AnimeTV pour Android TV**,
à partir de l'APK `6.6.7-Nightly` (jadx/apktool) — l'original étant
d'[amarullz](https://github.com/amarullz/AnimeTV).

*Deobfuscated & modernized rebuild of the AnimeTV Android TV application,
from the 6.6.7-Nightly APK.*

[![Build APK](https://github.com/Tenchirox/AnimeTV-for-androidtv/actions/workflows/build.yml/badge.svg)](https://github.com/Tenchirox/AnimeTV-for-androidtv/actions/workflows/build.yml)

## Téléchargement

Deux variantes (même package, même signature — interchangeables) :

| Variante | minSdk | targetSdk | Lecteur | Lien |
|---|---|---|---|---|
| **legacy** | 22 (Android 5.1+) | 34 | media3 1.4.1 | [animetv-latest.apk](https://github.com/Tenchirox/AnimeTV-for-androidtv/releases/latest/download/animetv-latest.apk) |
| **modern** | 26 (Android 8+) | 35 | media3 1.5.1 + récupération crash WebView | [animetv-latest-modern.apk](https://github.com/Tenchirox/AnimeTV-for-androidtv/releases/latest/download/animetv-latest-modern.apk) |

(ou choisir un asset versionné dans [Releases](https://github.com/Tenchirox/AnimeTV-for-androidtv/releases))

Package : `org.tenchirock.animetv` — **installable à côté de l'application
originale** (label "AnimeTV Beta"). Le code est partagé (`src/main`), les
implémentations spécifiques sont dans `src/legacy` et `src/modern`
(`CompatImpl`).

## Différences avec l'upstream

- **Mise à jour in-app via GitHub Releases** de ce fork (avec vérification
  d'intégrité **SHA-256** de l'APK téléchargé)
- **Fallback sous-titres OpenSubtitles** : quand la source sélectionnée ne
  fournit aucun sous-titre, l'app peut les récupérer sur
  [OpenSubtitles.com](https://www.opensubtitles.com) — voir ci-dessous
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
./gradlew assembleRelease               # APKs legacy + modern dans app/build/outputs/apk/{legacy,modern}/release/
./gradlew assembleLegacyRelease         # variante legacy uniquement
./gradlew assembleModernRelease         # variante modern uniquement
./gradlew testLegacyDebugUnitTest testModernDebugUnitTest
```

Requis : JDK 17 + Android SDK (compileSdk 35). La CI
(`.github/workflows/build.yml`) compile, teste et publie la release à
chaque push sur `master`.

## Sous-titres externes (OpenSubtitles)

Si la source ne fournit pas de sous-titres pour un épisode, l'app peut les
chercher sur OpenSubtitles (langue configurée, sinon FR puis EN) :

1. Créer un compte gratuit sur [opensubtitles.com](https://www.opensubtitles.com)
2. Générer une clé API : **Settings → API** (consommateur, gratuit)
3. Dans l'app : **Settings → OpenSubtitles API Key** → coller la clé

Laisser la clé vide désactive le fallback (comportement d'origine).

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
