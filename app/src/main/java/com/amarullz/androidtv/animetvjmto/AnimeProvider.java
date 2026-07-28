package com.amarullz.androidtv.animetvjmto;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.tvprovider.media.tv.Channel;
import androidx.tvprovider.media.tv.ChannelLogoUtils;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.WatchNextProgram;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestion des chaines de l'ecran d'accueil Android TV et du "Watch Next".
 *
 * <p>Trois chaines sont publiees : <b>Recent</b> (diffusions en cours),
 * <b>Trending</b> et <b>Popular</b>, alimentees par l'API GraphQL AniList
 * ({@value #ANILIST_API}).</p>
 *
 * <p>Reconstruit depuis les classes obfusquees {@code f3.j}, {@code f3.h}
 * et {@code f3.i} de l'APK 6.6.7.</p>
 */
public class AnimeProvider {
  private static final String _TAG = "ATVLOG-CHANNEL";

  private static final String ANILIST_API = "https://graphql.anilist.co/";

  /* Requete AniList : episodes recemment diffuses (0xFFFFFF = timestamp actuel) */
  private static final String QUERY_RECENT =
      "{\"query\":\"query ($tm: Int, $page: Int, $perPage: Int){ " +
      "Page(page: $page, perPage: $perPage) { pageInfo { perPage hasNextPage " +
      "currentPage } airingSchedules(airingAt_lesser:$tm,sort:TIME_DESC){ " +
      "airingAt episode timeUntilAiring media{ id isAdult title{ romaji " +
      "english } coverImage{ extraLarge } episodes format popularity " +
      "averageScore } } } }\",\"variables\":{\"tm\":0xFFFFFF,\"page\":1," +
      "\"perPage\":50}}";

  /* Requete AniList : tendances */
  private static final String QUERY_TRENDING =
      "{\"query\":\"query ($page: Int, $perPage: Int){ Page(page: $page, " +
      "perPage: $perPage) { pageInfo { perPage hasNextPage currentPage } " +
      "media(sort:TRENDING_DESC,isAdult:false, type: ANIME, status:RELEASING) " +
      "{id title{ romaji english } coverImage{ large } episodes format " +
      "averageScore } } }\",\"variables\":{\"page\":1,\"perPage\":50}}";

  /* Requete AniList : populaires */
  private static final String QUERY_POPULAR =
      "{\"query\":\"query ($page: Int, $perPage: Int){ Page(page: $page, " +
      "perPage: $perPage) { pageInfo { perPage hasNextPage currentPage } " +
      "media(sort:POPULARITY_DESC,isAdult:false, type: ANIME, " +
      "status_not_in:[HIATUS,CANCELLED,NOT_YET_RELEASED]) { id title{ romaji " +
      "english } coverImage{ large } episodes format averageScore } } }\"," +
      "\"variables\":{\"page\":1,\"perPage\":50}}";

  private static final String[] PROGRAM_PROJECTION = {
      TvContract.PreviewPrograms._ID,
      TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
      TvContract.PreviewPrograms.COLUMN_TITLE
  };

  private static final String[] PLAYNEXT_PROJECTION = {
      TvContract.WatchNextPrograms._ID,
      TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
      TvContract.WatchNextPrograms.COLUMN_TITLE
  };

  /** Callback de fin de requete AniList. */
  public interface RecentCallback {
    void onFinish(String result);
  }

  private final Context ctx;
  private final long channelId;

  public AnimeProvider(Context context, String channelName, String providerId) {
    ctx = context;
    AnimeApi.initHttpEngine(context);
    long id;
    try {
      id = initChannel(channelName, providerId);
    } catch (Exception e) {
      id = -1;
      Log.d(_TAG, e.toString());
    }
    channelId = id;
  }

  /* ------------------------------------------------------------------
   * Job de rafraichissement
   * ------------------------------------------------------------------ */

  /** Met a jour les 3 chaines puis replanifie le job periodique. */
  public static void executeJob(Context context) {
    if (Build.VERSION.SDK_INT < 29) {
      return;
    }

    /* Chaine "Recent" : episodes en cours de diffusion */
    AnimeProvider recent = new AnimeProvider(context, "Recent (Beta)", "org.tenchirock.animetv");
    if (recent.channelId >= 1) {
      try {
        AppExecutors.execute(() -> recent.requestRecent(new ChannelCallback(recent)));
      } catch (Exception ignored) {
      }
    }

    /* Chaine "Trending" */
    AnimeProvider trending =
        new AnimeProvider(context, "Trending (Beta)", "org.tenchirock.animetv.trending");
    if (trending.channelId >= 1) {
      try {
        AppExecutors.execute(() ->
            trending.requestQuery(QUERY_TRENDING, new ChannelCallback(trending)));
      } catch (Exception ignored) {
      }
    }

    /* Chaine "Popular" */
    AnimeProvider popular = new AnimeProvider(context, "Popular (Beta)", "org.tenchirock.animetv.popular");
    if (popular.channelId >= 1) {
      try {
        AppExecutors.execute(() ->
            popular.requestQuery(QUERY_POPULAR, new ChannelCallback(popular)));
      } catch (Exception ignored) {
      }
    }

    scheduleJob(context);
  }

  /** Planifie le job periodique de mise a jour des chaines (toutes les heures). */
  public static void scheduleJob(Context context) {
    Log.d(_TAG, "SCHEDULING JOB");
    JobInfo.Builder builder = new JobInfo.Builder(0,
        new ComponentName(context, ChannelService.class));
    builder.setMinimumLatency(3600000);
    builder.setOverrideDeadline(3800000);
    JobScheduler scheduler =
        (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    scheduler.schedule(builder.build());
  }

  /* ------------------------------------------------------------------
   * Requetes AniList
   * ------------------------------------------------------------------ */

  /** Execute la requete "Recent" (airingSchedules). */
  private void requestRecent(RecentCallback callback) {
    try {
      AnimeApi.Http http = new AnimeApi.Http(ANILIST_API);
      http.addHeader("Accept", "application/json");
      String body = QUERY_RECENT.replace("0xFFFFFF",
          (System.currentTimeMillis() / 1000) + "");
      http.setMethod("POST", body, "application/json");
      http.execute();
      JSONArray result = new JSONArray("[]");
      parseAiringSchedules(result, http.body.toString());
      callback.onFinish(result.toString());
      if (result.length() > 0) {
        Log.d(_TAG, "GOT RECENTS => " + result.length());
        callback.onFinish(result.toString());
        return;
      }
    } catch (Exception ignored) {
    }
    callback.onFinish("");
  }

  /** Execute une requete media generique (Trending / Popular). */
  private void requestQuery(String query, RecentCallback callback) {
    try {
      AnimeApi.Http http = new AnimeApi.Http(ANILIST_API);
      http.addHeader("Accept", "application/json");
      http.setMethod("POST", query, "application/json");
      http.execute();
      JSONArray result = new JSONArray("[]");
      parseMediaList(result, http.body.toString());
      callback.onFinish(result.toString());
      return;
    } catch (Exception ignored) {
    }
    callback.onFinish("");
  }

  /** Remplit les programmes de la chaine a partir du JSON AniList. */
  private static class ChannelCallback implements RecentCallback {
    private final AnimeProvider provider;

    ChannelCallback(AnimeProvider provider) {
      this.provider = provider;
    }

    @Override
    public void onFinish(String result) {
      try {
        JSONArray array = new JSONArray(result);
        provider.clearPrograms();
        for (int i = 0; i < array.length(); i++) {
          JSONObject anime = array.getJSONObject(i);
          provider.addProgram(
              anime.getString("title"),
              anime.getString("ep"),
              anime.getString("poster"),
              anime.getString("url"),
              anime.getString("tip"));
        }
      } catch (Exception ignored) {
      }
    }
  }

  /* ------------------------------------------------------------------
   * Parsing des reponses AniList
   * ------------------------------------------------------------------ */

  /** Parse une reponse "media" (Trending / Popular). */
  public static void parseMediaList(JSONArray out, String body) {
    try {
      JSONArray media = new JSONObject(body)
          .getJSONObject("data").getJSONObject("Page").getJSONArray("media");
      List<JSONObject> list = new ArrayList<>();
      for (int i = 0; i < media.length(); i++) {
        try {
          JSONObject anime = media.getJSONObject(i);
          JSONObject title = anime.getJSONObject("title");
          String titleEnglish = title.isNull("english") ? "" : title.getString("english");
          String titleRomaji = title.isNull("romaji") ? "" : title.getString("romaji");
          String poster = anime.getJSONObject("coverImage").getString("large");
          String format = anime.isNull("format") ? "" : anime.getString("format");
          int episodes = anime.isNull("episodes") ? 0 : anime.getInt("episodes");
          int score = anime.isNull("averageScore") ? 0 : anime.getInt("averageScore");
          long id = anime.getLong("id");

          JSONObject item = new JSONObject("{}");
          item.put("url", id + "/" + episodes);
          item.put("title", titleEnglish.isEmpty() ? titleRomaji : titleEnglish);
          item.put("poster", poster);
          StringBuilder ep = new StringBuilder();
          if (format.equals("MOVIE") || episodes == 0) {
            ep.append("Score: ").append(score);
          } else {
            ep.append(episodes).append("  Episodes  |  Score: ").append(score);
          }
          ep.append("  |  ").append(format);
          item.put("ep", ep.toString());
          item.put("type", format);
          item.put("tip", id);
          list.add(item);
        } catch (JSONException ignored) {
        }
      }
      for (JSONObject item : list) {
        out.put(item);
      }
    } catch (JSONException ignored) {
    }
  }

  /** Parse une reponse "airingSchedules" (Recent), triee par popularite. */
  public void parseAiringSchedules(JSONArray out, String body) {
    try {
      JSONArray schedules = new JSONObject(body)
          .getJSONObject("data").getJSONObject("Page").getJSONArray("airingSchedules");
      List<JSONObject> list = new ArrayList<>();
      for (int i = 0; i < schedules.length(); i++) {
        try {
          JSONObject anime = schedules.getJSONObject(i).getJSONObject("media");
          if (anime.getBoolean("isAdult")) {
            continue;
          }
          JSONObject title = anime.getJSONObject("title");
          String titleEnglish = title.isNull("english") ? "" : title.getString("english");
          String titleRomaji = title.isNull("romaji") ? "" : title.getString("romaji");
          String poster = anime.getJSONObject("coverImage").getString("extraLarge");
          String format = anime.isNull("format") ? "" : anime.getString("format");
          if (format.equalsIgnoreCase("TV_SHORT")) {
            format = "TV";
          }
          int episodes = anime.isNull("episodes") ? 0 : anime.getInt("episodes");
          long id = anime.getLong("id");
          int popularity = anime.isNull("popularity") ? 0 : anime.getInt("popularity");
          int score = anime.isNull("averageScore") ? 0 : anime.getInt("averageScore");

          JSONObject item = new JSONObject("{}");
          item.put("url", id + "/" + episodes);
          item.put("title", titleEnglish.isEmpty() ? titleRomaji : titleEnglish);
          item.put("poster", poster);
          StringBuilder ep = new StringBuilder();
          if (format.equals("MOVIE") || episodes == 0) {
            ep.append("Score: ").append(score);
          } else {
            ep.append(episodes).append("  Episodes  |  Score: ").append(score);
          }
          ep.append("  |  ").append(format);
          item.put("ep", ep.toString());
          item.put("type", format);
          item.put("tip", id);
          item.put("popularity", popularity);
          list.add(item);
        } catch (JSONException ignored) {
        }
      }
      /* Tri par popularite decroissante */
      Collections.sort(list, (a, b) -> {
        try {
          return Integer.compare(b.getInt("popularity"), a.getInt("popularity"));
        } catch (JSONException e) {
          return 0;
        }
      });
      for (JSONObject item : list) {
        out.put(item);
      }
    } catch (JSONException ignored) {
    }
  }

  /* ------------------------------------------------------------------
   * Programmes des chaines
   * ------------------------------------------------------------------ */

  /** Ajoute un programme a la chaine. */
  public void addProgram(String title, String desc, String poster, String uri, String tip) {
    Intent intent = new Intent(ctx, MainActivity.class);
    intent.setPackage(ctx.getPackageName());
    intent.putExtra("viewurl", uri);
    intent.putExtra("viewtip", tip);
    intent.putExtra("viewsd", "1");
    intent.putExtra("viewpos", "0");
    Uri intentUri = Uri.parse(intent.toUri(Intent.URI_ANDROID_APP_SCHEME));

    PreviewProgram program = new PreviewProgram.Builder()
        .setChannelId(channelId)
        .setType(TvContract.PreviewPrograms.TYPE_TV_EPISODE)
        .setTitle(title)
        .setDescription(desc)
        .setPosterArtAspectRatio(TvContract.PreviewPrograms.ASPECT_RATIO_2_3)
        .setPosterArtUri(Uri.parse(poster))
        .setIntentUri(intentUri)
        .build();
    ctx.getContentResolver().insert(TvContract.PreviewPrograms.CONTENT_URI,
        program.toContentValues());
  }

  /** Supprime tous les programmes de la chaine. */
  public void clearPrograms() {
    Cursor cursor = ctx.getContentResolver().query(
        TvContract.PreviewPrograms.CONTENT_URI, PROGRAM_PROJECTION,
        null, null, null);
    if (cursor == null || !cursor.moveToFirst()) {
      return;
    }
    int count = 0;
    do {
      PreviewProgram program = PreviewProgram.fromCursor(cursor);
      if (program.getChannelId() == channelId) {
        ctx.getContentResolver().delete(
            ContentUris.withAppendedId(TvContract.PreviewPrograms.CONTENT_URI,
                program.getId()),
            null, null);
        count++;
      }
    } while (cursor.moveToNext());
    cursor.close();
    Log.d(_TAG, "Cleanup " + count + " Programs");
  }

  /** Cree la chaine (ou retrouve l'existante par son provider id). */
  private long initChannel(String name, String providerId) {
    long channelId = findChannel(providerId);
    if (channelId != -1) {
      return channelId;
    }

    Intent intent = new Intent(ctx, MainActivity.class);
    intent.setPackage(ctx.getPackageName());
    Uri appLinkIntent = Uri.parse(intent.toUri(Intent.URI_ANDROID_APP_SCHEME));

    Channel channel = new Channel.Builder()
        .setType(TvContract.Channels.TYPE_PREVIEW)
        .setDisplayName(name)
        .setInternalProviderId(providerId)
        .setAppLinkIntentUri(appLinkIntent)
        .build();
    Uri channelUri = ctx.getContentResolver().insert(
        TvContract.Channels.CONTENT_URI, channel.toContentValues());
    Log.d(_TAG, "Created Channel = " + channelUri);
    long id = ContentUris.parseId(channelUri);
    Log.d(_TAG, "channel id " + id);

    /* Logo de la chaine */
    try {
      Drawable drawable = ctx.getDrawable(R.drawable.splash);
      Bitmap bitmap;
      if (drawable instanceof VectorDrawable) {
        bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
            drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
      } else {
        bitmap = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.splash);
      }
      ChannelLogoUtils.storeChannelLogo(ctx, id, bitmap);
    } catch (Exception e) {
      Log.i(_TAG, "Failed to store the logo", e);
    }
    return id;
  }

  /** Recherche une chaine existante par son internal_provider_id. */
  private long findChannel(String providerId) {
    try {
      Cursor cursor = ctx.getContentResolver().query(
          TvContract.Channels.CONTENT_URI,
          new String[]{TvContract.Channels._ID,
              TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID},
          null, null, null);
      if (cursor == null) {
        return -1;
      }
      int idIndex = cursor.getColumnIndex(TvContract.Channels._ID);
      int providerIndex =
          cursor.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID);
      while (cursor.moveToNext()) {
        if (providerId.equals(cursor.getString(providerIndex))) {
          long id = cursor.getLong(idIndex);
          Log.d(_TAG, "Found existing channel ID: " + id);
          cursor.close();
          return id;
        }
      }
      cursor.close();
    } catch (Exception e) {
      Log.e(_TAG, "Error finding channel", e);
    }
    return -1;
  }

  /* ------------------------------------------------------------------
   * Watch Next ("Continuer a regarder")
   * ------------------------------------------------------------------ */

  /** Supprime toutes les entrees Watch Next. */
  public static void clearPlayNext(Activity activity) {
    if (Build.VERSION.SDK_INT < 24) {
      return;
    }
    try {
      Cursor cursor = activity.getContentResolver().query(
          TvContract.WatchNextPrograms.CONTENT_URI, PLAYNEXT_PROJECTION,
          null, null, null);
      if (cursor == null || !cursor.moveToFirst()) {
        return;
      }
      do {
        WatchNextProgram program = WatchNextProgram.fromCursor(cursor);
        activity.getContentResolver().delete(
            ContentUris.withAppendedId(TvContract.WatchNextPrograms.CONTENT_URI,
                program.getId()),
            null, null);
      } while (cursor.moveToNext());
      cursor.close();
    } catch (Exception ignored) {
    }
  }

  /** Publie l'episode en cours dans le Watch Next de l'ecran d'accueil. */
  public static void setPlayNext(Activity activity, String title, String desc,
      String poster, String uri, String tip, int position, int duration, int sd) {
    if (Build.VERSION.SDK_INT < 24) {
      return;
    }
    try {
      clearPlayNext(activity);
      Intent intent = new Intent(activity, MainActivity.class);
      intent.setPackage(activity.getPackageName());
      intent.putExtra("viewurl", uri);
      intent.putExtra("viewtip", tip);
      intent.putExtra("viewsd", sd + "");
      intent.putExtra("viewpos", position + "");
      Uri intentUri = Uri.parse(intent.toUri(Intent.URI_ANDROID_APP_SCHEME));

      WatchNextProgram program = new WatchNextProgram.Builder()
          .setType(TvContract.WatchNextPrograms.TYPE_MOVIE)
          .setWatchNextType(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
          .setDurationMillis(duration * 1000)
          .setLastPlaybackPositionMillis(position * 1000)
          .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
          .setTitle(title)
          .setDescription(desc)
          .setPosterArtUri(Uri.parse(poster))
          .setIntentUri(intentUri)
          .build();
      Log.d(_TAG, "New Watch Next Update = " + activity.getContentResolver().insert(
          TvContract.WatchNextPrograms.CONTENT_URI, program.toContentValues()));
    } catch (Exception ignored) {
    }
  }
}
