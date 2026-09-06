package com.LemonLnch.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.bluetooth.BluetoothAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.core.content.FileProvider;

import com.LemonLnch.R;
import com.LemonLnch.service.HomeAccessibilityService;
import com.LemonLnch.service.KeepAliveService;
import com.LemonLnch.system.RootShell;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    static final int REQ_IMAGE = 40;
    static final int REQ_VIDEO = 41;
    static final int REQ_HOME_ROLE = 42;
    private static final int CURRENT_VERSION_CODE = 60;

    SharedPreferences prefs;
    GridLayout appsGrid;
    FrameLayout launcherRoot;
    ScrollView homeScroll;
    View searchPill;
    View bluetoothPill;
    final Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable statusPollRunnable;
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver bluetoothReceiver;
    private boolean statusMonitorStarted = false;
    VideoView currentVideoBackground;
    FrameLayout folderOverlay;
    Dialog folderDialog;
    View folderLastFocus;
    boolean showingSettings;
    boolean showingWeatherDetails;
    String movingPackage;
    String movingFolderName;
    String movingFolderPackage;
    String movingFolderOriginalOrder;
    String pendingFolderFocusPackage;
    String focusAfterLoadPackage;
    int pendingSettingsScrollY = -1;
    String pendingSettingsFocusTitle;
    String pendingSettingsFocusSection;
    List<AppEntry> cachedLaunchableApps;
    String cachedHiddenKey = "";
    long cachedLaunchableAppsAt;
    int screenWidthDp;
    int screenHeightDp;

    AppManager appManager;
    HomeUIBuilder homeUIBuilder;
    SettingsUIBuilder settingsUIBuilder;
    WeatherUIBuilder weatherUIBuilder;
    GradientPicker gradientPicker;
    DialogUtils dialogUtils;
    PreviewCardController previewCardController;
    AppShortcutManager appShortcutManager;
    MemoryCleaner memoryCleaner;

    private boolean hasBeenResumedBefore = false;

    int pendingCustomKeyCode = -1;
    String pendingCustomKeyName = null;

    private FileTransferServer fileTransferServer;
    private ListView fileListView;
    private final List<TransferItem> transferFiles = new ArrayList<>();
    private SharedPreferences transferPrefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences langPrefs = newBase.getSharedPreferences("launcher_settings", MODE_PRIVATE);
        String lang = langPrefs.getString("app_language", "system");
        Locale locale;
        if ("zh".equals(lang)) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else if ("en".equals(lang)) {
            locale = Locale.ENGLISH;
        } else {
            locale = Locale.getDefault();
        }
        Locale.setDefault(locale);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startKeepAliveService();
        requestNecessaryPermissions();

        prefs = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        transferPrefs = getSharedPreferences("file_transfer", MODE_PRIVATE);
        screenWidthDp = getResources().getConfiguration().screenWidthDp;
        screenHeightDp = getResources().getConfiguration().screenHeightDp;
        applyUpgradeFixes();

        appShortcutManager = new AppShortcutManager(this);
        memoryCleaner = new MemoryCleaner(this);

        appManager = new AppManager(this);
        homeUIBuilder = new HomeUIBuilder(this);
        settingsUIBuilder = new SettingsUIBuilder(this);
        weatherUIBuilder = new WeatherUIBuilder(this);
        gradientPicker = new GradientPicker(this);
        dialogUtils = new DialogUtils(this);
        previewCardController = new PreviewCardController(this);

        enterImmersiveMode();
        showHome();
        // 直播预览默认关闭，只有用户在“设置 → 控制”中开启后才连接预览服务。
        if (isLivePreviewEnabled()) {
            previewCardController.ensureServiceStarted();
        }
    }

    @Override
    protected void onDestroy() {
        stopStatusMonitor();
        if (previewCardController != null) previewCardController.onDestroy();
        stopVideoBackground();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopVideoBackground();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (isLivePreviewEnabled() && previewCardController != null) {
            previewCardController.onStop();
        }
        super.onStop();
    }

    private void applyUpgradeFixes() {
        if (!prefs.contains("app_container_enabled")) {
            prefs.edit().putBoolean("app_container_enabled", true).apply();
        }
        if (!prefs.contains("app_container_boot")) {
            prefs.edit().putBoolean("app_container_boot", true).apply();
        }
        int lastVersion = prefs.getInt("last_version_code", 0);
        if (lastVersion < 15) {
            prefs.edit()
                    .putBoolean("minimal_status", false)
                    .putBoolean("hide_featured", false)
                    .putBoolean("override_current_launcher", true)
                    .putBoolean("start_on_boot", true)
                    .putBoolean("auto_open_on_wake", true)
                    .putInt("last_version_code", CURRENT_VERSION_CODE)
                    .apply();
            return;
        }
        if (lastVersion < CURRENT_VERSION_CODE) {
            SharedPreferences.Editor editor = prefs.edit().putInt("last_version_code", CURRENT_VERSION_CODE);
            if (lastVersion < 18) {
                editor.putBoolean("override_current_launcher", true)
                        .putBoolean("start_on_boot", true)
                        .putBoolean("auto_open_on_wake", true);
            }
            if (lastVersion < 36) {
                editor.putBoolean("override_current_launcher", true)
                        .putBoolean("start_on_boot", true)
                        .putBoolean("auto_open_on_wake", true);
            }
            editor.apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isLivePreviewEnabled() && previewCardController != null) {
            previewCardController.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (isLivePreviewEnabled() && previewCardController != null) {
            previewCardController.onResume();
        }
        startStatusMonitor();
        updateServicePill();
        statusHandler.postDelayed(this::updateServicePill, 600);
        statusHandler.postDelayed(this::updateServicePill, 1500);
        statusHandler.postDelayed(this::updateServicePill, 3000);

        if (memoryCleaner != null && memoryCleaner.isAutoCleanEnabled()) {
            if (hasBeenResumedBefore) {
                statusHandler.postDelayed(() -> memoryCleaner.cleanMemory(true, null), 500);
            } else {
                hasBeenResumedBefore = true;
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isLivePreviewEnabled() && previewCardController != null) {
            previewCardController.onWindowFocusChanged();
        }
    }

    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(movingFolderName) && !TextUtils.isEmpty(movingFolderPackage)) {
            cancelFolderMove(true);
            return;
        }
        if (folderOverlay != null) {
            dismissFolderOverlay();
            return;
        }
        if (showingSettings) {
            showHome();
            return;
        }
        if (showingWeatherDetails) {
            showHome();
            return;
        }
        if (prefs.getBoolean("override_back_exit", true)) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!showingSettings && !showingWeatherDetails) {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                String digit = String.valueOf(keyCode - KeyEvent.KEYCODE_0);
                AppShortcutManager.AppShortcut shortcut = appShortcutManager.getDigitMapping(digit);
                if (shortcut != null) {
                    Intent intent = appShortcutManager.getLaunchIntentForPackage(this, shortcut.packageName);
                    if (intent != null) {
                        startExternalActivity(intent);
                        return true;
                    } else {
                        toast(getString(R.string.shortcut_app_not_installed));
                        return true;
                    }
                }
            }
            AppShortcutManager.CustomShortcut custom = appShortcutManager.getCustomMapping();
            if (custom != null && custom.keyCode == keyCode) {
                Intent intent = appShortcutManager.getLaunchIntentForPackage(this, custom.packageName);
                if (intent != null) {
                    startExternalActivity(intent);
                    return true;
                } else {
                    toast(getString(R.string.shortcut_app_not_installed));
                    return true;
                }
            }
        }

        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings(getString(R.string.section_theme));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_HOME && showingSettings) {
            showHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            toast(isDefaultHomeLauncher() ? getString(R.string.controls_home_role_granted) : getString(R.string.controls_home_role_denied));
            showSettings(getString(R.string.section_controls));
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_IMAGE) {
            prefs.edit().putString("bg_mode", "image").putString("bg_uri", uri.toString()).apply();
            toast(getString(R.string.theme_custom_image_applied));
            showSettings(getString(R.string.section_theme));
        } else if (requestCode == REQ_VIDEO) {
            prefs.edit().putString("bg_mode", "video").putString("bg_video_uri", uri.toString()).apply();
            toast(getString(R.string.theme_custom_video_applied));
            showSettings(getString(R.string.section_theme));
        }
    }

    boolean isLivePreviewEnabled() {
        return prefs != null && prefs.getBoolean("live_preview_enabled", false);
    }

    void setLivePreviewEnabled(boolean enabled) {
        prefs.edit().putBoolean("live_preview_enabled", enabled).apply();
        if (previewCardController != null) {
            previewCardController.detachView();
        }
        // 先重建主页，让预览 Surface 创建完成，再启动 lemon-TV 预览服务。
        showHome();
        if (enabled && previewCardController != null) {
            previewCardController.ensureServiceStarted();
        }
    }

    void openAppContainerSettings() {
        if (!isLivePreviewEnabled()) return;
        if (previewCardController != null) previewCardController.openPreviewSettings();
    }

    void openAppContainer() {
        if (!isLivePreviewEnabled()) return;
        if (previewCardController != null) previewCardController.openPreview();
    }

    void focusAppContainerCard() {
        if (homeUIBuilder != null) homeUIBuilder.focusAppContainerCard();
    }

    void focusDockFirst() {
        if (homeUIBuilder != null) homeUIBuilder.focusDockFirst();
    }

    void refreshDockOnly() {
        if (homeUIBuilder != null) {
            homeUIBuilder.refreshDock();
        }
    }

    void showHome() {
        if (previewCardController != null) previewCardController.detachView();
        showingSettings = false;
        showingWeatherDetails = false;
        currentVideoBackground = null;
        homeUIBuilder.build();
        appManager.loadApps();
        updateServicePill();
    }

    void showSettings(String selected) {
        showingSettings = true;
        showingWeatherDetails = false;
        settingsUIBuilder.build(selected);
    }

    void showWeatherDetails() {
        showingWeatherDetails = true;
        showingSettings = false;
        weatherUIBuilder.build();
    }

    void showGradientPicker() {
        gradientPicker.show();
    }

    void loadApps() {
        appManager.loadApps();
    }

    void launchEntry(AppEntry entry, boolean dismissFolder) {
        appManager.launchEntry(entry, dismissFolder);
    }

    void hideApp(String packageName) {
        appManager.hideApp(packageName);
    }

    public void addAppToDock(String packageName) {
        if (TextUtils.isEmpty(packageName)) return;
        for (int i = 0; i < 6; i++) {
            String existing = prefs.getString("dock_slot_" + i, "");
            if (TextUtils.equals(existing, packageName)) {
                toast(getString(R.string.dock_already_exists));
                return;
            }
        }
        for (int i = 0; i < 6; i++) {
            String existing = prefs.getString("dock_slot_" + i, "");
            if (TextUtils.isEmpty(existing)) {
                prefs.edit().putString("dock_slot_" + i, packageName).apply();
                toast(getString(R.string.dock_added));
                refreshDockOnly();
                return;
            }
        }
        toast(getString(R.string.dock_full));
    }

    void focusAppTile(String packageName) {
        appManager.focusAppTile(packageName);
    }

    List<AppEntry> queryLaunchableApps(PackageManager pm) {
        return appManager.queryLaunchableApps(pm);
    }

    void invalidateAppCache() {
        appManager.invalidateAppCache();
    }

    void dismissFolderOverlay() {
        dialogUtils.dismissFolderOverlay();
    }

    void setFolderBackgroundBlur(boolean enabled) {
        dialogUtils.setFolderBackgroundBlur(enabled);
    }

    void cancelFolderMove(boolean keepFolderOpen) {
        appManager.cancelFolderMove(keepFolderOpen);
    }

    void startExternalActivity(Intent intent) {
        if (intent == null) return;
        stopVideoBackground();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    void stopVideoBackground() {
        if (currentVideoBackground == null) return;
        try {
            currentVideoBackground.stopPlayback();
        } catch (Exception ignored) {
        }
        currentVideoBackground = null;
    }

    void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    int horizontalPaddingDp() {
        if (screenWidthDp < 600) return 24;
        if (screenWidthDp < 900) return 36;
        return 54;
    }

    int appsPerRow() {
        int defaultCount = prefs.getInt("apps_per_row", 5);
        if (screenWidthDp < 500) return Math.min(defaultCount, 3);
        if (screenWidthDp < 700) return Math.min(defaultCount, 4);
        if (screenWidthDp < 900) return Math.min(defaultCount, 5);
        return defaultCount;
    }

    void applyLanguage() {
        recreate();
    }

    boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String target = getPackageName() + "/" + HomeAccessibilityService.class.getName();
        for (AccessibilityServiceInfo service : services) {
            if (service.getId() != null && service.getId().equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    void openAccessibilitySettings() {
        ComponentName component = new ComponentName(this, HomeAccessibilityService.class);
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(details);
            toast(getString(R.string.controls_allow_restricted_toast));
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast(getString(R.string.controls_allow_restricted_alt));
        } catch (Exception e) {
            openAppInfo(getPackageName());
        }
    }

    void requestDefaultHomeLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    toast(getString(R.string.controls_home_already_default));
                    return;
                }
                try {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        openHomeSettings();
    }

    private void openHomeSettings() {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                new Intent("android.settings.HOME_SETTINGS"),
                new Intent(Settings.ACTION_HOME_SETTINGS),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + getPackageName()))
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                toast(getString(R.string.controls_home_choose));
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast(getString(R.string.controls_home_choose));
        } catch (Exception e) {
            toast(getString(R.string.controls_home_settings_failed));
        }
    }

    void testHomeLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.controls_home_chooser_title)));
        } catch (Exception e) {
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                openHomeSettings();
            }
        }
    }

    boolean isDefaultHomeLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null && getPackageName().equals(info.activityInfo.packageName);
    }

    String homeStatusText() {
        if (isDefaultHomeLauncher()) return getString(R.string.controls_home_status_default);
        return getString(R.string.controls_home_status_not_default);
    }

    void openGoogleSettings() {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_SETTINGS),
                new Intent("com.google.android.tv.settings.action.SETTINGS"),
                new Intent("android.settings.SETTINGS")
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        toast(getString(R.string.controls_google_settings_failed));
    }

    void openAppInfo(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    void openPicker(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    void askWallpaperUrl() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://example.com/wallpaper.jpg");
        input.setText(prefs.getString("bg_url", ""));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.theme_custom_url_dialog_title))
                .setView(input)
                .setPositiveButton(getString(R.string.common_download), (dialog, which) -> downloadWallpaper(input.getText().toString().trim()))
                .setNegativeButton(getString(R.string.common_cancel), null)
                .show();
    }

    private void downloadWallpaper(String url) {
        if (TextUtils.isEmpty(url)) return;
        toast(getString(R.string.theme_custom_url_downloading));
        new Thread(() -> {
            try (InputStream in = new java.net.URL(url).openStream();
                 FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "url_wallpaper.img"))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                prefs.edit().putString("bg_mode", "url").putString("bg_url", url).apply();
                runOnUiThread(() -> {
                    toast(getString(R.string.theme_custom_url_applied));
                    showSettings(getString(R.string.section_theme));
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(getString(R.string.theme_custom_url_failed)));
            }
        }).start();
    }

    void setBgMode(String mode) {
        prefs.edit().putString("bg_mode", mode).apply();
        showSettings(getString(R.string.section_theme));
    }

    String bgMode() {
        return prefs.getString("bg_mode", "light");
    }

    boolean prefBool(String key) {
        if ("override_back_exit".equals(key)
                || "start_on_boot".equals(key)
                || "auto_open_on_wake".equals(key)
                || "override_current_launcher".equals(key)
                || "use_24h".equals(key)) {
            return prefs.getBoolean(key, true);
        }
        return prefs.getBoolean(key, false);
    }

    void rememberSettingsState(View view, String title) {
        pendingSettingsFocusTitle = title;
        View current = view;
        while (current != null) {
            if (current instanceof ScrollView) {
                pendingSettingsScrollY = ((ScrollView) current).getScrollY();
                return;
            }
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        pendingSettingsScrollY = -1;
    }

    void updateServicePill() {
        try {
            if (searchPill != null) {
                boolean netOk = isNetworkConnectedNow();
                updateChipIconColor(searchPill, netOk ? Color.BLUE : Color.WHITE);
                searchPill.invalidate();
            }
            if (bluetoothPill != null) {
                boolean btOk = false;
                if (homeUIBuilder != null) {
                    btOk = homeUIBuilder.isBluetoothConnectedPublic();
                } else {
                    btOk = isBluetoothConnectedFallback();
                }
                updateChipIconColor(bluetoothPill, btOk ? Color.BLUE : Color.WHITE);
                bluetoothPill.invalidate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isNetworkConnectedNow() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) return false;
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            }
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBluetoothConnectedFallback() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;
            int a2dp = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP);
            int hs = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET);
            return a2dp == android.bluetooth.BluetoothProfile.STATE_CONNECTED
                    || hs == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateChipIconColor(View chip, int color) {
        if (chip == null) return;
        ImageView icon = null;
        Object tag = chip.getTag();
        if (tag instanceof ImageView) {
            icon = (ImageView) tag;
        } else if (chip instanceof FrameLayout) {
            FrameLayout fl = (FrameLayout) chip;
            for (int i = 0; i < fl.getChildCount(); i++) {
                if (fl.getChildAt(i) instanceof ImageView) {
                    icon = (ImageView) fl.getChildAt(i);
                    break;
                }
            }
        }
        if (icon == null) return;
        icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        icon.invalidate();
        chip.invalidate();
    }

    void startStatusMonitor() {
        if (statusMonitorStarted) {
            updateServicePill();
            return;
        }
        statusMonitorStarted = true;

        statusPollRunnable = new Runnable() {
            @Override
            public void run() {
                updateServicePill();
                statusHandler.postDelayed(this, 2500);
            }
        };

        connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateServicePill();
            }
        };
        IntentFilter netFilter = new IntentFilter();
        netFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                netFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            } catch (Exception ignored) {}
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(connectivityReceiver, netFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(connectivityReceiver, netFilter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (statusPollRunnable != null) {
                    statusHandler.removeCallbacks(statusPollRunnable);
                    statusHandler.postDelayed(() -> {
                        updateServicePill();
                        if (statusPollRunnable != null) {
                            statusHandler.postDelayed(statusPollRunnable, 2500);
                        }
                    }, 300);
                } else {
                    updateServicePill();
                }
            }
        };
        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        btFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        btFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        btFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothReceiver, btFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(bluetoothReceiver, btFilter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        statusHandler.post(statusPollRunnable);
        updateServicePill();
    }

    void stopStatusMonitor() {
        statusMonitorStarted = false;
        if (statusPollRunnable != null) {
            statusHandler.removeCallbacks(statusPollRunnable);
        }
        try {
            if (connectivityReceiver != null) {
                unregisterReceiver(connectivityReceiver);
                connectivityReceiver = null;
            }
        } catch (Exception ignored) {}
        try {
            if (bluetoothReceiver != null) {
                unregisterReceiver(bluetoothReceiver);
                bluetoothReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    private void startKeepAliveService() {
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 101);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 102);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                }
            }
        }
    }

    // ==================== 文件传输功能 ====================

    void showFileTransferDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 101);
            }
        }
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(true);

        final float density = getResources().getDisplayMetrics().density;
        final int screenW = getResources().getDisplayMetrics().widthPixels;
        final int screenH = getResources().getDisplayMetrics().heightPixels;
        final int safeW = Math.max(screenW, dp(320));
        final int safeH = Math.max(screenH, dp(240));

        final int horizontalPadPx = clampPx(Math.round(safeW * 0.022f), dp(10), dp(28));
        final int topBarHeightPx = clampPx(Math.round(safeH * 0.075f), dp(44), dp(58));
        final int listHeaderHeightPx = clampPx(Math.round(safeH * 0.058f), dp(38), dp(48));
        final int availableForInfo = Math.max(dp(150), safeH
                - horizontalPadPx * 2
                - topBarHeightPx
                - listHeaderHeightPx
                - dp(24));

        final int infoHeightPx = clampPx(Math.round(safeH * 0.29f), dp(150), dp(225));
        final int qrSizePx = clampPx(
                Math.min(Math.round(safeW * 0.235f), Math.round(infoHeightPx * 0.78f)),
                dp(108),
                dp(190));
        final boolean compact = safeW < dp(720) || safeH < dp(520);
        final int titleSizeSp = compact ? 20 : 24;
        final int addressSizeSp = compact ? 22 : 28;
        final int hintSizeSp = compact ? 12 : 14;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(15, 18, 24));
        root.setPadding(horizontalPadPx, dp(12), horizontalPadPx, dp(12));

        LinearLayout mainPanel = new LinearLayout(this);
        mainPanel.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        mainPanel.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, topBarHeightPx));

        ImageView transferIcon = new ImageView(this);
        transferIcon.setImageResource(R.drawable.ic_transfer);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                clampPx(Math.round(safeH * 0.045f), dp(24), dp(32)),
                clampPx(Math.round(safeH * 0.045f), dp(24), dp(32)));
        iconLp.rightMargin = dp(8);
        topBar.addView(transferIcon, iconLp);

        TextView title = new TextView(this);
        title.setText("文件快传");
        title.setTextColor(Color.WHITE);
        title.setTextSize(titleSizeSp);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        topBar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(compact ? 20 : 24);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setFocusable(true);
        closeBtn.setClickable(true);
        closeBtn.setBackgroundResource(R.drawable.glass_chip);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(
                clampPx(Math.round(safeW * 0.055f), dp(44), dp(52)),
                clampPx(Math.round(safeH * 0.062f), dp(38), dp(44))));

        LinearLayout infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.HORIZONTAL);
        infoPanel.setGravity(Gravity.CENTER_VERTICAL);
        int infoPad = compact ? dp(10) : dp(14);
        infoPanel.setPadding(infoPad, infoPad, infoPad, infoPad);
        infoPanel.setBackgroundColor(Color.argb(65, 255, 255, 255));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(infoHeightPx, availableForInfo));
        infoLp.topMargin = dp(6);
        infoLp.bottomMargin = dp(8);
        mainPanel.addView(infoPanel, infoLp);

        LinearLayout addrPanel = new LinearLayout(this);
        addrPanel.setOrientation(LinearLayout.VERTICAL);
        addrPanel.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams addrPanelLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        infoPanel.addView(addrPanel, addrPanelLp);

        TextView addrTitle = new TextView(this);
        addrTitle.setText("请使用手机 / 电脑浏览器访问");
        addrTitle.setTextColor(Color.argb(220, 255, 255, 255));
        addrTitle.setTextSize(compact ? 14 : 17);
        addrTitle.setMaxLines(1);
        addrPanel.addView(addrTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String ip = getLocalIpAddress();
        String url = "http://" + ip + ":6655";
        TextView addrUrl = new TextView(this);
        addrUrl.setText(url);
        addrUrl.setTextColor(Color.rgb(89, 174, 255));
        addrUrl.setTextSize(addressSizeSp);
        addrUrl.setTypeface(Typeface.DEFAULT_BOLD);
        addrUrl.setSingleLine(true);
        addrUrl.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        addrUrl.setMarqueeRepeatLimit(0);
        addrUrl.setSelected(true);
        addrUrl.setPadding(0, dp(4), dp(8), dp(4));
        addrPanel.addView(addrUrl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("同一局域网内打开地址即可上传，文件保存到 Download 目录");
        hint.setTextColor(Color.argb(180, 255, 255, 255));
        hint.setTextSize(hintSizeSp);
        hint.setMaxLines(compact ? 2 : 1);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        addrPanel.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView refreshIp = new TextView(this);
        refreshIp.setText("当前设备局域网地址");
        refreshIp.setTextColor(Color.argb(150, 255, 255, 255));
        refreshIp.setTextSize(compact ? 11 : 13);
        refreshIp.setPadding(0, dp(5), 0, 0);
        addrPanel.addView(refreshIp, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView qrImage = new ImageView(this);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap qrBitmap = generateQRCode(url);
        if (qrBitmap != null) {
            qrImage.setImageBitmap(qrBitmap);
        }
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(qrSizePx, qrSizePx);
        qrLp.leftMargin = compact ? dp(8) : dp(14);
        infoPanel.addView(qrImage, qrLp);

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        mainPanel.addView(listHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeaderHeightPx));

        TextView listTitle = new TextView(this);
        listTitle.setText("已接收文件");
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(compact ? 17 : 19);
        listTitle.setTypeface(Typeface.DEFAULT_BOLD);
        listTitle.setMaxLines(1);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView listHint = new TextView(this);
        listHint.setText("APK 文件点击后安装");
        listHint.setTextColor(Color.argb(155, 255, 255, 255));
        listHint.setTextSize(compact ? 11 : 13);
        listHint.setGravity(Gravity.CENTER_VERTICAL);
        listHint.setMaxLines(1);
        listHeader.addView(listHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fileListView = new ListView(this);
        fileListView.setDividerHeight(dp(1));
        fileListView.setDivider(new ColorDrawable(Color.argb(45, 255, 255, 255)));
        fileListView.setFocusable(true);
        fileListView.setItemsCanFocus(true);
        fileListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        fileListView.setClipToPadding(false);
        fileListView.setPadding(0, 0, 0, dp(4));
        fileListView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mainPanel.addView(fileListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        refreshFileList();

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
                w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            startFileTransferServer();
            closeBtn.requestFocus();
            enterImmersiveMode();
        });
        dialog.setOnDismissListener(d -> {
            stopFileTransferServer();
            enterImmersiveMode();
        });
        dialog.show();
    }

    private int clampPx(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String getLocalIpAddress() {
        String fallback = "127.0.0.1";
        try {
            List<String> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet4Address) || addr.isLoopbackAddress()) continue;
                    String host = addr.getHostAddress();
                    if (host == null || host.isEmpty()) continue;
                    if (isPrivateIpv4(host)) {
                        if (host.startsWith("192.168.")) return host;
                        candidates.add(host);
                    } else if (fallback.equals("127.0.0.1")) {
                        fallback = host;
                    }
                }
            }
            if (!candidates.isEmpty()) return candidates.get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fallback;
    }

    private boolean isPrivateIpv4(String host) {
        try {
            String[] p = host.split("\\.");
            if (p.length != 4) return false;
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
        } catch (Exception e) {
            return false;
        }
    }

    private Bitmap generateQRCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 480, 480);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void refreshFileList() {
        transferFiles.clear();
        Set<String> saved = transferPrefs != null
                ? transferPrefs.getStringSet("received_items", new HashSet<>())
                : new HashSet<>();

        List<String> stale = new ArrayList<>();
        for (String encoded : saved) {
            TransferItem item = TransferItem.fromPersisted(this, encoded);
            if (item != null && item.exists()) {
                transferFiles.add(item);
            } else {
                stale.add(encoded);
            }
        }
        if (!stale.isEmpty() && transferPrefs != null) {
            Set<String> updated = new HashSet<>(saved);
            updated.removeAll(stale);
            transferPrefs.edit().putStringSet("received_items", updated).apply();
        }
        Collections.sort(transferFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        if (fileListView != null) {
            fileListView.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return transferFiles.size(); }
                @Override public Object getItem(int position) { return transferFiles.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    LinearLayout row;
                    if (convertView == null) {
                        row = new LinearLayout(MainActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setFocusable(true);
                        row.setClickable(true);
                        row.setLongClickable(true);
                        row.setFocusableInTouchMode(false);
                        row.setPadding(dp(14), dp(7), dp(14), dp(7));

                        ImageView fileIcon = new ImageView(MainActivity.this);
                        fileIcon.setFocusable(false);
                        fileIcon.setClickable(false);
                        row.addView(fileIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

                        LinearLayout textBox = new LinearLayout(MainActivity.this);
                        textBox.setFocusable(false);
                        textBox.setClickable(false);
                        textBox.setOrientation(LinearLayout.VERTICAL);
                        LinearLayout.LayoutParams textBoxLp = new LinearLayout.LayoutParams(0,
                                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        textBoxLp.leftMargin = dp(12);
                        row.addView(textBox, textBoxLp);

                        TextView nameView = new TextView(MainActivity.this);
                        nameView.setTextColor(Color.WHITE);
                        nameView.setTextSize(16);
                        nameView.setSingleLine(true);
                        nameView.setEllipsize(TextUtils.TruncateAt.END);
                        nameView.setFocusable(false);
                        nameView.setClickable(false);
                        textBox.addView(nameView);

                        TextView metaView = new TextView(MainActivity.this);
                        metaView.setFocusable(false);
                        metaView.setClickable(false);
                        metaView.setTextColor(Color.argb(145, 255, 255, 255));
                        metaView.setTextSize(12);
                        textBox.addView(metaView);

                        TextView actionView = new TextView(MainActivity.this);
                        actionView.setTextColor(Color.rgb(89, 174, 255));
                        actionView.setTextSize(13);
                        actionView.setFocusable(false);
                        actionView.setClickable(false);
                        row.addView(actionView, new LinearLayout.LayoutParams(dp(90),
                                ViewGroup.LayoutParams.WRAP_CONTENT));

                        installRowInteractions(row);
                    } else {
                        row = (LinearLayout) convertView;
                    }
                    TransferItem item = transferFiles.get(position);
                    row.setTag(item);
                    ImageView fileIcon = (ImageView) row.getChildAt(0);
                    LinearLayout textBox = (LinearLayout) row.getChildAt(1);
                    TextView nameView = (TextView) textBox.getChildAt(0);
                    TextView metaView = (TextView) textBox.getChildAt(1);
                    TextView actionView = (TextView) row.getChildAt(2);
                    fileIcon.setImageResource(item.isApk() ? R.mipmap.ic_launcher : R.drawable.ic_transfer);
                    nameView.setText(item.name);
                    metaView.setText(formatFileSize(item.length()) + "  ·  " + formatTime(item.lastModified()));
                    actionView.setText(item.isApk() ? "点击安装" : "点击打开");
                    return row;
                }
            });
        }
    }

    private void installRowInteractions(final LinearLayout row) {
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        row.setClickable(true);
        row.setLongClickable(true);
        row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackgroundColor(Color.argb(60, 89, 174, 255));
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        row.setOnClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof TransferItem)) return;
            openTransferItem((TransferItem) tag);
        });
        row.setOnLongClickListener(v -> {
            Object tag = v.getTag();
            if (!(tag instanceof TransferItem)) return true;
            showDeleteTransferRecordDialog((TransferItem) tag);
            return true;
        });

        row.setOnKeyListener(new View.OnKeyListener() {
            private static final long LONG_PRESS_MS = 650L;
            private long downTime = -1L;
            private boolean longHandled;
            private final Handler handler = new Handler(Looper.getMainLooper());
            private final Runnable longPressAction = new Runnable() {
                @Override
                public void run() {
                    View target = row;
                    if (downTime > 0 && !longHandled && isViewFocused(target)) {
                        longHandled = true;
                        Object tag = target.getTag();
                        if (tag instanceof TransferItem) {
                            target.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            showDeleteTransferRecordDialog((TransferItem) tag);
                        }
                    }
                }
            };

            private boolean isConfirmKey(int keyCode) {
                return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A
                        || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT;
            }

            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (!isConfirmKey(keyCode)) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        downTime = event.getDownTime();
                        longHandled = false;
                        handler.removeCallbacks(longPressAction);
                        handler.postDelayed(longPressAction, LONG_PRESS_MS);
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(longPressAction);
                    long elapsed = event.getEventTime() - (downTime > 0 ? downTime : event.getDownTime());
                    downTime = -1L;
                    if (!longHandled && elapsed < LONG_PRESS_MS) {
                        Object tag = v.getTag();
                        if (tag instanceof TransferItem) {
                            openTransferItem((TransferItem) tag);
                        }
                    }
                    longHandled = false;
                    return true;
                }
                return true;
            }
        });
    }

    private boolean isViewFocused(View view) {
        return view != null && view.isShown() && view.hasFocus();
    }

    private void openTransferItem(TransferItem item) {
        if (item == null) return;
        if (item.isApk()) {
            installApk(item);
        } else {
            openTransferredFile(item);
        }
    }

    private void showDeleteTransferRecordDialog(TransferItem item) {
        if (item == null) return;

        final Dialog deleteDialog = new Dialog(this);
        deleteDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        deleteDialog.setCanceledOnTouchOutside(true);
        deleteDialog.setCancelable(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(24), dp(28), dp(22));
        box.setBackgroundColor(Color.rgb(30, 34, 42));

        TextView title = new TextView(this);
        title.setText("删除传输记录");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(item.name == null ? "" : item.name);
        name.setTextColor(Color.argb(205, 255, 255, 255));
        name.setTextSize(15);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(12);
        box.addView(name, nameLp);

        TextView tip = new TextView(this);
        tip.setText("确定从“已接收文件”列表中删除这条传输记录？\n实际文件仍保留在 Download 目录。");
        tip.setTextColor(Color.argb(160, 255, 255, 255));
        tip.setTextSize(13);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = dp(8);
        box.addView(tip, tipLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(22);
        box.addView(actions, actionsLp);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(14);
        cancel.setBackgroundResource(R.drawable.glass_chip);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(dp(92), dp(44));
        cancelLp.rightMargin = dp(10);
        actions.addView(cancel, cancelLp);

        TextView delete = new TextView(this);
        delete.setText("删除传输记录");
        delete.setGravity(Gravity.CENTER);
        delete.setTextColor(Color.WHITE);
        delete.setTextSize(14);
        delete.setBackgroundResource(R.drawable.glass_chip);
        delete.setClickable(true);
        delete.setFocusable(true);
        actions.addView(delete, new LinearLayout.LayoutParams(dp(132), dp(44)));

        cancel.setOnClickListener(v -> deleteDialog.dismiss());
        delete.setOnClickListener(v -> {
            deleteTransferRecord(item);
            deleteDialog.dismiss();
        });

        deleteDialog.setContentView(box);
        Window window = deleteDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        deleteDialog.setOnShowListener(d -> {
            Window w = deleteDialog.getWindow();
            if (w != null) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int width = Math.min(Math.round(dm.widthPixels * 0.62f), dp(560));
                width = Math.max(width, dp(360));
                w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
                w.setDimAmount(0.55f);
            }
            delete.requestFocus();
        });
        deleteDialog.show();
    }

    private void deleteTransferRecord(TransferItem item) {
        if (item == null || transferPrefs == null) return;
        Set<String> saved = new HashSet<>(transferPrefs.getStringSet("received_items", new HashSet<>()));
        String target = item.toPersisted();
        if (saved.remove(target)) {
            transferPrefs.edit().putStringSet("received_items", saved).apply();
        } else {
            java.util.Iterator<String> iterator = saved.iterator();
            while (iterator.hasNext()) {
                TransferItem candidate = TransferItem.fromPersisted(this, iterator.next());
                if (candidate != null && candidate.sameRecordAs(item)) {
                    iterator.remove();
                    break;
                }
            }
            transferPrefs.edit().putStringSet("received_items", saved).apply();
        }
        transferFiles.remove(item);
        if (fileListView != null && fileListView.getAdapter() != null) {
            ((BaseAdapter) fileListView.getAdapter()).notifyDataSetChanged();
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024f);
        if (size < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", size / 1024f / 1024f);
        return String.format(Locale.US, "%.1f GB", size / 1024f / 1024f / 1024f);
    }

    private String formatTime(long time) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new java.util.Date(time));
    }

    private void openTransferredFile(TransferItem item) {
        try {
            Uri uri = item.uri;
            if (uri == null && item.file != null) {
                uri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", item.file);
            }
            if (uri == null) throw new IOException("missing uri");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, guessMimeType(item.name));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开该文件");
        }
    }

    private String guessMimeType(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".m3u") || lower.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        return "*/*";
    }

    // ==================== 三套安装逻辑 ====================

    private void installApk(TransferItem item) {
        try {
            Uri apkUri = item.uri;
            if (apkUri == null && item.file != null) {
                apkUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", item.file);
            }
            if (apkUri == null) throw new IOException("missing apk uri");

            // 第一优先：Root 静默安装
            if (RootShell.isRootAvailable()) {
                installApkViaRoot(apkUri, item.file);
                return;
            }

            // 无 Root：先尝试系统安装程序
            startSystemInstall(apkUri);
        } catch (Exception e) {
            e.printStackTrace();
            toast("无法打开安装程序");
        }
    }

    private void startSystemInstall(Uri apkUri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !getPackageManager().canRequestPackageInstalls()) {
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                toast("请允许本应用安装未知应用，然后返回再次点击 APK");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            // 轮询检测安装结果，超时未成功则回退内置安装
            scheduleInstallFallbackCheck(apkUri);
        } catch (ActivityNotFoundException e) {
            toast("系统没有可用的安装程序，尝试内置安装");
            installApkViaBuiltIn(apkUri);
        } catch (Exception e) {
            toast("系统安装失败，尝试内置安装");
            installApkViaBuiltIn(apkUri);
        }
    }

    private void scheduleInstallFallbackCheck(Uri apkUri) {
        final long[] delays = new long[]{3000L, 6000L, 12000L};
        final boolean[] fallbackTriggered = new boolean[]{false};
        for (int i = 0; i < delays.length; i++) {
            final long delay = delays[i];
            final int index = i;
            getWindow().getDecorView().postDelayed(() -> {
                if (fallbackTriggered[0]) return;
                String pkg = getApkPackageName(apkUri);
                if (pkg != null && isPackageInstalled(pkg)) {
                    toast("安装成功");
                    fallbackTriggered[0] = true;
                } else if (index == delays.length - 1) {
                    toast("系统安装未完成，自动切换内置安装");
                    installApkViaBuiltIn(apkUri);
                    fallbackTriggered[0] = true;
                }
            }, delay);
        }
    }

    private void installApkViaRoot(Uri apkUri, File file) {
        toast("Root 安装中…");
        new Thread(() -> {
            String path = null;
            if (file != null) path = file.getAbsolutePath();
            else if (apkUri != null && "file".equals(apkUri.getScheme())) path = apkUri.getPath();
            if (path == null) {
                // 复制 content uri 到临时文件
                try {
                    File temp = File.createTempFile("apk_", ".apk", getCacheDir());
                    try (InputStream in = getContentResolver().openInputStream(apkUri);
                         OutputStream out = new FileOutputStream(temp)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                    }
                    path = temp.getAbsolutePath();
                } catch (Exception e) {
                    runOnUiThread(() -> toast("无法读取 APK 文件"));
                    return;
                }
            }
            RootShell.Result result = RootShell.exec("pm install -r " + path);
            boolean success = result.ok();
            final String msg = result.error.isEmpty() ? result.output : result.error;
            runOnUiThread(() -> {
                if (success) toast("Root 安装成功");
                else toast("Root 安装失败：" + msg.trim());
            });
        }).start();
    }

    private void installApkViaBuiltIn(Uri apkUri) {
        try {
            android.content.pm.PackageInstaller installer = getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams params = new android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = installer.createSession(params);
            android.content.pm.PackageInstaller.Session session = installer.openSession(sessionId);
            try (OutputStream out = session.openWrite("APK", 0, -1);
                 InputStream in = getContentResolver().openInputStream(apkUri)) {
                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                session.fsync(out);
            }
            Intent callback = new Intent("com.lemonlnch.ACTION_INSTALL_RESULT");
            callback.setPackage(getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(this, sessionId,
                    callback, flags);
            session.commit(pending.getIntentSender());
            session.close();
            toast("内置安装已提交，等待系统处理");
        } catch (Exception e) {
            toast("内置安装失败：" + e.getMessage());
        }
    }

    private String getApkPackageName(Uri apkUri) {
        try {
            String path = null;
            if ("file".equals(apkUri.getScheme())) {
                path = apkUri.getPath();
            } else {
                // content URI：复制到临时文件再获取
                File temp = File.createTempFile("apk_info_", ".apk", getCacheDir());
                try (InputStream in = getContentResolver().openInputStream(apkUri);
                     OutputStream out = new FileOutputStream(temp)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }
                path = temp.getAbsolutePath();
            }
            if (path == null) return null;
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(path, 0);
            return info != null ? info.packageName : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void startFileTransferServer() {
        if (fileTransferServer == null) {
            fileTransferServer = new FileTransferServer();
            fileTransferServer.start();
        }
    }

    private void stopFileTransferServer() {
        if (fileTransferServer != null) {
            fileTransferServer.stopServer();
            fileTransferServer = null;
        }
    }

    private void rememberTransferItem(TransferItem item) {
        if (transferPrefs == null || item == null) return;
        Set<String> saved = new HashSet<>(transferPrefs.getStringSet("received_items", new HashSet<>()));
        saved.add(item.toPersisted());
        transferPrefs.edit().putStringSet("received_items", saved).apply();
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "uploaded_file";
        fileName = fileName.replace('\\', '_').replace('/', '_');
        fileName = fileName.replaceAll("[\\u0000-\\u001F\\u007F]", "_").trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "uploaded_file";
        }
        return fileName.length() > 180 ? fileName.substring(0, 180) : fileName;
    }

    private String uniqueFileName(String requested) {
        String name = sanitizeFileName(requested);
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String candidate = name;
        int index = 1;
        while (new File(downloadDir, candidate).exists() && index < 10000) {
            candidate = base + " (" + index + ")" + ext;
            index++;
        }
        return candidate;
    }

    private TransferItem saveUploadedFile(String fileName, String mimeType, InputStream data, long length) throws IOException {
        String safeName = uniqueFileName(fileName);
        String resolvedMime = mimeType == null || mimeType.isEmpty() || "application/octet-stream".equalsIgnoreCase(mimeType)
                ? guessMimeType(safeName) : mimeType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, resolvedMime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(uri))) {
                        if (out == null) throw new IOException("无法写入 Download 文件");
                        copyExactly(data, out, length);
                    } catch (Exception e) {
                        try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                        throw e;
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                    return new TransferItem(safeName, uri, null, length, System.currentTimeMillis());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Exception lastError = null;
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                downloadDir.mkdirs();
            }
            if (downloadDir.exists() && downloadDir.canWrite()) {
                File outFile = new File(downloadDir, safeName);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    copyExactly(data, out, length);
                }
                try {
                    Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scan.setData(Uri.fromFile(outFile));
                    sendBroadcast(scan);
                } catch (Exception ignored) {}
                return new TransferItem(safeName, null, outFile, outFile.length(), outFile.lastModified());
            }
        } catch (Exception e) {
            lastError = e;
            e.printStackTrace();
        }

        try {
            File appDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (appDir == null) appDir = getExternalFilesDir(null);
            if (appDir == null) appDir = getFilesDir();
            if (!appDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                appDir.mkdirs();
            }
            File outFile = new File(appDir, safeName);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile))) {
                copyExactly(data, out, length);
            }
            return new TransferItem(safeName, null, outFile, outFile.length(), outFile.lastModified());
        } catch (Exception e) {
            e.printStackTrace();
            if (lastError != null) {
                throw new IOException("无法保存文件: " + lastError.getMessage() + " / " + e.getMessage(), e);
            }
            throw new IOException("无法保存文件: " + e.getMessage(), e);
        }
    }

    private void copyExactly(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long remaining = length;
        while (remaining > 0) {
            int want = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, want);
            if (read < 0) throw new IOException("上传数据不完整");
            out.write(buffer, 0, read);
            remaining -= read;
        }
        out.flush();
    }

    private TransferItem readPersistedItem(String value) {
        return TransferItem.fromPersisted(this, value);
    }

    private class FileTransferServer extends Thread {
        private ServerSocket serverSocket;
        private volatile boolean running = true;

        public void stopServer() {
            running = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new java.net.InetSocketAddress(6655), 50);
                while (running) {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(600000);
                    new Thread(() -> handleClient(client), "file-transfer-client").start();
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }

        private void handleClient(Socket client) {
            OutputStream out = null;
            try {
                Socket socket = client;
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 64 * 1024);
                out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);

                String requestLine = readLine(in);
                if (requestLine == null || requestLine.isEmpty()) {
                    try { socket.close(); } catch (Exception ignored) {}
                    return;
                }
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendResponse(out, 400, "text/plain; charset=utf-8", "Bad Request".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String method = parts[0];
                String path = parts[1];

                Map<String, String> headers = new java.util.HashMap<>();
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                                line.substring(colon + 1).trim());
                    }
                }

                if ("OPTIONS".equalsIgnoreCase(method)) {
                    sendCorsOk(out);
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    if ("/favicon.ico".equals(stripQuery(path))) {
                        sendResponse(out, 204, "text/plain", new byte[0]);
                    } else {
                        handleGet(path, out);
                    }
                } else if ("POST".equalsIgnoreCase(method) && "/upload".equals(stripQuery(path))) {
                    handleUpload(in, out, headers);
                } else {
                    send404(out);
                }
                out.flush();
            } catch (SocketTimeoutException e) {
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if (out != null) {
                        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        sendResponse(out, 500, "text/plain; charset=utf-8",
                                ("Server error: " + msg).getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }

        private void sendCorsOk(OutputStream out) throws IOException {
            String headers = "HTTP/1.1 204 No Content\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                    + "Access-Control-Allow-Headers: Content-Type, X-File-Name-UTF8, X-File-Mime, Content-Length\r\n"
                    + "Access-Control-Max-Age: 86400\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        private String stripQuery(String path) {
            int q = path.indexOf('?');
            return q >= 0 ? path.substring(0, q) : path;
        }

        private String readLine(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(256);
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\r') {
                    int next = in.read();
                    if (next != '\n' && next != -1) {
                        bytes.write(next);
                    }
                    break;
                }
                if (c == '\n') break;
                if (bytes.size() < 8192) bytes.write(c);
            }
            if (bytes.size() == 0 && c == -1) return null;
            return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        }

        private String decodeHeaderUtf8(String value) {
            if (value == null || value.isEmpty()) return value;
            try {
                byte[] raw = value.getBytes(StandardCharsets.ISO_8859_1);
                String utf8 = new String(raw, StandardCharsets.UTF_8);
                if (!utf8.contains("\uFFFD") && !utf8.equals(value)) {
                    return utf8;
                }
            } catch (Exception ignored) {
            }
            return value;
        }

        private void handleGet(String path, OutputStream out) throws IOException {
            String assetPath = stripQuery(path);
            if ("/".equals(assetPath)) assetPath = "/index.html";
            if (assetPath.contains("..")) {
                send404(out);
                return;
            }
            String assetName = "wap" + assetPath;
            try (InputStream assetIn = getAssets().open(assetName)) {
                sendResponse(out, 200, guessAssetMime(assetName), assetIn);
            } catch (IOException e) {
                send404(out);
            }
        }

        private String guessAssetMime(String fileName) {
            String lower = fileName.toLowerCase(Locale.US);
            if (lower.endsWith(".html")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".gif")) return "image/gif";
            return "application/octet-stream";
        }

        private void handleUpload(InputStream in, OutputStream out, Map<String, String> headers) throws IOException {
            String contentType = headers.get("content-type");
            String contentLengthHeader = headers.get("content-length");
            if (contentLengthHeader == null) {
                sendResponse(out, 411, "text/plain; charset=utf-8",
                        "Content-Length required".getBytes(StandardCharsets.UTF_8));
                return;
            }

            long contentLength;
            try {
                contentLength = Long.parseLong(contentLengthHeader.trim());
            } catch (NumberFormatException e) {
                sendResponse(out, 400, "text/plain; charset=utf-8",
                        "Invalid Content-Length".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (contentLength < 0 || contentLength > 8L * 1024L * 1024L * 1024L) {
                sendResponse(out, 413, "text/plain; charset=utf-8",
                        "File too large".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String fileName = decodeExplicitFilename(headers.get("x-file-name-utf8"));
            String mimeType = headers.get("x-file-mime");
            if (mimeType == null || mimeType.isEmpty()) {
                if (contentType != null && !contentType.toLowerCase(Locale.US).contains("multipart/")) {
                    mimeType = contentType;
                }
            }

            boolean isMultipart = contentType != null
                    && contentType.toLowerCase(Locale.US).contains("multipart/form-data");

            try {
                TransferItem item;
                if (isMultipart) {
                    item = handleMultipartUpload(in, contentLength, contentType, headers);
                } else {
                    if (fileName == null || fileName.isEmpty()) {
                        fileName = "uploaded_file";
                    }
                    File temp = File.createTempFile("lemon_raw_", ".part", getCacheDir());
                    try {
                        try (OutputStream tempOut = new BufferedOutputStream(new FileOutputStream(temp), 64 * 1024)) {
                            copyExactly(in, tempOut, contentLength);
                        }
                        try (InputStream tempIn = new BufferedInputStream(new java.io.FileInputStream(temp), 64 * 1024)) {
                            item = saveUploadedFile(fileName, mimeType, tempIn, temp.length());
                        }
                    } finally {
                        //noinspection ResultOfMethodCallIgnored
                        temp.delete();
                    }
                }
                if (item == null) {
                    sendResponse(out, 500, "text/plain; charset=utf-8",
                            "Save failed".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                rememberTransferItem(item);
                try {
                    runOnUiThread(() -> {
                        try { refreshFileList(); } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
                sendResponse(out, 200, "text/plain; charset=utf-8",
                        ("OK:" + sanitizeFileName(item.name)).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (msg.length() > 200) msg = msg.substring(0, 200);
                sendResponse(out, 500, "text/plain; charset=utf-8",
                        ("Upload error: " + msg).getBytes(StandardCharsets.UTF_8));
            }
        }

        private TransferItem handleMultipartUpload(InputStream in, long contentLength,
                                                   String contentType, Map<String, String> headers) throws IOException {
            String boundary = extractBoundary(contentType);
            if (boundary == null || boundary.isEmpty()) {
                throw new IOException("Boundary not found");
            }
            byte[] opening = ("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
            if (!consumeExactly(in, opening)) {
                throw new IOException("Invalid multipart body");
            }
            Map<String, String> partHeaders = new java.util.HashMap<>();
            long consumedBeforeFile = opening.length;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                consumedBeforeFile += line.getBytes(StandardCharsets.ISO_8859_1).length + 2;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    partHeaders.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                            line.substring(colon + 1).trim());
                }
            }
            consumedBeforeFile += 2;

            String disposition = partHeaders.get("content-disposition");
            String explicitName = headers.get("x-file-name-utf8");
            String fileName = decodeExplicitFilename(explicitName);
            if (fileName == null || fileName.isEmpty()) {
                fileName = extractFilename(disposition);
            }
            if (fileName == null) fileName = "uploaded_file";
            String mimeType = partHeaders.get("content-type");

            long partRemaining = contentLength - consumedBeforeFile;
            if (partRemaining <= 0) {
                throw new IOException("Empty multipart body");
            }

            File temp = File.createTempFile("lemon_transfer_", ".part", getCacheDir());
            byte[] marker = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
            final int markerLen = marker.length;
            byte[] readBuf = new byte[64 * 1024];
            byte[] window = new byte[markerLen];
            int windowSize = 0;
            long consumed = 0;
            boolean found = false;

            try (OutputStream tempOut = new BufferedOutputStream(new FileOutputStream(temp), 64 * 1024)) {
                while (consumed < partRemaining && !found) {
                    int toRead = (int) Math.min(readBuf.length, partRemaining - consumed);
                    int n = in.read(readBuf, 0, toRead);
                    if (n < 0) break;
                    consumed += n;
                    int offset = 0;
                    while (offset < n) {
                        if (windowSize < markerLen) {
                            int take = Math.min(markerLen - windowSize, n - offset);
                            System.arraycopy(readBuf, offset, window, windowSize, take);
                            windowSize += take;
                            offset += take;
                            if (windowSize < markerLen) continue;
                        }
                        if (startsWith(window, marker)) {
                            found = true;
                            break;
                        }
                        tempOut.write(window[0]);
                        System.arraycopy(window, 1, window, 0, markerLen - 1);
                        windowSize = markerLen - 1;
                    }
                }
                if (!found && windowSize > 0) {
                    tempOut.write(window, 0, windowSize);
                }
                tempOut.flush();
            }

            if (!found) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("Invalid multipart data (boundary not found)");
            }

            try (InputStream tempIn = new BufferedInputStream(new java.io.FileInputStream(temp), 64 * 1024)) {
                return saveUploadedFile(fileName, mimeType, tempIn, temp.length());
            } finally {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }

        private boolean startsWith(byte[] data, byte[] pattern) {
            if (data.length < pattern.length) return false;
            for (int i = 0; i < pattern.length; i++) if (data[i] != pattern[i]) return false;
            return true;
        }

        private boolean consumeExactly(InputStream in, byte[] expected) throws IOException {
            byte[] buffer = new byte[expected.length];
            int off = 0;
            while (off < expected.length) {
                int r = in.read(buffer, off, expected.length - off);
                if (r < 0) return false;
                off += r;
            }
            return java.util.Arrays.equals(buffer, expected);
        }

        private String extractBoundary(String contentType) {
            String lower = contentType.toLowerCase(Locale.US);
            int idx = lower.indexOf("boundary=");
            if (idx < 0) return null;
            String value = contentType.substring(idx + 9).trim();
            if (value.startsWith("\"")) {
                int end = value.indexOf('"', 1);
                return end > 1 ? value.substring(1, end) : null;
            }
            int semi = value.indexOf(';');
            return semi >= 0 ? value.substring(0, semi).trim() : value;
        }

        private String decodeExplicitFilename(String encoded) {
            if (encoded == null || encoded.isEmpty()) return null;
            try {
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {
                return null;
            }
        }

        private String extractFilename(String disposition) {
            if (disposition == null) return null;
            String decodedDisposition = decodeHeaderUtf8(disposition);
            String lower = decodedDisposition.toLowerCase(Locale.US);

            int star = lower.indexOf("filename*=");
            if (star >= 0) {
                String value = decodedDisposition.substring(star + 10).trim();
                if (value.startsWith("\"")) {
                    int endQuote = value.indexOf('\"', 1);
                    if (endQuote > 0) value = value.substring(1, endQuote);
                } else {
                    int semi = value.indexOf(';');
                    if (semi >= 0) value = value.substring(0, semi).trim();
                }
                int charsetSep = value.indexOf("''");
                if (charsetSep >= 0) {
                    String charset = value.substring(0, charsetSep);
                    String encoded = value.substring(charsetSep + 2);
                    try {
                        return URLDecoder.decode(encoded, charset.isEmpty() ? "UTF-8" : charset);
                    } catch (Exception ignored) {
                        try { return URLDecoder.decode(encoded, "UTF-8"); } catch (Exception ignored2) { }
                    }
                }
                try { return URLDecoder.decode(value, "UTF-8"); } catch (Exception ignored) { }
            }

            int normal = lower.indexOf("filename=");
            if (normal >= 0) {
                String value = decodedDisposition.substring(normal + 9).trim();
                if (value.startsWith("\"")) {
                    int endQuote = value.indexOf('\"', 1);
                    if (endQuote > 0) value = value.substring(1, endQuote);
                } else {
                    int semi = value.indexOf(';');
                    if (semi >= 0) value = value.substring(0, semi).trim();
                }
                return decodeHeaderUtf8(value);
            }
            return null;
        }

        private void sendResponse(OutputStream out, int statusCode, String mime, InputStream data) throws IOException {
            String reason = statusReason(statusCode);
            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ").append(statusCode).append(' ').append(reason).append("\r\n");
            headers.append("Content-Type: ").append(mime).append("\r\n");
            headers.append("Access-Control-Allow-Origin: *\r\n");
            headers.append("Cache-Control: no-store\r\n");
            headers.append("Connection: close\r\n\r\n");
            out.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            byte[] buffer = new byte[32 * 1024];
            int len;
            while ((len = data.read(buffer)) != -1) out.write(buffer, 0, len);
            out.flush();
        }

        private void sendResponse(OutputStream out, int statusCode, String mime, byte[] data) throws IOException {
            String reason = statusReason(statusCode);
            String headers = "HTTP/1.1 " + statusCode + " " + reason + "\r\n"
                    + "Content-Type: " + mime + "\r\n"
                    + "Content-Length: " + data.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            out.write(data);
            out.flush();
        }

        private String statusReason(int code) {
            switch (code) {
                case 200: return "OK";
                case 204: return "No Content";
                case 400: return "Bad Request";
                case 404: return "Not Found";
                case 411: return "Length Required";
                case 413: return "Payload Too Large";
                default: return "Error";
            }
        }

        private void send404(OutputStream out) throws IOException {
            sendResponse(out, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
        }
    }

    static class TransferItem {
        final String name;
        final Uri uri;
        final File file;
        final long size;
        final long modified;

        TransferItem(String name, Uri uri, File file, long size, long modified) {
            this.name = name;
            this.uri = uri;
            this.file = file;
            this.size = size;
            this.modified = modified;
        }

        boolean isApk() { return name != null && name.toLowerCase(Locale.US).endsWith(".apk"); }
        boolean exists() {
            if (file != null) return file.exists();
            return uri != null;
        }
        long length() {
            if (file != null) return file.length();
            return size;
        }
        long lastModified() {
            if (file != null) return file.lastModified();
            return modified;
        }

        boolean sameRecordAs(TransferItem other) {
            if (other == null) return false;
            String thisLocation = uri != null ? uri.toString() : (file != null ? file.getAbsolutePath() : "");
            String otherLocation = other.uri != null ? other.uri.toString() : (other.file != null ? other.file.getAbsolutePath() : "");
            return TextUtils.equals(name, other.name) && TextUtils.equals(thisLocation, otherLocation);
        }

        String toPersisted() {
            String type = uri != null ? "uri" : "file";
            String value = uri != null ? uri.toString() : file.getAbsolutePath();
            return type + "\t" + android.util.Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                    + "\t" + android.util.Base64.encodeToString(name.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                    + "\t" + size + "\t" + modified;
        }

        private static String queryDisplayName(Context context, Uri uri) {
            if (context == null || uri == null) return null;
            try (android.database.Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) return cursor.getString(index);
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        static TransferItem fromPersisted(Context context, String value) {
            try {
                String[] parts = value.split("\\t", -1);
                if (parts.length < 5) return null;
                String location = new String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
                String name = new String(android.util.Base64.decode(parts[2], android.util.Base64.DEFAULT), StandardCharsets.UTF_8);
                long size = Long.parseLong(parts[3]);
                long modified = Long.parseLong(parts[4]);
                if ("uri".equals(parts[0])) {
                    Uri uri = Uri.parse(location);
                    String canonicalName = queryDisplayName(context, uri);
                    if (canonicalName != null && !canonicalName.isEmpty()) {
                        name = canonicalName;
                    }
                    return new TransferItem(name, uri, null, size, modified);
                }
                File file = new File(location);
                String canonicalName = file.getName();
                if (canonicalName != null && !canonicalName.isEmpty()) {
                    name = canonicalName;
                }
                return new TransferItem(name, null, file, size, modified);
            } catch (Exception e) {
                return null;
            }
        }
    }

    static class AppEntry {
        final String packageName;
        final String label;
        final Intent launchIntent;
        final ApplicationInfo info;
        final Drawable banner;
        final boolean isFolder;
        final List<AppEntry> children;

        AppEntry(String packageName, String label, Intent launchIntent, ApplicationInfo info, Drawable banner) {
            this.packageName = packageName;
            this.label = label;
            this.launchIntent = launchIntent;
            this.info = info;
            this.banner = banner;
            this.isFolder = false;
            this.children = null;
        }

        private AppEntry(String folderName, List<AppEntry> children) {
            this.packageName = "folder:" + folderName;
            this.label = folderName;
            this.launchIntent = null;
            this.info = children.isEmpty() ? null : children.get(0).info;
            this.banner = children.isEmpty() ? null : children.get(0).banner;
            this.isFolder = true;
            this.children = children;
        }

        static AppEntry folder(String folderName, List<AppEntry> children) {
            return new AppEntry(folderName, children);
        }
    }

    static class GradientPreset {
        final String id;
        final String name;
        final boolean vertical;
        final int[] colors;

        GradientPreset(String id, String name, boolean vertical, String... colors) {
            this.id = id;
            this.name = name;
            this.vertical = vertical;
            this.colors = new int[colors.length];
            for (int i = 0; i < colors.length; i++) {
                this.colors[i] = Color.parseColor(colors[i]);
            }
        }
    }

    static class IconChipView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String icon;
        private final float density;

        IconChipView(Context context, String icon) {
            super(context);
            this.icon = icon == null ? "" : icon;
            this.density = context.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            paint.setColor(Color.WHITE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if ("search".equals(icon)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3.4f * density);
                canvas.drawCircle(cx - 4f * density, cy - 3f * density, 9f * density, paint);
                canvas.drawLine(cx + 4f * density, cy + 5f * density, cx + 15f * density, cy + 16f * density, paint);
                return;
            }
            if ("LemonLnch".equals(icon)) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(17f * density);
                canvas.drawText("M", cx, cy + 6f * density, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * density);
                canvas.drawRoundRect(cx - 19f * density, cy - 13f * density,
                        cx + 19f * density, cy + 13f * density,
                        7f * density, 7f * density, paint);
            }
        }
    }

    static class WeatherAnimationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String condition;
        private long startTime;
        private boolean running;

        WeatherAnimationView(Context context, String condition) {
            super(context);
            this.condition = condition == null ? "" : condition.toLowerCase(Locale.ROOT);
            setWillNotDraw(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            running = true;
            startTime = System.currentTimeMillis();
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            running = false;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float t = (System.currentTimeMillis() - startTime) / 1000f;

            paint.setShader(new android.graphics.LinearGradient(0, 0, w, h,
                    Color.argb(135, 82, 198, 230),
                    Color.argb(118, 255, 221, 170),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setShader(new android.graphics.RadialGradient(w * 0.78f, h * 0.2f, h * 0.62f,
                    Color.argb(86, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.78f, h * 0.2f, h * 0.62f, paint);
            paint.setShader(null);

            if (condition.contains("rain")) {
                drawClouds(canvas, w, h, t);
                drawRain(canvas, w, h, t);
            } else if (condition.contains("snow")) {
                drawClouds(canvas, w, h, t);
                drawSnow(canvas, w, h, t);
            } else if (condition.contains("storm") || condition.contains("thunder")) {
                drawClouds(canvas, w, h, t);
                drawRain(canvas, w, h, t);
                if (((int) (t * 2)) % 9 == 0) drawLightning(canvas, w, h);
            } else if (condition.contains("fog") || condition.contains("mist")) {
                drawFog(canvas, w, h, t);
            } else if (condition.contains("sun") || condition.contains("clear")) {
                drawSun(canvas, w, h, t);
            } else {
                drawClouds(canvas, w, h, t);
            }

            if (running) postInvalidateOnAnimation();
        }

        private void drawSun(Canvas canvas, int w, int h, float t) {
            float cx = w * 0.74f;
            float cy = h * 0.34f;
            float pulse = (float) Math.sin(t * 1.6f) * 7f;
            paint.setShader(new android.graphics.RadialGradient(cx, cy, h * 0.34f + pulse,
                    Color.argb(125, 255, 244, 175),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, h * 0.34f + pulse, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(55, 255, 255, 255));
            canvas.drawCircle(cx - h * 0.035f, cy - h * 0.055f, h * 0.18f, paint);
            paint.setShader(new android.graphics.RadialGradient(cx - h * 0.04f, cy - h * 0.05f, h * 0.2f,
                    Color.rgb(255, 250, 186),
                    Color.rgb(255, 186, 70),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, h * 0.17f, paint);
            paint.setShader(null);
        }

        private void drawClouds(Canvas canvas, int w, int h, float t) {
            if (condition.contains("clear") || condition.contains("part")) {
                drawSun(canvas, w, h, t);
            }
            drawCloud(canvas, (w * 0.58f + (t * 18f) % (w * 0.34f)) % (w + 120) - 60, h * 0.38f, h * 0.13f, 205);
            drawCloud(canvas, (w * 0.2f + (t * 10f) % (w * 0.44f)) % (w + 140) - 70, h * 0.62f, h * 0.09f, 118);
        }

        private void drawCloud(Canvas canvas, float x, float y, float r, int alpha) {
            paint.setColor(Color.argb(Math.min(95, alpha / 2), 0, 0, 0));
            canvas.drawRoundRect(x - r * 0.35f, y + r * 0.33f, x + r * 2.38f, y + r * 1.02f, r, r, paint);
            paint.setShader(new android.graphics.RadialGradient(x + r * 0.7f, y - r * 0.18f, r * 2.1f,
                    Color.argb(alpha, 255, 255, 255),
                    Color.argb(Math.max(72, alpha - 62), 222, 232, 238),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
            canvas.drawCircle(x + r * 0.9f, y - r * 0.28f, r * 1.12f, paint);
            canvas.drawCircle(x + r * 1.9f, y, r * 0.86f, paint);
            canvas.drawRoundRect(x - r * 0.45f, y, x + r * 2.4f, y + r * 0.9f, r, r, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(Math.min(115, alpha / 2), 255, 255, 255));
            canvas.drawCircle(x + r * 0.48f, y - r * 0.18f, r * 0.34f, paint);
        }

        private void drawRain(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(150, 190, 225, 255));
            paint.setStrokeWidth(3f);
            for (int i = 0; i < 18; i++) {
                float x = (i * w / 17f + (t * 34f)) % w;
                float y = ((i * 37f) + (t * 130f)) % h;
                canvas.drawLine(x, y, x - 10, y + 28, paint);
            }
        }

        private void drawSnow(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(175, 255, 255, 255));
            for (int i = 0; i < 18; i++) {
                float x = (i * w / 17f + (float) Math.sin(t + i) * 12f) % w;
                float y = ((i * 43f) + (t * 42f)) % h;
                canvas.drawCircle(x, y, 3f + (i % 3), paint);
            }
        }

        private void drawFog(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(72, 255, 255, 255));
            for (int i = 0; i < 5; i++) {
                float y = h * (0.22f + i * 0.14f);
                float x = ((t * 26f + i * 80f) % (w + 180)) - 90;
                canvas.drawRoundRect(x - 180, y, x + 260, y + h * 0.08f, 40, 40, paint);
            }
        }

        private void drawLightning(Canvas canvas, int w, int h) {
            paint.setColor(Color.argb(185, 255, 246, 138));
            paint.setStrokeWidth(6f);
            float x = w * 0.72f;
            canvas.drawLine(x, h * 0.18f, x - 24, h * 0.44f, paint);
            canvas.drawLine(x - 24, h * 0.44f, x + 8, h * 0.42f, paint);
            canvas.drawLine(x + 8, h * 0.42f, x - 18, h * 0.7f, paint);
        }
    }
}