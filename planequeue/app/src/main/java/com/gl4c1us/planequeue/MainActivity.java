package com.gl4c1us.planequeue;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("docs.google.com/spreadsheets")) {
                    scheduleInjection(450);
                    scheduleInjection(1400);
                    scheduleInjection(3200);
                }
            }
        });
        webView.loadUrl(SHEET_URL);
    }

    private void scheduleInjection(long delayMs) {
        handler.postDelayed(() -> {
            if (webView != null && !queueScript.isEmpty()) webView.evaluateJavascript(queueScript, null);
        }, delayMs);
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

        QueueBridge(Context context) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @JavascriptInterface public String getDone() { return prefs.getString(KEY_DONE, "[]"); }
        @JavascriptInterface public void saveDone(String json) {
            if (json != null && json.length() <= 200000) prefs.edit().putString(KEY_DONE, json).apply();
        }
        @JavascriptInterface public void clearDone() { prefs.edit().remove(KEY_DONE).apply(); }
    }
}
