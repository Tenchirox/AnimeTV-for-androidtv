package com.amarullz.androidtv.animetvjmto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.chromium.net.CronetEngine;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.dnsoverhttps.DnsOverHttps;

/**
 * Moteur reseau de l'application + gestion des mises a jour.
 *
 * <p>Reconstruit depuis les classes obfusquees {@code f3.f} (AnimeApi),
 * {@code f3.e} (AnimeApi.Http), {@code b1.h} (updateServerVar) et
 * {@code f3.a} (telechargement de l'APK) de l'APK 6.6.7.</p>
 *
 * <p>Trois moteurs HTTP au choix ({@link Conf#HTTP_CLIENT}) :
 * OkHttp (defaut, avec DNS-over-HTTPS optionnel), HttpURLConnection
 * ou Cronet (Chromium, via Google Play Services).</p>
 */
public class AnimeApi extends WebViewClient {
  private static final String _TAG = "ATVLOG-API";

  /* URL du fichier de configuration/mise a jour distant */
  private static final String SERVER_CONFIG_URL =
      "https://raw.githubusercontent.com/amarullz/kaicodex/main/shr/server.json";

  /* Moteur HTTP statique */
  public static DnsOverHttps dohClient = null;
  public static OkHttpClient bootstrapClient = null;
  public static String cacheDir = null;
  public static CronetEngine cronetClient = null;
  public static OkHttpClient httpClient = null;
  public static Cache appCache = null;
  public static boolean reqClearCache = false;

  public final Activity activity;
  public final WebResourceResponse badRequest;
  public final SharedPreferences pref;
  public boolean updateIsInProgress = false;
  public String prefServer = "";

  public AnimeApi(Activity mainActivity) {
    activity = mainActivity;
    cacheDir = mainActivity.getCacheDir().getAbsolutePath();
    Log.d(_TAG, "Cache Dir = " + cacheDir);
    initHttpEngine(mainActivity);
    AsyncTask.execute(() -> updateServerVar(false));
    pref = mainActivity.getSharedPreferences("SERVER", Context.MODE_PRIVATE);
    initPref();
    badRequest = new WebResourceResponse("text/plain", null, 400,
        "Bad Request", null, null);
  }

  /* ------------------------------------------------------------------
   * Requetes
   * ------------------------------------------------------------------ */

  /**
   * Execute une requete HTTP pour {@code shouldInterceptRequest}, avec
   * injection optionnelle de contenu et remplacement optionnel du domaine.
   *
   * @param request   requete WebView d'origine (headers recopies)
   * @param inject    contenu a injecter (URL de script ou HTML brut), ou null
   * @param injectContentType "inject-html" pour un ajout de HTML brut, sinon
   *                          type mime devant correspondre pour injecter un
   *                          {@code <script src="inject">}
   * @param changeDomain domaine de remplacement (referer/origin reecrits), null sinon
   * @return la reponse a servir a la WebView, ou null en cas d'erreur
   */
  public static WebResourceResponse defaultRequest(WebResourceRequest request,
      String inject, String injectContentType, String changeDomain) {
    Uri uri = request.getUrl();
    String url = uri.toString();
    if (changeDomain != null) {
      url = url.replace("://" + uri.getHost(), "://" + changeDomain);
      Log.d(_TAG, "CH-DOMAIN: " + url);
    }
    try {
      Http http = new Http(url);
      for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
        if (changeDomain == null ||
            !(header.getKey().equalsIgnoreCase("referer") ||
              header.getKey().equalsIgnoreCase("origin"))) {
          http.addHeader(header.getKey(), header.getValue());
        } else {
          http.addHeader(header.getKey(),
              header.getValue().replace("://" + uri.getHost(), "://" + changeDomain));
        }
      }
      http.execute();
      if (inject != null) {
        if (Objects.equals(injectContentType, "inject-html")) {
          /* Ajout de HTML brut a la fin du corps */
          http.body.write(inject.getBytes());
        } else {
          /* Ajout d'un <script> si le type mime correspond */
          if (injectContentType == null) {
            injectContentType = "text/html";
          }
          if (http.ctype[0].startsWith(injectContentType)) {
            http.body.write(("<script src=\"" + inject + "\"></script>").getBytes());
          }
        }
      }
      return new WebResourceResponse(http.ctype[0], http.ctype[1],
          new ByteArrayInputStream(http.body.toByteArray()));
    } catch (Exception e) {
      Log.e(_TAG, "defaultRequest ERR =" + url, e);
      return null;
    }
  }

  /* ------------------------------------------------------------------
   * Moteur HTTP (init)
   * ------------------------------------------------------------------ */

  /** (Re)initialise le moteur HTTP global (OkHttp / Cronet / DoH / cache). */
  public static void initHttpEngine(Context context) {
    long cacheSize = ((long) Conf.CACHE_SIZE_MB) * 1024 * 1024;

    /* Cronet (option HTTP_CLIENT == 2) */
    if (cronetClient != null) {
      try {
        cronetClient.shutdown();
      } catch (Exception ignored) {
      }
      cronetClient = null;
    }
    if (Conf.HTTP_CLIENT == 2) {
      try {
        String path = cacheDir != null ? cacheDir : "cacheDir";
        File cronetDir = new File(path, "cronet");
        if (reqClearCache) {
          cronetDir.delete();
        }
        cronetDir.mkdir();
        cronetClient = new CronetEngine.Builder(context)
            .setStoragePath(cronetDir.getAbsolutePath())
            .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, cacheSize)
            .enableHttp2(true)
            .enableQuic(true)
            .enableBrotli(true)
            .enablePublicKeyPinningBypassForLocalTrustAnchors(false)
            .addQuicHint(Conf.SOURCE_DOMAINS[1], 443, 443)
            .addQuicHint("megaf.cc", 443, 443)
            .build();
      } catch (Exception e) {
        Log.e(_TAG, "Cronet Init Error", e);
        cronetClient = null;
      }
    }

    /* Cache disque OkHttp */
    String path = cacheDir != null ? cacheDir : "cacheDir";
    appCache = new Cache(new File(path, "okhttpcache"), cacheSize);
    if (reqClearCache) {
      try {
        appCache.evictAll();
      } catch (IOException ignored) {
      }
    }
    bootstrapClient = new OkHttpClient.Builder().cache(appCache).build();

    /* DNS over HTTPS via 1.1.1.1 */
    okhttp3.HttpUrl dohUrl = okhttp3.HttpUrl.get("https://1.1.1.1/dns-query");
    dohClient = new DnsOverHttps.Builder()
        .client(bootstrapClient.newBuilder().build())
        .url(dohUrl)
        .build();

    if (Conf.USE_DOH) {
      httpClient = bootstrapClient.newBuilder().dns(dohClient).build();
    } else {
      httpClient = bootstrapClient.newBuilder().build();
    }
    reqClearCache = false;
  }

  /** Decoupe un Content-Type "mime; charset=xxx" en [mime, charset]. */
  public static String[] parseContentType(String contentType) {
    String[] res = {"application/octet-stream", null};
    if (contentType != null) {
      String[] parts = contentType.split(";");
      res[0] = parts[0].trim();
      if (parts.length == 2) {
        res[1] = parts[1].split("=")[1].trim();
      }
    }
    return res;
  }

  /**
   * Patche le JS de l'embed MegaCloud/MegaUp : hook dans {@code function Q() {}
   * pour empiler les arguments (cles de dechiffrement) dans window.__QKEYS.
   */
  public static void injectQKeyHook(ByteArrayOutputStream buffer) {
    try {
      String hook = "function Q(){ try{console.log(arguments);" +
          "if (!('__QKEYS' in window)) window.__QKEYS=[];" +
          " window.__QKEYS.push(arguments[0]);}catch(e){}";
      byte[] bytes = buffer.toString("UTF-8")
          .replace("function Q(){", hook)
          .replace("Function Q(){", hook)
          .getBytes(StandardCharsets.UTF_8);
      buffer.reset();
      buffer.write(bytes, 0, bytes.length);
    } catch (Exception e) {
      Log.e(_TAG, "Error replacing text: " + e.getMessage());
    }
  }

  /* ------------------------------------------------------------------
   * Assets
   * ------------------------------------------------------------------ */

  /** Sert un fichier des assets avec le type MIME deduit de l'extension. */
  public WebResourceResponse assetsRequest(String fn) {
    String mime;
    try {
      Log.d(_TAG, "ASSETS=" + fn);
      int dot = fn.lastIndexOf(".");
      if (dot >= 0) {
        switch (fn.substring(dot)) {
          case ".js":
            mime = "application/javascript";
            break;
          case ".css":
            mime = "text/css";
            break;
          case ".jpg":
            mime = "image/jpeg";
            break;
          case ".png":
            mime = "image/png";
            break;
          case ".svg":
            mime = "image/svg+xml";
            break;
          case ".html":
            mime = "text/html";
            break;
          default:
            mime = "text/plain";
            break;
        }
      } else {
        mime = "text/plain";
      }
      return new WebResourceResponse(mime, null, 200, "OK", null,
          activity.getAssets().open(fn));
    } catch (IOException e) {
      return badRequest;
    }
  }

  /** Lit un asset texte (UTF-8) en chaine. */
  public String assetsString(String fn) {
    try {
      StringBuilder sb = new StringBuilder();
      BufferedReader reader = new BufferedReader(new InputStreamReader(
          activity.getAssets().open(fn), StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line);
        sb.append("\n");
      }
      reader.close();
      return sb.toString();
    } catch (IOException e) {
      return "";
    }
  }

  /* ------------------------------------------------------------------
   * Preferences
   * ------------------------------------------------------------------ */

  /** Charge les preferences "SERVER" (config distante, source, cache). */
  public void initPref() {
    prefServer = pref.getString("server-json", "");
    if (!prefServer.equals("")) {
      try {
        Conf.SERVER_VER = new JSONObject(prefServer).getString("update");
      } catch (Exception ignored) {
      }
    }
    Conf.SOURCE_DOMAIN = pref.getInt("source-domain", Conf.SOURCE_DOMAIN);
    Conf.CACHE_SIZE_MB = pref.getInt("cache-size", Conf.CACHE_SIZE_MB);
    Conf.updateSource(Conf.SOURCE_DOMAIN);
    Log.d(_TAG, "DOMAIN = " + Conf.getDomain() + " / STREAM = " + Conf.STREAM_DOMAIN +
        " / UPDATE = " + Conf.SERVER_VER + " / Source-ID: " + Conf.SOURCE_DOMAIN);
  }

  /* ------------------------------------------------------------------
   * Mise a jour de l'APK
   * ------------------------------------------------------------------ */

  private String apkTempFile() {
    return activity.getFilesDir() + "/update.apk";
  }

  /** Installe un APK telecharge via FileProvider. */
  public void installApk(File apkfile) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri apkUri = FileProvider.getUriForFile(activity,
        activity.getPackageName() + ".provider", apkfile);
    List<ResolveInfo> resInfoList = activity.getPackageManager()
        .queryIntentActivities(intent, 0x10000);
    for (ResolveInfo resolveInfo : resInfoList) {
      activity.grantUriPermission(resolveInfo.activityInfo.packageName, apkUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
    Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
    install.setData(apkUri);
    Log.d(_TAG, "INSTALLING APK = " + apkUri);
    install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    activity.startActivity(install);
  }

  /** Lance le telechargement + installation d'une mise a jour. */
  public boolean startUpdateApk(String url, boolean isNightly) {
    if (updateIsInProgress) {
      Toast.makeText(activity, (isNightly ? "Nightly" : "Update") +
          " already on progress", Toast.LENGTH_SHORT).show();
      return false;
    }
    updateIsInProgress = true;
    AsyncTask.execute(new Runnable() {
      @Override
      public void run() {
        Log.d(_TAG, "DOWNLOADING APK = " + url);
        try {
          Http http = new Http(url);
          http.execute();
          int sizeMb = http.body.size() / (1024 * 1024);
          Log.d(_TAG, "APK DOWNLOADED = " + sizeMb + "MB");
          activity.runOnUiThread(() -> Toast.makeText(activity,
              (isNightly ? "Nightly" : "Update") + " has been downloaded (" +
                  sizeMb + "MB)", Toast.LENGTH_LONG).show());
          File fp = new File(apkTempFile());
          FileOutputStream fos = new FileOutputStream(fp);
          http.body.writeTo(fos);
          fos.flush();
          fos.close();
          installApk(fp);
          updateIsInProgress = false;
        } catch (Exception e) {
          activity.runOnUiThread(() -> Toast.makeText(activity,
              "Download " + (isNightly ? "Nightly " : "") + "Update Failed: " +
                  e.toString(), Toast.LENGTH_LONG).show());
          Log.d(_TAG, "DOWNLOAD ERR: " + e);
          updateIsInProgress = false;
        }
      }
    });
    return true;
  }

  /** Dialogue de proposition de mise a jour. */
  private void showUpdateDialog(String url, String appver, String appnote, String appsize) {
    new AlertDialog.Builder(activity)
        .setTitle("Update Available - Version " + appver)
        .setMessage("Download Size : " + appsize + "\n\nChangelogs:\n" + appnote)
        .setNegativeButton("Later", (dialog, which) -> {
          SharedPreferences.Editor ed = pref.edit();
          ed.putBoolean("update-disable", false);
          ed.apply();
        })
        .setNeutralButton("Don't Remind Me", (dialog, which) -> {
          SharedPreferences.Editor ed = pref.edit();
          ed.putBoolean("update-disable", true);
          ed.apply();
        })
        .setPositiveButton("Update Now", (dialog, which) -> {
          Toast.makeText(activity, "Downloading Update...", Toast.LENGTH_SHORT).show();
          startUpdateApk(url, false);
        })
        .show();
  }

  /**
   * Verifie la configuration distante et la disponibilite d'une nouvelle
   * version de l'application.
   *
   * @param showMessage force l'affichage du dialogue meme si l'utilisateur
   *                    a choisi "Don't Remind Me"
   */
  public void updateServerVar(boolean showMessage) {
    AsyncTask.execute(() -> {
      /* Supprime l'eventuel APK temporaire d'une precedente maj */
      try {
        File fp = new File(apkTempFile());
        if (fp.delete()) {
          Log.d(_TAG, "TEMP APK FILE DELETED");
        } else {
          Log.d(_TAG, "NO TEMP APK FILE");
        }
      } catch (Exception ignored) {
      }

      try {
        /* Recupere la configuration distante */
        Http http = new Http(SERVER_CONFIG_URL + "?" + System.currentTimeMillis());
        http.execute();
        String serverjson = http.body.toString();
        JSONObject j = new JSONObject(serverjson);
        String update = j.getString("update");
        if (!Conf.SERVER_VER.equals(update)) {
          Log.d(_TAG, "SERVER-UPDATED: " + serverjson);
          SharedPreferences.Editor ed = pref.edit();
          ed.putString("server-json", serverjson);
          ed.apply();
          initPref();
        } else {
          Log.d(_TAG, "SERVER UP TO DATE");
        }

        /* Nouvelle version de l'application ? */
        int appnum = j.getInt("appnum");
        if (appnum > BuildConfig.VERSION_CODE) {
          Log.d(_TAG, "NEW APK VERSION AVAILABLE");
          String appurl = j.getString("appurl");
          String appver = j.getString("appver");
          String appnote = j.getString("appnote");
          String appsize = j.getString("appsize");
          Log.d(_TAG, "showUpdateDialog = " + appver + " / " + appsize + " / " +
              appurl + " / " + appnote);
          boolean updateState = pref.getBoolean("update-disable", false);
          if (!updateState || showMessage) {
            activity.runOnUiThread(() -> showUpdateDialog(appurl, appver, appnote, appsize));
          } else {
            activity.runOnUiThread(() -> Toast.makeText(activity,
                "Update version " + appver + " is available...",
                Toast.LENGTH_SHORT).show());
          }
        } else {
          if (showMessage) {
            activity.runOnUiThread(() -> Toast.makeText(activity,
                "AnimeTV already up to date...", Toast.LENGTH_SHORT).show());
          }
          Log.d(_TAG, "APP UP TO DATE");
        }
      } catch (Exception ignored) {
      }
    });
  }

  @Override
  public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
    handler.proceed();
  }

  /* ------------------------------------------------------------------
   * Client HTTP unifie
   * ------------------------------------------------------------------ */

  /**
   * Requete HTTP simple utilisee par tout l'applicatif : OkHttp, Cronet ou
   * HttpURLConnection selon {@link Conf#HTTP_CLIENT}. Le corps de la reponse
   * est bufferise dans {@link #body} et le type mime dans {@link #ctype}.
   *
   * <p>Reconstruit depuis la classe obfusquee {@code f3.e}.</p>
   */
  public static class Http {
    public boolean nocache = false;
    public HttpURLConnection http = null;
    public Request.Builder req = null;
    public Response res = null;
    public ByteArrayOutputStream body = new ByteArrayOutputStream();
    public String[] ctype;

    public Http(String url) throws Exception {
      if (Conf.HTTP_CLIENT > 0) {
        /* HttpURLConnection direct ou via Cronet */
        if (Conf.HTTP_CLIENT == 2 && cronetClient != null) {
          http = (HttpURLConnection) cronetClient.openConnection(new URL(url));
        } else {
          http = (HttpURLConnection) new URL(url).openConnection();
        }
        http.setConnectTimeout(5000);
        http.setReadTimeout(10000);
      } else {
        /* OkHttp */
        req = new Request.Builder().url(url);
      }
    }

    /** Ajoute un header a la requete ("Pragma: no-cache" est intercepte). */
    public void addHeader(String name, String val) {
      if (name.equalsIgnoreCase("X-Requested-With") &&
          !val.equalsIgnoreCase("XMLHttpRequest")) {
        return;
      }
      if (name.equalsIgnoreCase("Pragma") && val.equalsIgnoreCase("no-cache")) {
        nocache = true;
        Log.d(_TAG, "HTTP-Request no cache");
      }
      if (req != null) {
        req.addHeader(name, val);
      } else if (http != null) {
        http.setRequestProperty(name, val);
      }
    }

    /** Execute la requete et bufferise corps + content-type. */
    public void execute() throws Exception {
      if (req != null) {
        /* OkHttp */
        if (httpClient == null) {
          if (Conf.USE_DOH && dohClient != null) {
            httpClient = bootstrapClient.newBuilder().dns(dohClient).build();
          } else {
            httpClient = bootstrapClient.newBuilder().build();
          }
        }
        if (nocache) {
          req.cacheControl(new CacheControl.Builder().noCache().noStore().build());
        }
        res = httpClient.newCall(req.build()).execute();
        body = new ByteArrayOutputStream();
        okhttp3.ResponseBody responseBody = res.body();
        if (responseBody != null) {
          long contentLength = responseBody.contentLength();
          byte[] bytes = responseBody.bytes();
          if (contentLength != -1 && contentLength != bytes.length) {
            throw new IOException("Content-Length (" + contentLength +
                ") and stream length (" + bytes.length + ") disagree");
          }
          body.write(bytes);
        }
        ctype = parseContentType(res.header("Content-Type"));
        return;
      }
      if (http == null) {
        return;
      }
      /* HttpURLConnection / Cronet */
      if (nocache) {
        http.setUseCaches(false);
      }
      ctype = parseContentType(http.getContentType());
      body = new ByteArrayOutputStream();
      InputStream inputStream = http.getInputStream();
      try {
        byte[] buf = new byte[1024];
        int n;
        while ((n = inputStream.read(buf, 0, 1024)) != -1) {
          body.write(buf, 0, n);
        }
      } catch (Exception ignored) {
      }
    }

    /** @return le code de reponse HTTP (0 si inconnu). */
    public int getResponseCode() {
      if (res != null) {
        return res.code();
      }
      if (http != null) {
        try {
          return http.getResponseCode();
        } catch (IOException e) {
          return 0;
        }
      }
      return 0;
    }

    /** Definit la methode (POST/PUT/DELETE) et le corps de la requete. */
    public void setMethod(String method, String bodyContent, String contentType)
        throws Exception {
      if (method.equalsIgnoreCase("DELETE")) {
        if (req != null) {
          req.method(method, null);
        } else if (http != null) {
          try {
            http.setRequestMethod(method);
          } catch (Exception ignored) {
          }
        }
        return;
      }
      if (req != null) {
        /* OkHttp : force un charset utf-8 si absent */
        String ct = contentType;
        if (ct != null && !ct.toLowerCase().contains("charset=")) {
          ct = ct + "; charset=utf-8";
        }
        okhttp3.MediaType mediaType =
            ct != null ? okhttp3.MediaType.parse(ct) : null;
        req.method(method,
            okhttp3.RequestBody.create(bodyContent != null ? bodyContent : "",
                mediaType));
      } else if (http != null) {
        /* HttpURLConnection */
        try {
          http.setRequestMethod(method);
          http.setRequestProperty("Content-Type", contentType);
          byte[] data = (bodyContent != null ? bodyContent : "")
              .getBytes(StandardCharsets.UTF_8);
          http.setRequestProperty("Content-Length", data.length + "");
          http.setDoOutput(true);
          OutputStream outputStream = http.getOutputStream();
          outputStream.write(data);
          outputStream.flush();
          outputStream.close();
        } catch (Exception ignored) {
        }
      }
    }
  }
}
