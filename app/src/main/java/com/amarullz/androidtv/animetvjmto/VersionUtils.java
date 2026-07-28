package com.amarullz.androidtv.animetvjmto;

import java.util.Locale;

/**
 * Utilitaires de comparaison de versions (pur Java, sans dependances
 * Android, pour etre testables en unitaire sur la JVM).
 */
public final class VersionUtils {

  private VersionUtils() {
  }

  /**
   * Compare deux versions ("v6.6.8" vs "6.6.7-Nightly") segment par segment.
   *
   * @return true si la version {@code latestTag} est strictement superieure
   *         a {@code currentName}
   */
  public static boolean isNewerVersion(String latestTag, String currentName) {
    try {
      int[] latest = versionParts(latestTag);
      int[] current = versionParts(currentName);
      int max = Math.max(latest.length, current.length);
      for (int i = 0; i < max; i++) {
        int l = i < latest.length ? latest[i] : 0;
        int c = i < current.length ? current[i] : 0;
        if (l != c) {
          return l > c;
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  /** Decoupe une version en segments numeriques ("v6.6.7-Nightly" -> 6,6,7). */
  public static int[] versionParts(String version) {
    version = version.trim();
    if (version.startsWith("v") || version.startsWith("V")) {
      version = version.substring(1);
    }
    String[] segments = version.split("\\.");
    int[] parts = new int[segments.length];
    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];
      int end = 0;
      while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
        end++;
      }
      parts[i] = end > 0 ? Integer.parseInt(segment.substring(0, end)) : 0;
    }
    return parts;
  }

  /** Formate une taille en octets ("12.4 MB"). */
  public static String formatSize(long bytes) {
    if (bytes <= 0) {
      return "? MB";
    }
    return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
  }

  /**
   * Extrait le hash hex d'un champ "digest" de l'API GitHub Releases
   * (format "sha256:ab12cd...").
   *
   * @return le hash hexadecimal, ou null si absent/autre algorithme
   */
  public static String parseSha256Digest(String digest) {
    if (digest != null && digest.startsWith("sha256:")) {
      return digest.substring(7).trim();
    }
    return null;
  }
}
