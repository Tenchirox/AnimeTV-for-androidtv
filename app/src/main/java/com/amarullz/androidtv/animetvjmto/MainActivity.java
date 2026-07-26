package com.amarullz.androidtv.animetvjmto;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;

import androidx.fragment.app.FragmentActivity;
import androidx.media.session.MediaButtonReceiver;

/**
 * Activite principale : contient la WebView (via {@link AnimeView}), gere
 * les touches telecommande -> evenements clavier JS, et la MediaSession
 * (touches media Bluetooth / telecommande).
 *
 * <p>Reconstruite depuis {@code com.amarullz.androidtv.animetvjmto.MainActivity}
 * et {@code f3.f0} (callback MediaSession) de l'APK 6.6.7.</p>
 */
public class MainActivity extends FragmentActivity {
  private static final String _TAG = "ATVLOG";
  private static final String _TAG_MEDIA = "ATVLOG_MEDIA";

  /* Keycodes OEM (telecommandes de certains boitiers TV) sans nom standard */
  private static final int KEYCODE_OEM_NEXT_1 = 272;
  private static final int KEYCODE_OEM_PREV_1 = 273;
  private static final int KEYCODE_OEM_NEXT_2 = 274;
  private static final int KEYCODE_OEM_PREV_2 = 275;

  /* Arguments passes par intent (ouverture d'un anime depuis l'exterieur) */
  public static String ARG_URL;
  public static String ARG_TIP;
  public static String ARG_POS;
  public static String ARG_SD;

  public AnimeView aView;
  public MediaSession mSession = null;

  /* Anti-rebond play/pause (ms) */
  public long lastPlayPause = 0;

  /* Etat media courant (pour la MediaSession) */
  public long _metaPosition = 0;
  public int _metaState = 0;
  public boolean _metaHaveNext = false;
  public boolean _metaHavePrev = false;
  public float _metaSpeed = 1.0f;
  public long _metaDuration = -1;
  public String _metaTitle = "";
  public String _metaArtist = "";
  public String _metaUrl = "";

  /* ------------------------------------------------------------------
   * Touches -> evenements clavier JS
   * ------------------------------------------------------------------ */

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (sendKeyEvent(event.getKeyCode(), event.getAction())) {
      /* Touche consommee par le JS */
      return false;
    }
    return super.dispatchKeyEvent(event);
  }

  /**
   * Traduit les keycodes Android en keycodes web envoyes a
   * {@code window._KEYEV(code)} dans la WebView.
   *
   * @return true si la touche a ete traitee
   */
  public boolean sendKeyEvent(int keyCode, int action) {
    int webCode;
    boolean send = action == KeyEvent.ACTION_DOWN;

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      /* BACK : envoye au relachement uniquement */
      send = action == KeyEvent.ACTION_UP;
      webCode = 27;
    } else if (keyCode == KeyEvent.KEYCODE_ENTER) {
      webCode = send ? 13 : 1013;
      send = true;
    } else if (keyCode == KeyEvent.KEYCODE_MENU) {
      webCode = 93;
    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
      webCode = 402;
    } else if (keyCode == KeyEvent.KEYCODE_F1) {
      webCode = 402;
    } else if (keyCode == KeyEvent.KEYCODE_F5) {
      /* PROG_BLUE / F5 : recharge l'accueil */
      if (send) {
        WebView webView = aView.webView;
        webView.clearCache(true);
        webView.loadUrl("https://" + Conf.getDomain() + "/__view/main.html");
      }
      webCode = 0;
    } else if (keyCode == KeyEvent.KEYCODE_F10 || keyCode == KeyEvent.KEYCODE_PROG_RED) {
      webCode = 93;
    } else if (keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
      /* PROG_BLUE : recharge l'accueil */
      if (send) {
        WebView webView = aView.webView;
        webView.clearCache(true);
        webView.loadUrl("https://" + Conf.getDomain() + "/__view/main.html");
      }
      webCode = 0;
    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
      webCode = 403;
    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
      webCode = 401;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
      webCode = 33;
    } else if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
      webCode = 34;
    } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
      send = action == KeyEvent.ACTION_UP;
      webCode = 27;
    } else if (keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
      webCode = 8;
    } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
        keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
      webCode = 402;
    } else {
      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_UP:
          webCode = 38;
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          webCode = 40;
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          webCode = 37;
          break;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          webCode = 39;
          break;
        case KeyEvent.KEYCODE_DPAD_CENTER:
          webCode = send ? 13 : 1013;
          send = true;
          break;
        default:
          switch (keyCode) {
            case KeyEvent.KEYCODE_INFO:
              webCode = 93;
              break;
            case KeyEvent.KEYCODE_CHANNEL_UP:
              webCode = 33;
              break;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
              webCode = 34;
              break;
            default:
              switch (keyCode) {
                case KEYCODE_OEM_NEXT_1:
                case KEYCODE_OEM_NEXT_2:
                  webCode = 403;
                  break;
                case KEYCODE_OEM_PREV_1:
                case KEYCODE_OEM_PREV_2:
                  webCode = 401;
                  break;
                default:
                  webCode = 0;
                  break;
              }
              break;
          }
          break;
      }
    }

    /* Pavé numerique 0-9 */
    if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
      webCode = (keyCode - KeyEvent.KEYCODE_0) + 48;
    }

    /* Anti-rebond play/pause (1100 ms) */
    if (webCode == 402 && send) {
      if (lastPlayPause < System.currentTimeMillis()) {
        lastPlayPause = System.currentTimeMillis() + 1100;
      } else {
        send = false;
      }
    }

    if (webCode > 0) {
      if (aView.webViewReady) {
        if (send) {
          aView.webView.evaluateJavascript(
              "try{ window._KEYEV(" + webCode + ");}catch(e){}", null);
        }
        return true;
      }
    }
    return false;
  }

  /* ------------------------------------------------------------------
   * MediaSession
   * ------------------------------------------------------------------ */

  /** Execute une commande media dans le JS (pb.vid_cmd). */
  public void mediaExec(String cmd, long position) {
    if (cmd.equals("pause") || cmd.equals("play")) {
      if (lastPlayPause >= System.currentTimeMillis()) {
        return;
      }
      lastPlayPause = System.currentTimeMillis() + 1100;
    }
    runOnUiThread(() -> {
      if (aView != null && aView.webView != null && aView.webViewReady) {
        aView.webView.evaluateJavascript(
            "try{pb.vid_cmd('" + cmd + "'," + position + ");}catch(e){}", null);
      }
    });
  }

  /** Met a jour l'etat de lecture de la MediaSession. */
  public void updateMediaState() {
    if (mSession == null) {
      return;
    }
    try {
      long actions = PlaybackState.ACTION_PLAY_PAUSE |
          PlaybackState.ACTION_PLAY |
          PlaybackState.ACTION_PAUSE |
          (_metaHaveNext ? PlaybackState.ACTION_SKIP_TO_NEXT : 0) |
          (_metaHavePrev ? PlaybackState.ACTION_SKIP_TO_PREVIOUS : 0) |
          PlaybackState.ACTION_SEEK_TO;
      mSession.setPlaybackState(new PlaybackState.Builder()
          .setActions(actions)
          .setState(_metaState, _metaPosition, _metaSpeed,
              SystemClock.elapsedRealtime())
          .build());
    } catch (Exception ignored) {
    }
  }

  /** Met a jour les metadonnees de la MediaSession. */
  public void updateMediaMeta() {
    if (mSession == null) {
      return;
    }
    try {
      MediaMetadata.Builder builder = new MediaMetadata.Builder();
      builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
          _metaTitle + " - " + _metaArtist);
      builder.putString(MediaMetadata.METADATA_KEY_TITLE, _metaTitle);
      builder.putString(MediaMetadata.METADATA_KEY_ARTIST, _metaArtist);
      builder.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, _metaUrl);
      builder.putString(MediaMetadata.METADATA_KEY_ART_URI, _metaUrl);
      builder.putLong(MediaMetadata.METADATA_KEY_DURATION, _metaDuration);
      mSession.setMetadata(builder.build());
    } catch (Exception ignored) {
    }
  }

  /** Callback des touches media (Bluetooth / telecommande). */
  private class AnimeMediaSessionCallback extends MediaSession.Callback {
    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
      KeyEvent keyEvent =
          mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      if (keyEvent != null) {
        sendKeyEvent(keyEvent.getKeyCode(), keyEvent.getAction());
      }
      return true;
    }

    @Override
    public void onPause() {
      Log.d(_TAG_MEDIA, "MEDIA-SESSION ONPAUSE");
      mediaExec("pause", 0);
    }

    @Override
    public void onPlay() {
      Log.d(_TAG_MEDIA, "MEDIA-SESSION ONPLAY");
      mediaExec("play", 0);
    }

    @Override
    public void onSeekTo(long position) {
      mediaExec("seek", position / 1000);
    }

    @Override
    public void onSkipToNext() {
      sendKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN);
      sendKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP);
    }

    @Override
    public void onSkipToPrevious() {
      sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN);
      sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_UP);
    }

    @Override
    public void onStop() {
      mediaExec("pause", 0);
    }
  }

  /* ------------------------------------------------------------------
   * Cycle de vie
   * ------------------------------------------------------------------ */

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    aView.setFullscreen(newConfig.orientation);
  }

  /** Choisit le mode d'affichage avec le meilleur taux de rafraichissement. */
  private void initRefreshRate() {
    if (getPackageManager().hasSystemFeature("android.hardware.touchscreen")) {
      if (Build.VERSION.SDK_INT >= 30) {
        Window window = getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        Display.Mode[] supportedModes = getDisplay().getSupportedModes();
        Display.Mode currentMode = getDisplay().getMode();
        Log.d(_TAG, "Current Mode " + currentMode.getModeId() + " : " +
            currentMode.getPhysicalWidth() + "x" + currentMode.getPhysicalHeight() +
            "@" + currentMode.getRefreshRate() + "hz");
        float maxRefreshRate = 60.0f;
        int bestModeId = -1;
        for (Display.Mode mode : supportedModes) {
          Log.d(_TAG, "Mode " + mode.getModeId() + " : " +
              mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + "@" +
              mode.getRefreshRate() + "hz");
          if (currentMode.getPhysicalHeight() == mode.getPhysicalHeight() &&
              currentMode.getPhysicalWidth() == mode.getPhysicalWidth() &&
              currentMode.getRefreshRate() <= mode.getRefreshRate()) {
            maxRefreshRate = mode.getRefreshRate();
            bestModeId = mode.getModeId();
          }
        }
        if (bestModeId > -1) {
          attributes.preferredDisplayModeId = bestModeId;
          window.setAttributes(attributes);
          Log.d(_TAG, "Max Mode Value : " + maxRefreshRate + "hz");
        }
      }
      Log.d(_TAG, "Have Touch Screen");
    } else {
      Log.d(_TAG, "No Touch Screen");
    }
  }

  /** Initialise la MediaSession (touches media Bluetooth). */
  private void initMediaSession() {
    try {
      mSession = new MediaSession(this, getPackageName());
      if (Build.VERSION.SDK_INT >= 31) {
        mSession.setMediaButtonBroadcastReceiver(
            new ComponentName(this, MediaButtonReceiver.class));
      } else {
        mSession.setMediaButtonReceiver(PendingIntent.getBroadcast(
            getApplicationContext(), 0,
            new Intent(Intent.ACTION_MEDIA_BUTTON, null, getApplicationContext(),
                MediaButtonReceiver.class),
            PendingIntent.FLAG_IMMUTABLE));
      }
      mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
          MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
      mSession.setCallback(new AnimeMediaSessionCallback());
    } catch (Exception e) {
      mSession = null;
    }
  }

  /** Lit les arguments d'appel (intent ou etat sauvegarde). */
  private void updateInstance(Bundle bundle) {
    if (bundle == null) {
      Bundle extras = getIntent().getExtras();
      if (extras != null) {
        ARG_URL = extras.getString("viewurl");
        ARG_TIP = extras.getString("viewtip");
        ARG_POS = extras.getString("viewpos");
        ARG_SD = extras.getString("viewsd");
      } else {
        ARG_URL = null;
        ARG_TIP = null;
        ARG_POS = null;
        ARG_SD = null;
      }
    } else {
      ARG_URL = bundle.getString("viewurl");
      ARG_TIP = bundle.getString("viewtip");
      ARG_POS = bundle.getString("viewpos");
      ARG_SD = bundle.getString("viewsd");
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    setContentView(R.layout.activity_main);

    initRefreshRate();
    initMediaSession();
    updateInstance(savedInstanceState);

    aView = new AnimeView(this);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK && aView.webView.canGoBack()) {
      aView.webView.goBack();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    ARG_URL = intent.getStringExtra("viewurl");
    ARG_TIP = intent.getStringExtra("viewtip");
    ARG_POS = intent.getStringExtra("viewpos");
    aView.updateArgs();
  }

  @Override
  protected void onPause() {
    aView.onStartPause(false);
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onStart() {
    super.onStart();
    aView.onStartPause(true);
  }

  @Override
  protected void onStop() {
    AppExecutors.execute(() -> aView.updatePlayNext());
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    /* Libere le lecteur, la WebView et la reconnaissance vocale */
    if (aView != null) {
      aView.release();
    }
    if (mSession != null) {
      mSession.release();
      mSession = null;
    }
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    aView.onSaveRestore(true, outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    aView.onSaveRestore(false, savedInstanceState);
    super.onRestoreInstanceState(savedInstanceState);
  }
}
