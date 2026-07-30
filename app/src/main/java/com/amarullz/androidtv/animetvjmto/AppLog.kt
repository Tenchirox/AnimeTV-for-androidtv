package com.amarullz.androidtv.animetvjmto

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Buffer de logs interne a l'application + visualiseur integre.
 *
 * Capture les logs Java (via [ALog]) et les logs JS de la WebView
 * (console.log via onConsoleMessage), pour debugger sur un appareil sans
 * adb. Le buffer conserve les [MAX_LINES] dernieres lignes.
 */
object AppLog {

    private const val MAX_LINES = 1000
    private val buffer = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Ajoute une ligne de log au buffer (thread-safe). */
    @JvmStatic
    @Synchronized
    fun add(tag: String, message: String) {
        if (buffer.size >= MAX_LINES) buffer.pollFirst()
        buffer.addLast("${timeFormat.format(Date())} $tag: $message")
    }

    /** Vide le buffer. */
    @JvmStatic
    @Synchronized
    fun clear() {
        buffer.clear()
    }

    /** @return tout le contenu du buffer, une entree par ligne. */
    @JvmStatic
    @Synchronized
    fun dump(): String = buildString {
        for (line in buffer) {
            appendLine(line)
        }
    }

    /** Affiche le visualiseur de logs (refresh auto, copie, effacer). */
    @JvmStatic
    fun showDialog(context: Context) {
        val density = context.resources.displayMetrics.density
        val pad = (12 * density).toInt()

        /* Contenu : texte monospace defilant */
        val textView = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(0, 230, 118))
            setBackgroundColor(Color.rgb(16, 16, 16))
            setPadding(pad, pad, pad, pad)
            movementMethod = ScrollingMovementMethod()
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(context)
        scrollView.addView(textView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        /* Boutons */
        fun button(label: String, onClick: (android.view.View) -> Unit) =
            Button(context).apply {
                text = label
                setOnClickListener(onClick)
            }

        val refreshBtn = button("Refresh") {
            textView.text = dump()
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
        val clearBtn = button("Clear") {
            clear()
            textView.text = ""
        }
        val copyBtn = button("Copy") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("AnimeTV Logs", dump()))
            Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        val closeBtn = button("Close") { /* dismiss below */ }

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(refreshBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(clearBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(copyBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("AnimeTV Logs")
            .setView(content)
            .create()

        /* Refresh auto toutes les 1.5s (auto-scroll en bas) */
        val handler = Handler(Looper.getMainLooper())
        val refresher = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    textView.text = dump()
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    handler.postDelayed(this, 1500)
                }
            }
        }
        dialog.setOnShowListener {
            textView.text = dump()
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            handler.postDelayed(refresher, 1500)
        }
        dialog.setOnDismissListener { handler.removeCallbacks(refresher) }

        closeBtn.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
