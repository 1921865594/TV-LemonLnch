package com.LemonLnch.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.LemonLnch.system.RootShell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MemoryCleaner {
    private static final String PREFS_NAME = "launcher_settings";
    private static final String KEY_AUTO_CLEAN_ENABLED = "auto_clean_enabled";
    private static final String KEY_WHITE_LIST = "auto_clean_white_list";

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MemoryCleaner(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isAutoCleanEnabled() {
        return prefs.getBoolean(KEY_AUTO_CLEAN_ENABLED, false);
    }

    public void setAutoCleanEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CLEAN_ENABLED, enabled).apply();
    }

    public Set<String> getWhiteList() {
        return new HashSet<>(prefs.getStringSet(KEY_WHITE_LIST, new HashSet<>()));
    }

    public void setWhiteList(Set<String> whiteList) {
        prefs.edit().putStringSet(KEY_WHITE_LIST, whiteList).apply();
    }

    public void addToWhiteList(String packageName) {
        Set<String> set = getWhiteList();
        set.add(packageName);
        setWhiteList(set);
    }

    public void removeFromWhiteList(String packageName) {
        Set<String> set = getWhiteList();
        set.remove(packageName);
        setWhiteList(set);
    }

    public List<AppInfo> getInstalledApps() {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo appInfo : installed) {
            String pkg = appInfo.packageName;
            // 只显示有启动界面的应用
            if (pm.getLaunchIntentForPackage(pkg) != null || pm.getLeanbackLaunchIntentForPackage(pkg) != null) {
                String label = appInfo.loadLabel(pm).toString();
                Drawable icon = appInfo.loadIcon(pm);
                apps.add(new AppInfo(pkg, label, icon));
            }
        }
        return apps;
    }

    public static class AppInfo {
        public final String packageName;
        public final String label;
        public final Drawable icon;

        public AppInfo(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    public interface CleanCallback {
        void onCleaned(long freedMb);
    }

    public void cleanMemory(boolean showToast, CleanCallback callback) {
        new Thread(() -> {
            long freed = doClean();
            if (showToast) {
                mainHandler.post(() -> Toast.makeText(context, "清理完成，释放 " + freed + " MB", Toast.LENGTH_SHORT).show());
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onCleaned(freed));
            }
        }).start();
    }

    private long doClean() {
        long before = getAvailableMemory();
        Set<String> whiteList = getWhiteList();
        // 自身包名始终保留
        whiteList.add(context.getPackageName());

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;

        List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
        if (runningProcesses != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                String pkg = processInfo.processName;
                if (pkg == null) continue;
                // processName 可能包含多个包名以冒号分隔，取第一个
                if (pkg.contains(":")) {
                    pkg = pkg.split(":")[0];
                }
                if (whiteList.contains(pkg)) continue;
                if (isSystemPackage(pkg)) continue;

                if (RootShell.isRootAvailable()) {
                    RootShell.exec("am force-stop " + pkg);
                    RootShell.exec("killall " + pkg);
                } else {
                    am.killBackgroundProcesses(pkg);
                }
            }
        }

        // 等待系统回收内存
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long after = getAvailableMemory();
        long freed = after - before;
        return Math.max(freed, 0);
    }

    private long getAvailableMemory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.getMemoryInfo(mi);
            return mi.availMem / (1024 * 1024); // MB
        }
        return 0;
    }

    private boolean isSystemPackage(String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}