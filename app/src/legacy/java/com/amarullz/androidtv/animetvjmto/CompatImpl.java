package com.amarullz.androidtv.animetvjmto;

import android.webkit.WebSettings;

/**
 * Implementation "legacy" (minSdk 22, targetSdk 34, media3 1.4.1).
 *
 * <p>Les implementations specifiques a chaque variante (legacy/modern)
 * vivent dans les source sets {@code src/legacy} et {@code src/modern} ;
 * le code partage dans {@code src/main} appelle ces hooks.</p>
 */
public final class CompatImpl {

  /** true sur la variante modern (fonctionnalites recentes actives). */
  public static final boolean MODERN = false;

  private CompatImpl() {
  }

  /** Configuration WebView specifique a la variante. */
  public static void configureWebView(WebSettings settings) {
    /* legacy : configuration partagee uniquement (src/main) */
  }

  /**
   * Crash du processus de rendu WebView (API 26+).
   *
   * @return true si gere (page rechargee), false pour le comportement
   *         systeme par defaut
   */
  public static boolean onRenderProcessGone(AnimeView view) {
    /* legacy : comportement systeme par defaut */
    return false;
  }
}
