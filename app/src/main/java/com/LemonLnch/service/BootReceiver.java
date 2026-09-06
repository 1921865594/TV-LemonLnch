package com.LemonLnch.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.LemonLnch.ui.MainActivity;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        boolean bootAction = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        boolean wakeAction = Intent.ACTION_DREAMING_STOPPED.equals(action);

        if (bootAction && !context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
                .getBoolean("start_on_boot", true)) return;
        if (wakeAction && !context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)
                .getBoolean("auto_open_on_wake", true)) return;

        launchNow(context);
        if (bootAction) {
            scheduleLauncher(context, 350, 201);
            scheduleLauncher(context, 900, 202);
            scheduleLauncher(context, 1800, 203);
            scheduleLauncher(context, 3200, 204);
        } else {
            scheduleLauncher(context, 350, 205);
            scheduleLauncher(context, 900, 206);
        }
    }

    private void launchNow(Context context) {
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launch);
        } catch (Exception ignored) {
        }
    }

    private void scheduleLauncher(Context context, long delayMs, int requestCode) {
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                requestCode,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, pending);
        }
    }
}
