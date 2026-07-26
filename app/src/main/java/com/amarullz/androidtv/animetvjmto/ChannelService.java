package com.amarullz.androidtv.animetvjmto;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Job periodique de rafraichissement des chaines de l'ecran d'accueil
 * Android TV (Recent / Trending / Popular).
 */
public class ChannelService extends JobService {
  @Override
  public boolean onStartJob(JobParameters params) {
    AnimeProvider.executeJob(this);
    return false;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return false;
  }
}
