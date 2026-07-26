package com.amarullz.androidtv.animetvjmto;

/**
 * Configuration globale de l'application.
 *
 * <p>Reconstruite depuis la classe obfusquée {@code l4.j1} de l'APK 6.6.7
 * (R8 avait fusionné les statiques de Conf dans une classe utilitaire).</p>
 */
public class Conf {

  /* Domaine courant (mis a jour selon SOURCE_DOMAIN) */
  public static String DOMAIN = "animekai.to";

  /**
   * Liste des domaines "sources" de catalogue anime.
   * L'index 0 est le domaine actif courant, les index 1..8 les sources
   * selectionnables dans les reglages de l'application.
   */
  public static String[] SOURCE_DOMAINS = {
      "animekai.to",   /* 0 : domaine courant (recopie de la source choisie) */
      "animekai.to",   /* 1 : AnimeKai (defaut) */
      "anix.to",       /* 2 : Anix */
      "aniwatchtv.to", /* 3 : AniWatch (megacloud) */
      "aniwatchtv.to", /* 4 : AniWatch (rapid-cloud) */
      "animeflix.live",/* 5 : Animeflix */
      "kaa.lt",        /* 6 : KAA */
      "api.gojo.wtf",  /* 7 : Gojo */
      "www.miruro.tv"  /* 8 : Miruro */
  };

  /* API utilisee par la source 5 (animeflix) */
  public static String SOURCE_DOMAIN5_API = "api.animeflix.dev";

  /* Source actuellement selectionnee (1..8) */
  public static int SOURCE_DOMAIN = 1;

  /* Domaine de remplacement force (reglage utilisateur), vide = desactive */
  public static String SOURCE_DOMAIN_USED = "";

  /* Domaines des hebergeurs de flux video */
  public static String STREAM_DOMAIN = "krussdomi.com";
  public static String STREAM_DOMAIN2 = "megaf.cc";
  public static String STREAM_DOMAIN3 = "megacloud.blog";
  public static String STREAM_DOMAIN4 = "rapid-cloud.co";
  public static String STREAM_DOMAIN5 = "megaup.nl";

  /* Version du "serveur" (fichier de config distant), sert a detecter les maj */
  public static String SERVER_VER = "1.0-APK";

  /* Taille du cache HTTP en Mo (5..150) */
  public static int CACHE_SIZE_MB = 100;

  /* Type de flux prefere (reglage utilisateur) */
  public static int STREAM_TYPE = 0;

  /* Lecture progressive via le cache reseau */
  public static boolean PROGRESSIVE_CACHE = false;

  /* Utiliser le DNS-over-HTTPS (1.1.1.1) */
  public static boolean USE_DOH = true;

  /* Moteur HTTP : 0 = OkHttp, 1 = HttpURLConnection, 2 = Cronet */
  public static int HTTP_CLIENT = 0;

  /* Identifiant client MyAnimeList */
  public static String MAL_CLIENT_ID = "0e9466e5ec09684cc69da53f20b07af6";

  /* User-Agent utilise partout (WebView, proxy, lecteur) */
  public static String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)" +
      " Chrome/136.0.0.0 Safari/537.36";

  /** Applique une source (1..8) comme domaine courant. */
  public static void updateSource(int num) {
    SOURCE_DOMAIN = num;
    if (num > 0 && num < SOURCE_DOMAINS.length) {
      SOURCE_DOMAINS[0] = DOMAIN = SOURCE_DOMAINS[num];
    }
  }

  /** @return le domaine source actuellement actif. */
  public static String getDomain() {
    if (SOURCE_DOMAIN > 0 && SOURCE_DOMAIN < SOURCE_DOMAINS.length) {
      return SOURCE_DOMAINS[SOURCE_DOMAIN];
    }
    return DOMAIN;
  }

  /** @return true si le host donne est l'un des domaines sources (1..8). */
  public static boolean isSourceDomain(String host) {
    for (int i = 1; i < SOURCE_DOMAINS.length; i++) {
      if (host.equals(SOURCE_DOMAINS[i])) {
        return true;
      }
    }
    return false;
  }
}
