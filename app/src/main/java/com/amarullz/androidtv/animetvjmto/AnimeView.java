package com.amarullz.androidtv.animetvjmto;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Html;
import android.text.InputFilter;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.ui.AspectRatioFrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Controleur principal de l'application : pilote la WebView (UI HTML/JS),
 * le lecteur video ExoPlayer/Media3, le pont Javascript {@code _JSAPI},
 * les dialogues natifs, la recherche vocale et le "Watch Next".
 *
 * <p>Reconstruit depuis les classes obfusquees {@code f3.e0} (AnimeView),
 * {@code f3.d0} (JSViewApi), {@code f3.v} (WebChromeClient),
 * {@code f3.w} (listener lecteur), {@code f3.k} (fabrique DataSource) et
 * {@code f3.x} (PromptCallback) de l'APK 6.6.7.</p>
 */
@UnstableApi
public class AnimeView extends WebViewClient {
  private static final String _TAG = "ATVLOG-VIEW";

  /* Version de build (recalculee depuis le timestamp de compilation) */
  public static String BUILD_VERSION = "2307210136";

  /* Codes retour de la recherche vocale envoyes au JS */
  private static final int VOICE_STATUS_STARTED = 1;
  private static final int VOICE_STATUS_READY = 2;
  private static final int VOICE_STATUS_PARTIAL = 3;
  private static final int VOICE_STATUS_RESULT = 4;
  private static final int VOICE_STATUS_END = 5;
  private static final int VOICE_STATUS_ERROR = 6;
  private static final int VOICE_STATUS_RMS = 7;

  public final Activity activity;
  public final WebView webView;
  public SurfaceView videoView = null;
  public ExoPlayer videoPlayer = null;
  public final ImageView splash;
  public final FrameLayout videoLayout;
  public AspectRatioFrameLayout videoFrame = null;
  public final AnimeApi aApi;
  public final String playerInjectString;
  public boolean webViewReady = false;

  /* Cache de la derniere reponse /getSources (megacloud/rapid-cloud),
   * servi au JS via l'endpoint local /__cache_subtitle (sous-titres).
   * volatile : ecrit depuis le thread WebView, lu depuis d'autres threads. */
  public volatile String cachedSourcesJson = "";

  public final AudioManager audioManager;
  public int sysBrightness;
  public int sysheightNav = 0;
  public int sysheightStat = 0;

  public DefaultTrackSelector trackSelector = null;
  public int videoSizeWidth = 0;
  public int videoSizeHeight = 0;
  public String videoAudioLanguage = "";
  public int videoSelectedQuality = 0;

  /* Groupes de pistes courants (pour la selection audio/qualite) */
  private Tracks.Group currentAudioGroup = null;
  private Tracks.Group currentVideoGroup = null;

  /* Profil utilisateur (prefixe de stockage JS) */
  public int profile_sel = -1;
  public String profile_prefix = "";

  /* Recherche vocale */
  public SpeechRecognizer voiceRecognizer = null;
  public final RecognitionListener voiceListener = new VoiceRecognitionListener();

  /* Metadonnees "Watch Next" (chaines TV) */
  public boolean pnUpdated = false;
  public String pnTitle = "";
  public String pnDesc = "";
  public String pnPoster = "";
  public String pnUri = "";
  public String pnTip = "";
  public int pnSd = 1;
  public int pnPos = 0;
  public int pnDuration = 0;

  /* Etat de lecture (pause/reprise) */
  public int videoStatCurrentPosition = 0;
  public boolean videoStatIsPlaying = false;
  public int videoStatScaleType = 0;
  public String videoStatCurrentUrl = "";

  public AnimeView(Activity activity) {
    this.activity = activity;

    /* Luminosite systeme courante */
    try {
      sysBrightness = Settings.System.getInt(activity.getContentResolver(),
          Settings.System.SCREEN_BRIGHTNESS);
    } catch (Exception e) {
      sysBrightness = 127;
    }
    Log.d(_TAG, "ATVLOG Current Sys Brightness = " + sysBrightness);
    BUILD_VERSION = (String) DateFormat.format("yyMMddHHmm",
        new Date(BuildConfig.TIMESTAMP));

    splash = activity.findViewById(R.id.splash);
    videoLayout = activity.findViewById(R.id.video_layout);
    webView = activity.findViewById(R.id.webview);
    webView.requestFocus();
    webView.setBackgroundColor(0);
    webView.setWebViewClient(this);

    /* Configuration WebView */
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setJavaScriptCanOpenWindowsAutomatically(false);
    settings.setSupportMultipleWindows(false);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setDomStorageEnabled(true);
    settings.setUseWideViewPort(false);
    settings.setUserAgentString(Conf.USER_AGENT);
    settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
    if (Build.VERSION.SDK_INT >= 23) {
      settings.setOffscreenPreRaster(true);
    }
    if (Build.VERSION.SDK_INT >= 33) {
      settings.setAlgorithmicDarkeningAllowed(false);
    }
    settings.setGeolocationEnabled(false);
    webView.addJavascriptInterface(new JSViewApi(), "_JSAPI");
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

    setFullscreen(0);
    audioManager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
    webView.setWebChromeClient(new AnimeWebChromeClient());
    webView.setVerticalScrollBarEnabled(false);

    initVideoView();
    videoViewSetScale(videoStatScaleType);

    aApi = new AnimeApi(activity);
    playerInjectString = aApi.assetsString("inject/view_player.html");

    webView.loadUrl("https://" + Conf.getDomain() + "/__view/login/login.html#appstart");

    AnimeProvider.executeJob(activity);
  }

  /* ------------------------------------------------------------------
   * Crypto (appele depuis le JS)
   * ------------------------------------------------------------------ */

  /** Double chiffrement RC4 (k1 puis k2) + Base64, '/' remplace par '_'. */
  public static String vidIdEncode(String value, String key1, String key2)
      throws Exception {
    SecretKeySpec keySpec1 = new SecretKeySpec(key1.getBytes(), "RC4");
    SecretKeySpec keySpec2 = new SecretKeySpec(key2.getBytes(), "RC4");
    Cipher cipher1 = Cipher.getInstance("RC4");
    Cipher cipher2 = Cipher.getInstance("RC4");
    cipher1.init(Cipher.DECRYPT_MODE, keySpec1, cipher1.getParameters());
    cipher2.init(Cipher.DECRYPT_MODE, keySpec2, cipher2.getParameters());
    return new String(
        Base64.encode(cipher2.doFinal(cipher1.doFinal(value.getBytes())),
            Base64.DEFAULT),
        StandardCharsets.UTF_8).replace("/", "_").trim();
  }

  /** Dechiffrement AES/CBC/PKCS7 (fallback PKCS5). */
  public static String decryptAES(byte[] data, byte[] key, byte[] iv) {
    try {
      Cipher cipher;
      try {
        cipher = Cipher.getInstance("AES/CBC/PKCS7PADDING");
      } catch (Throwable e) {
        cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      }
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
          new IvParameterSpec(iv));
      return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "";
    }
  }

  /* ------------------------------------------------------------------
   * Lecteur video (ExoPlayer / Media3)
   * ------------------------------------------------------------------ */

  /**
   * Fabrique une DataSource HTTP pour le lecteur, avec les headers
   * Origin/Referer adaptes a l'hebergeur de la video courante.
   *
   * <p>Reconstruit depuis la classe obfusquee {@code f3.k}.</p>
   */
  private HttpDataSource.Factory createVideoDataSourceFactory() {
    AnimeDataSource.sd5query = "";
    Map<String, String> headers = new HashMap<>();
    try {
      String host = new URL(videoStatCurrentUrl).getHost();
      int sourceDomain = Conf.SOURCE_DOMAIN;
      if (sourceDomain == 5) {
        /* Animeflix : memorise l'url (hack query gogocden) */
        AnimeDataSource.sd5query = videoStatCurrentUrl;
        headers.put("Origin", "https://" + Conf.SOURCE_DOMAIN5_API);
      } else if (sourceDomain == 1) {
        headers.put("Referer", "https://" + Conf.SOURCE_DOMAINS[1] + "/");
      } else if (sourceDomain == 3 || sourceDomain == 4) {
        headers.put("Origin", "https://" + Conf.STREAM_DOMAIN3);
        headers.put("Referer", "https://" + Conf.STREAM_DOMAIN3 + "/");
      } else if (host.contains("megaup.nl")) {
        headers.put("Origin", "https://megaup.nl");
        headers.put("Referer", "https://megaup.nl/");
      } else if (host.contains("megaf.cc")) {
        headers.put("Origin", "https://megaf.cc");
        headers.put("Referer", "https://megaf.cc/");
      } else if (host.contains("mp4upload.com")) {
        headers.put("Origin", "https://" + host);
        headers.put("Referer", "https://www.mp4upload.com/");
      } else if (host.contains("krussdomi.com")) {
        headers.put("Origin", "https://krussdomi.com");
      } else if (host.contains("megacloud.blog")) {
        headers.put("Origin", "https://megacloud.blog");
      } else if (host.contains("rapid-cloud.co")) {
        headers.put("Origin", "https://rapid-cloud.co");
      } else {
        /* Defaut : domaine de second niveau */
        String[] parts = host.split("\\.");
        headers.put("Origin",
            "https://" + parts[parts.length - 2] + "." + parts[parts.length - 1]);
      }
    } catch (Exception ignored) {
    }
    Log.d(_TAG, "VIDEO-DATA-SOURCE : " + videoStatCurrentUrl +
        " / ORIGIN : " + headers.get("Origin"));

    AnimeDataSource.Factory factory = new AnimeDataSource.Factory();
    factory.setUserAgent(Conf.USER_AGENT);
    factory.setDefaultRequestProperties(headers);
    factory.setAllowCrossProtocolRedirects(true);
    return factory;
  }

  /** (Re)cree toute la chaine de lecture video. */
  public void initVideoView() {
    if (videoView != null) {
      videoLayout.removeAllViews();
      if (videoPlayer != null) {
        videoPlayer.release();
        videoPlayer = null;
      }
      videoView = null;
    }
    setVideoSize(0, 0);

    trackSelector = new DefaultTrackSelector(activity);

    /* Buffer : 10 min min/max, 2.5s pour demarrer, 5s apres rebuffer,
     * 2 min de back-buffer */
    DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
        .setBufferDurationsMs(600000, 600000, 2500, 5000)
        .setBackBuffer(120000, true)
        .build();

    videoPlayer = new ExoPlayer.Builder(activity)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .build();

    videoFrame = new AspectRatioFrameLayout(activity);
    videoView = new SurfaceView(activity);
    videoFrame.addView(videoView, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    videoLayout.addView(videoFrame, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    videoPlayer.setVideoSurfaceView(videoView);
    videoPlayer.addListener(new PlayerEventListener());
  }

  /**
   * Selectionne les pistes audio (langue preferee) et video (qualite
   * choisie), puis envoie la liste des langues disponibles au JS.
   */
  public void initVideoTracks() {
    if (videoPlayer == null) {
      return;
    }
    Tracks tracks = videoPlayer.getCurrentTracks();
    StringBuilder availLangs = new StringBuilder();
    boolean audioSelected = false;
    Tracks.Group audioGroup = null;
    Tracks.Group videoGroup = null;
    int[] heights = null;
    int[] sortedHeights = null;

    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_AUDIO) {
        audioGroup = group;
        androidx.media3.common.TrackGroup trackGroup = group.getMediaTrackGroup();
        for (int i = 0; i < trackGroup.length; i++) {
          Format format = trackGroup.getFormat(i);
          String label = format.label != null ? format.label :
              (format.language != null ? format.language : "");
          if (!audioSelected && !videoAudioLanguage.isEmpty() &&
              !label.isEmpty() &&
              label.toLowerCase().startsWith(videoAudioLanguage.toLowerCase())) {
            Log.d(_TAG, "[TRACK] Audio Select(" + i + ", " + label + ")");
            trackSelector.setParameters(trackSelector.buildUponParameters()
                .setOverrideForType(new TrackSelectionOverride(trackGroup, i)));
            audioSelected = true;
          }
          if (!label.isEmpty()) {
            availLangs.append(",").append(
                label.toLowerCase().substring(0, Math.min(3, label.length())));
          }
          Log.d(_TAG, "[TRACK] Audio Available(" + i + ", " + label + ")");
        }
      } else if (group.getType() == C.TRACK_TYPE_VIDEO) {
        videoGroup = group;
        androidx.media3.common.TrackGroup trackGroup = group.getMediaTrackGroup();
        heights = new int[trackGroup.length];
        sortedHeights = new int[trackGroup.length];
        for (int i = 0; i < trackGroup.length; i++) {
          Format format = trackGroup.getFormat(i);
          heights[i] = 0;
          sortedHeights[i] = 0;
          if (format.roleFlags == 0) {
            heights[i] = format.height;
            sortedHeights[i] = format.height;
          }
        }
        Arrays.sort(sortedHeights);
      }
    }
    currentAudioGroup = audioGroup;
    currentVideoGroup = videoGroup;

    if (!audioSelected) {
      Log.d(_TAG, "[TRACK] Audio Select Default");
      /* Pas d'override : piste par defaut */
    }

    if (videoGroup != null && heights != null) {
      int count = heights.length;
      int selectedTrack = -1;
      for (int i = 0; i < count; i++) {
        int sortedIndex = count - 1 - i;
        if (i == videoSelectedQuality - 1) {
          int height = sortedHeights[sortedIndex];
          for (int j = 0; j < heights.length; j++) {
            if (heights[j] == height) {
              selectedTrack = j;
              break;
            }
          }
        }
        Log.d(_TAG, "[TRACK] Sorted: " + i + " => " + sortedHeights[sortedIndex]);
      }
      if (selectedTrack != -1) {
        Log.d(_TAG, "[TRACK] Quality Selected: " + selectedTrack + " => " +
            heights[selectedTrack]);
        trackSelector.setParameters(trackSelector.buildUponParameters()
            .setOverrideForType(new TrackSelectionOverride(
                videoGroup.getMediaTrackGroup(), selectedTrack)));
      } else {
        Log.d(_TAG, "[TRACK] Quality Selected: Auto - RES");
        trackSelector.setParameters(trackSelector.buildUponParameters()
            .clearOverride(videoGroup.getMediaTrackGroup()));
      }
    } else {
      Log.d(_TAG, "[TRACK] Quality Selected: Auto - NORES");
    }

    Log.d(_TAG, "[TRACK] Avail-Langs = " + availLangs);
    activity.runOnUiThread(() -> {
      if (webView != null) {
        webView.evaluateJavascript(
            "try{__VIDLANGAVAIL(\"" + availLangs + "\");}catch(e){}", null);
      }
    });
  }

  /** Change la source video du lecteur (HLS/DASH/progressive). */
  public void videoSetSource(String url) {
    try {
      if (url.equals("")) {
        videoPlayer.stop();
        videoPlayer.clearMediaItems();
        ((MainActivity) activity).mSession.setActive(false);
        return;
      }
      DataSource.Factory dataSourceFactory = createVideoDataSourceFactory();
      MediaSource mediaSource;
      if (url.endsWith("#dash")) {
        Log.d(_TAG, "VIDEO-SET-SOURCE (DASH) : " + url);
        mediaSource = new DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url));
      } else if (url.endsWith(".mkv")) {
        Log.d(_TAG, "VIDEO-SET-SOURCE (MKV) : " + url);
        mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory,
            new DefaultExtractorsFactory())
            .createMediaSource(MediaItem.fromUri(url));
      } else {
        /* Detection automatique (HLS principalement) */
        Log.d(_TAG, "VIDEO-SET-SOURCE (HLS) : " + url);
        mediaSource = new DefaultMediaSourceFactory(activity)
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url));
      }
      videoPlayer.setMediaSource(mediaSource);
      videoPlayer.prepare();
      ((MainActivity) activity).mSession.setActive(true);
    } catch (Exception ignored) {
    }
  }

  /** Definit le type d'agrandissement video (0=FIT, 1=ZOOM, 2=FILL). */
  public void videoViewSetScale(int type) {
    videoStatScaleType = type;
    activity.runOnUiThread(() -> {
      if (videoFrame == null) {
        return;
      }
      switch (type) {
        case 1:
          videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
          break;
        case 2:
          videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
          break;
        default:
          videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
          break;
      }
    });
  }

  /** Listener des evenements du lecteur vers la MediaSession. */
  private class PlayerEventListener implements Player.Listener {
    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      MainActivity mainActivity = (MainActivity) activity;
      mainActivity._metaState = isPlaying ? 3 : 2; /* PLAYING / PAUSED */
      mainActivity._metaPosition = videoPlayer.getCurrentPosition();
      Log.d("ATVLOG_MEDIA", "mediaSetState=" + mainActivity._metaState);
      mainActivity.updateMediaState();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters params) {
      MainActivity mainActivity = (MainActivity) activity;
      mainActivity._metaSpeed = params.speed;
      mainActivity._metaPosition = videoPlayer.getCurrentPosition();
      Log.d("ATVLOG_MEDIA", "mediaSetSpeed=" + params.speed);
      mainActivity.updateMediaState();
    }

    @Override
    public void onPlaybackStateChanged(int state) {
      MainActivity mainActivity = (MainActivity) activity;
      long duration = videoPlayer.getDuration();
      mainActivity._metaDuration = duration < 0 ? -1 : duration;
      Log.d("ATVLOG_MEDIA", "mediaSetDuration=" + duration);
      mainActivity.updateMediaMeta();
      mainActivity._metaPosition = videoPlayer.getCurrentPosition();
      mainActivity.updateMediaState();
    }

    @Override
    public void onRenderedFirstFrame() {
      initVideoTracks();
    }
  }

  /* ------------------------------------------------------------------
   * Dialogues natifs pilotes par le JS (prompt JSON)
   * ------------------------------------------------------------------ */

  /** Callback d'un dialogue prompt JS. */
  public interface PromptCallback {
    void onCancel();

    void onResult(String result);
  }

  /**
   * Affiche un dialogue natif pilote par un message JSON :
   * {type:"list", list:[...], sel?, multi?, selpos?, nodim?, allowsel?}
   * ou {type:"text", deval?, ispin?, maxlen?, message?, html?}.
   */
  public boolean listPrompt(String message, final PromptCallback callback) {
    try {
      JSONObject json = new JSONObject(message);
      Log.d(_TAG, "PROMPT: " + json);
      String type = json.getString("type");
      CharSequence title = json.getString("title");
      if (type.equals("list")) {
        JSONArray list = json.getJSONArray("list");
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        String[] items = new String[list.length()];
        for (int i = 0; i < list.length(); i++) {
          if (Build.VERSION.SDK_INT >= 29) {
            items[i] = list.getString(i);
          } else {
            /* Bug de rendu des tabulations sur anciennes versions */
            items[i] = list.getString(i).replaceAll("\t", " ");
          }
        }
        int initialSelection = 0;
        if (json.has("sel")) {
          /* Liste a choix unique */
          int sel = json.getInt("sel");
          final int currentSelection = json.has("allowsel") ? -1 : sel;
          builder.setSingleChoiceItems(items, sel, (dialog, which) -> {
            if (which != currentSelection) {
              callback.onResult(String.valueOf(which));
            } else {
              callback.onCancel();
            }
            dialog.cancel();
          });
          builder.setOnDismissListener(dialog -> callback.onCancel());
          initialSelection = sel;
        } else {
          if (json.has("multi")) {
            /* Liste a choix multiples */
            JSONArray multi = json.getJSONArray("multi");
            final boolean[] checked = new boolean[items.length];
            final boolean[] initialChecked = new boolean[items.length];
            for (int i = 0; i < items.length; i++) {
              if (multi.length() > i) {
                checked[i] = multi.getBoolean(i);
                initialChecked[i] = checked[i];
              } else {
                checked[i] = false;
                initialChecked[i] = false;
              }
            }
            builder.setMultiChoiceItems(items, checked,
                (dialog, which, isChecked) -> checked[which] = isChecked);
            builder.setOnDismissListener(dialog -> {
              String result;
              boolean changed = false;
              try {
                JSONArray states = new JSONArray("[]");
                for (int i = 0; i < initialChecked.length; i++) {
                  if (initialChecked[i] != checked[i]) {
                    changed = true;
                  }
                  states.put(i, checked[i]);
                }
                result = states.toString();
              } catch (JSONException e) {
                result = "";
              }
              if (changed) {
                callback.onResult(result);
              } else {
                callback.onCancel();
              }
            });
          } else {
            /* Liste simple */
            builder.setItems(items, (dialog, which) -> {
              callback.onResult(String.valueOf(which));
              dialog.cancel();
            });
            builder.setOnDismissListener(dialog -> callback.onCancel());
          }
        }
        AlertDialog dialog = builder.create();
        if (json.has("nodim")) {
          Window window = dialog.getWindow();
          Objects.requireNonNull(window);
          window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        if (json.has("selpos")) {
          initialSelection = json.getInt("selpos");
        }
        dialog.getListView().setSelection(initialSelection);
      } else if (type.equals("text")) {
        /* Saisie de texte */
        final EditText editText = new EditText(activity);
        editText.setSingleLine(true);
        editText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        editText.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        if (json.has("deval")) {
          editText.setText(json.getString("deval"));
        }
        if (json.has("ispin")) {
          editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
              android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
          editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        } else if (json.has("maxlen")) {
          editText.setFilters(new InputFilter[]{
              new InputFilter.LengthFilter(json.getInt("maxlen"))});
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        if (json.has("message")) {
          String msg = json.getString("message");
          if (json.has("html")) {
            builder.setMessage(Html.fromHtml(msg));
          } else {
            builder.setMessage(msg);
          }
        }
        FrameLayout frame = new FrameLayout(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        float density = activity.getResources().getDisplayMetrics().density;
        params.leftMargin = (int) (density * 24.0f + 0.5f);
        params.rightMargin = (int) (density * 24.0f + 0.5f);
        editText.setLayoutParams(params);
        frame.addView(editText);
        builder.setView(frame);
        builder.setPositiveButton("OK", (dialog, which) -> {
          dialog.cancel();
          try {
            JSONObject result = new JSONObject("{}");
            result.put("value", editText.getText().toString());
            callback.onResult(result.toString());
          } catch (JSONException e) {
            callback.onCancel();
          }
        });
        builder.setOnDismissListener(dialog -> callback.onCancel());
        builder.show();
        editText.requestFocus();
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /* ------------------------------------------------------------------
   * Login MyAnimeList
   * ------------------------------------------------------------------ */

  /** Affiche le dialogue de connexion MyAnimeList. */
  public void malLoginDialog() {
    View view = LayoutInflater.from(activity)
        .inflate(R.layout.mal_login_dialog, null);
    final EditText user = view.findViewById(R.id.user);
    final EditText password = view.findViewById(R.id.password);
    new AlertDialog.Builder(activity)
        .setTitle("MyAnimeList Login")
        .setView(view)
        .setPositiveButton("Login", (dialog, which) ->
            malStartLogin(user.getText().toString(), password.getText().toString()))
        .setNegativeButton("Cancel", (dialog, which) -> {
        })
        .show();
  }

  /** Execute la requete de login MAL (password grant). */
  private void malStartLogin(String user, String password) {
    Log.d(_TAG, "Login Mal -> " + user + ":" + password);
    ProgressDialog progressDialog = new ProgressDialog(activity);
    progressDialog.setMessage("Login to MyAnimeList..");
    progressDialog.show();
    AppExecutors.execute(() -> {
      try {
        AnimeApi.Http http = new AnimeApi.Http(
            "https://api.myanimelist.net/v2/auth/token");
        http.addHeader("X-MAL-Client-ID", Conf.MAL_CLIENT_ID);
        http.addHeader("Accept", "application/json");
        String body = "client_id=" + Conf.MAL_CLIENT_ID +
            "&grant_type=password" +
            "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8.name()) +
            "&username=" + URLEncoder.encode(user, StandardCharsets.UTF_8.name());
        http.setMethod("POST", body, "application/x-www-form-urlencoded");
        http.execute();
        String result = http.body.toString();
        JSONObject json = new JSONObject(result);
        json.put("user", user);
        Log.d(_TAG, "Login Mal -> RESULT = " + result);
        activity.runOnUiThread(() -> webView.evaluateJavascript(
            "_MAL.onlogin(" + json + ");", null));
      } catch (Exception e) {
        activity.runOnUiThread(() -> webView.evaluateJavascript(
            "_MAL.onlogin(null);", null));
        Log.d(_TAG, "Login Mal -> ERROR = " + e);
      }
      progressDialog.dismiss();
    });
  }

  /* ------------------------------------------------------------------
   * Cycle de vie
   * ------------------------------------------------------------------ */

  /** Sauvegarde / restaure l'etat (rotation, process death). */
  public void onSaveRestore(boolean isSave, Bundle bundle) {
    if (isSave) {
      webView.saveState(bundle);
      if (videoPlayer.getDuration() > 0) {
        bundle.putInt("VIDEO_CURRPOS", (int) videoPlayer.getCurrentPosition());
      } else {
        bundle.putInt("VIDEO_CURRPOS", 0);
      }
      bundle.putString("VIDEO_CURR_URL", videoStatCurrentUrl);
      bundle.putInt("VIDEO_SCALETYPE", videoStatScaleType);
      return;
    }
    webView.restoreState(bundle);
    int position = bundle.getInt("VIDEO_CURRPOS", 0);
    videoStatScaleType = bundle.getInt("VIDEO_SCALETYPE", 0);
    String url = bundle.getString("VIDEO_CURR_URL");
    Log.d(_TAG, "ONRESTORE -> " + position);
    initVideoView();
    videoViewSetScale(videoStatScaleType);
    if (url == null) {
      url = "";
    }
    if (url.equals("") || position <= 0) {
      return;
    }
    try {
      videoPlayer.seekTo(position);
      videoPlayer.play();
      videoView.setKeepScreenOn(true);
    } catch (Exception ignored) {
    }
  }

  /** Gere onStart (recreation du lecteur) / onPause (sauvegarde position). */
  public void onStartPause(boolean isStart) {
    if (isStart) {
      initVideoView();
      videoViewSetScale(videoStatScaleType);
      if (!videoStatCurrentUrl.equals("")) {
        videoSetSource(videoStatCurrentUrl);
        if (videoStatCurrentPosition > 0) {
          videoPlayer.seekTo(videoStatCurrentPosition);
          if (videoStatIsPlaying) {
            videoPlayer.play();
            videoView.setKeepScreenOn(true);
          }
        }
      }
      Log.d(_TAG, "ONSTART -> " + videoStatCurrentPosition);
    } else {
      if (videoPlayer.getDuration() > 0) {
        videoStatCurrentPosition = (int) videoPlayer.getCurrentPosition();
        videoStatIsPlaying = videoPlayer.isPlaying();
      } else {
        videoStatCurrentPosition = 0;
        videoStatIsPlaying = false;
      }
      Log.d(_TAG, "ONPAUSE -> " + videoStatCurrentPosition);
    }
  }

  /** px -> dp. */
  public int px2dp(float px) {
    return (int) (px /
        (activity.getResources().getDisplayMetrics().densityDpi / 160.0f));
  }

  /** Execute un runnable sur le thread UI en bloquant le thread appelant. */
  public void runOnUiThreadWait(Runnable runnable) {
    final Object lock = new Object();
    Runnable wrapper = () -> {
      synchronized (lock) {
        runnable.run();
        lock.notify();
      }
    };
    synchronized (lock) {
      activity.runOnUiThread(wrapper);
      try {
        lock.wait();
      } catch (InterruptedException ignored) {
      }
    }
  }

  /** Signale au JS que la page player embarquee a ete injectee. */
  public void sendVidpageLoaded() {
    Log.d(_TAG, "sendVidpageLoaded --> 1");
    AppExecutors.execute(() ->
        activity.runOnUiThread(() ->
            webView.evaluateJavascript("__VIDPAGELOADCB(1);", null)));
  }

  /** Plein ecran immersif (paysage) ou barres visibles (portrait). */
  public void setFullscreen(int orientation) {
    if (orientation == 0) {
      orientation = activity.getResources().getConfiguration().orientation;
    }
    if (orientation == 1) {
      activity.getWindow().clearFlags(1024);
      activity.getWindow().clearFlags(512);
      activity.getWindow().addFlags(2048);
      activity.getWindow().getDecorView().setSystemUiVisibility(257);
    } else {
      activity.getWindow().clearFlags(2048);
      activity.getWindow().addFlags(512);
      activity.getWindow().addFlags(1024);
      activity.getWindow().getDecorView().setSystemUiVisibility(3846);
      activity.getWindow().setFlags(512, 512);
    }
    if (webView != null) {
      updateInsets();
      AppExecutors.execute(() -> activity.runOnUiThread(() ->
          webView.evaluateJavascript(
              "try{__INSETCHANGE(_JSAPI.getSysHeight(false)," +
                  "_JSAPI.getSysHeight(true));}catch(e){}", null)));
    }
  }

  /** Memorise la taille video et la transmet au JS. */
  public void setVideoSize(int width, int height) {
    videoSizeWidth = width;
    videoSizeHeight = height;
    Log.d(_TAG, "VIDEO SIZE " + width + "x" + height);
    AppExecutors.execute(() -> activity.runOnUiThread(() ->
        webView.evaluateJavascript(
            "try{__VIDRESCB(" + width + "," + height + ");}catch(e){}", null)));
  }

  /** Calcule la hauteur des barres systeme (statut / navigation) en dp. */
  public void updateInsets() {
    sysheightStat = 0;
    sysheightNav = 0;
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsets insets = activity.getWindowManager()
          .getCurrentWindowMetrics().getWindowInsets();
      sysheightStat = px2dp(insets.getInsets(
          WindowInsets.Type.statusBars()).top);
      sysheightNav = px2dp(insets.getInsets(
          WindowInsets.Type.navigationBars()).bottom);
    } else {
      Resources resources = activity.getResources();
      int navId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
      if (navId > 0) {
        sysheightNav = px2dp(resources.getDimensionPixelSize(navId));
      }
      int statId = resources.getIdentifier("status_bar_height", "dimen", "android");
      if (statId > 0) {
        sysheightStat = px2dp(resources.getDimensionPixelSize(statId));
      }
    }
    Log.d(_TAG, "SYS-BAR Size: " + sysheightStat + " / " + sysheightNav);
  }

  @Override
  public void onPageFinished(WebView view, String url) {
    Log.d(_TAG, "ATVLOG-API --> " + url);
    splash.setVisibility(View.GONE);
    videoLayout.setVisibility(View.VISIBLE);
    webView.setVisibility(View.VISIBLE);
    activity.runOnUiThread(() -> webView.requestFocus());
    webViewReady = true;
  }

  /* ------------------------------------------------------------------
   * Recherche vocale
   * ------------------------------------------------------------------ */

  /** Ouvre la recherche vocale (demande la permission si besoin). */
  public void voiceSearchOpen() {
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED) {
      voiceRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
      voiceRecognizer.setRecognitionListener(voiceListener);
      Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      intent.putExtra("calling_package", activity.getPackageName());
      intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
      intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
      voiceSearchCallback("", VOICE_STATUS_STARTED);
      voiceRecognizer.startListening(intent);
    } else {
      ActivityCompat.requestPermissions(activity,
          new String[]{Manifest.permission.RECORD_AUDIO}, 500);
      voiceSearchCallback("", VOICE_STATUS_ERROR);
    }
  }

  /** Ferme la recherche vocale. */
  public void voiceSearchClose() {
    voiceSearchCallback("", VOICE_STATUS_ERROR);
    try {
      voiceRecognizer.stopListening();
      voiceRecognizer.cancel();
      voiceRecognizer.destroy();
    } catch (Exception ignored) {
    }
  }

  /** Envoie un evenement de recherche vocale au JS. */
  public void voiceSearchCallback(String text, int status) {
    Log.d(_TAG, "Voice Search (" + status + "): " + text);
    activity.runOnUiThread(() -> {
      try {
        JSONObject json = new JSONObject("{}");
        json.put("status", status);
        json.put("value", text);
        webView.evaluateJavascript("__VOICESEARCH(" + json.toString() + ");", null);
      } catch (Exception ignored) {
      }
    });
  }

  /** Listener de reconnaissance vocale -> evenements JS. */
  private class VoiceRecognitionListener implements RecognitionListener {
    @Override
    public void onReadyForSpeech(Bundle params) {
      voiceSearchCallback("", VOICE_STATUS_READY);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
      voiceSearchCallback(rmsdB + "", VOICE_STATUS_RMS);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
      voiceSearchCallback("", VOICE_STATUS_END);
    }

    @Override
    public void onError(int error) {
      voiceSearchCallback("", VOICE_STATUS_ERROR);
    }

    @Override
    public void onResults(Bundle results) {
      List<String> texts =
          results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      if (texts != null && !texts.isEmpty()) {
        voiceSearchCallback(texts.get(0), VOICE_STATUS_RESULT);
      } else {
        voiceSearchCallback("", VOICE_STATUS_ERROR);
      }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
      List<String> texts =
          partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      if (texts != null && !texts.isEmpty()) {
        voiceSearchCallback(texts.get(0), VOICE_STATUS_PARTIAL);
      }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
  }

  /* ------------------------------------------------------------------
   * WebChromeClient (dialogues JS -> dialogues natifs)
   * ------------------------------------------------------------------ */

  private class AnimeWebChromeClient extends WebChromeClient {
    @Override
    public android.graphics.Bitmap getDefaultVideoPoster() {
      /* Bitmap noir 1x1 pour eviter le damier par defaut */
      android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
          1, 1, android.graphics.Bitmap.Config.RGB_565);
      bitmap.eraseColor(android.graphics.Color.argb(255, 0, 0, 0));
      return bitmap;
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message,
        final JsResult result) {
      new AlertDialog.Builder(activity)
          .setMessage(message)
          .setNeutralButton(android.R.string.ok,
              (dialog, which) -> dialog.dismiss())
          .show();
      result.cancel();
      return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message,
        final JsResult result) {
      String title = "AnimeTV";
      CharSequence content = message;
      try {
        JSONObject json = new JSONObject(message);
        title = json.getString("title");
        content = json.getString("message");
        if (json.has("html")) {
          content = Html.fromHtml((String) content);
        }
      } catch (Exception ignored) {
      }
      new AlertDialog.Builder(activity)
          .setTitle(title)
          .setMessage(content)
          .setPositiveButton("Yes", (dialog, which) -> result.confirm())
          .setNegativeButton("No", (dialog, which) -> result.cancel())
          .setOnDismissListener(dialog -> result.cancel())
          .show();
      return true;
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message,
        String defaultValue, final JsPromptResult result) {
      return listPrompt(message, new PromptCallback() {
        @Override
        public void onCancel() {
          result.cancel();
        }

        @Override
        public void onResult(String value) {
          result.confirm(value);
        }
      });
    }
  }

  /* ------------------------------------------------------------------
   * shouldInterceptRequest : coeur du proxy / bloqueur
   * ------------------------------------------------------------------ */

  @Override
  public WebResourceResponse shouldInterceptRequest(WebView view,
      WebResourceRequest request) {
    Uri uri = request.getUrl();
    String url = uri.toString();
    String host = uri.getHost();
    if (host == null) {
      return aApi.badRequest;
    }
    String accept = request.getRequestHeaders().get("Accept");
    String path = uri.getPath();
    if (path == null) {
      path = "/";
    }

    /* Animekai : bloque les challenges Cloudflare hors frame principal */
    if (host.contains("animekai.bz") && !request.isForMainFrame()) {
      Log.d(_TAG, "ANIMEKAI REQUEST :: " + url);
      if (path.contains("manifest") || path.contains("/challenge-platform/")) {
        return aApi.badRequest;
      }
    }
    if (accept == null) {
      return aApi.badRequest;
    }

    /* Assets locaux : /__view/xxx -> assets/xxx */
    if (path.startsWith("/__view/")) {
      return aApi.assetsRequest(uri.getPath().substring(3));
    }

    /* Cache des sous-titres (derniere reponse /getSources) */
    if (path.startsWith("/__cache_subtitle")) {
      String value;
      if (path.startsWith("/__cache_subtitle/clear")) {
        cachedSourcesJson = "";
        value = "OK";
      } else {
        value = cachedSourcesJson;
      }
      return new WebResourceResponse("text/plain", "UTF-8",
          new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }

    if (!path.startsWith("/__proxy/")) {
      /* Injection du script megaup dans jquery (source AnimeKai) */
      if (url.contains("/jquery.min.js") && Conf.SOURCE_DOMAIN == 1) {
        Log.d(_TAG, "ANIMEKAI REQUEST JQUERY REQUEST " + url);
        return AnimeApi.defaultRequest(request,
            aApi.assetsString("inject/megaup.js"), "inject-html", null);
      }

      /* Embeds YouTube : page injectee maison */
      if (url.startsWith("https://www.youtube.com/embed/") ||
          url.startsWith("https://www.youtube-nocookie.com/embed/")) {
        return AnimeApi.defaultRequest(request,
            aApi.assetsString("inject/yt.html"), "inject-html", null);
      }
      if (host.contains("youtube.com") || host.contains("youtube-nocookie.com") ||
          host.contains("googlevideo.com")) {
        if (accept.contains("text/css") || accept.contains("image/")) {
          return aApi.badRequest;
        }
        if (url.endsWith("/endscreen.js") || url.endsWith("/captions.js") ||
            url.endsWith("/embed.js") || url.contains("/log_event?alt=json") ||
            url.contains(".com/ptracking") || url.contains(".com/api/stats/")) {
          return aApi.badRequest;
        }
        return super.shouldInterceptRequest(view, request);
      }

      /* Page de redirection locale */
      if (path.equals("/__REDIRECT")) {
        return aApi.assetsRequest("inject/redirect.html");
      }

      /* Domaines sources : requete via le moteur HTTP (cache),
       * avec remplacement de domaine eventuel */
      if (Conf.isSourceDomain(host)) {
        WebResourceResponse response;
        if (Conf.SOURCE_DOMAIN_USED.isEmpty()) {
          response = AnimeApi.defaultRequest(request, null, null, null);
        } else {
          response = AnimeApi.defaultRequest(request, null, null,
              Conf.SOURCE_DOMAIN_USED);
        }
        return response != null ? response : super.shouldInterceptRequest(view, request);
      }

      /* MegaCloud / RapidCloud : bloque le CSS, cache /getSources */
      if (host.contains("megacloud.blog") || host.contains("rapid-cloud.co")) {
        if (accept.startsWith("text/css")) {
          return aApi.badRequest;
        }
        boolean isGetSources = path.contains("/getSources");
        WebResourceResponse response =
            AnimeApi.defaultRequest(request, null, null, null);
        if (isGetSources) {
          String cacheValue = "";
          try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = response.getData().read(chunk)) != -1) {
              buffer.write(chunk, 0, n);
            }
            cacheValue = buffer.toString("UTF-8");
            cachedSourcesJson = cacheValue;
          } catch (Exception ignored) {
          }
          Log.d(_TAG, "CACHE VALUE =" + cacheValue);
          response.setData(new ByteArrayInputStream(
              cacheValue.getBytes(StandardCharsets.UTF_8)));
        }
        return response;
      }

      /* API Animeflix : proxy avec Origin/Referer forces */
      if (host.equals("api.animeflix.dev")) {
        try {
          AnimeApi.Http http = new AnimeApi.Http(url);
          http.addHeader("Referer", "https://api.animeflix.dev");
          http.addHeader("Origin", "https://api.animeflix.dev");
          for (Map.Entry<String, String> header :
              request.getRequestHeaders().entrySet()) {
            String key = header.getKey();
            if (!(key.equalsIgnoreCase("origin") ||
                key.equalsIgnoreCase("referer") ||
                key.equalsIgnoreCase("X-Org-Prox") ||
                key.equalsIgnoreCase("X-Ref-Prox"))) {
              http.addHeader(key, header.getValue());
            }
          }
          http.execute();
          return new WebResourceResponse(http.ctype[0], http.ctype[1],
              new ByteArrayInputStream(http.body.toByteArray()));
        } catch (Exception e) {
          Log.e(_TAG, "AFLIX-API ERR =" + url, e);
          return super.shouldInterceptRequest(view, request);
        }
      }

      /* Hebergeurs krussdomi/megaup/megaf : injection du player +
       * hook des cles de dechiffrement */
      if (host.contains("krussdomi.com") || host.contains("megaup.nl") ||
          host.contains("megaf.cc")) {
        boolean isEmbedJs =
            url.startsWith("https://" + host + "/assets/mcloud/min/embed.js");
        if (!accept.startsWith("text/html") && !isEmbedJs) {
          if (accept.startsWith("text/css") || accept.startsWith("image/")) {
            Log.d(_TAG, "BLOCK CSS/IMG = " + url);
            return aApi.badRequest;
          }
        }
        Log.d(_TAG, "VIEW PLAYER REQ = " + url);
        try {
          AnimeApi.Http http = new AnimeApi.Http(url);
          for (Map.Entry<String, String> header :
              request.getRequestHeaders().entrySet()) {
            http.addHeader(header.getKey(), header.getValue());
          }
          http.execute();
          int responseCode = http.getResponseCode();
          if (responseCode == 200) {
            if (isEmbedJs) {
              /* Patch function Q(){ -> capture des cles dans __QKEYS */
              AnimeApi.injectQKeyHook(http.body);
            } else if (accept.startsWith("text/html")) {
              /* Injection du player maison dans la page embed */
              try {
                http.body.write(playerInjectString.getBytes());
                sendVidpageLoaded();
              } catch (Exception ignored) {
              }
            }
            return new WebResourceResponse(http.ctype[0], http.ctype[1],
                new ByteArrayInputStream(http.body.toByteArray()));
          }
        } catch (Exception ignored) {
        }
        return aApi.badRequest;
      }

      /* mp4upload : convertit video.mp4 en reponse sources JSON */
      String sourcesJson = "{}";
      if (host.contains("mp4upload.com") && url.endsWith("video.mp4")) {
        Log.d(_TAG, "GOT-MASTER-M3U8 mp4upload = " + url);
        sourcesJson = buildSourcesJson(url);
        sendM3U8Callback(sourcesJson);
      } else {
        /* Google Fonts : via cache HTTP sauf en mode progressif */
        if (host.contains("fonts.gstatic.com") || host.contains("fonts.googleapis.com")) {
          if (Conf.PROGRESSIVE_CACHE) {
            return super.shouldInterceptRequest(view, request);
          }
          return AnimeApi.defaultRequest(request, null, null, null);
        }

        /* Blacklist publicite / tracking */
        if (host.contains("rosebudemphasizelesson.com") ||
            host.contains("simplewebanalysis.com") ||
            host.contains("addthis.com") ||
            host.contains("amung.us") ||
            host.contains("www.googletagmanager.com") ||
            host.contains("megastatics.com") ||
            host.contains("ontosocietyweary.com") ||
            host.contains("doubleclick.net") ||
            host.contains("ggpht.com") ||
            host.contains("play.google.com") ||
            host.contains("www.google.com") ||
            host.contains("googleapis.com") ||
            host.contains("precedelaxative.com")) {
          return aApi.badRequest;
        }

        /* master.m3u8 (sources 3/4) : envoie l'URL au JS */
        if ((Conf.SOURCE_DOMAIN == 3 || Conf.SOURCE_DOMAIN == 4) &&
            path.endsWith("/master.m3u8")) {
          Log.d(_TAG, "GOT-MASTER-M3U8 = " + url);
          sourcesJson = buildSourcesJson(url);
          sendM3U8Callback(sourcesJson);
          return aApi.badRequest;
        }
      }

      /* Defaut : mode progressif ou POST -> direct, sinon via cache HTTP */
      if (Conf.PROGRESSIVE_CACHE || request.getMethod().equalsIgnoreCase("POST")) {
        return super.shouldInterceptRequest(view, request);
      }
      return AnimeApi.defaultRequest(request, null, null, null);
    }

    /* ==================================================================
     * PROXY /__proxy/<url cible>
     * Headers speciaux :
     *   X-Org-Prox   : Origin force
     *   X-Ref-Prox   : Referer force
     *   X-NoH-Proxy  : n'envoie que Origin/Referer/User-Agent
     *   X-Stream-Prox: mode "stream" (Origin/Referer deduits du domaine,
     *                  X-Requested-With, User-Agent force)
     *   X-Dash-Prox  : variante sans Referer/X-Requested-With
     *   X-Post-Prox  : force POST avec content-type custom
     *   X-Fixdomain-Prox : desactive le remplacement de domaine
     *   Post-Body    : corps POST (url-encode)
     * ================================================================== */
    try {
      String streamProx = request.getRequestHeaders().get("X-Stream-Prox");
      String targetUrl = url.replace("https://" + host + "/__proxy/", "");

      /* Remplacement de domaine eventuel */
      String fixDomain = request.getRequestHeaders().get("X-Fixdomain-Prox");
      if (!Conf.SOURCE_DOMAIN_USED.isEmpty() && fixDomain == null) {
        targetUrl = targetUrl.replace("://" + host, "://" + Conf.SOURCE_DOMAIN_USED);
        Log.d(_TAG, "CH-PROXY: " + targetUrl);
      }

      String method = request.getMethod();
      boolean isPost = method.equals("POST") || method.equals("PUT");
      String postBody = uri.getQuery();
      if (postBody == null) {
        postBody = "";
      }
      String postBodyHeader = request.getRequestHeaders().get("Post-Body");
      if (postBodyHeader != null) {
        postBodyHeader = URLDecoder.decode(postBodyHeader, "UTF-8");
      }
      String postProx = request.getRequestHeaders().get("X-Post-Prox");
      boolean isPostProx = postProx != null;
      if (isPostProx) {
        isPost = true;
      } else {
        postProx = method;
      }
      method = isPostProx ? "POST" : method;
      String actualMethod = isPostProx ? "POST" : method;

      boolean hasPostBodyHeader = false;
      if (isPost) {
        if (postBodyHeader != null) {
          Log.d(_TAG, "PROXY-" + actualMethod + " POSTBODY = " + targetUrl +
              " >> " + postBodyHeader);
          hasPostBodyHeader = true;
        } else {
          if (targetUrl.contains("?")) {
            targetUrl = targetUrl.substring(0, targetUrl.indexOf("?"));
          }
          if (isPostProx) {
            postBody = URLDecoder.decode(postBody, "UTF-8");
          }
          Log.d(_TAG, "PROXY-" + actualMethod + ": " + targetUrl + " >> " + postBody);
        }
      } else {
        Log.d(_TAG, "PROXY-" + actualMethod + " = " + targetUrl);
      }

      String orgProx = request.getRequestHeaders().get("X-Org-Prox");
      String refProx = request.getRequestHeaders().get("X-Ref-Prox");
      String noHProxy = request.getRequestHeaders().get("X-NoH-Proxy");

      AnimeApi.Http http = new AnimeApi.Http(targetUrl);
      if (streamProx != null) {
        /* Mode stream : Origin/Referer deduits du domaine cible */
        String[] parts = streamProx.split("\\.");
        String domain = parts[parts.length - 2] + "." + parts[parts.length - 1];
        http.addHeader("Origin", "https://" + domain);
        boolean isDash = request.getRequestHeaders().get("X-Dash-Prox") != null;
        if (!isDash) {
          http.addHeader("Referer", "https://" + domain + "/");
          http.addHeader("X-Requested-With", "XMLHttpRequest");
        }
        http.addHeader("User-Agent", Conf.USER_AGENT);
      } else if (noHProxy != null) {
        /* Mode "no headers" : seulement Origin/Referer/User-Agent */
        if (orgProx != null) {
          http.addHeader("Origin", orgProx);
        }
        if (refProx != null) {
          http.addHeader("Referer", orgProx);
        }
        http.addHeader("User-Agent", Conf.USER_AGENT);
      } else {
        /* Mode normal : recopie les headers en substituant
         * Origin/Referer par X-Org-Prox / X-Ref-Prox */
        for (Map.Entry<String, String> header :
            request.getRequestHeaders().entrySet()) {
          String key = header.getKey();
          if (isPostProx && key.equalsIgnoreCase("content-type")) {
            continue;
          }
          if (key.equalsIgnoreCase("origin") && orgProx != null) {
            http.addHeader("Origin", orgProx);
            continue;
          }
          if (key.equalsIgnoreCase("referer") && refProx != null) {
            http.addHeader("Referer", refProx);
            continue;
          }
          if (key.equalsIgnoreCase("X-Org-Prox") ||
              key.equalsIgnoreCase("X-Ref-Prox") ||
              key.equalsIgnoreCase("X-Post-Prox") ||
              key.equalsIgnoreCase("X-Stream-Prox") ||
              key.equalsIgnoreCase("X-NoH-Proxy") ||
              key.equalsIgnoreCase("X-Fixdomain-Prox") ||
              key.equalsIgnoreCase("X-Dash-Prox") ||
              key.equalsIgnoreCase("Post-Body")) {
            continue;
          }
          http.addHeader(key, header.getValue());
        }
      }

      if (isPost) {
        if (hasPostBodyHeader) {
          http.setMethod(actualMethod, postBodyHeader,
              request.getRequestHeaders().get("Content-Type"));
        } else {
          http.setMethod(actualMethod, postBody,
              isPostProx ? postProx : "application/x-www-form-urlencoded");
        }
      } else if (actualMethod.equalsIgnoreCase("DELETE")) {
        http.setMethod("DELETE", null, null);
      }
      http.execute();
      return new WebResourceResponse(http.ctype[0], http.ctype[1],
          new ByteArrayInputStream(http.body.toByteArray()));
    } catch (Exception e) {
      return aApi.badRequest;
    }
  }

  /** Construit le JSON {"result":{"sources":[{"file":url}]}}. */
  private String buildSourcesJson(String url) {
    try {
      JSONObject json = new JSONObject("{\"result\":{\"sources\":[{}]}}");
      json.getJSONObject("result").getJSONArray("sources").getJSONObject(0)
          .put("file", url);
      return json.toString();
    } catch (Exception e) {
      return "{}";
    }
  }

  /** Envoie l'URL du flux au JS (__M3U8CB). */
  private void sendM3U8Callback(String json) {
    Log.d(_TAG, "sendM3U8Req = " + json);
    AppExecutors.execute(() ->
        activity.runOnUiThread(() ->
            webView.evaluateJavascript("__M3U8CB(" + json + ");", null)));
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    /* Seules les navigations vers le domaine courant sont autorisees */
    return !request.getUrl().toString()
        .startsWith("https://" + Conf.getDomain() + "/");
  }

  /* ------------------------------------------------------------------
   * Interface Javascript _JSAPI
   * ------------------------------------------------------------------ */

  /**
   * API native exposee a la WebView sous le nom {@code _JSAPI}.
   *
   * <p>Reconstruite depuis la classe obfusquee {@code f3.d0}.</p>
   */
  public class JSViewApi {
    /* Valeurs mises en cache par les runnables UI */
    public int videoLastBufferPercent = 0;
    public boolean videoIsPlaying = false;
    public int videoDuration = 0;
    public int videoPosition = 0;

    /* ---------------- Crypto ---------------- */

    @JavascriptInterface
    public String aesDec(String cipherText, String key, String ivHex) {
      int length = ivHex.length();
      byte[] iv = new byte[length / 2];
      for (int i = 0; i < length; i += 2) {
        iv[i / 2] = (byte) (Character.digit(ivHex.charAt(i + 1), 16) +
            (Character.digit(ivHex.charAt(i), 16) << 4));
      }
      try {
        return decryptAES(Base64.decode(cipherText, Base64.DEFAULT),
            key.getBytes(), iv);
      } catch (Exception e) {
        return "";
      }
    }

    @JavascriptInterface
    public String vidEncode(String vid, String key1, String key2) {
      try {
        return vidIdEncode(vid, key1, key2);
      } catch (Exception e) {
        return "";
      }
    }

    @JavascriptInterface
    public String sha1sum(String value) {
      try {
        byte[] digest = MessageDigest.getInstance("SHA-1")
            .digest(value.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
          sb.append(String.format("%02x", b));
        }
        return sb.toString();
      } catch (Exception e) {
        return "";
      }
    }

    /* ---------------- Application ---------------- */

    @JavascriptInterface
    public void appQuit() {
      activity.finish();
    }

    @JavascriptInterface
    public String getVersion(int type) {
      if (type == 0) {
        return "6.6.7-Nightly";
      }
      if (type == 2) {
        return "666";
      }
      return BUILD_VERSION;
    }

    @JavascriptInterface
    public void checkUpdate() {
      aApi.updateServerVar(true);
    }

    @JavascriptInterface
    public boolean installApk(String url, boolean isNightly) {
      return aApi.startUpdateApk(url, isNightly);
    }

    @JavascriptInterface
    public boolean isOnUpdate() {
      return aApi.updateIsInProgress;
    }

    @JavascriptInterface
    public void goToUrl(String url) {
      runOnUiThreadWait(() -> webView.loadUrl(url));
    }

    @JavascriptInterface
    public void reloadHome() {
      runOnUiThreadWait(() ->
          webView.loadUrl("https://" + Conf.getDomain() + "/__view/main.html"));
    }

    @JavascriptInterface
    public void openIntentUri(String uri) {
      activity.runOnUiThread(() -> activity.startActivity(
          new Intent(Intent.ACTION_VIEW, Uri.parse(uri))));
    }

    @JavascriptInterface
    public void showToast(String text) {
      activity.runOnUiThread(() ->
          Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public boolean getHaveTouchscreen() {
      return activity.getPackageManager()
          .hasSystemFeature("android.hardware.touchscreen");
    }

    /* ---------------- Arguments d'appel (intent) ---------------- */

    @JavascriptInterface
    public String getArg(String name) {
      switch (name) {
        case "sd":
          return MainActivity.ARG_SD != null ? MainActivity.ARG_SD : "";
        case "pos":
          return MainActivity.ARG_POS != null ? MainActivity.ARG_POS : "";
        case "tip":
          return MainActivity.ARG_TIP != null ? MainActivity.ARG_TIP : "";
        case "url":
          return MainActivity.ARG_URL != null ? MainActivity.ARG_URL : "";
        default:
          return "";
      }
    }

    @JavascriptInterface
    public void clearArg() {
      MainActivity.ARG_URL = null;
      MainActivity.ARG_TIP = null;
      MainActivity.ARG_POS = null;
      MainActivity.ARG_SD = null;
    }

    /* ---------------- Domaines / reseau ---------------- */

    @JavascriptInterface
    public String dns() {
      return Conf.getDomain();
    }

    @JavascriptInterface
    public String dnsver() {
      return Conf.SERVER_VER;
    }

    @JavascriptInterface
    public String flix_dns() {
      return Conf.SOURCE_DOMAIN5_API;
    }

    @JavascriptInterface
    public int getSd() {
      return Conf.SOURCE_DOMAIN;
    }

    @JavascriptInterface
    public void setSd(int source) {
      if (source >= 1 && source < Conf.SOURCE_DOMAINS.length) {
        android.content.SharedPreferences.Editor editor = aApi.pref.edit();
        editor.putInt("source-domain", source);
        editor.apply();
        Conf.updateSource(source);
      }
      activity.runOnUiThread(() -> initVideoView());
    }

    @JavascriptInterface
    public String getSdomain() {
      return Conf.SOURCE_DOMAIN_USED;
    }

    @JavascriptInterface
    public void setSdomain(String domain) {
      Conf.SOURCE_DOMAIN_USED = domain;
      Log.d(_TAG, "Change Source Domain: " + domain);
    }

    @JavascriptInterface
    public int getStreamType() {
      return Conf.STREAM_TYPE;
    }

    @JavascriptInterface
    public void setStreamType(int type, int clean) {
      Log.d(_TAG, "[X] setStreamType = " + type + " / clean=" + clean);
      Conf.STREAM_TYPE = type;
    }

    @JavascriptInterface
    public void setStreamServer(int mirror, int clean) {
      Log.d(_TAG, "[X] setStreamServer = " + mirror + " / clean=" + clean);
    }

    @JavascriptInterface
    public void setDOH(boolean value) {
      Conf.USE_DOH = value;
    }

    @JavascriptInterface
    public void setHttpClient(int value) {
      Conf.HTTP_CLIENT = value;
      AnimeApi.initHttpEngine(activity);
    }

    @JavascriptInterface
    public void setProgCache(boolean value) {
      Conf.PROGRESSIVE_CACHE = value;
    }

    /* ---------------- Cache ---------------- */

    @JavascriptInterface
    public int getCacheSz() {
      return Conf.CACHE_SIZE_MB;
    }

    @JavascriptInterface
    public void setCacheSz(int size) {
      if (size < 5 || size > 150) {
        size = 100;
      }
      Conf.CACHE_SIZE_MB = size;
      android.content.SharedPreferences.Editor editor = aApi.pref.edit();
      editor.putInt("cache-size", size);
      editor.apply();
      AnimeApi.initHttpEngine(activity);
    }

    @JavascriptInterface
    public void clearCache() {
      activity.runOnUiThread(() -> webView.clearCache(true));
      AnimeApi.reqClearCache = true;
      AnimeApi.initHttpEngine(activity);
    }

    /* ---------------- Stockage JS (SharedPreferences) ---------------- */

    @JavascriptInterface
    public void storeSet(String key, String value) {
      android.content.SharedPreferences.Editor editor = aApi.pref.edit();
      editor.putString("viewstorage_" + key, value);
      editor.apply();
    }

    @JavascriptInterface
    public String storeGet(String key, String def) {
      return aApi.pref.getString("viewstorage_" + key, def);
    }

    @JavascriptInterface
    public void storeDel(String key) {
      android.content.SharedPreferences.Editor editor = aApi.pref.edit();
      editor.remove("viewstorage_" + key);
      editor.apply();
    }

    /* ---------------- Profils ---------------- */

    @JavascriptInterface
    public String profileGetPrefix() {
      return profile_prefix;
    }

    @JavascriptInterface
    public void profileSetPrefix(String value) {
      profile_prefix = value;
    }

    @JavascriptInterface
    public int profileGetSel() {
      return profile_sel;
    }

    @JavascriptInterface
    public void profileSetSel(int value) {
      profile_sel = value;
    }

    /* ---------------- Systeme ---------------- */

    @JavascriptInterface
    public int getSysHeight(boolean nav) {
      updateInsets();
      return nav ? sysheightNav : sysheightStat;
    }

    @JavascriptInterface
    public int setBrightness(int delta) {
      sysBrightness += delta;
      if (sysBrightness < 0) {
        sysBrightness = 0;
      } else if (sysBrightness > 255) {
        sysBrightness = 255;
      }
      activity.runOnUiThread(() -> {
        if (delta != 0) {
          android.view.WindowManager.LayoutParams params =
              activity.getWindow().getAttributes();
          params.screenBrightness = sysBrightness / 255.0f;
          activity.getWindow().setAttributes(params);
        }
      });
      return sysBrightness;
    }

    @JavascriptInterface
    public int setVolume(int delta) {
      int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      float maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      float percent = (volume * 100.0f) / maxVolume;
      if (delta != 0) {
        percent += delta;
        if (percent < 0.0f) {
          percent = 0.0f;
        } else if (percent > 100.0f) {
          percent = 100.0f;
        }
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
            (int) ((maxVolume * percent) / 100.0f), 0);
      }
      return (int) percent;
    }

    @JavascriptInterface
    public void setLandscape(boolean landscape) {
      activity.runOnUiThread(() -> activity.setRequestedOrientation(
          landscape ? 6 : 2)); /* SENSOR_LANDSCAPE / USER */
    }

    @JavascriptInterface
    public void showIme(boolean show) {
      Log.d(_TAG, "SHOW IME = " + show);
      activity.runOnUiThread(() -> {
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager)
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (show) {
          imm.showSoftInput(webView, 0);
        } else {
          imm.hideSoftInputFromWindow(webView.getWindowToken(), 0);
        }
      });
    }

    @JavascriptInterface
    public void playClick() {
      runOnUiThreadWait(() -> audioManager.playSoundEffect(
          AudioManager.FX_KEY_CLICK, 0.5f));
    }

    /* ---------------- Dialogues ---------------- */

    @JavascriptInterface
    public void asyncPrompt(String message, int callbackNum) {
      activity.runOnUiThread(() -> listPrompt(message, new PromptCallback() {
        @Override
        public void onCancel() {
          webView.evaluateJavascript(
              "_API.asyncPrompCb(" + callbackNum + ",null);", null);
        }

        @Override
        public void onResult(String result) {
          /* Le resultat est du JSON (index, tableau ou objet) insere brut */
          webView.evaluateJavascript(
              "_API.asyncPrompCb(" + callbackNum + "," + result + ");", null);
        }
      }));
    }

    @JavascriptInterface
    public void malLogin() {
      runOnUiThreadWait(() -> malLoginDialog());
    }

    /* ---------------- MyAnimeList ---------------- */

    /* (voir malLogin ci-dessus) */

    /* ---------------- Watch Next ---------------- */

    @JavascriptInterface
    public void playNextClear() {
      pnUpdated = false;
      AppExecutors.execute(() -> AnimeProvider.clearPlayNext(activity));
    }

    @JavascriptInterface
    public void playNextMeta(String title, String desc, String poster,
        String uri, String tip, int sd) {
      pnUpdated = false;
      pnTitle = title;
      pnDesc = desc;
      pnPoster = poster;
      pnUri = uri;
      pnTip = tip;
      pnSd = sd;
      Log.d(_TAG, "Update Meta (" + uri + "; " + title + "; " + desc + "; " +
          tip + "; " + sd + "; Poster=" + poster + ")");
    }

    @JavascriptInterface
    public void playNextPos(int position, int duration) {
      pnUpdated = true;
      pnPos = position;
      pnDuration = duration;
    }

    @JavascriptInterface
    public void playNextRegister() {
      AppExecutors.execute(() -> updatePlayNext());
    }

    /* ---------------- Recherche vocale ---------------- */

    @JavascriptInterface
    public boolean haveMic(boolean checkSpeech) {
      if (SpeechRecognizer.isRecognitionAvailable(activity)) {
        Log.d(_TAG, "Speech available");
        return true;
      }
      Log.d(_TAG, "Speech not available");
      return false;
    }

    @JavascriptInterface
    public void voiceSearch() {
      activity.runOnUiThread(() -> voiceSearchOpen());
    }

    @JavascriptInterface
    public void voiceClose() {
      activity.runOnUiThread(() -> voiceSearchClose());
    }

    /* ---------------- Lecteur video ---------------- */

    @JavascriptInterface
    public void videoSetUrl(String url) {
      Log.d(_TAG, "Video Set URL = " + url);
      videoIsPlaying = false;
      videoDuration = 0;
      videoPosition = 0;
      videoStatCurrentUrl = url;
      activity.runOnUiThread(() -> {
        if (!url.equals("")) {
          videoSetSource(url);
          videoPlayer.play();
          videoView.setKeepScreenOn(true);
        } else {
          videoPlayer.stop();
          videoPlayer.clearMediaItems();
          videoSetSource("");
        }
      });
    }

    @JavascriptInterface
    public void videoPlay(boolean play) {
      activity.runOnUiThread(() -> {
        if (play) {
          videoPlayer.play();
          videoView.setKeepScreenOn(true);
        } else {
          videoPlayer.pause();
          videoView.setKeepScreenOn(false);
        }
      });
    }

    @JavascriptInterface
    public boolean videoIsPlaying() {
      runOnUiThreadWait(() -> {
        try {
          videoIsPlaying = videoPlayer.isPlaying();
        } catch (Exception ignored) {
        }
      });
      return videoIsPlaying;
    }

    @JavascriptInterface
    public int videoGetDuration() {
      runOnUiThreadWait(() -> {
        try {
          videoDuration = (int) Math.floor(videoPlayer.getDuration());
        } catch (Exception ignored) {
        }
      });
      return videoDuration;
    }

    @JavascriptInterface
    public int videoGetPosition() {
      runOnUiThreadWait(() -> {
        try {
          videoPosition = (int) Math.ceil(videoPlayer.getCurrentPosition());
        } catch (Exception ignored) {
        }
      });
      return videoPosition;
    }

    @JavascriptInterface
    public void videoSetPosition(int position) {
      activity.runOnUiThread(() -> videoPlayer.seekTo(position));
    }

    @JavascriptInterface
    public int videoBufferPercent() {
      if (videoStatCurrentUrl.equals("")) {
        videoLastBufferPercent = 0;
        return -1;
      }
      activity.runOnUiThread(() -> {
        try {
          videoLastBufferPercent = videoPlayer.getBufferedPercentage();
        } catch (Exception ignored) {
        }
      });
      return videoLastBufferPercent;
    }

    @JavascriptInterface
    public void videoSetScale(int type) {
      activity.runOnUiThread(() -> videoViewSetScale(type));
    }

    @JavascriptInterface
    public void videoSetSpeed(float speed) {
      activity.runOnUiThread(() -> {
        try {
          if (videoSupportSpeed()) {
            videoPlayer.setPlaybackSpeed(speed);
          }
        } catch (Exception ignored) {
        }
      });
    }

    @JavascriptInterface
    public boolean videoSupportSpeed() {
      return Build.VERSION.SDK_INT > 26;
    }

    @JavascriptInterface
    public void videoTracks() {
      Log.d(_TAG, "Tracks = " + videoPlayer.getCurrentTracks());
    }

    @JavascriptInterface
    public void videoAudioTrack(String language, boolean updatenow) {
      videoAudioLanguage = language;
      if (updatenow) {
        activity.runOnUiThread(() -> initVideoTracks());
      }
    }

    @JavascriptInterface
    public void videoTrackQuality(int quality, boolean updatenow) {
      videoSelectedQuality = quality;
      if (updatenow) {
        activity.runOnUiThread(() -> initVideoTracks());
      }
    }

    /* ---------------- MediaSession ---------------- */

    @JavascriptInterface
    public void videoHaveNP(boolean haveNext, boolean havePrev) {
      MainActivity mainActivity = (MainActivity) activity;
      mainActivity._metaHaveNext = haveNext;
      mainActivity._metaHavePrev = havePrev;
      Log.d("ATVLOG_MEDIA", "mediaSetPrevNext=" + haveNext + " / " + havePrev);
      mainActivity.updateMediaState();
    }

    @JavascriptInterface
    public void videoSetMeta(String title, String artist, String poster) {
      MainActivity mainActivity = (MainActivity) activity;
      mainActivity._metaTitle = title;
      mainActivity._metaArtist = artist;
      mainActivity._metaUrl = poster;
      Log.d("ATVLOG_MEDIA", "mediaSetMeta=" + title);
      mainActivity.updateMediaMeta();
    }
  }

  /* ------------------------------------------------------------------
   * Divers
   * ------------------------------------------------------------------ */

  /** Publie le "Watch Next" si la progression le justifie. */
  public void updatePlayNext() {
    if (pnUpdated) {
      pnUpdated = false;
      if (pnPos > 2 && (pnDuration - pnPos) > 5) {
        AnimeProvider.setPlayNext(activity, pnTitle, pnDesc, pnPoster, pnUri,
            pnTip, pnPos, pnDuration, pnSd);
      }
    }
  }

  /** Envoie l'evenement __ARGUPDATE() au JS. */
  public void updateArgs() {
    activity.runOnUiThread(() ->
        webView.evaluateJavascript("__ARGUPDATE();", null));
  }

  /** Libere le lecteur, la WebView et la reconnaissance vocale. */
  public void release() {
    try {
      if (voiceRecognizer != null) {
        voiceRecognizer.destroy();
        voiceRecognizer = null;
      }
    } catch (Exception ignored) {
    }
    try {
      if (videoPlayer != null) {
        videoPlayer.release();
        videoPlayer = null;
      }
    } catch (Exception ignored) {
    }
    try {
      webView.removeJavascriptInterface("_JSAPI");
      webView.destroy();
    } catch (Exception ignored) {
    }
  }
}
