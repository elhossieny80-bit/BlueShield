package com.blueshield.invoices;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * أبلكيشن BlueShield: بيفتح البرنامج (Apps Script) جوه شاشة كاملة،
 * من غير شريط المتصفح ومن غير رسالة Google اللي فوق.
 */
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 11;
    private static final int REQ_FILE = 12;

    private WebView web;
    private ProgressBar bar;
    private View offline;
    private String appUrl;

    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private ValueCallback<Uri[]> fileCallback;
    private long lastBack = 0;

    /* بيخلي إطار البرنامج ياخد الشاشة كلها، فيستخبى الشريط الرمادي بتاع Google */
    private static final String FULLSCREEN_JS =
            "(function(){try{var f=document.getElementById('sandboxFrame')||document.querySelector('iframe');"
            + "if(!f)return;f.style.cssText='position:fixed;top:0;left:0;right:0;bottom:0;width:100%;height:100%;border:0;margin:0;z-index:2147483647;background:#fff';"
            + "document.documentElement.style.overflow='hidden';document.body.style.overflow='hidden';}catch(e){}})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appUrl = getString(R.string.app_url);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        bp.gravity = Gravity.TOP;
        root.addView(bar, bp);

        offline = buildOfflineView();
        offline.setVisibility(View.GONE);
        root.addView(offline, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        setupWebView();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            loadApp();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private View buildOfflineView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.WHITE);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView t = new TextView(this);
        t.setText("مفيش إنترنت دلوقتي\nاتأكد إن الموبايل متوصل بالنت، وبعدين دوس «حاول تاني».");
        t.setTextSize(18);
        t.setTextColor(Color.rgb(23, 54, 93));
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        Button b = new Button(this);
        b.setText("حاول تاني");
        b.setOnClickListener(v -> loadApp());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20);
        box.addView(b, lp);
        return box;
    }

    private boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    private void loadApp() {
        if (!online()) {
            offline.setVisibility(View.VISIBLE);
            return;
        }
        offline.setVisibility(View.GONE);
        web.loadUrl(appUrl);
    }

    private static boolean isAppHost(Uri u) {
        if (u == null || u.getHost() == null) return false;
        String s = u.getScheme();
        if (!"https".equals(s)) return false;
        String h = u.getHost().toLowerCase();
        return h.equals("script.google.com") || h.endsWith(".googleusercontent.com") || h.equals("accounts.google.com")
                || h.endsWith(".gstatic.com") || h.endsWith(".googleapis.com");
    }

    /* أي لينك برّه البرنامج (واتساب، خرايط، تليفون...) يفتح في الأبلكيشن بتاعه */
    private void openOutside(String url) {
        try {
            Intent i;
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "مفيش أبلكيشن على الموبايل يفتح اللينك ده.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "مقدرتش أفتح اللينك.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupWebView() {
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        ws.setSupportMultipleWindows(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setTextZoom(100);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "BSApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (isAppHost(u)) return false;
                if (!req.isForMainFrame() && "https".equals(u.getScheme())) return false;
                openOutside(u.toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(FULLSCREEN_JS, null);
                Handler h = new Handler(Looper.getMainLooper());
                h.postDelayed(() -> view.evaluateJavascript(FULLSCREEN_JS, null), 600);
                h.postDelayed(() -> view.evaluateJavascript(FULLSCREEN_JS, null), 2000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) offline.setVisibility(View.VISIBLE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                if (p >= 60) view.evaluateJavascript(FULLSCREEN_JS, null);
            }

            /* الحضور والانصراف بالموقع */
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    geoCallback = callback;
                    geoOrigin = origin;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }

            /* لينك بيفتح في صفحة جديدة (target=_blank / window.open): نفتحه برّه */
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    private boolean done = false;
                    private void go(WebView v, String url) {
                        if (done || url == null || url.isEmpty() || url.startsWith("about:")) return;
                        done = true;
                        openOutside(url);
                        v.stopLoading();
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        go(v, req.getUrl().toString());
                        return true;
                    }
                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                        go(v, url);
                    }
                });
                WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
                tr.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }

            /* رفع صورة أو ملف من الموبايل */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        /* تنزيل ملف عادي (مش من جوه البرنامج) */
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) return;
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, cd, mime);
                r.setMimeType(mime);
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "بينزل في «التنزيلات»…", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                openOutside(url);
            }
        });
    }

    /* البرنامج بيبعت ملفات الـ PDF والإكسيل هنا، فتتحفظ في «التنزيلات» وتتفتح */
    private class Bridge {
        @JavascriptInterface
        public void saveFile(String b64, String name, String mime) {
            final String fname = safeName(name);
            final String type = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                Uri uri = writeDownload(data, fname, type);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "اتحفظ في «التنزيلات»: " + fname, Toast.LENGTH_LONG).show();
                    if (uri != null) openFile(uri, type);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "مقدرتش أحفظ الملف.", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }
    }

    private static String safeName(String n) {
        String s = n == null ? "file" : n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "file" : s;
    }

    private Uri writeDownload(byte[] data, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("insert failed");
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new Exception("no stream");
                os.write(data);
            }
            return uri;
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, name);
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(data);
            }
            return null;
        }
    }

    private void openFile(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            /* مفيش أبلكيشن يفتح النوع ده — الملف محفوظ في «التنزيلات» */
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOCATION && geoCallback != null) {
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, ok, false);
            if (!ok) {
                new AlertDialog.Builder(this)
                        .setMessage("من غير إذن الموقع، مش هتقدر تسجّل الحضور والانصراف.\nتقدر تديله الإذن بعدين من إعدادات الموبايل ← Apps ← BlueShield ← Permissions ← Location.")
                        .setPositiveButton("تمام", null).show();
            }
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    /* زرار الرجوع: دوستين ورا بعض يقفلوا الأبلكيشن (عشان البرنامج مايتقفلش بالغلط) */
    @Override
    public void onBackPressed() {
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "دوس رجوع تاني عشان تقفل البرنامج", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }
}
