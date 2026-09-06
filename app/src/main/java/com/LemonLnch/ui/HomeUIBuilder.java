package com.LemonLnch.ui;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import java.lang.reflect.Method;
import java.util.Set;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.LemonLnch.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeUIBuilder {
    private final MainActivity activity;

    private FrameLayout page1;
    private FrameLayout page2;
    private boolean isPage1 = true;
    private View dockView;
    private View firstDockSlot;
    private List<View> dockSlots = new ArrayList<>();
    private View appContainerCardView;
    private List<View> topControls = new ArrayList<>();
    private float downX;
    private float downY;
    private boolean intercepting = false;
    private final int touchSlop;

    HomeUIBuilder(MainActivity activity) {
        this.activity = activity;
        ViewConfiguration config = ViewConfiguration.get(activity);
        this.touchSlop = config.getScaledTouchSlop();
    }

    void build() {
        // 重建时清空焦点目标引用，避免关闭直播预览后仍指向已脱离层级的旧卡片
        appContainerCardView = null;
        firstDockSlot = null;
        dockSlots.clear();
        topControls.clear();
        dockView = null;

        PageSwitcherFrameLayout root = new PageSwitcherFrameLayout(activity);
        activity.launcherRoot = root;
        root.setClipChildren(false);
        root.setClipToPadding(false);
        addBackground(root);

        View topWash = new View(activity);
        topWash.setBackgroundResource(R.drawable.home_scrim);
        root.addView(topWash, new FrameLayout.LayoutParams(-1, -1));

        page1 = new FrameLayout(activity);
        page1.setClipChildren(false);
        page1.setClipToPadding(false);

        // 直播预览默认关闭；关闭时桌面不创建/显示直播预览控件。
        if (activity.isLivePreviewEnabled()) {
            addAppContainerCard(page1);
        }

        if (!activity.prefs.getBoolean("minimal_status", false)) {
            View topBar = buildHomeTopBar();
            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, activity.dp(64), Gravity.TOP);
            topLp.topMargin = activity.dp(14);
            topLp.leftMargin = activity.dp(activity.horizontalPaddingDp());
            topLp.rightMargin = activity.dp(activity.horizontalPaddingDp());
            page1.addView(topBar, topLp);
        }

        if (!activity.prefs.getBoolean("hide_featured", false)) {
            dockView = buildDock();
            FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, activity.dp(110), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            dockLp.bottomMargin = activity.dp(16);
            dockLp.leftMargin = activity.dp(activity.horizontalPaddingDp());
            dockLp.rightMargin = activity.dp(activity.horizontalPaddingDp());
            page1.addView(dockView, dockLp);
        }

        page2 = new FrameLayout(activity);
        page2.setClipChildren(false);
        page2.setClipToPadding(false);

        activity.homeScroll = new ScrollView(activity);
        activity.homeScroll.setFillViewport(false);
        activity.homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        activity.homeScroll.setClipChildren(false);
        activity.homeScroll.setClipToPadding(false);
        activity.homeScroll.setPadding(0, activity.dp(14), 0, activity.dp(28));
        page2.addView(activity.homeScroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(activity.dp(activity.horizontalPaddingDp()), activity.dp(18),
                activity.dp(activity.horizontalPaddingDp()), activity.dp(18));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        activity.homeScroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        if (!activity.prefs.getBoolean("hide_app_titles", false)) {
            TextView title = new TextView(activity);
            title.setText("应用列表");
            title.setTextColor(Color.WHITE);
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        }

        activity.appsGrid = new GridLayout(activity);
        activity.appsGrid.setColumnCount(activity.appsPerRow());
        activity.appsGrid.setPadding(0, activity.dp(14), 0, activity.dp(42));
        activity.appsGrid.setClipChildren(false);
        activity.appsGrid.setClipToPadding(false);
        content.addView(activity.appsGrid, new LinearLayout.LayoutParams(-1, -2));

        root.addView(page1, new FrameLayout.LayoutParams(-1, -1));
        root.addView(page2, new FrameLayout.LayoutParams(-1, -1));

        switchToPage(true);
        activity.setContentView(root);
    }

    private class PageSwitcherFrameLayout extends FrameLayout {
        public PageSwitcherFrameLayout(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                int keyCode = event.getKeyCode();
                if (!isPage1) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) {
                        switchToPage(true);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isFocusAtTopRowOfAppGrid()) {
                        switchToPage(true);
                        return true;
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    intercepting = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (intercepting) return true;
                    float dy = ev.getY() - downY;
                    float dx = ev.getX() - downX;
                    float absDy = Math.abs(dy);
                    float absDx = Math.abs(dx);

                    if (isPage1 && absDy > touchSlop && absDy > absDx * 1.5f && dy < 0) {
                        intercepting = true;
                        switchToPage(false);
                        return true;
                    }

                    if (!isPage1 && absDy > touchSlop && absDy > absDx * 1.5f && dy > 0
                            && activity.homeScroll != null && !activity.homeScroll.canScrollVertically(-1)) {
                        intercepting = true;
                        switchToPage(true);
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    intercepting = false;
                    return false;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return true;
        }
    }

    private boolean isFocusAtTopRowOfAppGrid() {
        View focus = activity.getCurrentFocus();
        if (focus == null || activity.appsGrid == null) return false;
        int columnCount = activity.appsPerRow();
        if (columnCount <= 0) return false;

        View temp = focus;
        while (temp != null && temp.getParent() != activity.appsGrid) {
            temp = (View) temp.getParent();
        }
        if (temp == null) return false;

        int index = activity.appsGrid.indexOfChild(temp);
        return index >= 0 && index < columnCount;
    }

    private void switchToPage(boolean showPage1) {
        isPage1 = showPage1;
        if (page1 != null) page1.setVisibility(showPage1 ? View.VISIBLE : View.GONE);
        if (page2 != null) page2.setVisibility(showPage1 ? View.GONE : View.VISIBLE);

        if (showPage1) {
            View target = firstDockSlot;
            if (target == null && activity.searchPill != null) target = activity.searchPill;
            if (target == null && appContainerCardView != null) target = appContainerCardView;
            if (target != null) {
                target.post(target::requestFocus);
            }
        } else {
            activity.appsGrid.post(() -> {
                if (activity.appsGrid != null && activity.appsGrid.getChildCount() > 0) {
                    View cell = activity.appsGrid.getChildAt(0);
                    if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
                        View card = ((LinearLayout) cell).getChildAt(0);
                        if (card != null) card.requestFocus();
                    }
                }
            });
        }
    }

    void focusAppContainerCard() {
        if (appContainerCardView != null) appContainerCardView.requestFocus();
    }

    void focusDockFirst() {
        if (firstDockSlot != null) firstDockSlot.requestFocus();
        else if (activity.appsGrid != null) switchToPage(false);
    }

    private void addAppContainerCard(FrameLayout page) {
        FrameLayout card = new FrameLayout(activity);
        appContainerCardView = card;

        if (activity.previewCardController != null) {
            activity.previewCardController.attachToContainer(card);
        }

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    View topFocus = activity.searchPill;
                    if (topFocus != null) {
                        topFocus.requestFocus();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (firstDockSlot != null) {
                        firstDockSlot.requestFocus();
                        return true;
                    }
                    switchToPage(false);
                    return true;
                }
            }
            return false;
        });

        page.addView(card);
        Runnable layout = () -> {
            int pw = page.getWidth();
            int ph = page.getHeight();
            if (pw <= 0 || ph <= 0) return;
            // 等比例稍放大：由 0.52/0.46 提升到约 0.60/0.54，最小尺寸同步放大
            int maxW = (int) (pw * 0.60f);
            int maxH = (int) (ph * 0.54f);
            int width = Math.min(maxW, (int) (maxH * 16f / 9f));
            int height = Math.min(maxH, (int) (width * 9f / 16f));
            width = Math.max(activity.dp(360), width);
            height = Math.max(activity.dp(202), height);
            if (width > pw - activity.dp(24)) {
                width = pw - activity.dp(24);
                height = (int) (width * 9f / 16f);
            }
            if (height > ph - activity.dp(24)) {
                height = ph - activity.dp(24);
                width = (int) (height * 16f / 9f);
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    width, height, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = Math.max(activity.dp(12), (int) (ph * 0.15f));
            if (lp.topMargin + height > ph - activity.dp(12)) {
                lp.topMargin = Math.max(0, ph - height - activity.dp(12));
            }
            card.setLayoutParams(lp);
        };
        page.post(layout);
        page.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> layout.run());

        if (activity.searchPill != null) {
            activity.searchPill.setNextFocusDownId(card.getId());
            card.setNextFocusUpId(activity.searchPill.getId());
        }
    }

    private void addBackground(FrameLayout root) {
        String mode = activity.prefs.getString("bg_mode", "light");
        if ("dark".equals(mode)) {
            root.setBackgroundResource(R.drawable.bg_launcher);
            return;
        }
        if ("gradient".equals(mode)) {
            root.setBackground(activity.gradientPicker.currentGradientDrawable());
            return;
        }
        if ("image".equals(mode)) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageURI(Uri.parse(activity.prefs.getString("bg_uri", "")));
            } catch (Exception e) {
                image.setImageResource(R.drawable.wallpaper_mountain);
            }
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if ("url".equals(mode)) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            File f = new File(activity.getFilesDir(), "url_wallpaper.img");
            if (f.exists()) image.setImageURI(Uri.fromFile(f));
            else image.setImageResource(R.drawable.wallpaper_mountain);
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if ("video".equals(mode)) {
            VideoView video = new VideoView(activity);
            try {
                activity.currentVideoBackground = video;
                video.setVideoURI(Uri.parse(activity.prefs.getString("bg_video_uri", "")));
                video.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    video.start();
                });
                root.addView(video, new FrameLayout.LayoutParams(-1, -1));
                return;
            } catch (Exception ignored) {
            }
        }
        ImageView wallpaper = new ImageView(activity);
        wallpaper.setImageResource(R.drawable.wallpaper_mountain);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
    }

    private View buildHomeTopBar() {
        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        int chipWidth = activity.screenWidthDp < 600 ? activity.dp(56) : activity.dp(78);
        int chipHeight = activity.dp(50);
        int marginRight = activity.dp(10);

        TextClock clock = new TextClock(activity);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour(activity.prefs.getBoolean("use_24h", true) ? "HH:mm" : "h:mm");
        clock.setGravity(Gravity.CENTER);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(activity.screenWidthDp < 600 ? 18 : 24);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setBackgroundResource(R.drawable.glass_chip);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                activity.screenWidthDp < 600 ? activity.dp(80) : activity.dp(120), chipHeight);
        top.addView(clock, clockLp);

        TextView filler = new TextView(activity);
        top.addView(filler, new LinearLayout.LayoutParams(0, 1, 1));

        // ===== 火箭按钮（清理内存） =====
        View rocketButton = imageIconChip(R.drawable.ic_rocket, 24, Color.WHITE);
        rocketButton.setOnClickListener(v -> activity.memoryCleaner.cleanMemory(true, null));
        LinearLayout.LayoutParams rocketLp = new LinearLayout.LayoutParams(chipWidth, chipHeight);
        rocketLp.rightMargin = marginRight;
        top.addView(rocketButton, rocketLp);

        // ===== 文件传输按钮（弹出文件快传弹窗） =====
        View transferButton = imageIconChip(R.drawable.ic_transfer, 24, Color.WHITE);
        transferButton.setOnClickListener(v -> {
            activity.showFileTransferDialog();
        });
        LinearLayout.LayoutParams transferLp = new LinearLayout.LayoutParams(chipWidth, chipHeight);
        transferLp.rightMargin = marginRight;
        top.addView(transferButton, transferLp);

        // ===== 网络按钮 =====
        int networkColor = isNetworkConnected() ? Color.BLUE : Color.WHITE;
        activity.searchPill = imageIconChip(R.drawable.ic_network, 28, networkColor);
        activity.searchPill.setOnClickListener(v -> openNetworkSettings());
        LinearLayout.LayoutParams networkLp = new LinearLayout.LayoutParams(chipWidth, chipHeight);
        networkLp.rightMargin = marginRight;
        top.addView(activity.searchPill, networkLp);

        // ===== 蓝牙按钮 =====
        int bluetoothColor = isBluetoothConnected() ? Color.BLUE : Color.WHITE;
        activity.bluetoothPill = imageIconChip(R.drawable.ic_bluetooth, 24, bluetoothColor);
        activity.bluetoothPill.setOnClickListener(v -> openBluetoothSettings());
        LinearLayout.LayoutParams btLp = new LinearLayout.LayoutParams(chipWidth, chipHeight);
        btLp.rightMargin = marginRight;
        top.addView(activity.bluetoothPill, btLp);

        // ===== 设置按钮 =====
        View lemonSettings = imageIconChip(R.drawable.ic_settings_gear, 24, Color.WHITE);
        lemonSettings.setOnClickListener(v -> activity.showSettings("简介"));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(chipWidth, chipHeight);
        settingsLp.rightMargin = marginRight;
        top.addView(lemonSettings, settingsLp);

        // ===== 更新 topControls 列表（顺序与视觉顺序一致） =====
        topControls.clear();
        topControls.add(rocketButton);
        topControls.add(transferButton);
        topControls.add(activity.searchPill);
        topControls.add(activity.bluetoothPill);
        // 构建完成后立即刷新一次网络/蓝牙颜色
        if (activity.statusHandler != null) {
            activity.statusHandler.post(activity::updateServicePill);
        } else {
            activity.updateServicePill();
        }
        topControls.add(lemonSettings);

        View.OnKeyListener topBarKeyListener = (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int index = topControls.indexOf(v);
                    if (index > 0) {
                        topControls.get(index - 1).requestFocus();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int index = topControls.indexOf(v);
                    if (index >= 0 && index < topControls.size() - 1) {
                        topControls.get(index + 1).requestFocus();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    // 有直播预览时下键进卡片；关闭后下键直接到 dock
                    if (appContainerCardView != null && appContainerCardView.getParent() != null) {
                        appContainerCardView.requestFocus();
                        return true;
                    }
                    if (firstDockSlot != null) {
                        firstDockSlot.requestFocus();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        };

        for (View control : topControls) {
            control.setOnKeyListener(topBarKeyListener);
        }

        return top;
    }

    private View imageIconChip(int resId, int iconDp, int color) {
        FrameLayout chip = new FrameLayout(activity);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setBackgroundResource(R.drawable.glass_chip);
        chip.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.06f));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(resId);
        // SRC_IN 保证矢量图标可被稳定染色（蓝/白），兼容安卓 9
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 方便 MainActivity 实时改色时精确定位 ImageView
        chip.setTag(icon);
        icon.setTag("status_icon");
        chip.addView(icon, new FrameLayout.LayoutParams(activity.dp(iconDp), activity.dp(iconDp), Gravity.CENTER));
        return chip;
    }

    private void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? activity.dp(12) : 0).setDuration(130).start();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) return false;
                return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
            }
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        } catch (Exception e) {
            try {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            } catch (Exception e2) {
                return false;
            }
        }
    }

    public boolean isNetworkConnectedPublic() {
        return isNetworkConnected();
    }

    /** Public so MainActivity can refresh icon color on resume. */
    public boolean isBluetoothConnectedPublic() {
        return isBluetoothConnected();
    }

    private boolean isBluetoothConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return false;
        try {
            if (!adapter.isEnabled()) return false;
        } catch (SecurityException e) {
            return false;
        }

        // Android 12+ (API 31) needs BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (activity.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                // Still try profile state; some devices allow read without runtime grant
            }
        }

        try {
            int a2dpState = adapter.getProfileConnectionState(BluetoothProfile.A2DP);
            int headsetState = adapter.getProfileConnectionState(BluetoothProfile.HEADSET);
            if (a2dpState == BluetoothProfile.STATE_CONNECTED
                    || headsetState == BluetoothProfile.STATE_CONNECTED) {
                return true;
            }
            // Also check HID / other profiles when available
            try {
                int hidState = adapter.getProfileConnectionState(4); // BluetoothProfile.HID_DEVICE = 4 (API 28+)
                if (hidState == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (Exception ignored) {}

            // Android 9 and many TV boxes: profile state may lag or be incomplete.
            // Fall back to bonded devices + reflection isConnected().
            Set<BluetoothDevice> bonded = null;
            try {
                bonded = adapter.getBondedDevices();
            } catch (SecurityException se) {
                bonded = null;
            }
            if (bonded != null && !bonded.isEmpty()) {
                for (BluetoothDevice device : bonded) {
                    if (device == null) continue;
                    try {
                        Method isConnectedMethod = device.getClass().getMethod("isConnected");
                        Object result = isConnectedMethod.invoke(device);
                        if (result instanceof Boolean && (Boolean) result) {
                            return true;
                        }
                    } catch (Exception ignored) {
                        // Some OEM firmwares hide the method; ignore.
                    }
                }
            }
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void openNetworkSettings() {
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.toast("无法打开网络设置");
        }
    }

    private void openBluetoothSettings() {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.toast("无法打开蓝牙设置");
        }
    }

    private View buildDock() {
        LinearLayout dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        int slotCount = 6;
        int slotMargin = activity.dp(6);
        int slotWidth = (activity.getResources().getDisplayMetrics().widthPixels
                - activity.dp(activity.horizontalPaddingDp() * 2) - slotMargin * (slotCount - 1)) / slotCount;
        int slotHeight = activity.dp(100);

        dockSlots.clear();
        for (int i = 0; i < slotCount; i++) {
            View slot = createDockSlot(i, slotWidth, slotHeight);
            if (i == 0) firstDockSlot = slot;
            dockSlots.add(slot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(slotWidth, slotHeight);
            if (i > 0) lp.leftMargin = slotMargin;
            dock.addView(slot, lp);
        }
        return dock;
    }

    public void refreshDock() {
        if (page1 == null || dockView == null) return;
        if (activity.prefs.getBoolean("hide_featured", false)) return;

        page1.removeView(dockView);
        dockView = buildDock();

        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(-1, activity.dp(110), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockLp.bottomMargin = activity.dp(16);
        dockLp.leftMargin = activity.dp(activity.horizontalPaddingDp());
        dockLp.rightMargin = activity.dp(activity.horizontalPaddingDp());
        page1.addView(dockView, dockLp);
    }

    private View createDockSlot(final int index, int width, int height) {
        final String packageName = activity.prefs.getString("dock_slot_" + index, "");
        final MainActivity.AppEntry app = findAppByPackage(packageName);

        FrameLayout slot = new FrameLayout(activity);
        slot.setFocusable(true);
        slot.setClickable(true);
        slot.setBackgroundResource(R.drawable.weather_card);
        slot.setClipToOutline(false);
        slot.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));

        if (app != null) {
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(activity.appManager.loadAppIcon(activity.getPackageManager(), app));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(activity.dp(64), activity.dp(64), Gravity.CENTER);
            slot.addView(icon, iconLp);

            TextView label = new TextView(activity);
            label.setText(app.label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(13);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            labelLp.bottomMargin = activity.dp(2);
            slot.addView(label, labelLp);
        } else {
            TextView empty = new TextView(activity);
            empty.setText("+");
            empty.setTextColor(Color.argb(180, 255, 255, 255));
            empty.setTextSize(28);
            empty.setGravity(Gravity.CENTER);
            slot.addView(empty, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        }

        slot.setOnClickListener(v -> {
            if (app != null) {
                try {
                    activity.startExternalActivity(app.launchIntent);
                } catch (Exception e) {
                    activity.toast("Unable to open " + app.label);
                }
            } else {
                showAppPickerForDock(index);
            }
        });

        slot.setOnLongClickListener(v -> {
            showDockSlotOptions(slot, index, packageName);
            return true;
        });

        slot.setOnFocusChangeListener((v, hasFocus) -> {
            animateFocus(v, hasFocus, 1.05f);
        });

        slot.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int currentIndex = dockSlots.indexOf(v);
                    if (currentIndex > 0) {
                        dockSlots.get(currentIndex - 1).requestFocus();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int currentIndex = dockSlots.indexOf(v);
                    if (currentIndex >= 0 && currentIndex < dockSlots.size() - 1) {
                        dockSlots.get(currentIndex + 1).requestFocus();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    // 有直播预览卡片时先聚焦卡片；关闭后可从 dock 任意位置上键到达顶部按钮
                    if (appContainerCardView != null && appContainerCardView.getParent() != null) {
                        appContainerCardView.requestFocus();
                        return true;
                    }
                    if (!topControls.isEmpty()) {
                        int dockIndex = dockSlots.indexOf(v);
                        int targetIndex = dockIndex >= 0
                                ? Math.min(dockIndex, topControls.size() - 1)
                                : 0;
                        if (targetIndex < 0) targetIndex = 0;
                        topControls.get(targetIndex).requestFocus();
                        return true;
                    }
                    if (activity.searchPill != null) {
                        activity.searchPill.requestFocus();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    switchToPage(false);
                    return true;
                }
            }
            return false;
        });

        return slot;
    }

    private MainActivity.AppEntry findAppByPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) return null;
        List<MainActivity.AppEntry> apps = activity.appManager.queryLaunchableApps(activity.getPackageManager());
        for (MainActivity.AppEntry app : apps) {
            if (TextUtils.equals(app.packageName, packageName)) return app;
        }
        return null;
    }

    private void showDockSlotOptions(View anchor, final int slotIndex, final String currentPackage) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        panel.setBackgroundResource(R.drawable.settings_option);

        TextView clearBtn = new TextView(activity);
        clearBtn.setText("清除");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setTextSize(16);
        clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        clearBtn.setClickable(true);
        clearBtn.setFocusable(true);

        TextView replaceBtn = new TextView(activity);
        replaceBtn.setText(TextUtils.isEmpty(currentPackage) ? "添加" : "替换");
        replaceBtn.setTextColor(Color.WHITE);
        replaceBtn.setTextSize(16);
        replaceBtn.setTypeface(Typeface.DEFAULT_BOLD);
        replaceBtn.setGravity(Gravity.CENTER);
        replaceBtn.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        replaceBtn.setClickable(true);
        replaceBtn.setFocusable(true);

        panel.addView(clearBtn, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(replaceBtn, new LinearLayout.LayoutParams(-1, -2));

        final PopupWindow popup = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);

        clearBtn.setOnClickListener(v -> {
            popup.dismiss();
            if (TextUtils.isEmpty(currentPackage)) {
                activity.toast("此槽位为空");
            } else {
                activity.prefs.edit().putString("dock_slot_" + slotIndex, "").apply();
                activity.toast("已清除");
                activity.showHome();
            }
        });

        replaceBtn.setOnClickListener(v -> {
            popup.dismiss();
            showAppPickerForDock(slotIndex);
        });

        popup.showAsDropDown(anchor, 0, -anchor.getHeight() - panel.getHeight());
    }

    private void showAppPickerForDock(final int slotIndex) {
        final List<MainActivity.AppEntry> apps = new ArrayList<>(activity.appManager.queryLaunchableApps(activity.getPackageManager()));
        if (apps.isEmpty()) {
            activity.toast("没有可用的应用");
            return;
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return apps.size();
            }

            @Override
            public Object getItem(int position) {
                return apps.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(activity).inflate(
                            android.R.layout.simple_list_item_1, parent, false);
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                    layout.setGravity(Gravity.CENTER_VERTICAL);
                    layout.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));

                    ImageView iconView = new ImageView(activity);
                    iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40));
                    iconLp.rightMargin = activity.dp(12);
                    layout.addView(iconView, iconLp);

                    TextView textView = new TextView(activity);
                    textView.setTextColor(Color.WHITE);
                    textView.setTextSize(18);
                    textView.setTypeface(Typeface.DEFAULT_BOLD);
                    textView.setSingleLine(true);
                    textView.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                    layout.addView(textView, textLp);

                    convertView = layout;
                }

                MainActivity.AppEntry app = apps.get(position);
                ImageView iconView = (ImageView) ((LinearLayout) convertView).getChildAt(0);
                iconView.setImageDrawable(activity.appManager.loadAppIcon(activity.getPackageManager(), app));
                TextView textView = (TextView) ((LinearLayout) convertView).getChildAt(1);
                textView.setText(app.label);

                return convertView;
            }
        };

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("选择应用")
                .setAdapter(adapter, (dialogInterface, which) -> {
                    MainActivity.AppEntry selected = apps.get(which);
                    activity.prefs.edit().putString("dock_slot_" + slotIndex, selected.packageName).apply();
                    activity.toast("已设置: " + selected.label);
                    activity.showHome();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }
}