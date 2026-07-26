package com.amarullz.androidtv.animetvjmto;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pool de threads partage pour les taches de fond de l'application.
 *
 * <p>Remplace {@code AsyncTask.execute()} (deprecie, et surtout limite a un
 * executeur SERIE : les taches s'executaient une par une). Ici les taches
 * reseau (verification de maj, chaines TV, callbacks JS) s'executent en
 * parallele, comme avec {@code AsyncTask.THREAD_POOL_EXECUTOR}.</p>
 */
public final class AppExecutors {
  private static final ExecutorService POOL =
      Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "animetv-worker");
        thread.setDaemon(true);
        return thread;
      });

  private AppExecutors() {
  }

  /** Execute une tache de fond (equivalent d'AsyncTask.execute). */
  public static void execute(Runnable runnable) {
    POOL.execute(runnable);
  }
}
