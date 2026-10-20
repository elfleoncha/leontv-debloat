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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == 284 && event.getAction() == KeyEvent.ACTION_DOWN) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            return true;
        }
        return super.onKeyEvent(event);
    }
}
