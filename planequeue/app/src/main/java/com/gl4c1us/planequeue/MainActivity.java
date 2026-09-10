package com.gl4c1us.planequeue;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String SHEET_URL = "https://docs.google.com/spreadsheets/u/0/d/1n3R3m8aNgCvfLVrw9ahocm0-VhgV2ZZjvz-1zmdRo80/htmlview";
    private WebView webView;
    private String queueScript = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        queueScript = readAsset("queue.js");
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new QueueBridge(this), "PlaneQueueNative");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                scheduleInjection(1200);
                scheduleInjection(3000);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                scheduleInjection(100);
                scheduleInjection(700);
                scheduleInjection(1800);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request == null || request.isForMainFrame()) {
                    scheduleInjection(100);
                    scheduleInjection(700);
                }
            }
        });
        webView.loadUrl(SHEET_URL);
        scheduleInjection(1800);
        scheduleInjection(4500);
    }

    private void scheduleInjection(long delayMs) {
        handler.postDelayed(() -> {
            if (webView != null && !queueScript.isEmpty()) webView.evaluateJavascript(queueScript, null);
        }, delayMs);
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

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
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
        @JavascriptInterface public void enterPip() { activity.runOnUiThread(activity::enterMiniMode); }
    }
}
