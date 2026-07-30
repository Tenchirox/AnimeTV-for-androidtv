package com.amarullz.androidtv.animetvjmto

import android.util.Log

/**
 * Wrapper de log : ecrit dans logcat ET dans le buffer interne
 * [AppLog] (visible depuis l'application elle-meme).
 */
object ALog {

    @JvmStatic
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        AppLog.add(tag, message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        AppLog.add(tag, message)
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        Log.e(tag, message)
        AppLog.add("E/$tag", message)
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        AppLog.add("E/$tag", "$message ($throwable)")
    }
}
