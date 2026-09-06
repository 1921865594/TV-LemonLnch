package com.LemonLnch.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.LemonLnch.R;
import com.LemonLnch.system.RootShell;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DialogUtils {
    private final MainActivity activity;

    DialogUtils(MainActivity activity) {
        this.activity = activity;
    }

    void showAppMenu(PackageManager pm, MainActivity.AppEntry entry) {
        if (entry.isFolder) {
            showFolderMenu(entry);
            return;
        }
        View restoreFocus = activity.getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction(activity.getString(R.string.action_open),
                activity.getString(R.string.action_open_desc), () -> activity.launchEntry(entry, true), false));
        actions.add(new AppAction(activity.getString(R.string.action_add_to_dock),
                activity.getString(R.string.action_add_to_dock_desc),
                () -> activity.addAppToDock(entry.packageName), false));
        actions.add(new AppAction(activity.getString(R.string.action_move),
                activity.getString(R.string.action_move_desc), () -> {
            activity.movingPackage = entry.packageName;
            activity.focusAfterLoadPackage = entry.packageName;
            activity.loadApps();
        }, false));
        actions.add(new AppAction(activity.getString(R.string.action_folder),
                activity.getString(R.string.action_folder_desc), () -> promptAddToFolder(entry), false));
        actions.add(new AppAction(activity.getString(R.string.action_hide),
                activity.getString(R.string.action_hide_desc), () -> activity.hideApp(entry.packageName), true));

        // 重命名软件名称按钮
        actions.add(new AppAction("重命名软件名称",
                "自定义显示的名称", () -> promptRenameApp(entry), false));

        // 卸载按钮
        actions.add(new AppAction("卸载",
                "卸载该应用", () -> uninstallApp(entry.packageName), true));

        actions.add(new AppAction(activity.getString(R.string.action_info),
                activity.getString(R.string.action_info_desc), () -> activity.openAppInfo(entry.packageName), false));
        showAppActionDialog(entry, actions, restoreFocus);
    }

    private void promptRenameApp(MainActivity.AppEntry entry) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("输入新的显示名称");
        input.setText(entry.label);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(activity)
                .setTitle("重命名软件名称")
                .setView(input)
                .setNegativeButton(activity.getString(R.string.action_cancel), null)
                .setPositiveButton(activity.getString(R.string.common_save), (d, which) -> {
                    String newName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) {
                        activity.toast("名称不能为空");
                        return;
                    }
                    activity.appManager.renameApp(entry.packageName, newName);
                    activity.toast("名称已更新");
                })
                .show();
    }

    private final ExecutorService uninstallExecutor = Executors.newSingleThreadExecutor();
    private static final String ACTION_UNINSTALL_RESULT = "com.LemonLnch.ACTION_UNINSTALL_RESULT";
    private BroadcastReceiver uninstallResultReceiver;

    /**
     * 卸载入口：三套卸载逻辑自动按序尝试
     * 1. Root 权限静默卸载（优先）
     * 2. 系统原生卸载程序（无 Root 时首选）
     * 3. 应用内置卸载程序（系统卸载失败后自动回退）
     */
    private void uninstallApp(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            activity.toast("无效的包名");
            return;
        }
        if (packageName.equals(activity.getPackageName())) {
            activity.toast("不能卸载自身");
            return;
        }
        if (!isPackageInstalled(packageName)) {
            activity.toast("应用未安装");
            activity.loadApps();
            return;
        }

        // 第一优先：Root 静默卸载
        if (RootShell.isRootAvailable()) {
            activity.toast("Root 卸载中…");
            uninstallExecutor.execute(() -> {
                RootShell.Result result = RootShell.exec("pm uninstall --user 0 " + packageName);
                if (!result.ok() || isPackageInstalled(packageName)) {
                    result = RootShell.exec("pm uninstall " + packageName);
                }
                if ((!result.ok() || isPackageInstalled(packageName))) {
                    RootShell.Result force = RootShell.exec(
                            "pm uninstall --user 0 --force " + packageName);
                    if (force.ok()) result = force;
                }

                final boolean stillInstalled = isPackageInstalled(packageName);
                final boolean success = !stillInstalled;
                final String err = result == null ? "" : (
                        TextUtils.isEmpty(result.error) ? result.output : result.error);
                activity.runOnUiThread(() -> {
                    if (success) {
                        activity.toast("Root 卸载成功");
                        activity.loadApps();
                    } else {
                        activity.toast("Root 卸载失败，回退系统卸载");
                        uninstallViaSystemNative(packageName);
                    }
                });
            });
            return;
        }

        // 无 Root：走系统原生卸载
        uninstallViaSystemNative(packageName);
    }

    // ==================== 卸载逻辑 1：系统原生卸载程序 ====================
    private void uninstallViaSystemNative(String packageName) {
        boolean launched = false;

        Intent deleteIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
        deleteIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(deleteIntent);
            launched = true;
        } catch (Exception ignored) {
        }

        if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.parse("package:" + packageName));
            uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(uninstallIntent);
                launched = true;
            } catch (Exception ignored) {
            }
        }

        if (launched) {
            activity.toast("已打开系统卸载程序，等待操作");
            // 轮询检测，超时未卸载则自动进入内置卸载
            scheduleUninstallFallbackCheck(packageName);
            return;
        }

        // 系统卸载无法启动，直接进入内置卸载
        activity.toast("系统卸载不可用，尝试内置卸载");
        uninstallViaBuiltIn(packageName);
    }

    /**
     * 延迟检查系统卸载是否完成；若超时仍未完成，自动回退到内置卸载
     */
    private void scheduleUninstallFallbackCheck(String packageName) {
        final long[] delays = new long[]{3000L, 6000L, 12000L};
        final boolean[] fallbackTriggered = new boolean[]{false};
        for (int i = 0; i < delays.length; i++) {
            final long delay = delays[i];
            final int index = i;
            activity.getWindow().getDecorView().postDelayed(() -> {
                if (fallbackTriggered[0]) return;
                if (!isPackageInstalled(packageName)) {
                    activity.toast("系统卸载成功");
                    activity.loadApps();
                    unregisterUninstallResultReceiver();
                    fallbackTriggered[0] = true;
                } else if (index == delays.length - 1) {
                    activity.toast("系统卸载未完成，自动切换内置卸载");
                    uninstallViaBuiltIn(packageName);
                    fallbackTriggered[0] = true;
                }
            }, delay);
        }
    }

    // ==================== 卸载逻辑 2：应用内置卸载程序（PackageInstaller） ====================
    private void uninstallViaBuiltIn(String packageName) {
        new AlertDialog.Builder(activity)
                .setTitle("内置卸载程序")
                .setMessage("系统卸载未完成，是否通过应用内置卸载程序继续卸载？\n\n" + packageName
                        + "\n\n将使用 PackageInstaller 提交卸载请求。")
                .setNegativeButton(activity.getString(R.string.action_cancel), (d, which) -> {
                    if (RootShell.isRootAvailable()) {
                        uninstallViaRoot(packageName);
                    } else {
                        activity.toast("已取消卸载");
                    }
                })
                .setPositiveButton("确认卸载", (d, which) -> startBuiltInUninstall(packageName))
                .show();
    }

    private void startBuiltInUninstall(String packageName) {
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInstaller installer = pm.getPackageInstaller();

            registerUninstallResultReceiver(packageName);

            Intent callback = new Intent(ACTION_UNINSTALL_RESULT);
            callback.setPackage(activity.getPackageName());
            callback.putExtra("packageName", packageName);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(activity, packageName.hashCode() & 0xFFFF,
                    callback, flags);

            activity.toast("内置卸载程序：正在提交卸载请求…");
            installer.uninstall(packageName, pending.getIntentSender());
            scheduleUninstallResultCheck(packageName, "内置卸载");
        } catch (SecurityException se) {
            activity.toast("内置卸载失败：权限不足");
            if (RootShell.isRootAvailable()) {
                uninstallViaRoot(packageName);
            }
        } catch (Exception e) {
            activity.toast("内置卸载失败：" + safeMsg(e));
            if (RootShell.isRootAvailable()) {
                uninstallViaRoot(packageName);
            }
        }
    }

    private void registerUninstallResultReceiver(final String packageName) {
        unregisterUninstallResultReceiver();
        uninstallResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                activity.runOnUiThread(() -> {
                    if (status == PackageInstaller.STATUS_SUCCESS || !isPackageInstalled(packageName)) {
                        activity.toast("内置卸载成功");
                        activity.loadApps();
                    } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                        if (confirm != null) {
                            try {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(confirm);
                                activity.toast("请在系统界面确认卸载");
                            } catch (Exception e) {
                                activity.toast("内置卸载需要确认，但无法打开确认页");
                            }
                        } else {
                            activity.toast("内置卸载等待用户确认");
                        }
                    } else {
                        activity.toast("内置卸载失败"
                                + (TextUtils.isEmpty(msg) ? ("(status=" + status + ")") : (": " + msg)));
                        if (RootShell.isRootAvailable()) {
                            uninstallViaRoot(packageName);
                        }
                    }
                    unregisterUninstallResultReceiver();
                });
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_UNINSTALL_RESULT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(uninstallResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(uninstallResultReceiver, filter);
            }
        } catch (Exception e) {
        }
    }

    private void unregisterUninstallResultReceiver() {
        if (uninstallResultReceiver != null) {
            try {
                activity.unregisterReceiver(uninstallResultReceiver);
            } catch (Exception ignored) {
            }
            uninstallResultReceiver = null;
        }
    }

    // ==================== 卸载逻辑 3：Root 权限卸载 ====================
    private void uninstallViaRoot(String packageName) {
        if (!RootShell.isRootAvailable()) {
            activity.toast("未检测到 Root 权限，无法使用 Root 卸载");
            return;
        }
        activity.toast("Root 卸载中…");
        uninstallExecutor.execute(() -> {
            RootShell.Result result = RootShell.exec("pm uninstall --user 0 " + packageName);
            if (!result.ok() || isPackageInstalled(packageName)) {
                result = RootShell.exec("pm uninstall " + packageName);
            }
            if ((!result.ok() || isPackageInstalled(packageName))) {
                RootShell.Result force = RootShell.exec(
                        "pm uninstall --user 0 --force " + packageName);
                if (force.ok()) result = force;
            }

            final boolean stillInstalled = isPackageInstalled(packageName);
            final boolean success = !stillInstalled;
            final String err = result == null ? "" : (
                    TextUtils.isEmpty(result.error) ? result.output : result.error);
            activity.runOnUiThread(() -> {
                if (success) {
                    activity.toast("Root 卸载成功");
                    activity.loadApps();
                } else {
                    activity.toast("Root 卸载失败"
                            + (TextUtils.isEmpty(err) ? "" : (": " + err.trim())));
                }
            });
        });
    }

    private void scheduleUninstallResultCheck(String packageName, String methodLabel) {
        final long[] delays = new long[]{1500L, 3000L, 6000L};
        for (long delay : delays) {
            activity.getWindow().getDecorView().postDelayed(() -> {
                if (!isPackageInstalled(packageName)) {
                    activity.toast(methodLabel + "成功");
                    activity.loadApps();
                    unregisterUninstallResultReceiver();
                }
            }, delay);
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            activity.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static String safeMsg(Throwable e) {
        if (e == null) return "未知错误";
        String m = e.getMessage();
        return TextUtils.isEmpty(m) ? e.getClass().getSimpleName() : m;
    }

    void showFolderMenu(MainActivity.AppEntry folder) {
        View restoreFocus = activity.getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction(activity.getString(R.string.action_open),
                activity.getString(R.string.action_open_folder_desc), () -> showFolder(folder), false));
        actions.add(new AppAction(activity.getString(R.string.action_move),
                activity.getString(R.string.action_move_desc), () -> {
            activity.movingPackage = folder.packageName;
            activity.focusAfterLoadPackage = folder.packageName;
            activity.loadApps();
        }, false));

        actions.add(new AppAction(activity.getString(R.string.action_rename),
                activity.getString(R.string.action_rename_desc), () -> promptRenameFolder(folder.label), false));

        actions.add(new AppAction(activity.getString(R.string.action_delete),
                activity.getString(R.string.action_delete_desc), () -> activity.appManager.removeFolder(folder.label), true));
        showAppActionDialog(folder, actions, restoreFocus);
    }

    private void promptRenameFolder(String oldName) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("输入新的文件夹名称");
        input.setText(oldName);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(activity)
                .setTitle("重命名文件夹")
                .setView(input)
                .setNegativeButton(activity.getString(R.string.action_cancel), null)
                .setPositiveButton(activity.getString(R.string.common_save), (d, which) -> {
                    String newName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) {
                        activity.toast("名称不能为空");
                        return;
                    }
                    if (newName.equals(oldName)) return;
                    activity.appManager.renameFolder(oldName, newName);
                    activity.toast("文件夹已重命名");
                })
                .show();
    }

    private void showAppActionDialog(MainActivity.AppEntry entry, List<AppAction> actions, View restoreFocus) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int panelMaxWidth = screenW - activity.dp(48);
        int panelWidth = Math.min(activity.dp(560), panelMaxWidth);

        FrameLayout dim = new FrameLayout(activity);
        dim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        dim.setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(28), activity.dp(24), activity.dp(28), activity.dp(24));
        panel.setBackground(actionPanelBackground());
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        dim.addView(panel, panelLp);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, activity.dp(12));
        ImageView icon = new ImageView(activity);
        if (entry.isFolder) {
            icon.setImageResource(R.mipmap.ic_launcher);
        } else {
            icon.setImageDrawable(activity.appManager.loadAppIcon(activity.getPackageManager(), entry));
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(54), activity.dp(54));
        iconLp.rightMargin = activity.dp(16);
        header.addView(icon, iconLp);

        LinearLayout titleBox = new LinearLayout(activity);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(activity);
        title.setText(entry.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(title);
        TextView subtitle = new TextView(activity);
        subtitle.setText(entry.packageName);
        subtitle.setTextColor(Color.argb(150, 255, 255, 255));
        subtitle.setTextSize(12);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(activity);
        divider.setBackgroundColor(Color.argb(55, 255, 255, 255));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, activity.dp(1));
        dividerLp.bottomMargin = activity.dp(18);
        panel.addView(divider, dividerLp);

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        panel.addView(scroll, scrollLp);

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(2);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));

        List<View> buttons = new ArrayList<>();
        int buttonWidth = (panelWidth - activity.dp(56) - activity.dp(24)) / 2;
        for (AppAction action : actions) {
            TextView button = createActionButton(action, dialog);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = buttonWidth;
            lp.height = activity.dp(72);
            lp.setMargins(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6));
            grid.addView(button, lp);
            buttons.add(button);
        }

        TextView cancel = createActionButton(
                new AppAction(activity.getString(R.string.action_cancel),
                        activity.getString(R.string.action_cancel_desc), dialog::dismiss, false), dialog);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, activity.dp(58));
        cancelLp.topMargin = activity.dp(12);
        panel.addView(cancel, cancelLp);

        dialog.setContentView(dim);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnDismissListener(d -> {
            if (restoreFocus != null) restoreFocus.postDelayed(restoreFocus::requestFocus, 40);
        });
        dialog.setOnShowListener(d -> {
            panel.setAlpha(0f);
            panel.setScaleX(0.96f);
            panel.setScaleY(0.96f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
            if (!buttons.isEmpty()) buttons.get(0).requestFocus();
        });
        dialog.show();
    }

    private TextView createActionButton(AppAction action, Dialog dialog) {
        TextView button = new TextView(activity);
        button.setText(action.title + "\n" + action.subtitle);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(activity.dp(18), 0, activity.dp(14), 0);
        button.setTextColor(action.danger ? Color.rgb(255, 176, 176) : Color.WHITE);
        button.setBackground(actionButtonBackground(false, action.danger));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(actionButtonBackground(hasFocus, action.danger));
            button.setTextColor(hasFocus ? Color.BLACK : (action.danger ? Color.rgb(255, 176, 176) : Color.WHITE));
            v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f)
                    .translationZ(hasFocus ? activity.dp(10) : 0).setDuration(130).start();
        });
        button.setOnClickListener(v -> {
            dialog.dismiss();
            action.action.run();
        });
        return button;
    }

    GradientDrawable actionPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 28, 28, 28));
        bg.setCornerRadius(activity.dp(28));
        bg.setStroke(activity.dp(1), Color.argb(90, 255, 255, 255));
        return bg;
    }

    private GradientDrawable actionButtonBackground(boolean focused, boolean danger) {
        GradientDrawable bg = new GradientDrawable();
        int color = focused ? Color.argb(245, 255, 255, 255)
                : (danger ? Color.argb(155, 86, 38, 38) : Color.argb(175, 68, 68, 68));
        bg.setColor(color);
        bg.setCornerRadius(activity.dp(16));
        bg.setStroke(focused ? activity.dp(3) : activity.dp(1),
                focused ? Color.WHITE : Color.argb(42, 255, 255, 255));
        return bg;
    }

    void showAppSearch() {
        PackageManager pm = activity.getPackageManager();
        List<MainActivity.AppEntry> allApps = new ArrayList<>(activity.appManager.queryLaunchableApps(pm));
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.argb(215, 8, 10, 14));
        int padH = activity.horizontalPaddingDp();
        root.setPadding(activity.dp(padH), activity.dp(48), activity.dp(padH), activity.dp(42));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(28), activity.dp(24), activity.dp(28), activity.dp(24));
        panel.setBackground(actionPanelBackground());
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.search_title));
        title.setTextColor(Color.WHITE);
        title.setTextSize(activity.screenWidthDp < 600 ? 24 : 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(46)));

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setHint(activity.getString(R.string.search_hint));
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.argb(145, 255, 255, 255));
        input.setTextSize(20);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(actionButtonBackground(false, false));
        input.setPadding(activity.dp(18), 0, activity.dp(18), 0);
        input.setOnClickListener(v -> showKeyboard(input));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) input.postDelayed(() -> showKeyboard(input), 80);
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, activity.dp(62));
        inputLp.bottomMargin = activity.dp(22);
        panel.addView(input, inputLp);

        TextView playSearch = createActionButton(
                new AppAction(activity.getString(R.string.search_play),
                        activity.getString(R.string.search_play_desc), () ->
                        openPlayStoreSearch(input.getText().toString().trim()), false), dialog);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, activity.dp(64));
        playLp.bottomMargin = activity.dp(18);
        panel.addView(playSearch, playLp);

        TextView installedHeading = new TextView(activity);
        installedHeading.setText(activity.getString(R.string.search_installed));
        installedHeading.setTextColor(Color.argb(190, 255, 255, 255));
        installedHeading.setTextSize(18);
        installedHeading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(installedHeading, new LinearLayout.LayoutParams(-1, activity.dp(34)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        GridLayout results = new GridLayout(activity);
        results.setColumnCount(activity.screenWidthDp < 600 ? 3 : 4);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));

        Runnable[] populate = new Runnable[1];
        populate[0] = () -> {
            String q = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            results.removeAllViews();
            int shown = 0;
            for (MainActivity.AppEntry app : allApps) {
                String haystack = (app.label + " " + app.packageName).toLowerCase(Locale.ROOT);
                if (TextUtils.isEmpty(q) || !haystack.contains(q)) continue;
                View tile = createSearchResultTile(pm, app, dialog);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = activity.dp(activity.screenWidthDp < 600 ? 200 : 255);
                lp.height = activity.dp(92);
                lp.setMargins(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
                results.addView(tile, lp);
                shown++;
                if (shown >= 48) break;
            }
            if (shown == 0) {
                TextView empty = new TextView(activity);
                empty.setText(TextUtils.isEmpty(q)
                        ? activity.getString(R.string.search_empty_hint)
                        : activity.getString(R.string.search_no_results));
                empty.setTextColor(Color.argb(180, 255, 255, 255));
                empty.setTextSize(22);
                results.addView(empty, new ViewGroup.LayoutParams(-1, activity.dp(80)));
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                openPlayStoreSearch(input.getText().toString().trim());
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate[0].run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        populate[0].run();

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> input.postDelayed(() -> {
            input.requestFocus();
            input.performClick();
            showKeyboard(input);
        }, 120));
        dialog.setOnDismissListener(d -> {
            if (activity.searchPill != null) activity.searchPill.postDelayed(activity.searchPill::requestFocus, 50);
        });
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private View createSearchResultTile(PackageManager pm, MainActivity.AppEntry entry, Dialog searchDialog) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(14), 0, activity.dp(14), 0);
        row.setBackground(actionButtonBackground(false, false));
        row.setFocusable(true);
        row.setClickable(true);

        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(activity.appManager.loadAppIcon(pm, entry));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52));
        iconLp.rightMargin = activity.dp(14);
        row.addView(icon, iconLp);

        TextView label = new TextView(activity);
        label.setText(entry.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(17);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnFocusChangeListener((v, hasFocus) -> {
            row.setBackground(actionButtonBackground(hasFocus, false));
            label.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
            row.animate().scaleX(hasFocus ? 1.035f : 1f).scaleY(hasFocus ? 1.035f : 1f).setDuration(130).start();
        });
        row.setOnClickListener(v -> {
            searchDialog.dismiss();
            activity.launchEntry(entry, true);
        });
        row.setOnLongClickListener(v -> {
            showAppActionDialog(entry, buildSearchActions(entry, searchDialog), row);
            return true;
        });
        return row;
    }

    private List<AppAction> buildSearchActions(MainActivity.AppEntry entry, Dialog searchDialog) {
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction(activity.getString(R.string.action_open),
                activity.getString(R.string.action_open_desc), () -> {
            searchDialog.dismiss();
            activity.launchEntry(entry, true);
        }, false));
        actions.add(new AppAction(activity.getString(R.string.action_add_to_dock),
                activity.getString(R.string.action_add_to_dock_desc),
                () -> activity.addAppToDock(entry.packageName), false));
        actions.add(new AppAction(activity.getString(R.string.action_folder),
                activity.getString(R.string.action_folder_desc), () -> promptAddToFolder(entry), false));
        actions.add(new AppAction("重命名软件名称",
                "自定义显示的名称", () -> promptRenameApp(entry), false));
        actions.add(new AppAction(activity.getString(R.string.action_hide),
                activity.getString(R.string.action_hide_desc), () -> {
            searchDialog.dismiss();
            activity.hideApp(entry.packageName);
        }, true));
        actions.add(new AppAction("卸载",
                "卸载该应用", () -> uninstallApp(entry.packageName), true));
        actions.add(new AppAction(activity.getString(R.string.action_info),
                activity.getString(R.string.action_info_desc), () -> activity.openAppInfo(entry.packageName), false));
        return actions;
    }

    private void showKeyboard(EditText input) {
        if (input == null) return;
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void openPlayStoreSearch(String query) {
        if (TextUtils.isEmpty(query)) {
            activity.toast(activity.getString(R.string.search_play_empty));
            return;
        }
        PackageManager pm = activity.getPackageManager();
        Intent market = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=" + Uri.encode(query) + "&c=apps"));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (market.resolveActivity(pm) != null) {
            try {
                activity.startActivity(market);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Intent web = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query) + "&c=apps"));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (web.resolveActivity(pm) != null) {
            try {
                activity.startActivity(web);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        activity.toast(activity.getString(R.string.search_play_unavailable));
    }

    void showFolder(MainActivity.AppEntry folder) {
        if (folder.children == null || folder.children.isEmpty()) {
            activity.toast(activity.getString(R.string.folder_empty));
            return;
        }
        dismissFolderOverlay();
        activity.folderLastFocus = activity.getCurrentFocus();
        setFolderBackgroundBlur(true);
        PackageManager pm = activity.getPackageManager();

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            activity.folderOverlay = null;
            activity.folderDialog = null;
            setFolderBackgroundBlur(false);
            if (activity.folderLastFocus != null) {
                activity.folderLastFocus.requestFocus();
                activity.folderLastFocus = null;
            }
        });
        activity.folderDialog = dialog;

        FrameLayout overlay = new FrameLayout(activity);
        activity.folderOverlay = overlay;
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setClickable(true);
        overlay.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        overlay.setBackgroundColor(Color.argb(170, 226, 238, 236));
        overlay.setOnClickListener(v -> dialog.dismiss());
        overlay.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(48), activity.dp(32), activity.dp(48), activity.dp(40));
        panel.setClickable(true);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.argb(184, 246, 248, 247));
        panelBg.setCornerRadius(activity.dp(22));
        panelBg.setStroke(activity.dp(1), Color.argb(170, 255, 255, 255));
        panel.setBackground(panelBg);
        panel.setElevation(activity.dp(10));

        TextView title = new TextView(activity);
        title.setText(folder.label);
        title.setTextColor(Color.rgb(76, 72, 84));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, activity.dp(46));
        titleLp.bottomMargin = activity.dp(18);
        panel.addView(title, titleLp);

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(activity.dp(760), screenW - activity.dp(activity.screenWidthDp < 600 ? 60 : 140));
        int panelHeight = Math.min(activity.dp(540), screenH - activity.dp(activity.screenWidthDp < 600 ? 80 : 120));
        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);

        FrameLayout grid = new FrameLayout(activity);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, activity.dp(8));
        int panelHorizontalPadding = activity.dp(48) * 2;
        int tileWidth = Math.min(activity.dp(172), Math.max(activity.dp(146), (panelWidth - panelHorizontalPadding) / 3));
        int tileHeight = activity.dp(130);
        int columns = 3;
        int rows = Math.max(1, (int) Math.ceil(folder.children.size() / 3f));
        int gridWidth = tileWidth * columns;
        int gridHeight = rows * tileHeight;
        List<View> folderCards = new ArrayList<>();
        for (int i = 0; i < folder.children.size(); i++) {
            MainActivity.AppEntry child = folder.children.get(i);
            View cell = createFolderAppTile(pm, child, tileWidth, folder.label);
            FrameLayout.LayoutParams cellLp = new FrameLayout.LayoutParams(tileWidth, tileHeight);
            cellLp.leftMargin = (i % columns) * tileWidth;
            cellLp.topMargin = (i / columns) * tileHeight;
            grid.addView(cell, cellLp);
            if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
                folderCards.add(((LinearLayout) cell).getChildAt(0));
            }
        }
        installFolderKeyNavigation(folderCards, scroll, columns, folder.label);
        FrameLayout gridHolder = new FrameLayout(activity);
        gridHolder.setClipChildren(false);
        gridHolder.setClipToPadding(false);
        gridHolder.addView(grid, new FrameLayout.LayoutParams(gridWidth, gridHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(gridHolder, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.CENTER);
        overlay.addView(panel, panelLp);
        dialog.setContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0f;
            window.setAttributes(attrs);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        overlay.requestFocus();
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setAlpha(0f);
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
        grid.post(() -> {
            if (!folderCards.isEmpty()) {
                View target = null;
                if (!TextUtils.isEmpty(activity.pendingFolderFocusPackage)) {
                    for (View card : folderCards) {
                        Object tag = card.getTag();
                        if (TextUtils.equals(activity.pendingFolderFocusPackage, tag == null ? null : tag.toString())) {
                            target = card;
                            break;
                        }
                    }
                    activity.pendingFolderFocusPackage = null;
                }
                if (target == null) target = folderCards.get(0);
                target.requestFocus();
            }
        });
    }

    private View createFolderAppTile(PackageManager pm, MainActivity.AppEntry entry, int tileWidth, String folderName) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        GridLayout.LayoutParams gridLp = new GridLayout.LayoutParams();
        gridLp.width = tileWidth;
        gridLp.height = activity.dp(130);
        gridLp.setMargins(activity.dp(9), activity.dp(0), activity.dp(9), activity.dp(8));
        cell.setLayoutParams(gridLp);

        FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(entry.packageName);
        card.setClipToOutline(true);
        card.setElevation(activity.dp(5));
        card.setTranslationZ(activity.dp(2));
        boolean hasBanner = entry.banner != null;
        card.setBackgroundResource(hasBanner ? R.drawable.app_card_banner : R.drawable.app_card);
        if (hasBanner) {
            card.setForeground(activity.getResources().getDrawable(R.drawable.app_card_banner_foreground));
        }
        int cardWidth = tileWidth - activity.dp(22);
        int cardHeight = Math.round(cardWidth * 9f / 16f);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cell.addView(card, cardLp);

        ImageView image = new ImageView(activity);
        if (hasBanner) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = activity.appManager.loadAppIcon(pm, entry);
            int baseColor = activity.appManager.dominantColor(appIcon);
            card.setBackground(activity.appManager.buildIconCardBackground(baseColor));
            card.setPadding(activity.dp(14), activity.dp(6), activity.dp(12), activity.dp(6));
            image.setImageDrawable(appIcon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Math.min(activity.dp(50), cardHeight - activity.dp(18));
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.addView(image, iconLp);

            TextView cardLabel = new TextView(activity);
            cardLabel.setText(entry.label);
            cardLabel.setTextColor(activity.appManager.readableTextColor(baseColor));
            cardLabel.setTextSize(13);
            cardLabel.setTypeface(Typeface.DEFAULT_BOLD);
            cardLabel.setGravity(Gravity.CENTER_VERTICAL);
            cardLabel.setSingleLine(true);
            cardLabel.setMaxLines(1);
            cardLabel.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = iconSize + activity.dp(14);
            card.addView(cardLabel, textLp);
        }

        TextView label = new TextView(activity);
        label.setText(entry.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(cardWidth, activity.dp(24));
        labelLp.topMargin = activity.dp(3);
        cell.addView(label, labelLp);

        card.setOnClickListener(v -> {
            if (TextUtils.equals(activity.movingFolderName, folderName)
                    && TextUtils.equals(activity.movingFolderPackage, entry.packageName)) {
                activity.appManager.finishFolderMove(folderName, entry.packageName);
                return;
            }
            dismissFolderOverlay();
            try {
                activity.startExternalActivity(entry.launchIntent);
            } catch (ActivityNotFoundException ex) {
                activity.toast(activity.getString(R.string.toast_unable_open) + entry.label);
            }
        });
        card.setOnLongClickListener(v -> {
            showFolderChildMenu(pm, entry, folderName);
            return true;
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            animateFocus(v, hasFocus, 1.07f);
            v.setElevation(hasFocus ? activity.dp(12) : activity.dp(5));
            v.setTranslationZ(hasFocus ? activity.dp(8) : activity.dp(2));
            label.setShadowLayer(hasFocus ? activity.dp(4) : 0, 0, hasFocus ? activity.dp(2) : 0, Color.argb(180, 0, 0, 0));
            if (hasFocus && TextUtils.equals(activity.movingFolderName, folderName)
                    && TextUtils.equals(activity.movingFolderPackage, entry.packageName)) {
                v.postDelayed(() -> activity.appManager.startFolderMoveWiggle(v, folderName, entry.packageName), 150);
            }
        });
        if (TextUtils.equals(activity.movingFolderName, folderName)
                && TextUtils.equals(activity.movingFolderPackage, entry.packageName)) {
            activity.appManager.startFolderMoveWiggle(card, folderName, entry.packageName);
        }
        return cell;
    }

    private void installFolderKeyNavigation(List<View> cards, ScrollView scroll, int columns, String folderName) {
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            View card = cards.get(i);
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                Object tag = v.getTag();
                String packageName = tag == null ? "" : tag.toString();
                boolean movingInsideFolder = TextUtils.equals(activity.movingFolderName, folderName)
                        && TextUtils.equals(activity.movingFolderPackage, packageName);
                if (movingInsideFolder) {
                    int delta = 0;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) delta = -1;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) delta = 1;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) delta = -columns;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) delta = columns;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        activity.appManager.finishFolderMove(folderName, packageName);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                        activity.appManager.cancelFolderMove(true);
                        return true;
                    } else {
                        return false;
                    }
                    if (delta != 0) {
                        activity.appManager.moveFolderChildByDelta(folderName, packageName, delta);
                        moveFolderCardInPlace(cards, v, delta, scroll, columns, folderName);
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissFolderOverlay();
                    return true;
                }
                int target = index;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    target = index % columns == 0 ? index : index - 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    target = (index % columns == columns - 1 || index + 1 >= cards.size()) ? index : index + 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    target = index - columns >= 0 ? index - columns : index;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (index + columns < cards.size()) {
                        target = index + columns;
                    } else {
                        int nextRowStart = ((index / columns) + 1) * columns;
                        target = nextRowStart < cards.size() ? nextRowStart : index;
                    }
                } else {
                    return false;
                }
                View next = cards.get(target);
                next.requestFocus();
                scroll.post(() -> next.requestRectangleOnScreen(new Rect(0, 0, next.getWidth(), next.getHeight()), false));
                return true;
            });
        }
    }

    private void moveFolderCardInPlace(List<View> cards, View focusedCard, int delta,
                                       ScrollView scroll, int columns, String folderName) {
        int from = cards.indexOf(focusedCard);
        if (from < 0) return;
        int to = Math.max(0, Math.min(cards.size() - 1, from + delta));
        if (from == to) return;

        View cell = (View) focusedCard.getParent();
        if (!(cell.getParent() instanceof FrameLayout)) return;
        FrameLayout grid = (FrameLayout) cell.getParent();
        grid.removeView(cell);
        grid.addView(cell, to);
        cards.remove(from);
        cards.add(to, focusedCard);

        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (!(rawLp instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rawLp;
            lp.leftMargin = (i % columns) * lp.width;
            lp.topMargin = (i / columns) * lp.height;
            child.setLayoutParams(lp);
        }

        installFolderKeyNavigation(cards, scroll, columns, folderName);
        focusedCard.requestFocus();
        focusedCard.animate().scaleX(1.11f).scaleY(1.11f).setDuration(70)
                .withEndAction(() -> focusedCard.animate().scaleX(1.07f).scaleY(1.07f).setDuration(90)
                        .withEndAction(() -> activity.appManager.startFolderMoveWiggle(focusedCard, folderName,
                                focusedCard.getTag() == null ? "" : focusedCard.getTag().toString()))
                        .start())
                .start();
        scroll.post(() -> focusedCard.requestRectangleOnScreen(
                new Rect(0, 0, focusedCard.getWidth(), focusedCard.getHeight()), false));
    }

    private void showFolderChildMenu(PackageManager pm, MainActivity.AppEntry entry, String folderName) {
        View restoreFocus = activity.getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction(activity.getString(R.string.action_open),
                activity.getString(R.string.action_open_desc), () -> activity.launchEntry(entry, true), false));
        actions.add(new AppAction(activity.getString(R.string.action_move),
                activity.getString(R.string.action_move_folder_desc), () -> {
            activity.movingFolderName = folderName;
            activity.movingFolderPackage = entry.packageName;
            activity.movingFolderOriginalOrder = activity.prefs.getString(folderOrderKey(folderName), "");
            if (restoreFocus != null) {
                restoreFocus.postDelayed(() -> {
                    restoreFocus.requestFocus();
                    activity.appManager.startFolderMoveWiggle(restoreFocus, folderName, entry.packageName);
                }, 90);
            }
        }, false));
        actions.add(new AppAction(activity.getString(R.string.action_remove),
                activity.getString(R.string.action_remove_desc), () -> activity.appManager.removeAppFromFolder(entry.packageName), true));
        actions.add(new AppAction(activity.getString(R.string.action_hide),
                activity.getString(R.string.action_hide_desc), () -> activity.hideApp(entry.packageName), true));
        actions.add(new AppAction(activity.getString(R.string.action_info),
                activity.getString(R.string.action_info_desc), () -> activity.openAppInfo(entry.packageName), false));
        showAppActionDialog(entry, actions, restoreFocus);
    }

    void dismissFolderOverlay() {
        if (activity.folderDialog != null) {
            activity.folderDialog.dismiss();
            return;
        }
        if (activity.folderOverlay == null) return;
        activity.folderOverlay = null;
        setFolderBackgroundBlur(false);
        if (activity.folderLastFocus != null) {
            activity.folderLastFocus.requestFocus();
            activity.folderLastFocus = null;
        }
    }

    void setFolderBackgroundBlur(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            View target = activity.launcherRoot != null ? activity.launcherRoot : activity.homeScroll;
            if (target == null) return;
            if (enabled) {
                target.setRenderEffect(RenderEffect.createBlurEffect(activity.dp(32), activity.dp(32), Shader.TileMode.CLAMP));
            } else {
                target.setRenderEffect(null);
            }
        } catch (Exception ignored) {
        }
    }

    private void promptAddToFolder(MainActivity.AppEntry entry) {
        Map<String, String> existingAssignments = activity.appManager.readPackageFolders();
        List<String> folders = new ArrayList<>();
        for (String folder : existingAssignments.values()) {
            if (!TextUtils.isEmpty(folder) && !folders.contains(folder)) folders.add(folder);
        }
        Collections.sort(folders, Collator.getInstance(Locale.getDefault()));
        folders.add(activity.getString(R.string.folder_create_new));
        String[] items = folders.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.folder_add_title))
                .setItems(items, (d, which) -> {
                    String selected = items[which];
                    if (activity.getString(R.string.folder_create_new).equals(selected)) {
                        promptCreateFolder(entry);
                    } else {
                        activity.appManager.addAppToFolder(entry, selected);
                    }
                })
                .setNegativeButton(activity.getString(R.string.action_cancel), null)
                .create();
        dialog.setOnShowListener(d -> {
            try {
                dialog.getListView().requestFocus();
            } catch (Exception ignored) {
            }
        });
        dialog.show();
    }

    private void promptCreateFolder(MainActivity.AppEntry entry) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint(activity.getString(R.string.folder_name_hint));
        input.setText(activity.prefs.getString("last_folder_name", "新建文件夹"));
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.folder_add_title))
                .setView(input)
                .setNegativeButton(activity.getString(R.string.action_cancel), null)
                .setPositiveButton(activity.getString(R.string.folder_save), (d, which) -> {
                    String folder = input.getText().toString().trim();
                    if (TextUtils.isEmpty(folder)) {
                        activity.toast(activity.getString(R.string.folder_name_empty));
                        return;
                    }
                    activity.appManager.addAppToFolder(entry, folder);
                })
                .show();
    }

    private String folderOrderKey(String folderName) {
        return "folder_order_" + Uri.encode(folderName == null ? "" : folderName);
    }

    void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? activity.dp(12) : 0).setDuration(130).start();
    }

    Runnable buildAppShortcutListInto(LinearLayout container) {
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            int childCount = container.getChildCount();
            for (int i = childCount - 1; i >= 1; i--) {
                container.removeViewAt(i);
            }
            for (int i = 0; i <= 9; i++) {
                final String digit = String.valueOf(i);
                addShortcutListItem(container, digit, false, refresh[0]);
            }
            addShortcutListItem(container, null, true, refresh[0]);
        };
        refresh[0].run();
        return refresh[0];
    }

    private void addShortcutListItem(LinearLayout container, String digit, boolean isCustom,
                                     Runnable refresh) {
        AppShortcutManager shortcutManager = activity.appShortcutManager;
        TextView item = new TextView(activity);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        item.setTextSize(18);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackgroundResource(R.drawable.settings_option);

        String displayText;
        if (isCustom) {
            AppShortcutManager.CustomShortcut custom = shortcutManager.getCustomMapping();
            if (custom != null) {
                displayText = activity.getString(R.string.shortcut_custom_bound,
                        custom.keyName, custom.label);
            } else {
                displayText = activity.getString(R.string.shortcut_custom);
            }
        } else {
            AppShortcutManager.AppShortcut shortcut = shortcutManager.getDigitMapping(digit);
            if (shortcut != null) {
                displayText = digit + "  →  " + shortcut.label;
            } else {
                displayText = digit + "  →  " + activity.getString(R.string.shortcut_unset);
            }
        }
        item.setText(displayText);
        item.setTag(isCustom ? null : digit);

        item.setOnFocusChangeListener((v, hasFocus) -> {
            animateFocus(v, hasFocus, 1.03f);
        });

        item.setOnClickListener(v -> {
            if (isCustom) {
                showKeyCaptureDialog(() -> showAppPickerForShortcut(true, null, refresh));
            } else {
                showAppPickerForShortcut(false, digit, refresh);
            }
        });

        item.setOnLongClickListener(v -> {
            if (isCustom) {
                shortcutManager.clearCustomMapping();
                activity.toast(activity.getString(R.string.shortcut_cleared));
            } else {
                shortcutManager.clearDigitMapping(digit);
                activity.toast(activity.getString(R.string.shortcut_cleared));
            }
            refresh.run();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(56));
        lp.bottomMargin = activity.dp(8);
        container.addView(item, lp);
    }

    private void showKeyCaptureDialog(Runnable onKeyCaptured) {
        Dialog keyDialog = new Dialog(activity);
        keyDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        keyDialog.setCanceledOnTouchOutside(true);

        activity.pendingCustomKeyCode = -1;
        activity.pendingCustomKeyName = null;

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.argb(200, 0, 0, 0));
        root.setPadding(activity.dp(40), activity.dp(40), activity.dp(40), activity.dp(40));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(24), activity.dp(20), activity.dp(24), activity.dp(20));
        panel.setBackground(actionPanelBackground());
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int width = Math.min(activity.dp(560), activity.getResources().getDisplayMetrics().widthPixels - activity.dp(80));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, panelLp);

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.shortcut_capture_title));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(44)));

        TextView hint = new TextView(activity);
        hint.setText(activity.getString(R.string.shortcut_capture_hint));
        hint.setTextColor(Color.argb(180, 255, 255, 255));
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, activity.dp(36)));

        TextView capturedKey = new TextView(activity);
        capturedKey.setText(activity.getString(R.string.shortcut_capture_waiting));
        capturedKey.setTextColor(Color.WHITE);
        capturedKey.setTextSize(20);
        capturedKey.setTypeface(Typeface.DEFAULT_BOLD);
        capturedKey.setGravity(Gravity.CENTER);
        capturedKey.setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12));
        capturedKey.setBackgroundResource(R.drawable.settings_option);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, activity.dp(64));
        keyLp.bottomMargin = activity.dp(16);
        panel.addView(capturedKey, keyLp);

        TextView confirmBtn = createActionButton(
                new AppAction(activity.getString(R.string.shortcut_capture_confirm),
                        activity.getString(R.string.shortcut_capture_confirm_desc),
                        () -> {
                            if (activity.pendingCustomKeyCode != -1) {
                                keyDialog.dismiss();
                                onKeyCaptured.run();
                            } else {
                                activity.toast(activity.getString(R.string.shortcut_capture_waiting));
                            }
                        }, false),
                keyDialog);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, activity.dp(58));
        confirmBtn.setGravity(Gravity.CENTER);
        panel.addView(confirmBtn, btnLp);

        keyDialog.setContentView(root);
        Window window = keyDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }

        keyDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (isFilteredKey(keyCode)) {
                return false;
            }
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                activity.toast(activity.getString(R.string.shortcut_capture_digit_forbidden));
                return true;
            }
            activity.pendingCustomKeyCode = keyCode;
            activity.pendingCustomKeyName = KeyEvent.keyCodeToString(keyCode);
            capturedKey.setText(activity.pendingCustomKeyName);
            confirmBtn.requestFocus();
            return true;
        });

        keyDialog.setOnShowListener(d -> {
            root.requestFocus();
            confirmBtn.requestFocus();
        });
        keyDialog.show();
    }

    private boolean isFilteredKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_BACK;
    }

    private void showAppPickerForShortcut(boolean isCustom, String digit, Runnable refresh) {
        List<MainActivity.AppEntry> apps = new ArrayList<>(activity.appManager.queryLaunchableApps(activity.getPackageManager()));
        if (apps.isEmpty()) {
            activity.toast(activity.getString(R.string.shortcut_no_apps));
            return;
        }

        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int position) { return apps.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
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
                .setTitle(activity.getString(R.string.shortcut_choose_app))
                .setAdapter(adapter, (dialogInterface, which) -> {
                    MainActivity.AppEntry selected = apps.get(which);
                    if (isCustom) {
                        int keyCode = activity.pendingCustomKeyCode;
                        String keyName = activity.pendingCustomKeyName;
                        if (keyCode == -1) {
                            activity.toast(activity.getString(R.string.shortcut_capture_waiting));
                            return;
                        }
                        activity.appShortcutManager.setCustomMapping(keyCode, keyName, selected.packageName, selected.label);
                        activity.pendingCustomKeyCode = -1;
                        activity.pendingCustomKeyName = null;
                    } else {
                        activity.appShortcutManager.setDigitMapping(digit, selected.packageName, selected.label);
                    }
                    activity.toast(activity.getString(R.string.shortcut_bound));
                    refresh.run();
                })
                .setNegativeButton(activity.getString(R.string.action_cancel), null)
                .create();
        dialog.setOnShowListener(d -> {
            try {
                dialog.getListView().requestFocus();
            } catch (Exception ignored) {}
        });
        dialog.show();
    }

    private static class AppAction {
        final String title;
        final String subtitle;
        final Runnable action;
        final boolean danger;

        AppAction(String title, String subtitle, Runnable action, boolean danger) {
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
            this.danger = danger;
        }
    }
}