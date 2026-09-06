package com.LemonLnch.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.LemonLnch.R;
import com.tv.player.IPlayerCallback;
import com.tv.player.IPlayerService;

public final class PreviewCardController implements SurfaceHolder.Callback {
    private static final String PLAYER_PACKAGE = "com.lemoniptv.lite";
    private static final String PLAYER_SERVICE = "top.yogiczy.lemonlnch.preview.PreviewService";
    private static final String PLAYER_MAIN_ACTIVITY = "top.yogiczy.lemonlnch.activities.LeanbackActivity";
    private static final int RETRY_LIMIT = 60;
    private static final long RETRY_BASE_DELAY_MS = 500L;
    private static final long RETRY_MAX_DELAY_MS = 3000L;
    private static final long CHECK_INTERVAL_MS = 5000L;
    private static final long REFRESH_COOLDOWN_MS = 8000L;
    private static final long LOADING_CHECK_INTERVAL_MS = 1000L;
    // 防抖：两次 forceRecover 之间至少间隔 1500ms，避免 onResume + onWindowFocusChanged 重复恢复
    private static final long RECOVERY_DEBOUNCE_MS = 1500L;

    private final MainActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout container;
    private SurfaceView surfaceView;
    private IPlayerService playerService;
    private IBinder playerBinder;
    private boolean serviceBound;
    private boolean surfaceReady;
    private int retryCount;
    private long lastRefreshAt;
    private View loadingView;
    private boolean isLoading = false;
    private boolean pendingResume = false;
    private boolean destroyed = false;
    private boolean binding = false;
    // 新增：防止并发恢复调用
    private boolean recoveryPending = false;
    // 新增：记录上次 forceRecover 调用时间，用于防抖
    private long lastRecoverAt = 0;

    private final Runnable checkRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (surfaceReady && serviceBound) {
                checkPlaybackHealth();
            }
            if (surfaceReady && !destroyed) {
                mainHandler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        }
    };

    private final Runnable loadingCheckRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || !isLoading || !surfaceReady || !serviceBound) {
                return;
            }
            if (playerService == null) {
                hideLoading();
                return;
            }
            try {
                if (playerService.isPlaying()) {
                    hideLoading();
                    retryCount = 0;
                } else {
                    mainHandler.postDelayed(this, LOADING_CHECK_INTERVAL_MS);
                }
            } catch (Exception e) {
                handleServiceLost();
            }
        }
    };

    // 修复：resumeRetryRunnable 现在会被正确调用（在 onServiceConnected 中 surface 未就绪时 post）
    private final Runnable resumeRetryRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || !pendingResume) return;
            if (surfaceReady && serviceBound && playerService != null) {
                refreshPreview();
                pendingResume = false;
            } else {
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    private final Runnable retryRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed || !surfaceReady) return;
            bindServiceAsync();
        }
    };

    public PreviewCardController(MainActivity activity) {
        this.activity = activity;
    }

    public void ensureServiceStarted() {
        if (destroyed) return;
        if (serviceBound) return;
        if (surfaceView == null) {
            bindServiceOnly();
        } else {
            bindAndResume();
        }
    }

    private void bindServiceOnly() {
        if (destroyed || serviceBound || binding) return;
        binding = true;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PLAYER_PACKAGE, PLAYER_SERVICE));
        try {
            serviceBound = activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            if (!serviceBound) {
                binding = false;
                scheduleRetry();
            }
        } catch (Exception e) {
            serviceBound = false;
            binding = false;
            scheduleRetry();
        }
    }

    public void attachToContainer(FrameLayout card) {
        detachView();
        this.container = card;
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        card.setBackgroundResource(R.drawable.app_container_card);
        card.setElevation(activity.dp(12));
        card.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        card.setContentDescription("直播预览");
        SurfaceView preview = new SurfaceView(activity);
        preview.setId(R.id.surface_preview);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            preview.setTransitionName("preview_surface");
        }
        preview.setFocusable(false);
        preview.setZOrderMediaOverlay(false);
        preview.getHolder().addCallback(this);
        card.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        TextView title = new TextView(activity);
        title.setText("直播预览");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.BOTTOM | Gravity.START);
        title.setPadding(activity.dp(8), 0, activity.dp(8), activity.dp(7));
        title.setBackgroundColor(Color.argb(105, 0, 0, 0));
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(-1, activity.dp(34), Gravity.BOTTOM);
        card.addView(title, titleLp);
        loadingView = createLoadingView();
        card.addView(loadingView, new FrameLayout.LayoutParams(-1, -1));
        TextView focusBorder = new TextView(activity);
        focusBorder.setBackgroundResource(R.drawable.preview_card_focus);
        focusBorder.setClickable(false);
        focusBorder.setFocusable(false);
        card.addView(focusBorder, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, hasFocus) -> v.animate()
                .scaleX(hasFocus ? 1.025f : 1f)
                .scaleY(hasFocus ? 1.025f : 1f)
                .translationZ(hasFocus ? activity.dp(16) : 0)
                .setDuration(130)
                .start());
        card.setOnClickListener(v -> openPlayerFullScreen());
        card.setOnLongClickListener(v -> {
            triggerAutoPlay();
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                openPlayerFullScreen();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                activity.focusDockFirst();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && activity.searchPill != null) {
                activity.searchPill.requestFocus();
                return true;
            }
            return false;
        });
        this.surfaceView = preview;
        startChecking();
    }

    private View createLoadingView() {
        FrameLayout loading = new FrameLayout(activity);
        loading.setBackgroundColor(Color.argb(140, 255, 255, 255));
        loading.setClickable(false);
        loading.setFocusable(false);
        ProgressBar progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(
                activity.dp(48), activity.dp(48), Gravity.CENTER);
        loading.addView(progressBar, progressLp);
        TextView text = new TextView(activity);
        text.setText("正在加载中...");
        text.setTextColor(Color.WHITE);
        text.setTextSize(14);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        textLp.topMargin = activity.dp(64);
        loading.addView(text, textLp);
        loading.setVisibility(View.GONE);
        return loading;
    }

    private void showLoading() {
        if (loadingView != null && !isLoading) {
            isLoading = true;
            loadingView.setVisibility(View.VISIBLE);
            if (loadingView.getParent() != null) {
                loadingView.bringToFront();
            }
            mainHandler.removeCallbacks(loadingCheckRunnable);
            mainHandler.postDelayed(loadingCheckRunnable, LOADING_CHECK_INTERVAL_MS);
        }
    }

    private void hideLoading() {
        if (loadingView != null && isLoading) {
            isLoading = false;
            loadingView.setVisibility(View.GONE);
            mainHandler.removeCallbacks(loadingCheckRunnable);
        }
    }

    private void startChecking() {
        stopChecking();
        mainHandler.postDelayed(checkRunnable, CHECK_INTERVAL_MS);
    }

    private void stopChecking() {
        mainHandler.removeCallbacks(checkRunnable);
    }

    private void checkPlaybackHealth() {
        if (playerService == null || !serviceBound || !surfaceReady) return;
        try {
            boolean playing = playerService.isPlaying();
            if (playing) {
                hideLoading();
                retryCount = 0;
            } else if (System.currentTimeMillis() - lastRefreshAt >= REFRESH_COOLDOWN_MS) {
                refreshPreview();
            }
        } catch (Exception e) {
            handleServiceLost();
        }
    }

    private void refreshPreview() {
        if (playerService == null || !serviceBound || !surfaceReady) return;
        Surface surface = surfaceView.getHolder().getSurface();
        if (surface == null) return;
        showLoading();
        lastRefreshAt = System.currentTimeMillis();
        try {
            playerService.setSurface(surface);
            playerService.startPreview(null);
            retryCount = 0;
        } catch (Exception e) {
            handleServiceLost();
        }
    }

    public void detachView() {
        stopChecking();
        hideLoading();
        mainHandler.removeCallbacks(retryRunnable);
        mainHandler.removeCallbacks(loadingCheckRunnable);
        mainHandler.removeCallbacks(resumeRetryRunnable);
        pendingResume = false;
        if (playerService != null) {
            try {
                playerService.setSurface(null);
                playerService.pausePreview();
                playerService.unregisterCallback(playerCallback);
            } catch (Exception ignored) {
            }
        }
        unlinkDeath();
        if (serviceBound) {
            try { activity.unbindService(serviceConnection); } catch (Exception ignored) {}
        }
        serviceBound = false;
        playerService = null;
        playerBinder = null;
        surfaceReady = false;
        container = null;
        surfaceView = null;
        loadingView = null;
        isLoading = false;
        retryCount = 0;
        binding = false;
        recoveryPending = false;
    }

    /**
     * 修复：onStart 总是调用 forceRecover，不再有条件判断。
     * 原因：即使 serviceBound 为 true，服务也可能已在后台被系统杀死。
     */
    public void onStart() {
        if (destroyed) return;
        forceRecover();
    }

    /**
     * 修复：onResume 只负责恢复检查循环，不再调用 forceRecover。
     * 原因：onStart 已经完成了恢复，onResume 只需确保检查循环在运行。
     */
    public void onResume() {
        if (destroyed) return;
        startChecking();
    }

    /**
     * 修复：onPause 只暂停播放器，不解绑服务（保持绑定以支持快速恢复）。
     */
    public void onPause() {
        stopChecking();
        hideLoading();
        pendingResume = false;
        mainHandler.removeCallbacks(resumeRetryRunnable);
        if (playerService != null) {
            try { playerService.pausePreview(); } catch (Exception e) { handleServiceLost(); }
        }
    }

    /**
     * 新增：onStop 调用 forceRecover 来解绑服务。
     * 原因：当应用完全进入后台时，应释放服务绑定，确保回来时能干净地重新绑定。
     */
    public void onStop() {
        if (destroyed) return;
        forceRecover();
    }

    public void onDestroy() {
        destroyed = true;
        detachView();
    }

    public void openPreviewSettings() {
        triggerAutoPlay();
    }

    public void openPreview() {
        openPlayerFullScreen();
    }

    /**
     * 修复：onWindowFocusChanged 使用防抖，避免与 onResume 的 forceRecover 重复。
     * 只在服务确实未绑定时才恢复。
     */
    public void onWindowFocusChanged() {
        if (destroyed) return;
        if (!serviceBound || playerService == null) {
            forceRecover();
        }
    }

    private void triggerAutoPlay() {
        if (destroyed) return;
        if (playerService != null) {
            try {
                showLoading();
                playerService.startPreview(null);
                activity.toast("正在自动加载直播频道");
            } catch (Exception e) {
                handleServiceLost();
            }
        } else {
            forceRecover();
        }
    }

    private void openPlayerFullScreen() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PLAYER_PACKAGE, PLAYER_MAIN_ACTIVITY));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            activity.toast("无法启动 lemon-TV，请确认已安装");
        }
    }

    /**
     * 修复：强制恢复 - 添加防抖和并发控制。
     * 1. 使用 recoveryPending 标志防止并发调用
     * 2. 使用 lastRecoverAt 时间戳防止短时间内重复调用
     */
    private void forceRecover() {
        if (destroyed || surfaceView == null) return;
        // 防抖：两次恢复之间至少间隔 RECOVERY_DEBOUNCE_MS
        long now = System.currentTimeMillis();
        if (now - lastRecoverAt < RECOVERY_DEBOUNCE_MS) return;
        // 防止并发
        if (recoveryPending) return;

        recoveryPending = true;
        lastRecoverAt = now;

        stopChecking();
        mainHandler.removeCallbacks(retryRunnable);
        mainHandler.removeCallbacks(resumeRetryRunnable);
        mainHandler.removeCallbacks(loadingCheckRunnable);

        // 解绑现有服务
        if (serviceBound) {
            try { activity.unbindService(serviceConnection); } catch (Exception ignored) {}
        }
        serviceBound = false;
        playerService = null;
        playerBinder = null;
        retryCount = 0;
        binding = false;
        pendingResume = true;

        // 重新绑定服务
        bindServiceAsync();
    }

    private void bindServiceAsync() {
        if (destroyed || surfaceView == null || binding) return;
        binding = true;
        showLoading();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(PLAYER_PACKAGE, PLAYER_SERVICE));
        try {
            serviceBound = activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            if (!serviceBound) {
                binding = false;
                scheduleRetry();
            }
        } catch (Exception e) {
            serviceBound = false;
            binding = false;
            scheduleRetry();
        }
    }

    private void bindAndResume() {
        if (destroyed || surfaceView == null) return;
        if (!serviceBound || playerService == null) {
            serviceBound = false;
            playerService = null;
            playerBinder = null;
            binding = false;
            bindServiceAsync();
            return;
        }
        attachAndPlay();
    }

    private void attachAndPlay() {
        if (destroyed || playerService == null || surfaceView == null || !surfaceReady) return;
        Surface surface = surfaceView.getHolder().getSurface();
        if (surface == null) return;
        showLoading();
        try {
            playerService.setSurface(surface);
            playerService.startPreview(null);
            retryCount = 0;
            pendingResume = false;
        } catch (Exception e) {
            handleServiceLost();
        }
    }

    private final android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (destroyed) return;
            binding = false;
            recoveryPending = false; // 恢复完成，清除标志
            playerBinder = service;
            playerService = IPlayerService.Stub.asInterface(service);
            serviceBound = true;
            retryCount = 0;
            try {
                service.linkToDeath(deathRecipient, 0);
                playerService.registerCallback(playerCallback);
            } catch (Exception ignored) {}
            // 只要surface准备好了就立即尝试播放
            if (surfaceReady) {
                attachAndPlay();
            } else {
                // 修复：surface 尚未准备好时，设置 pendingResume 并启动重试
                pendingResume = true;
                mainHandler.postDelayed(resumeRetryRunnable, 300);
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) { handleServiceLost(); }
        @Override public void onNullBinding(ComponentName name) { handleServiceLost(); }
    };

    private final IBinder.DeathRecipient deathRecipient = this::handleServiceLost;

    private void unlinkDeath() {
        if (playerBinder != null) {
            try { playerBinder.unlinkToDeath(deathRecipient, 0); } catch (Exception ignored) {}
        }
        playerBinder = null;
    }

    private void handleServiceLost() {
        activity.runOnUiThread(() -> {
            if (destroyed) return;
            recoveryPending = false; // 清除恢复标志
            unlinkDeath();
            playerService = null;
            serviceBound = false;
            binding = false;
            hideLoading();
            scheduleRetry();
        });
    }

    private void scheduleRetry() {
        if (destroyed || !surfaceReady || retryCount >= RETRY_LIMIT) return;
        retryCount++;
        long delay = Math.min(RETRY_MAX_DELAY_MS, RETRY_BASE_DELAY_MS * retryCount);
        mainHandler.removeCallbacks(retryRunnable);
        mainHandler.postDelayed(retryRunnable, delay);
        showLoading();
    }

    private final IPlayerCallback.Stub playerCallback = new IPlayerCallback.Stub() {
        @Override public void onPlayError(int errorCode, String errorMsg) {
            activity.runOnUiThread(() -> {
                if (destroyed || !surfaceReady) return;
                hideLoading();
                refreshPreview();
            });
        }
        @Override public void onPlayStateChanged(int state) {
            activity.runOnUiThread(() -> {
                if (destroyed || !surfaceReady) return;
                if (state == 1) { // STATE_PLAYING
                    hideLoading();
                    retryCount = 0;
                } else if (state == 3) { // STATE_ERROR
                    hideLoading();
                }
            });
        }
    };

    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (destroyed) return;
        surfaceReady = true;
        if (serviceBound && playerService != null) {
            attachAndPlay();
        } else if (!serviceBound) {
            // 修复：surface 已准备好但服务未绑定，主动触发恢复
            forceRecover();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (destroyed) return;
        surfaceReady = false;
        hideLoading();
        if (playerService != null) {
            try {
                playerService.setSurface(null);
                playerService.pausePreview();
            } catch (Exception ignored) {
            }
        }
    }
}
