package com.LemonLnch.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.LemonLnch.ui.MainActivity;

public class HomeAccessibilityService extends AccessibilityService {
    private static final long LAUNCH_COOLDOWN_MS = 90;
    private static final long FOLLOW_UP_LAUNCH_MS = 140;
    private static final long SECOND_FOLLOW_UP_LAUNCH_MS = 420;
    private static final long THIRD_FOLLOW_UP_LAUNCH_MS = 820;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable followUpLaunch = this::launchHomeNow;
    private final Runnable secondFollowUpLaunch = this::launchHomeNow;
    private final Runnable thirdFollowUpLaunch = this::launchHomeNow;
    private long lastLaunchAt;

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.notificationTimeout = 0;
            setServiceInfo(info);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_UP) return false;
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            requestHomeBurst();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!getSharedPreferences("launcher_settings", MODE_PRIVATE)
                .getBoolean("override_current_launcher", true)) return;
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!isKnownSystemLauncher(pkg)) return;

        requestHomeBurst();
    }

    private void requestHomeBurst() {
        if (!getSharedPreferences("launcher_settings", MODE_PRIVATE)
                .getBoolean("override_current_launcher", true)) return;
        long now = System.currentTimeMillis();
        if (now - lastLaunchAt < LAUNCH_COOLDOWN_MS) return;
        lastLaunchAt = now;

        handler.removeCallbacks(followUpLaunch);
        handler.removeCallbacks(secondFollowUpLaunch);
        handler.removeCallbacks(thirdFollowUpLaunch);
        launchHomeNow();
        handler.postDelayed(followUpLaunch, FOLLOW_UP_LAUNCH_MS);
        handler.postDelayed(secondFollowUpLaunch, SECOND_FOLLOW_UP_LAUNCH_MS);
        handler.postDelayed(thirdFollowUpLaunch, THIRD_FOLLOW_UP_LAUNCH_MS);
    }

    private void launchHomeNow() {
        if (!getSharedPreferences("launcher_settings", MODE_PRIVATE)
                .getBoolean("override_current_launcher", true)) return;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    private boolean isKnownSystemLauncher(String pkg) {
        String lower = pkg == null ? "" : pkg.toLowerCase();
        return "com.google.android.tvlauncher".equals(pkg)
                || "com.google.android.leanbacklauncher".equals(pkg)
                || "com.google.android.apps.tv.launcherx".equals(pkg)
                || "com.amazon.tv.launcher".equals(pkg)
                || (lower.startsWith("com.google.android") && lower.contains("launcher"));
    }
}
