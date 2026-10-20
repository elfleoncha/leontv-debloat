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
    public static MainActivity instance = null;
    private static final int UPLOAD_PORT = 8756;
    private volatile boolean uploadServerRunning = false;
    private android.media.SoundPool sounds;
    private int sndTick = 0, sndOpen = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
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

        try {
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            sounds = new android.media.SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build();
            sndTick = sounds.load(this, R.raw.tick, 1);
            sndOpen = sounds.load(this, R.raw.open, 1);
        } catch (Exception ignored) {
        }

        startUploadServer();
        applySavedWallpaper();
    }

    private java.io.File wallpaperFile() {
        return new java.io.File(getFilesDir(), "wallpaper.jpg");
    }

    private void applySavedWallpaper() {
        try {
            if (!wallpaperFile().exists()) return;
            byte[] b = new byte[(int) wallpaperFile().length()];
            java.io.FileInputStream in = new java.io.FileInputStream(wallpaperFile());
            int n = in.read(b);
            in.close();
            if (n <= 0) return;
            final String uri = "data:image/jpeg;base64," + Base64.encodeToString(b, Base64.NO_WRAP);
            web.postDelayed(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript("window.__setWallpaper && window.__setWallpaper('" + uri + "')", null);
                }
            }, 900);
        } catch (Exception ignored) {
        }
    }

    private void startUploadServer() {
        android.util.Log.i("VALENHTTP", "startUploadServer called, running=" + uploadServerRunning);
        if (uploadServerRunning) return;
        uploadServerRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (java.net.ServerSocket ss = new java.net.ServerSocket(UPLOAD_PORT)) {
                    android.util.Log.i("VALENHTTP", "listening on " + UPLOAD_PORT);
                    while (uploadServerRunning) {
                        final java.net.Socket sock = ss.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleUpload(sock);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    android.util.Log.e("VALENHTTP", "server died", e);
                }
            }
        }).start();
    }

    private void handleUpload(java.net.Socket sock) {
        try {
            java.io.InputStream in = sock.getInputStream();
            java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream();
            int c;
            byte[] tail = new byte[4];
            while ((c = in.read()) != -1) {
                head.write(c);
                tail[0] = tail[1];
                tail[1] = tail[2];
                tail[2] = tail[3];
                tail[3] = (byte) c;
                if (tail[0] == 13 && tail[1] == 10 && tail[2] == 13 && tail[3] == 10) break;
                if (head.size() > 16384) break;
            }
            String header = new String(head.toByteArray(), "UTF-8");
            String firstLine = header.split("\r?\n")[0];
            java.io.OutputStream out = sock.getOutputStream();

            if (firstLine.startsWith("GET")) {
                String page = UPLOAD_PAGE;
                byte[] body = page.getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.write(body);
            } else if (firstLine.startsWith("POST /upload")) {
                int len = 0;
                for (String line : header.split("\r?\n")) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        len = Integer.parseInt(line.substring(15).trim());
                    }
                }
                byte[] data = new byte[len];
                int read = 0;
                while (read < len) {
                    int r = in.read(data, read, len - read);
                    if (r < 0) break;
                    read += r;
                }
                boolean ok = saveWallpaper(data, read);
                String json = ok ? "{\"ok\":true}" : "{\"ok\":false}";
                byte[] body = json.getBytes("UTF-8");
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.write(body);
                if (ok) applySavedWallpaper();
            } else {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
            }
            out.flush();
            sock.close();
        } catch (Exception ignored) {
            try { sock.close(); } catch (Exception ignored2) {}
        }
    }

    private boolean saveWallpaper(byte[] data, int len) {
        try {
            android.graphics.BitmapFactory.Options opt = new android.graphics.BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(data, 0, len, opt);
            int scale = 1;
            while (opt.outWidth / scale > 1920 || opt.outHeight / scale > 1080) scale *= 2;
            android.graphics.BitmapFactory.Options opt2 = new android.graphics.BitmapFactory.Options();
            opt2.inSampleSize = scale;
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, len, opt2);
            if (bmp == null) return false;
            java.io.FileOutputStream fos = new java.io.FileOutputStream(wallpaperFile());
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, fos);
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
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

    public void showVolumeHud() {
        if (web != null) {
            web.post(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript("window.__volHud && window.__volHud()", null);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
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
        if (k == 24 || k == 25) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                web.evaluateJavascript("window.__vol && window.__vol(" + (k == 24 ? 1 : -1) + ")", null);
            }
            return true;
        }
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

    private static final String UPLOAD_PAGE = """
<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>LeonTV - Fondo de pantalla</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{min-height:100vh;display:flex;align-items:center;justify-content:center;
background:radial-gradient(60vw 60vh at 20% 10%,#1b2340,#05060a);color:#fff;
font-family:-apple-system,'Segoe UI',Roboto,sans-serif;padding:24px}
.card{width:100%;max-width:420px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);
border-radius:24px;padding:28px;backdrop-filter:blur(18px);box-shadow:0 30px 80px rgba(0,0,0,.6)}
h1{font-size:20px;font-weight:600;letter-spacing:.02em;margin-bottom:6px}
p{font-size:13px;color:rgba(255,255,255,.55);margin-bottom:20px;line-height:1.5}
.drop{border:1.5px dashed rgba(255,255,255,.18);border-radius:16px;padding:28px 16px;text-align:center;
font-size:13px;color:rgba(255,255,255,.6);cursor:pointer;transition:.2s}
.drop:hover{border-color:rgba(255,255,255,.4);background:rgba(255,255,255,.04)}
.drop.over{border-color:#8ab4f8;background:rgba(138,180,248,.10)}
button{margin-top:16px;width:100%;padding:14px;border:0;border-radius:14px;font-size:15px;font-weight:600;
background:#fff;color:#0b0d12;cursor:pointer;transition:.15s}
button:disabled{opacity:.35;cursor:default}
button:not(:disabled):active{transform:scale(.98)}
.msg{margin-top:14px;font-size:13px;text-align:center;min-height:18px}
.ok{color:#7ee2a8}.err{color:#ff8a8a}
img.prev{width:100%;border-radius:14px;margin-top:16px;display:none}
</style></head><body>
<div class="card">
<h1>Fondo de pantalla</h1>
<p>Elige una foto y se pondr&aacute; al instante en la tele. Se recorta a 16:9 autom&aacute;ticamente.</p>
<div class="drop" id="drop">Toca para elegir una imagen<br><small>o arr&aacute;strala aqu&iacute;</small></div>
<input type="file" id="file" accept="image/*" hidden>
<button id="go" disabled>Aplicar fondo</button>
<img class="prev" id="prev">
<div class="msg" id="msg"></div>
</div>
<script>
const drop=document.getElementById('drop'),file=document.getElementById('file'),
go=document.getElementById('go'),msg=document.getElementById('msg'),prev=document.getElementById('prev');
let blob=null;
drop.onclick=()=>file.click();
file.onchange=e=>{if(e.target.files[0])pick(e.target.files[0])};
['dragover','dragenter'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.add('over')}));
['dragleave','drop'].forEach(t=>drop.addEventListener(t,e=>{e.preventDefault();drop.classList.remove('over')}));
drop.addEventListener('drop',e=>{if(e.dataTransfer.files[0])pick(e.dataTransfer.files[0])});
function pick(f){blob=f;prev.src=URL.createObjectURL(f);prev.style.display='block';go.disabled=false;msg.textContent='';}
go.onclick=async()=>{
  if(!blob)return;
  go.disabled=true;msg.className='msg';msg.textContent='Enviando...';
  try{const r=await fetch('/upload',{method:'POST',body:blob});
    const j=await r.json();
    if(j.ok){msg.className='msg ok';msg.textContent='Fondo aplicado en la tele';}
    else{msg.className='msg err';msg.textContent='No se pudo aplicar';}
  }catch(e){msg.className='msg err';msg.textContent='Error de red';}
  go.disabled=false;
};
</script></body></html>
""";

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
        public void tick() {
            try {
                if (sounds != null && sndTick != 0) sounds.play(sndTick, 0.75f, 0.75f, 1, 0, 1f);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void openSound() {
            try {
                if (sounds != null && sndOpen != 0) sounds.play(sndOpen, 0.80f, 0.80f, 1, 0, 1f);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public int volMax() {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                return am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            } catch (Exception e) {
                return 15;
            }
        }

        @JavascriptInterface
        public int volGet() {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            } catch (Exception e) {
                return 0;
            }
        }

        @JavascriptInterface
        public void volSet(int v) {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                if (v < 0) v = 0;
                if (v > max) v = max;
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0);
            } catch (Exception ignored) {
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
