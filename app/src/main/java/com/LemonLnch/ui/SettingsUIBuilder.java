package com.LemonLnch.ui;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.LemonLnch.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsUIBuilder {
    private final MainActivity activity;

    SettingsUIBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build(String selected) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(52, 52, 52));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.HORIZONTAL);
        int padLeftRight = activity.screenWidthDp < 700 ? activity.dp(24) : activity.dp(58);
        int menuWidth = activity.screenWidthDp < 700 ? activity.dp(240) : activity.dp(396);
        page.setPadding(padLeftRight, activity.dp(58), padLeftRight, activity.dp(34));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        page.addView(menu, new LinearLayout.LayoutParams(menuWidth, -1));

        TextView title = new TextView(activity);
        title.setText(R.string.settings_title);
        title.setTextColor(Color.argb(120, 255, 255, 255));
        title.setTextSize(activity.screenWidthDp < 700 ? 28 : 36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = activity.dp(28);
        menu.addView(title, titleLp);

        ScrollView menuScroll = new ScrollView(activity);
        menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        menuScroll.setClipChildren(false);
        menuScroll.setClipToPadding(false);
        menuScroll.setPadding(0, 0, 0, activity.dp(24));
        menu.addView(menuScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout menuItems = new LinearLayout(activity);
        menuItems.setOrientation(LinearLayout.VERTICAL);
        menuItems.setClipChildren(false);
        menuItems.setClipToPadding(false);
        menuScroll.addView(menuItems, new ScrollView.LayoutParams(-1, -2));

        String[] sections = {
                activity.getString(R.string.section_theme),
                activity.getString(R.string.section_controls),
                activity.getString(R.string.shortcut_section),
                activity.getString(R.string.section_language),
                activity.getString(R.string.section_permissions),
                activity.getString(R.string.section_system_settings),
                activity.getString(R.string.section_about),
                activity.getString(R.string.section_help)
        };

        View selectedMenuItem = null;
        for (String section : sections) {
            boolean isSelected = section.equals(selected);
            TextView item = settingsMenuItem(section, isSelected);

            if (activity.getString(R.string.section_system_settings).equals(section)) {
                item.setOnClickListener(v -> activity.openGoogleSettings());
            } else {
                item.setOnClickListener(v -> activity.showSettings(section));
            }

            item.setOnFocusChangeListener((v, hasFocus) -> {
                animateFocus(v, hasFocus, 1.0f);
                if (hasFocus) {
                    menuScroll.post(() -> menuScroll.requestChildRectangleOnScreen(menuItems,
                            new Rect(v.getLeft(), v.getTop() - activity.dp(12), v.getRight(), v.getBottom() + activity.dp(12)), false));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(56));
            lp.bottomMargin = activity.dp(12);
            menuItems.addView(item, lp);
            if (isSelected) selectedMenuItem = item;
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, activity.dp(4), activity.dp(12), activity.dp(40));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(0, -1, 1);
        scrollLp.leftMargin = activity.screenWidthDp < 700 ? activity.dp(16) : activity.dp(72);
        page.addView(scroll, scrollLp);

        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setClipChildren(false);
        options.setClipToPadding(false);
        options.setPadding(0, 0, activity.dp(34), activity.dp(32));
        scroll.addView(options, new ScrollView.LayoutParams(-1, -2));

        boolean restoreOptionFocus = !TextUtils.isEmpty(activity.pendingSettingsFocusTitle);
        fillSettingsOptions(options, selected);
        activity.setContentView(root);
        if (activity.pendingSettingsScrollY >= 0) {
            final int y = activity.pendingSettingsScrollY;
            activity.pendingSettingsScrollY = -1;
            scroll.post(() -> scroll.scrollTo(0, y));
        }
        if ((restoreOptionFocus && !TextUtils.isEmpty(activity.pendingSettingsFocusTitle)) && selectedMenuItem != null) {
            activity.pendingSettingsFocusTitle = null;
            View target = selectedMenuItem;
            target.post(target::requestFocus);
        } else if (!restoreOptionFocus && selectedMenuItem != null) {
            View target = selectedMenuItem;
            target.post(target::requestFocus);
        }
    }

    private void fillSettingsOptions(LinearLayout options, String selected) {
        if (activity.getString(R.string.section_theme).equals(selected)) {
            addHeading(options, activity.getString(R.string.theme_wallpaper));
            addModeOption(options, activity.getString(R.string.mode_light), activity.getString(R.string.mode_light_desc), "light");
            addModeOption(options, activity.getString(R.string.mode_dark), activity.getString(R.string.mode_dark_desc), "dark");
            addOption(options, activity.getString(R.string.theme_custom_image), activity.getString(R.string.theme_custom_image_desc), "image".equals(activity.bgMode()), v -> activity.openPicker("image/*", MainActivity.REQ_IMAGE));
            addOption(options, activity.getString(R.string.theme_custom_url), activity.getString(R.string.theme_custom_url_desc), "url".equals(activity.bgMode()), v -> activity.askWallpaperUrl());
            addOption(options, activity.getString(R.string.theme_custom_video), activity.getString(R.string.theme_custom_video_desc), "video".equals(activity.bgMode()), v -> activity.openPicker("video/*", MainActivity.REQ_VIDEO));
            addOption(options, activity.getString(R.string.theme_custom_gradient), activity.getString(R.string.theme_custom_gradient_desc), "gradient".equals(activity.bgMode()), v -> {
                activity.rememberSettingsState(v, activity.getString(R.string.theme_custom_gradient));
                activity.showGradientPicker();
            });
            return;
        }
        if (activity.getString(R.string.section_controls).equals(selected)) {
            // 直播预览开关必须位于“控制”页顶端，默认关闭。
            addToggle(options, activity.getString(R.string.live_preview_title),
                    activity.getString(R.string.live_preview_desc),
                    "live_preview_enabled", activity.getString(R.string.section_controls));

            addToggle(options, activity.getString(R.string.appearance_use_24h), activity.getString(R.string.appearance_use_24h_desc), "use_24h", activity.getString(R.string.section_controls));

            addHeading(options, activity.getString(R.string.controls_apps_per_row));
            addAppsOption(options, 5);
            addAppsOption(options, 6);
            addAppsOption(options, 7);
            addHeading(options, activity.getString(R.string.controls_home_launcher));
            addOption(options, activity.getString(R.string.controls_set_default), activity.homeStatusText(), activity.isDefaultHomeLauncher(), v -> activity.requestDefaultHomeLauncher());
            addOption(options, activity.getString(R.string.controls_test_home), activity.getString(R.string.controls_test_home_desc), false, v -> activity.testHomeLauncher());

            addHeading(options, activity.getString(R.string.auto_clean_title));
            addToggle(options, activity.getString(R.string.auto_clean_toggle_title),
                    activity.getString(R.string.auto_clean_toggle_desc),
                    "auto_clean_enabled", activity.getString(R.string.section_controls));

            addOption(options, activity.getString(R.string.auto_clean_whitelist_setting),
                    activity.getString(R.string.auto_clean_whitelist_desc),
                    false, v -> showWhitelistDialog());

            // ===== 新增：隐藏应用管理 =====
            addHeading(options, "隐藏应用管理");
            addOption(options, "查看已隐藏应用",
                    "点击查看并恢复被隐藏的应用",
                    false, v -> showHiddenAppsDialog());

            addHeading(options, activity.getString(R.string.controls_remote_boot));
            addToggle(options, activity.getString(R.string.controls_override_back), activity.getString(R.string.controls_override_back_desc), "override_back_exit", activity.getString(R.string.section_controls));
            addToggle(options, activity.getString(R.string.controls_start_boot), activity.getString(R.string.controls_start_boot_desc), "start_on_boot", activity.getString(R.string.section_controls));
            addToggle(options, activity.getString(R.string.controls_auto_wake), activity.getString(R.string.controls_auto_wake_desc), "auto_open_on_wake", activity.getString(R.string.section_controls));
            addHeading(options, activity.getString(R.string.controls_accessibility));
            addOption(options, activity.getString(R.string.controls_allow_restricted), activity.getString(R.string.controls_allow_restricted_desc), false, v -> {
                activity.toast(activity.getString(R.string.controls_allow_restricted_toast));
                activity.openAppInfo(activity.getPackageName());
            });
            addOption(options, activity.getString(R.string.controls_open_accessibility), activity.isAccessibilityServiceEnabled() ? activity.getString(R.string.controls_enabled) : activity.getString(R.string.controls_enable_service), activity.isAccessibilityServiceEnabled(), v -> activity.openAccessibilitySettings());
            addToggle(options, activity.getString(R.string.controls_fallback), activity.getString(R.string.controls_fallback_desc), "override_current_launcher", activity.getString(R.string.section_controls));
            addOption(options, activity.getString(R.string.controls_google_settings), activity.getString(R.string.controls_google_settings_desc), false, v -> activity.openGoogleSettings());
            addOption(options, activity.getString(R.string.controls_app_info), activity.getString(R.string.controls_app_info_desc), false, v -> activity.openAppInfo(activity.getPackageName()));
            return;
        }
        if (activity.getString(R.string.shortcut_section).equals(selected)) {
            addHeading(options, activity.getString(R.string.shortcut_section));
            activity.dialogUtils.buildAppShortcutListInto(options);
            return;
        }
        if (activity.getString(R.string.section_permissions).equals(selected)) {
            addHeading(options, activity.getString(R.string.section_permissions));
            fillPermissionsOptions(options);
            return;
        }
        if (activity.getString(R.string.section_language).equals(selected)) {
            addHeading(options, activity.getString(R.string.language_title));
            String currentLang = activity.prefs.getString("app_language", "system");
            addOption(options, activity.getString(R.string.language_system), activity.getString(R.string.language_system_desc), "system".equals(currentLang), v -> {
                activity.prefs.edit().putString("app_language", "system").apply();
                activity.applyLanguage();
                activity.showSettings(activity.getString(R.string.section_language));
            });
            addOption(options, activity.getString(R.string.language_chinese), activity.getString(R.string.language_chinese_desc), "zh".equals(currentLang), v -> {
                activity.prefs.edit().putString("app_language", "zh").apply();
                activity.applyLanguage();
                activity.showSettings(activity.getString(R.string.section_language));
            });
            addOption(options, activity.getString(R.string.language_english), activity.getString(R.string.language_english_desc), "en".equals(currentLang), v -> {
                activity.prefs.edit().putString("app_language", "en").apply();
                activity.applyLanguage();
                activity.showSettings(activity.getString(R.string.section_language));
            });
            return;
        }
        if (activity.getString(R.string.section_about).equals(selected)) {
            addHeading(options, activity.getString(R.string.about_title));
            addNote(options, activity.getString(R.string.about_note));
            addOption(options, activity.getString(R.string.about_reset), activity.getString(R.string.about_reset_desc), false, v -> {
                activity.prefs.edit().clear().apply();
                activity.toast(activity.getString(R.string.about_reset_toast));
                activity.showHome();
            });
            return;
        }
        addHeading(options, selected);
        addNote(options, activity.getString(R.string.help_note));
    }

    // ===== 隐藏应用管理弹窗 =====
    private void showHiddenAppsDialog() {
        Set<String> hidden = activity.prefs.getStringSet("hidden_apps", new HashSet<>());
        List<MainActivity.AppEntry> hiddenApps = new ArrayList<>();
        for (String pkg : hidden) {
            try {
                android.content.pm.ApplicationInfo info = activity.getPackageManager().getApplicationInfo(pkg, 0);
                Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                String label = info.loadLabel(activity.getPackageManager()).toString();
                hiddenApps.add(new MainActivity.AppEntry(pkg, label, launch, info, null));
            } catch (Exception ignored) {}
        }
        if (hiddenApps.isEmpty()) {
            activity.toast("没有已隐藏的应用");
            return;
        }

        android.app.Dialog dialog = new android.app.Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(24), activity.dp(20), activity.dp(24), activity.dp(20));
        panel.setBackground(activity.dialogUtils.actionPanelBackground());

        TextView title = new TextView(activity);
        title.setText("已隐藏应用");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(44)));

        TextView tip = new TextView(activity);
        tip.setText("长按任意应用可恢复显示");
        tip.setTextColor(Color.argb(160, 255, 255, 255));
        tip.setTextSize(14);
        panel.addView(tip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(32)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        panel.addView(scroll, scrollLp);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (MainActivity.AppEntry app : hiddenApps) {
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(activity.dp(14), activity.dp(8), activity.dp(14), activity.dp(8));
            row.setBackgroundResource(R.drawable.settings_option);
            row.setFocusable(true);
            row.setClickable(true);
            row.setLongClickable(true);

            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(activity.appManager.loadAppIcon(activity.getPackageManager(), app));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(36), activity.dp(36));
            iconLp.rightMargin = activity.dp(12);
            row.addView(icon, iconLp);

            TextView label = new TextView(activity);
            label.setText(app.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(17);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            row.setOnFocusChangeListener((v, hasFocus) -> {
                animateFocus(v, hasFocus, 1.02f);
            });

            row.setOnLongClickListener(v -> {
                activity.appManager.unhideApp(app.packageName);
                activity.toast("已恢复显示：" + app.label);
                dialog.dismiss();
                activity.showSettings(activity.getString(R.string.section_controls));
                return true;
            });

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(56));
            rowLp.bottomMargin = activity.dp(8);
            list.addView(row, rowLp);
        }

        TextView closeBtn = new TextView(activity);
        closeBtn.setText("关闭");
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(16);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setBackgroundResource(R.drawable.glass_chip);
        closeBtn.setFocusable(true);
        closeBtn.setClickable(true);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(50));
        closeLp.topMargin = activity.dp(12);
        panel.addView(closeBtn, closeLp);

        dialog.setContentView(panel);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            int w = Math.min(activity.dp(560),
                    activity.getResources().getDisplayMetrics().widthPixels - activity.dp(48));
            window.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.setOnShowListener(d -> {
            if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
        });
        dialog.show();
    }

    private void fillPermissionsOptions(LinearLayout options) {
        List<PermissionItem> permissions = buildPermissionList();
        addNote(options, activity.getString(R.string.permission_note));
        for (PermissionItem item : permissions) {
            addPermissionOption(options, item);
        }
    }

    private List<PermissionItem> buildPermissionList() {
        List<PermissionItem> items = new ArrayList<>();
        Context ctx = activity;
        String packageName = activity.getPackageName();

        items.add(new PermissionItem(
                activity.getString(R.string.permission_autostart),
                true,
                () -> openAppDetails()
        ));

        boolean batteryIgnored = false;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) batteryIgnored = pm.isIgnoringBatteryOptimizations(packageName);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_battery),
                batteryIgnored,
                () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + packageName));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                }
        ));

        boolean notificationsEnabled = false;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) notificationsEnabled = nm.areNotificationsEnabled();
        items.add(new PermissionItem(
                activity.getString(R.string.permission_notifications),
                notificationsEnabled,
                () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                }
        ));

        boolean modifyAudioGranted = checkPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_media_volume),
                modifyAudioGranted,
                () -> openPermissionSettings(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        ));

        boolean queryAllGranted = checkPermission(Manifest.permission.QUERY_ALL_PACKAGES);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_query_apps),
                queryAllGranted,
                () -> openPermissionSettings(Manifest.permission.QUERY_ALL_PACKAGES)
        ));

        boolean storageGranted = checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_storage),
                storageGranted,
                () -> {
                    openPermissionSettings(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
        ));

        boolean canWriteSettings = Settings.System.canWrite(ctx);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_write_settings),
                canWriteSettings,
                () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + packageName));
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                }
        ));

        items.add(new PermissionItem(
                activity.getString(R.string.permission_clipboard),
                true,
                () -> openAppDetails()
        ));

        boolean shortcutSupported = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager sm = (ShortcutManager) ctx.getSystemService(Context.SHORTCUT_SERVICE);
            if (sm != null) shortcutSupported = sm.isRequestPinShortcutSupported();
        }
        items.add(new PermissionItem(
                activity.getString(R.string.permission_shortcut),
                shortcutSupported,
                () -> openAppDetails()
        ));

        boolean overlayGranted = Settings.canDrawOverlays(ctx);
        items.add(new PermissionItem(
                activity.getString(R.string.permission_background_popup),
                overlayGranted,
                () -> openOverlaySettings()
        ));

        items.add(new PermissionItem(
                activity.getString(R.string.permission_overlay),
                overlayGranted,
                () -> openOverlaySettings()
        ));

        items.add(new PermissionItem(
                activity.getString(R.string.permission_persistent_notification),
                notificationsEnabled,
                () -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        openAppDetails();
                    }
                }
        ));

        return items;
    }

    private boolean checkPermission(String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void openPermissionSettings(String permission) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void openAppDetails() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.toast(activity.getString(R.string.permission_open_failed));
        }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            openAppDetails();
        }
    }

    private void addPermissionOption(LinearLayout options, PermissionItem item) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(22), 0, activity.dp(18), 0);
        row.setBackgroundResource(R.drawable.settings_option);
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.0f));
        row.setOnClickListener(v -> {
            activity.rememberSettingsState(v, item.label);
            item.action.run();
        });

        TextView labelView = new TextView(activity);
        labelView.setText(item.label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(21);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -1, 1));

        TextView check = new TextView(activity);
        if (item.granted) {
            check.setText("✓");
            check.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            check.setText("✗");
            check.setTextColor(Color.parseColor("#FF5252"));
        }
        check.setTextSize(26);
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(activity.dp(58), -1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(72));
        lp.bottomMargin = activity.dp(12);
        options.addView(row, lp);
    }

    private void showWhitelistDialog() {
        java.util.List<MemoryCleaner.AppInfo> apps = activity.memoryCleaner.getInstalledApps();
        if (apps.isEmpty()) {
            activity.toast("没有可用的应用");
            return;
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        builder.setTitle(R.string.auto_clean_whitelist_title);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8));

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout listLayout = new LinearLayout(activity);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        Set<String> currentWhiteList = activity.memoryCleaner.getWhiteList();
        Map<String, Boolean> checkedMap = new java.util.HashMap<>();
        for (MemoryCleaner.AppInfo app : apps) {
            checkedMap.put(app.packageName, currentWhiteList.contains(app.packageName));
        }

        for (MemoryCleaner.AppInfo app : apps) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, activity.dp(4), 0, activity.dp(4));

            ImageView iconView = new ImageView(activity);
            iconView.setImageDrawable(app.icon);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40));
            iconLp.rightMargin = activity.dp(12);
            row.addView(iconView, iconLp);

            TextView nameView = new TextView(activity);
            nameView.setText(app.label);
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(16);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            row.addView(nameView, nameLp);

            android.widget.CheckBox checkBox = new android.widget.CheckBox(activity);
            checkBox.setChecked(Boolean.TRUE.equals(checkedMap.get(app.packageName)));
            checkBox.setTag(app.packageName);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> checkedMap.put(app.packageName, isChecked));
            row.addView(checkBox);

            listLayout.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        scrollView.addView(listLayout);
        container.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        builder.setView(container);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.common_save, (dialog, which) -> {
            Set<String> newWhiteList = new HashSet<>();
            for (Map.Entry<String, Boolean> entry : checkedMap.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    newWhiteList.add(entry.getKey());
                }
            }
            activity.memoryCleaner.setWhiteList(newWhiteList);
            activity.toast(activity.getString(R.string.auto_clean_whitelist_saved));
            activity.showSettings(activity.getString(R.string.section_controls));
        });
        builder.show();
    }

    private TextView settingsMenuItem(String text, boolean selected) {
        TextView item = new TextView(activity);
        item.setText(text);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(activity.dp(18), 0, activity.dp(18), 0);
        item.setTextSize(24);
        item.setSingleLine(true);
        item.setTextColor(selected ? Color.BLACK : Color.argb(215, 255, 255, 255));
        item.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        item.setBackgroundResource(selected ? R.drawable.settings_menu_selected : R.drawable.settings_menu_item);
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.0f));
        return item;
    }

    private void addModeOption(LinearLayout options, String title, String subtitle, String mode) {
        addOption(options, title, subtitle, mode.equals(activity.bgMode()), v -> {
            activity.rememberSettingsState(v, title);
            activity.setBgMode(mode);
        });
    }

    private void addAppsOption(LinearLayout options, int count) {
        String title = count + " " + activity.getString(R.string.controls_apps_suffix);
        addOption(options, title, "", activity.appsPerRow() == count, v -> {
            activity.rememberSettingsState(v, title);
            activity.prefs.edit().putInt("apps_per_row", count).apply();
            activity.showSettings(activity.getString(R.string.section_controls));
        });
    }

    private void addToggle(LinearLayout options, String title, String subtitle, String key, String section) {
        boolean isChecked = activity.prefs.getBoolean(key, false);
        addOption(options, title, subtitle, isChecked, v -> {
            activity.rememberSettingsState(v, title);
            boolean newValue = !activity.prefs.getBoolean(key, false);
            if (key.equals("live_preview_enabled")) {
                activity.setLivePreviewEnabled(newValue);
                return;
            }
            activity.prefs.edit().putBoolean(key, newValue).apply();
            if (key.equals("auto_clean_enabled") && newValue) {
                activity.runOnUiThread(() -> showWhitelistDialog());
            } else {
                activity.showSettings(section);
            }
        });
    }

    private void addHeading(LinearLayout options, String text) {
        TextView heading = new TextView(activity);
        heading.setText(text);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = activity.dp(22);
        lp.bottomMargin = activity.dp(14);
        options.addView(heading, lp);
    }

    private void addOption(LinearLayout options, String title, String subtitle, boolean checked, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(22), 0, activity.dp(18), 0);
        row.setBackgroundResource(R.drawable.settings_option);
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.0f));
        if (listener != null) row.setOnClickListener(listener);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(21);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = new TextView(activity);
            sub.setText(subtitle);
            sub.setTextColor(Color.argb(170, 255, 255, 255));
            sub.setTextSize(13);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(sub);
        }

        TextView check = new TextView(activity);
        check.setText(checked ? String.valueOf((char) 0x2713) : "");
        check.setTextColor(Color.WHITE);
        check.setTextSize(26);
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(activity.dp(58), -1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(TextUtils.isEmpty(subtitle) ? 58 : 72));
        lp.bottomMargin = activity.dp(12);
        options.addView(row, lp);
        if (title.equals(activity.pendingSettingsFocusTitle)) {
            activity.pendingSettingsFocusTitle = null;
            row.post(row::requestFocus);
        }
    }

    private void addNote(LinearLayout options, String text) {
        TextView note = new TextView(activity);
        note.setText(text);
        note.setTextColor(Color.argb(145, 255, 255, 255));
        note.setTextSize(15);
        note.setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(18));
        options.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? activity.dp(12) : 0).setDuration(130).start();
    }

    private static class PermissionItem {
        final String label;
        final boolean granted;
        final Runnable action;

        PermissionItem(String label, boolean granted, Runnable action) {
            this.label = label;
            this.granted = granted;
            this.action = action;
        }
    }
}