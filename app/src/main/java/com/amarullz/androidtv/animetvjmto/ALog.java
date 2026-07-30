package com.amarullz.androidtv.animetvjmto;

import android.util.Log;

/**
 * Wrapper de log : ecrit dans logcat ET dans le buffer interne
 * {@link AppLog} (visible depuis l'application elle-meme).
 */
public final class ALog {
  private ALog() {
  }

  public static void d(String tag, String message) {
    Log.d(tag, message);
    AppLog.add(tag, message);
  }

  public static void i(String tag, String message) {
    Log.i(tag, message);
    AppLog.add(tag, message);
  }

  public static void e(String tag, String message) {
    Log.e(tag, message);
    AppLog.add("E/" + tag, message);
  }

  public static void e(String tag, String message, Throwable throwable) {
    Log.e(tag, message, throwable);
    AppLog.add("E/" + tag, message + " (" + throwable + ")");
  }
}
