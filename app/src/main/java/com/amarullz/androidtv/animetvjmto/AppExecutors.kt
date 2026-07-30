package com.amarullz.androidtv.animetvjmto

import java.util.concurrent.Executors

/**
 * Pool de threads partage pour les taches de fond de l'application.
 *
 * Remplace `AsyncTask.execute()` (deprecie, et surtout limite a un
 * executeur SERIE : les taches s'executaient une par une). Ici les taches
 * reseau (verification de maj, chaines TV, callbacks JS) s'executent en
 * parallele, comme avec `AsyncTask.THREAD_POOL_EXECUTOR`.
 */
object AppExecutors {

    private val pool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "animetv-worker").apply { isDaemon = true }
    }

    /** Execute une tache de fond (equivalent d'AsyncTask.execute). */
    @JvmStatic
    fun execute(runnable: Runnable) {
        pool.execute(runnable)
    }
}
