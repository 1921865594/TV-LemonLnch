package com.LemonLnch.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;

/**
 * 管理应用直达的数字映射和自定义按键映射。
 * 数据存储在 SharedPreferences("launcher_settings") 中。
 */
public class AppShortcutManager {

    // SharedPreferences 键前缀
    private static final String PREF_DIGIT_PKG = "shortcut_digit_";
    private static final String PREF_DIGIT_LABEL = "shortcut_digit_label_";
    private static final String PREF_CUSTOM_CODE = "shortcut_custom_code";
    private static final String PREF_CUSTOM_KEYNAME = "shortcut_custom_keyname";
    private static final String PREF_CUSTOM_PKG = "shortcut_custom_pkg";
    private static final String PREF_CUSTOM_LABEL = "shortcut_custom_label";

    private final SharedPreferences prefs;
    private final Context context;

    public AppShortcutManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE);
    }

    // ==================== 数字映射 ====================

    /**
     * 获取数字键对应的应用快捷方式，未绑定返回 null。
     */
    public AppShortcut getDigitMapping(String digit) {
        String pkg = prefs.getString(PREF_DIGIT_PKG + digit, "");
        String label = prefs.getString(PREF_DIGIT_LABEL + digit, "");
        if (TextUtils.isEmpty(pkg)) return null;
        return new AppShortcut(pkg, label);
    }

    /**
     * 设置数字键绑定。
     */
    public void setDigitMapping(String digit, String pkg, String label) {
        prefs.edit()
                .putString(PREF_DIGIT_PKG + digit, pkg)
                .putString(PREF_DIGIT_LABEL + digit, label)
                .apply();
    }

    /**
     * 清空数字键绑定。
     */
    public void clearDigitMapping(String digit) {
        prefs.edit()
                .remove(PREF_DIGIT_PKG + digit)
                .remove(PREF_DIGIT_LABEL + digit)
                .apply();
    }

    /**
     * 判断数字键是否已绑定。
     */
    public boolean isDigitBound(String digit) {
        return !TextUtils.isEmpty(prefs.getString(PREF_DIGIT_PKG + digit, ""));
    }

    // ==================== 自定义映射 ====================

    /**
     * 获取自定义按键映射，未绑定返回 null。
     */
    public CustomShortcut getCustomMapping() {
        int code = prefs.getInt(PREF_CUSTOM_CODE, -1);
        if (code == -1) return null;
        String keyName = prefs.getString(PREF_CUSTOM_KEYNAME, "");
        String pkg = prefs.getString(PREF_CUSTOM_PKG, "");
        String label = prefs.getString(PREF_CUSTOM_LABEL, "");
        if (TextUtils.isEmpty(pkg)) return null;
        return new CustomShortcut(code, keyName, pkg, label);
    }

    /**
     * 设置自定义按键绑定。
     */
    public void setCustomMapping(int keyCode, String keyName, String pkg, String label) {
        prefs.edit()
                .putInt(PREF_CUSTOM_CODE, keyCode)
                .putString(PREF_CUSTOM_KEYNAME, keyName)
                .putString(PREF_CUSTOM_PKG, pkg)
                .putString(PREF_CUSTOM_LABEL, label)
                .apply();
    }

    /**
     * 清空自定义按键绑定。
     */
    public void clearCustomMapping() {
        prefs.edit()
                .remove(PREF_CUSTOM_CODE)
                .remove(PREF_CUSTOM_KEYNAME)
                .remove(PREF_CUSTOM_PKG)
                .remove(PREF_CUSTOM_LABEL)
                .apply();
    }

    public boolean isCustomBound() {
        return prefs.getInt(PREF_CUSTOM_CODE, -1) != -1;
    }

    // ==================== 启动辅助 ====================

    /**
     * 根据包名获取启动 Intent，优先使用 Leanback 启动器，回退到普通启动器。
     * 若应用未安装，返回 null。
     */
    public Intent getLaunchIntentForPackage(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) {
            launch = pm.getLaunchIntentForPackage(packageName);
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return launch;
    }

    /**
     * 判断应用是否已安装。
     */
    public boolean isAppInstalled(String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ==================== 数据类 ====================

    public static class AppShortcut {
        public final String packageName;
        public final String label;

        AppShortcut(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    public static class CustomShortcut {
        public final int keyCode;
        public final String keyName;
        public final String packageName;
        public final String label;

        CustomShortcut(int keyCode, String keyName, String packageName, String label) {
            this.keyCode = keyCode;
            this.keyName = keyName;
            this.packageName = packageName;
            this.label = label;
        }
    }
}