package com.amarullz.androidtv.animetvjmto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Replanifie le job de mise a jour des chaines TV au demarrage de l'appareil.
 */
public class ChannelStartServiceReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    AnimeProvider.scheduleJob(context);
  }
}
