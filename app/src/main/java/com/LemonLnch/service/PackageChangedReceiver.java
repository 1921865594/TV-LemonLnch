package com.LemonLnch.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.LemonLnch.ui.MainActivity;

public class PackageChangedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);
    }
}
