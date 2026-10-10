package com.dingxiao.jiugongge;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView web;
    private TextToSpeech tts;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQ = 10001;

    private boolean pageLoaded = false;
    private boolean fromAssets = false;
    private boolean askedPerm = false;

    /* 外部最新网页（改网页不用重装 APK）；没有这个文件时用 APK 内置版 */
    private static final String EXT_PAGE = "/sdcard/Download/ks_app/kingSchool.html";

    public class TtsBridge {
        @JavascriptInterface
        public void speak(String text) {
            if (tts == null || text == null) return;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w");
        }
        @JavascriptInterface
        public void saveBackup(String json) {
            try {
                File dir = new File("/sdcard/Download/ks_app");
                if (!dir.exists()) dir.mkdirs();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(new File(dir, "存档备份.json"));
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Exception e) { }
        }
    }

    private boolean hasExtPerm() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private void loadExternal() {
        web.loadUrl("file://" + EXT_PAGE);
        pageLoaded = true;
        fromAssets = false;
    }

    private void loadAssets() {
        web.loadUrl("file:///android_asset/index.html");
        pageLoaded = true;
        fromAssets = true;
    }

    private void loadPage() {
        if (pageLoaded) return;
        File f = new File(EXT_PAGE);
        if (hasExtPerm() && f.exists() && f.canRead() && f.length() > 4096) {
            loadExternal();
        } else if (hasExtPerm()) {
            loadAssets();
        }
        /* 无权限：不加载，等授权后 onResume 重试 */
    }

    private void askPerm() {
        try {
            Intent it = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            it.setData(Uri.parse("package:" + getPackageName()));
            startActivity(it);
        } catch (Exception e) {
            try {
                Intent it2 = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(it2);
            } catch (Exception e2) { }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.US);
            }
        });

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);

        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQ);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(web);
        loadPage();
        if (!pageLoaded && !askedPerm) {
            askedPerm = true;
            askPerm();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!pageLoaded) {
            loadPage();
        } else if (fromAssets && hasExtPerm()) {
            File f = new File(EXT_PAGE);
            if (f.exists() && f.canRead() && f.length() > 4096) {
                web.loadUrl("file://" + EXT_PAGE);   /* 授权后从内置版切到最新外部版 */
                pageLoaded = true;
                fromAssets = false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQ) {
            if (filePathCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null) {
                    result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.__appBack ? window.__appBack() : 'EXIT'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (v == null || v.contains("EXIT")) { MainActivity.super.onBackPressed(); }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}