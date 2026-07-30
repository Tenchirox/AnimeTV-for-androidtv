package com.amarullz.androidtv.animetvjmto;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Buffer de logs interne a l'application + visualiseur integre.
 *
 * <p>Capture les logs Java (via {@link ALog}) et les logs JS de la WebView
 * (console.log via onConsoleMessage), pour debugger sur un appareil sans
 * adb. Le buffer conserve les {@value #MAX_LINES} dernieres lignes.</p>
 */
public final class AppLog {
  private static final int MAX_LINES = 1000;
  private static final ArrayDeque<String> BUFFER = new ArrayDeque<>();
  private static final SimpleDateFormat TIME =
      new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private AppLog() {
  }

  /** Ajoute une ligne de log au buffer (thread-safe). */
  public static synchronized void add(String tag, String message) {
    if (BUFFER.size() >= MAX_LINES) {
      BUFFER.pollFirst();
    }
    BUFFER.addLast(TIME.format(new Date()) + " " + tag + ": " + message);
  }

  /** Vide le buffer. */
  public static synchronized void clear() {
    BUFFER.clear();
  }

  /** @return tout le contenu du buffer, une entree par ligne. */
  public static synchronized String dump() {
    StringBuilder sb = new StringBuilder();
    for (String line : BUFFER) {
      sb.append(line).append('\n');
    }
    return sb.toString();
  }

  /** Affiche le visualiseur de logs (refresh auto, copie, effacer). */
  public static void showDialog(Context context) {
    float density = context.getResources().getDisplayMetrics().density;
    int pad = (int) (12 * density);

    /* Contenu : texte monospace defilant */
    final TextView textView = new TextView(context);
    textView.setTypeface(Typeface.MONOSPACE);
    textView.setTextSize(11);
    textView.setTextColor(Color.rgb(0, 230, 118));
    textView.setBackgroundColor(Color.rgb(16, 16, 16));
    textView.setPadding(pad, pad, pad, pad);
    textView.setMovementMethod(new ScrollingMovementMethod());
    textView.setTextIsSelectable(true);

    final ScrollView scrollView = new ScrollView(context);
    scrollView.addView(textView, new ScrollView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    /* Boutons */
    Button refreshBtn = new Button(context);
    refreshBtn.setText("Refresh");
    Button clearBtn = new Button(context);
    clearBtn.setText("Clear");
    Button copyBtn = new Button(context);
    copyBtn.setText("Copy");
    Button closeBtn = new Button(context);
    closeBtn.setText("Close");

    LinearLayout buttons = new LinearLayout(context);
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.addView(refreshBtn, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    buttons.addView(clearBtn, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    buttons.addView(copyBtn, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    buttons.addView(closeBtn, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    LinearLayout content = new LinearLayout(context);
    content.setOrientation(LinearLayout.VERTICAL);
    content.addView(scrollView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    content.addView(buttons);

    final AlertDialog dialog = new AlertDialog.Builder(context)
        .setTitle("AnimeTV Logs")
        .setView(content)
        .create();

    /* Refresh auto toutes les 1.5s (auto-scroll en bas) */
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable refresher = new Runnable() {
      @Override
      public void run() {
        if (dialog.isShowing()) {
          textView.setText(dump());
          scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
          handler.postDelayed(this, 1500);
        }
      }
    };
    dialog.setOnShowListener(d -> {
      textView.setText(dump());
      scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
      handler.postDelayed(refresher, 1500);
    });
    dialog.setOnDismissListener(d -> handler.removeCallbacks(refresher));

    refreshBtn.setOnClickListener(v -> {
      textView.setText(dump());
      scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    });
    clearBtn.setOnClickListener(v -> {
      clear();
      textView.setText("");
    });
    copyBtn.setOnClickListener(v -> {
      ClipboardManager clipboard =
          (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("AnimeTV Logs", dump()));
      Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
    });
    closeBtn.setOnClickListener(v -> dialog.dismiss());

    dialog.show();
  }
}
