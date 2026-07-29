package com.amarullz.androidtv.animetvjmto;

import android.webkit.WebSettings;

/**
 * Implementation "modern" (minSdk 26, targetSdk 35, media3 1.5.1).
 *
 * <p>Active les fonctionnalites indisponibles sur les anciens devices :
   recuperation de crash du renderer WebView (API 26+).</p>
 */
public final class CompatImpl {

  /** true sur la variante modern (fonctionnalites recentes actives). */
  public static final boolean MODERN = true;

  private CompatImpl() {
  }

  /** Configuration WebView specifique a la variante. */
  public static void configureWebView(WebSettings settings) {
    /* modern : navigation securisee explicite (API 26+) */
    settings.setSafeBrowsingEnabled(true);
  }

  /**
   * Crash du processus de rendu WebView : recharge la page d'accueil au
   * lieu de laisser l'application figee sur un ecran noir.
   *
   * @return true (evenement gere)
   */
  public static boolean onRenderProcessGone(AnimeView view) {
    try {
      view.webViewReady = false;
      view.webView.loadUrl("https://" + Conf.getDomain() +
          "/__view/main.html");
    } catch (Exception ignored) {
    }
    return true;
  }
}
