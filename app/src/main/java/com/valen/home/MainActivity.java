package com.valen.home;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView web;
    private volatile boolean overlayOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setBlockNetworkLoads(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setDefaultFontSize(16);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.i("VALENWEB", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new Bridge(), "valen");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.postDelayed(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript("window.__refresh && window.__refresh()", null);
                }
            }, 350);
        }
    }

    @Override
    public void onBackPressed() {
        if (overlayOpen) {
            web.evaluateJavascript("window.__closeOverlay && window.__closeOverlay();", null);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == 19 || k == 20 || k == 21 || k == 22 || k == 23 || k == 66 || k == 4 || k == 82) {
                web.evaluateJavascript("window.__key && window.__key(" + k + ")", null);
                return true;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (k == 19 || k == 20 || k == 21 || k == 22 || k == 23 || k == 66 || k == 4 || k == 82) {
                web.evaluateJavascript("window.__key && window.__key(-" + k + ")", null);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private String foldersJson() {
        String fallback = "{\"folders\":[{\"name\":\"VER\",\"apps\":[\"com.netflix.ninja\",\"com.google.android.youtube.tv\",\"com.pecana.iptvextremepro\",\"com.android.mgstv\"]},"
                + "{\"name\":\"APPS\",\"apps\":[\"org.smarttube.stable\",\"org.videolan.vlc\",\"org.localsend.localsend_app\",\"com.yablio.sendfilestotv\"]},"
                + "{\"name\":\"EXTRA\",\"apps\":[\"com.phlox.tvwebbrowser\",\"com.android.vending\",\"com.mxtech.videoplayer.ad\",\"com.google.android.apps.mediashell\"]}]}";
        try {
            File f = new File(getExternalFilesDir(null), "folders.json");
            if (!f.exists()) {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                out.write(fallback.getBytes(StandardCharsets.UTF_8));
                out.close();
                return fallback;
            }
            byte[] buf = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int n = in.read(buf);
            in.close();
            if (n > 0) return new String(buf, 0, n, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private String iconDataUri(String pkg) {
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            int size = 72;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            if (d instanceof BitmapDrawable) {
                d.setBounds(0, 0, size, size);
            } else {
                d.setBounds(0, 0, size, size);
            }
            d.draw(c);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.WEBP, 80, bos);
            return "data:image/webp;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public class Bridge {
        @JavascriptInterface
        public String folders() {
            return foldersJson();
        }

        @JavascriptInterface
        public String app(String pkg) {
            JSONObject o = new JSONObject();
            try {
                PackageManager pm = getPackageManager();
                if (pm.getLaunchIntentForPackage(pkg) == null) throw new Exception("not launchable");
                String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                o.put("pkg", pkg);
                o.put("label", label);
                o.put("icon", iconDataUri(pkg));
                o.put("ok", true);
            } catch (Exception e) {
                try {
                    o.put("ok", false);
                    o.put("pkg", pkg);
                } catch (Exception ignored) {
                }
            }
            return o.toString();
        }

        @JavascriptInterface
        public void launch(String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (Exception ignored) {
                }
            }
        }

        @JavascriptInterface
        public void open(String what) {
            String action = Settings.ACTION_SETTINGS;
            if ("wifi".equals(what)) action = Settings.ACTION_WIFI_SETTINGS;
            else if ("apps".equals(what)) action = Settings.ACTION_APPLICATION_SETTINGS;
            else if ("display".equals(what)) action = Settings.ACTION_DISPLAY_SETTINGS;
            else if ("sound".equals(what)) action = Settings.ACTION_SOUND_SETTINGS;
            else if ("about".equals(what)) action = Settings.ACTION_DEVICE_INFO_SETTINGS;
            try {
                startActivity(new Intent(action));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void settings() {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void setOverlay(boolean open) {
            overlayOpen = open;
        }

        @JavascriptInterface
        public String all() {
            org.json.JSONArray out = new org.json.JSONArray();
            java.util.HashMap<String, String> seen = new java.util.HashMap<>();
            PackageManager pm = getPackageManager();
            String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(cat);
                for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || seen.containsKey(pkg)) continue;
                    try {
                        if (pm.getApplicationInfo(pkg, 0) == null) continue;
                        JSONObject o = new JSONObject();
                        o.put("pkg", pkg);
                        o.put("cmp", pkg + "/" + ri.activityInfo.name);
                        o.put("label", pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
                        o.put("icon", iconDataUri(pkg));
                        o.put("cat", pm.getApplicationInfo(pkg, 0).category);
                        seen.put(pkg, "");
                        out.put(o);
                    } catch (Exception ignored) {
                    }
                }
            }
            return out.toString();
        }

        @JavascriptInterface
        public void launchCmp(String cmp) {
            try {
                int i = cmp.indexOf('/');
                String pkg = cmp.substring(0, i);
                String cls = cmp.substring(i + 1);
                Intent it = new Intent(Intent.ACTION_MAIN);
                it.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                it.setComponent(new android.content.ComponentName(pkg, cls));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } catch (Exception e) {
                try {
                    Intent it2 = new Intent(Intent.ACTION_MAIN);
                    it2.setComponent(new android.content.ComponentName(cmp.substring(0, cmp.indexOf('/')), cmp.substring(cmp.indexOf('/') + 1)));
                    it2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it2);
                } catch (Exception ignored) {
                }
            }
        }

        @JavascriptInterface
        public String info() {
            JSONObject o = new JSONObject();
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                o.put("ramFree", mi.availMem / (1024 * 1024));
                o.put("ramTotal", mi.totalMem / (1024 * 1024));
                java.io.File data = new java.io.File("/data");
                android.os.StatFs st = new android.os.StatFs(data.getPath());
                o.put("diskFree", (st.getAvailableBytes() / (1024 * 1024)));
                o.put("diskTotal", (st.getTotalBytes() / (1024 * 1024)));
                o.put("model", android.os.Build.MODEL);
                o.put("android", android.os.Build.VERSION.RELEASE);
                String ip = "";
                String ssid = "";
                try {
                    android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    if (wm != null && wm.getConnectionInfo() != null) {
                        ssid = wm.getConnectionInfo().getSSID().replace("\"", "");
                    }
                } catch (Exception ignored) {
                }
                try {
                    android.net.wifi.WifiManager wm2 = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                    int ipInt = wm2 == null ? 0 : wm2.getConnectionInfo().getIpAddress();
                    if (ipInt != 0) {
                        ip = String.format("%d.%d.%d.%d", (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
                    }
                } catch (Exception ignored) {
                }
                java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
                while (ifs.hasMoreElements()) {
                    java.net.NetworkInterface ni = ifs.nextElement();
                    if (ip.length() > 0) break;
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (java.net.InetAddress a : java.util.Collections.list(ni.getInetAddresses())) {
                        if (a instanceof java.net.Inet4Address) {
                            ip = a.getHostAddress();
                            break;
                        }
                    }
                    if (!ip.isEmpty()) break;
                }
                if (ip.isEmpty()) {
                    try {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                                Runtime.getRuntime().exec(new String[]{"ip", "-4", "addr", "show", "wlan0"}).getInputStream()));
                        String line;
                        while ((line = br.readLine()) != null) {
                            int k = line.indexOf("inet ");
                            if (k >= 0) {
                                String rest = line.substring(k + 5).trim();
                                ip = rest.split("/")[0];
                                break;
                            }
                        }
                        br.close();
                    } catch (Exception ignored) {
                    }
                }
                o.put("ssid", ssid);
                o.put("ip", ip);
                o.put("apps", 0);
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public void log(String msg) {
            android.util.Log.i("VALENJS", msg);
        }

        @JavascriptInterface
        public String time() {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
