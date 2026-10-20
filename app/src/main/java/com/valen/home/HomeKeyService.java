package com.valen.home;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The Mi TV Stick 4K remote sends KEYCODE_ALL_APPS (284) instead of KEYCODE_HOME.
 * Android 9 only delivers that key to the system launcher, so this service turns it
 * into a real HOME action while ValenHome is the default launcher.
 */
public class HomeKeyService extends AccessibilityService {

    private String lastPkg = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && event.getPackageName() != null) {
                lastPkg = event.getPackageName().toString();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (k == 284 && event.getAction() == KeyEvent.ACTION_DOWN) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            return true;
        }
        // Volume keys: handled here so the stock SystemUI panel never shows while Home is on screen.
        if ((k == 24 || k == 25) && "com.valen.home".equals(lastPkg)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                try {
                    android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                    int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                    int cur = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                    int next = cur + (k == 24 ? 1 : -1);
                    if (next < 0) next = 0;
                    if (next > max) next = max;
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, next, 0);
                } catch (Exception ignored) {
                }
                if (MainActivity.instance != null) MainActivity.instance.showVolumeHud();
            }
            return true;
        }
        return super.onKeyEvent(event);
    }
}
