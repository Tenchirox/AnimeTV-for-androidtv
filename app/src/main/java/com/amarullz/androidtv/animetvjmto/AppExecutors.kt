package com.amarullz.androidtv.animetvjmto

import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Pool de threads partage pour les taches de fond de l'application.
 *
 * Remplace `AsyncTask.execute()` (deprecie, et surtout limite a un
 * executeur SERIE : les taches s'executaient une par une). Ici les taches
 * reseau (verification de maj, chaines TV, callbacks JS) s'executent en
 * parallele, comme avec `AsyncTask.THREAD_POOL_EXECUTOR`.
 */
object AppExecutors {

    private val pool = ThreadPoolExecutor(
        4, 4,
        60L, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    ).apply {
        threadFactory = java.util.concurrent.ThreadFactory { r ->
            Thread(r, "animetv-worker").apply { isDaemon = true }
        }
    }

    /** Execute une tache de fond (equivalent d'AsyncTask.execute). */
    @JvmStatic
    fun execute(runnable: Runnable) {
        pool.execute(runnable)
    }
}
