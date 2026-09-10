package com.gl4c1us.planequeue;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Rational;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String SHEET_ID = "1n3R3m8aNgCvfLVrw9ahocm0-VhgV2ZZjvz-1zmdRo80";
    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/u/0/d/" + SHEET_ID + "/htmlview";
    private static final String SHEET_CSV_URL = "https://docs.google.com/spreadsheets/d/" + SHEET_ID + "/gviz/tq?tqx=out:csv";
    private static final long AUTO_REFRESH_MS = 15000L;

    private WebView webView;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshSheet();
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.BLACK);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);

        webView.addJavascriptInterface(new QueueBridge(this), "PlaneQueueNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS);
    }

    private void notifyRefreshStarted() {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("window.PlaneQueueRefreshStarted && window.PlaneQueueRefreshStarted();", null);
            }
        });
    }

    private void refreshSheet() {
        if (!refreshInProgress.compareAndSet(false, true)) return;
        notifyRefreshStarted();

        networkExecutor.execute(() -> {
            try {
                String csv = fetchText(SHEET_CSV_URL);
                if (csv == null) throw new IOException("The sheet returned no data.");

                String trimmed = csv.trim();
                if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") || trimmed.contains("accounts.google.com")) {
                    throw new IOException("Google returned a sign-in page instead of the queue. Check sheet sharing permissions.");
                }

                String payload = Base64.encodeToString(csv.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("window.PlaneQueueReceiveCsv && window.PlaneQueueReceiveCsv('" + payload + "');", null);
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = "Could not load the queue sheet.";
                String payload = Base64.encodeToString(message.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                runOnUiThread(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("window.PlaneQueueReceiveError && window.PlaneQueueReceiveError('" + payload + "');", null);
                    }
                });
            } finally {
                refreshInProgress.set(false);
            }
        });
    }

    private String fetchText(String urlString) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PlaneQueue/1.4");
            connection.setRequestProperty("Accept", "text/csv,text/plain,*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Sheet request failed (HTTP " + code + ").");
            }

            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 5 * 1024 * 1024) throw new IOException("Sheet response was unexpectedly large.");
                    out.write(buffer, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void enterMiniMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Picture-in-picture is not supported on this device.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (webView != null) {
            webView.evaluateJavascript("document.documentElement.classList.add('pq-pip');", null);
        }

        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true);
        }
        enterPictureInPictureMode(builder.build());
    }

    private void openSheet() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHEET_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the sheet.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (webView != null) {
            String js = isInPictureInPictureMode
                    ? "document.documentElement.classList.add('pq-pip');"
                    : "document.documentElement.classList.remove('pq-pip');";
            webView.evaluateJavascript(js, null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        autoRefreshHandler.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();
        if (webView != null) {
            webView.removeJavascriptInterface("PlaneQueueNative");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    public static class QueueBridge {
        private static final String PREFS = "plane_queue_state";
        private static final String KEY_DONE = "done_json";
        private final SharedPreferences prefs;
        private final MainActivity activity;

        QueueBridge(MainActivity activity) {
            this.activity = activity;
            prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @JavascriptInterface public String getDone() { return prefs.getString(KEY_DONE, "[]"); }
        @JavascriptInterface public void saveDone(String json) {
            if (json != null && json.length() <= 200000) prefs.edit().putString(KEY_DONE, json).apply();
        }
        @JavascriptInterface public void clearDone() { prefs.edit().remove(KEY_DONE).apply(); }
        @JavascriptInterface public void refreshSheet() { activity.refreshSheet(); }
        @JavascriptInterface public void enterPip() { activity.runOnUiThread(activity::enterMiniMode); }
        @JavascriptInterface public void openSheet() { activity.runOnUiThread(activity::openSheet); }
    }
}
