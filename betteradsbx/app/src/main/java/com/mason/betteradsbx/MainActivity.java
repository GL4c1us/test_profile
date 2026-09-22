package com.mason.betteradsbx;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String INJECT = "(function(){try{"+
      "if(location.hostname!=='globe.adsbexchange.com')return;"+
      "if(!document.getElementById('bax-style')){var s=document.createElement('style');s.id='bax-style';s.textContent='"+
      "#sidebar_container,#selected_infoblock,#highlighted_infoblock{display:none!important}"+
      "#map_container{left:0!important;right:0!important;width:100%!important;bottom:0!important}"+
      "#bax-panel{position:fixed;left:0;right:0;bottom:0;z-index:2147483647;background:rgba(10,13,18,.97);color:#fff;font-family:sans-serif;border-radius:18px 18px 0 0;box-shadow:0 -5px 26px rgba(0,0,0,.45);padding:8px 12px 14px;max-height:48vh;overflow:auto;box-sizing:border-box}"+
      "#bax-handle{width:42px;height:4px;border-radius:4px;background:#66707e;margin:1px auto 8px}"+
      "#bax-head{display:flex;gap:8px;align-items:center;margin-bottom:6px}#bax-title{font-weight:800;font-size:19px;flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}#bax-reg{font-size:13px;color:#aeb8c7}"+
      "#bax-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin:6px 0}.bax-cell{background:#171c24;border:1px solid #27303c;border-radius:10px;padding:6px 7px;min-width:0}.bax-k{display:block;font-size:9px;color:#8995a7}.bax-v{display:block;font-size:13px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}"+
      "#bax-extra{font-size:11px;line-height:1.45;color:#c6cfdb;padding:2px 2px 6px}"+
      "#bax-searchrow{display:flex;gap:7px;position:sticky;bottom:0;background:rgba(10,13,18,.98);padding-top:6px}#bax-q{min-width:0;flex:1;border:1px solid #394556;background:#111720;color:white;border-radius:12px;padding:11px 12px;font-size:15px;outline:none}.bax-btn{border:0;border-radius:12px;padding:0 13px;font-weight:800;background:#2d7ff9;color:white}.bax-btn.alt{background:#252d39;color:#d9e3f0;padding:0 10px}"+
      "#bax-status{font-size:10px;color:#7f8a9a;margin-top:4px;text-align:center}';document.head.appendChild(s);}"+
      "if(!document.getElementById('bax-panel')){var p=document.createElement('div');p.id='bax-panel';p.innerHTML='<div id=\"bax-handle\"></div><div id=\"bax-head\"><div id=\"bax-title\">Select an aircraft</div><div id=\"bax-reg\"></div></div><div id=\"bax-grid\"><div class=\"bax-cell\"><span class=\"bax-k\">ALTITUDE</span><span class=\"bax-v\" id=\"bax-alt\">--</span></div><div class=\"bax-cell\"><span class=\"bax-k\">SPEED</span><span class=\"bax-v\" id=\"bax-spd\">--</span></div><div class=\"bax-cell\"><span class=\"bax-k\">TYPE</span><span class=\"bax-v\" id=\"bax-type\">--</span></div><div class=\"bax-cell\"><span class=\"bax-k\">HEX</span><span class=\"bax-v\" id=\"bax-hex\">--</span></div></div><div id=\"bax-extra\">Tap an aircraft on the map, or search below.</div><div id=\"bax-searchrow\"><input id=\"bax-q\" autocomplete=\"off\" autocapitalize=\"characters\" placeholder=\"Tail number / callsign / hex\"><button id=\"bax-go\" class=\"bax-btn\">TRACK</button><button id=\"bax-login\" class=\"bax-btn alt\">LOGIN</button></div><div id=\"bax-status\">ADS-B Exchange Premium session stays in this app</div>';document.body.appendChild(p);"+
      "var go=function(){var q=(document.getElementById('bax-q').value||'').trim().toUpperCase();if(!q)return;var si=document.getElementById('search_input');if(si){si.value=q;si.dispatchEvent(new Event('input',{bubbles:true}));var f=document.getElementById('search_form');if(f)f.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));document.getElementById('bax-status').textContent='Searching '+q;}else{location.href='https://globe.adsbexchange.com/?icao='+encodeURIComponent(q.toLowerCase());}};"+
      "document.getElementById('bax-go').onclick=go;document.getElementById('bax-q').onkeydown=function(e){if(e.key==='Enter'){e.preventDefault();go();this.blur();}};document.getElementById('bax-login').onclick=function(){location.href='https://account.adsbexchange.com/';};}"+
      "var t=function(id){var e=document.getElementById(id);return e?(e.textContent||'').trim():''};var v=function(id){var x=t(id);return(!x||x==='n/a')?'--':x};"+
      "var call=v('selected_callsign'),reg=v('selected_registration'),typ=v('selected_icaotype'),hex=v('selected_icao');var title=call!=='--'?call:(reg!=='--'?reg:'Select an aircraft');"+
      "document.getElementById('bax-title').textContent=title;document.getElementById('bax-reg').textContent=(reg!=='--'&&reg!==title)?reg:'';document.getElementById('bax-alt').textContent=v('selected_altitude1');document.getElementById('bax-spd').textContent=v('selected_speed1');document.getElementById('bax-type').textContent=typ;document.getElementById('bax-hex').textContent=hex;"+
      "var rows=[['Full type',v('selected_typelong')],['Country',v('selected_country')],['Route',v('selected_route')],['Squawk',v('selected_squawk1')],['Track',v('selected_track1')],['Vertical',v('selected_vert_rate')],['Position',v('selected_position')],['Source',v('selected_source')],['Last seen',v('selected_seen')]];var good=rows.filter(function(x){return x[1]!=='--';});document.getElementById('bax-extra').textContent=good.length?good.map(function(x){return x[0]+': '+x[1]}).join('  |  '):'Tap an aircraft on the map, or search below.';"+
      "}catch(e){}})();";

    private final Runnable injector = new Runnable() {
        @Override public void run() {
            if (webView != null) webView.evaluateJavascript(INJECT, null);
            handler.postDelayed(this, 1200);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(getApplicationContext());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " BetterADSBx/0.2");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(INJECT, null);
            }
        });

        setContentView(webView);
        webView.loadUrl("https://globe.adsbexchange.com/");
        handler.postDelayed(injector, 2500);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(injector);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}