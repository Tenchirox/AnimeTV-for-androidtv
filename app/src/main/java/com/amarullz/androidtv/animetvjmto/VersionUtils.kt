package com.amarullz.androidtv.animetvjmto

import java.util.Locale

/**
 * Utilitaires de comparaison de versions (pur Kotlin, sans dependances
 * Android, pour etre testables en unitaire sur la JVM).
 */
object VersionUtils {

    /**
     * Compare deux versions ("v6.6.8" vs "6.6.7-Nightly") segment par segment.
     *
     * @return true si la version `latestTag` est strictement superieure
     *         a `currentName`
     */
    @JvmStatic
    fun isNewerVersion(latestTag: String, currentName: String): Boolean {
        return try {
            val latest = versionParts(latestTag)
            val current = versionParts(currentName)
            val max = maxOf(latest.size, current.size)
            for (i in 0 until max) {
                val l = if (i < latest.size) latest[i] else 0
                val c = if (i < current.size) current[i] else 0
                if (l != c) return l > c
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Decoupe une version en segments numeriques ("v6.6.7-Nightly" -> 6,6,7). */
    @JvmStatic
    fun versionParts(version: String): IntArray {
        var v = version.trim()
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1)
        }
        return v.split(".").map { segment ->
            val end = segment.indexOfFirst { !it.isDigit() }.let {
                if (it == -1) segment.length else it
            }
            if (end > 0) segment.substring(0, end).toInt() else 0
        }.toIntArray()
    }

    /** Formate une taille en octets ("12.4 MB"). */
    @JvmStatic
    fun formatSize(bytes: Long): String =
        if (bytes <= 0) "? MB"
        else String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

    /**
     * Extrait le hash hex d'un champ "digest" de l'API GitHub Releases
     * (format "sha256:ab12cd...").
     *
     * @return le hash hexadecimal, ou null si absent/autre algorithme
     */
    @JvmStatic
    fun parseSha256Digest(digest: String?): String? =
        if (digest != null && digest.startsWith("sha256:")) digest.substring(7).trim()
        else null
}
