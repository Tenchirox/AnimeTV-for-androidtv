package com.amarullz.androidtv.animetvjmto

/**
 * Configuration globale de l'application.
 *
 * Reconstruite depuis la classe obfusquee `l4.j1` de l'APK 6.6.7
 * (R8 avait fusionne les statiques de Conf dans une classe utilitaire).
 */
object Conf {

    /** Domaine courant (mis a jour selon SOURCE_DOMAIN). */
    @JvmField
    @Volatile
    var DOMAIN = "animekai.to"

    /**
     * Liste des domaines "sources" de catalogue anime.
     * L'index 0 est le domaine actif courant, les index 1..8 les sources
     * selectionnables dans les reglages de l'application.
     *
     * IMPORTANT : ne jamais retirer d'entree (la logique SD1..SD8 depend
     * des index). Les sources mortes sont marquees "rip" et masquees dans
     * l'UI (voir m.js __SOURCE_ACTIVE).
     */
    @JvmField
    @Volatile
    var SOURCE_DOMAINS = arrayOf(
        "kaa.lt",         /* 0 : domaine courant (recopie de la source choisie) */
        "animekai.to",    /* 1 : AnimeKai (rip) */
        "anix.to",        /* 2 : Anix (rip) */
        "aniwatchtv.to",  /* 3 : AniWatch (megacloud) */
        "aniwatchtv.to",  /* 4 : AniWatch (rapid-cloud) */
        "animeflix.live", /* 5 : Animeflix (rip) */
        "kaa.lt",         /* 6 : KAA / KickAss */
        "api.gojo.wtf",   /* 7 : Gojo */
        "www.miruro.tv"   /* 8 : Miruro */
    )

    /** API utilisee par la source 5 (animeflix). */
    @JvmField
    @Volatile
    var SOURCE_DOMAIN5_API = "api.animeflix.dev"

    /** Source utilisee par defaut (les sources 1, 2 et 5 sont mortes). */
    const val SOURCE_DOMAIN_DEFAULT = 6

    /** Source actuellement selectionnee (1..8). */
    @JvmField
    @Volatile
    var SOURCE_DOMAIN = SOURCE_DOMAIN_DEFAULT

    /** @return true si la source est morte (masquee dans l'UI). */
    @JvmStatic
    fun isDeadSource(source: Int): Boolean =
        source == 1 || source == 2 || source == 5

    /**
     * Normalise une source : morte -> defaut, 4 -> 3 (Aniwatch etait
     * affiche en double alors que 3 et 4 sont traites a l'identique).
     */
    @JvmStatic
    fun normalizeSource(source: Int): Int = when {
        isDeadSource(source) -> SOURCE_DOMAIN_DEFAULT
        source == 4 -> 3
        else -> source
    }

    /** Domaine de remplacement force (reglage utilisateur), vide = desactive. */
    @JvmField
    @Volatile
    var SOURCE_DOMAIN_USED = ""

    /** Domaines des hebergeurs de flux video. */
    @JvmField
    @Volatile
    var STREAM_DOMAIN = "krussdomi.com"

    @JvmField
    @Volatile
    var STREAM_DOMAIN3 = "megacloud.blog"

    /** Version du "serveur" (fichier de config distant), sert a detecter les maj. */
    @JvmField
    @Volatile
    var SERVER_VER = "1.0-APK"

    /** Taille du cache HTTP en Mo (5..150). */
    @JvmField
    @Volatile
    var CACHE_SIZE_MB = 100

    /** Type de flux prefere (reglage utilisateur). */
    @JvmField
    @Volatile
    var STREAM_TYPE = 0

    /** Lecture progressive via le cache reseau. */
    @JvmField
    @Volatile
    var PROGRESSIVE_CACHE = false

    /** Utiliser le DNS-over-HTTPS (1.1.1.1). */
    @JvmField
    @Volatile
    var USE_DOH = true

    /** Moteur HTTP : 0 = OkHttp, 1 = HttpURLConnection, 2 = Cronet. */
    @JvmField
    @Volatile
    var HTTP_CLIENT = 0

    /** Identifiant client MyAnimeList. */
    @JvmField
    @Volatile
    var MAL_CLIENT_ID = "0e9466e5ec09684cc69da53f20b07af6"

    /** User-Agent utilise partout (WebView, proxy, lecteur). */
    @JvmField
    @Volatile
    var USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)" +
        " Chrome/136.0.0.0 Safari/537.36"

    /** Applique une source (1..8) comme domaine courant. */
    @JvmStatic
    fun updateSource(num: Int) {
        SOURCE_DOMAIN = num
        if (num in 1 until SOURCE_DOMAINS.size) {
            SOURCE_DOMAINS[0] = DOMAIN.also { DOMAIN = SOURCE_DOMAINS[num] }
        }
    }

    /** @return le domaine source actuellement actif. */
    @JvmStatic
    fun getDomain(): String =
        if (SOURCE_DOMAIN in 1 until SOURCE_DOMAINS.size) SOURCE_DOMAINS[SOURCE_DOMAIN]
        else DOMAIN

    /** @return true si le host donne est l'un des domaines sources (1..8). */
    @JvmStatic
    fun isSourceDomain(host: String): Boolean {
        for (i in 1 until SOURCE_DOMAINS.size) {
            if (SOURCE_DOMAINS[i] == host) return true
        }
        return false
    }
}
