package com.LemonLnch.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.LemonLnch.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AppManager {
    private final MainActivity activity;

    AppManager(MainActivity activity) {
        this.activity = activity;
    }

    void loadApps() {
        if (activity.appsGrid == null) return;
        PackageManager pm = activity.getPackageManager();
        List<MainActivity.AppEntry> entries = buildDisplayEntries(queryLaunchableApps(pm));
        applySavedDisplayOrder(entries);
        activity.appsGrid.removeAllViews();

        int columns = activity.appsPerRow();
        int padH = activity.horizontalPaddingDp();
        int gridWidth = activity.getResources().getDisplayMetrics().widthPixels - activity.dp(padH * 2);
        int cellWidth = Math.max(activity.dp(150), gridWidth / columns);
        activity.appsGrid.setColumnCount(columns);

        for (MainActivity.AppEntry entry : entries) {
            activity.appsGrid.addView(createAppTile(pm, entry, cellWidth));
        }
        if (!TextUtils.isEmpty(activity.focusAfterLoadPackage)) {
            String focusPackage = activity.focusAfterLoadPackage;
            activity.focusAfterLoadPackage = null;
            activity.appsGrid.post(() -> focusAppTile(focusPackage));
        }
    }

    void focusAppTile(String packageName) {
        if (activity.appsGrid == null || TextUtils.isEmpty(packageName)) return;
        for (int i = 0; i < activity.appsGrid.getChildCount(); i++) {
            View cell = activity.appsGrid.getChildAt(i);
            Object tag = cell.getTag();
            if (TextUtils.equals(packageName, tag == null ? null : tag.toString()) && cell instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) cell;
                if (layout.getChildCount() > 0) layout.getChildAt(0).requestFocus();
                return;
            }
        }
    }

    List<MainActivity.AppEntry> queryLaunchableApps(PackageManager pm) {
        String hiddenKey = hiddenAppsKey();
        long now = System.currentTimeMillis();
        if (activity.cachedLaunchableApps != null
                && TextUtils.equals(activity.cachedHiddenKey, hiddenKey)
                && now - activity.cachedLaunchableAppsAt < 30000L) {
            List<MainActivity.AppEntry> cached = new ArrayList<>(activity.cachedLaunchableApps);
            applySavedOrder(cached);
            return cached;
        }
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> resolves = new ArrayList<>(pm.queryIntentActivities(leanback, 0));
        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        resolves.addAll(pm.queryIntentActivities(normal, 0));

        Set<String> seen = new HashSet<>();
        Set<String> hidden = activity.prefs.getStringSet("hidden_apps", new HashSet<>());
        List<MainActivity.AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : resolves) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (activity.getPackageName().equals(pkg) || seen.contains(pkg)) continue;
            if (hidden.contains(pkg)) continue;
            seen.add(pkg);

            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            Drawable banner = null;
            try {
                banner = info.activityInfo.loadBanner(pm);
                if (banner == null) banner = info.activityInfo.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) {
            }
            String defaultLabel = String.valueOf(info.loadLabel(pm));
            String displayName = getCustomAppName(pkg, defaultLabel);
            apps.add(new MainActivity.AppEntry(pkg, displayName, launch, info.activityInfo.applicationInfo, banner));
        }
        try {
            for (ApplicationInfo appInfo : pm.getInstalledApplications(0)) {
                String pkg = appInfo.packageName;
                if (activity.getPackageName().equals(pkg) || seen.contains(pkg) || hidden.contains(pkg)) continue;
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                seen.add(pkg);
                Drawable banner = null;
                try {
                    banner = appInfo.loadBanner(pm);
                } catch (Exception ignored) {
                }
                String defaultLabel = String.valueOf(appInfo.loadLabel(pm));
                String displayName = getCustomAppName(pkg, defaultLabel);
                apps.add(new MainActivity.AppEntry(pkg, displayName, launch, appInfo, banner));
            }
        } catch (Exception ignored) {
        }
        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
        activity.cachedLaunchableApps = new ArrayList<>(apps);
        activity.cachedHiddenKey = hiddenKey;
        activity.cachedLaunchableAppsAt = now;
        applySavedOrder(apps);
        return apps;
    }

    void invalidateAppCache() {
        activity.cachedLaunchableApps = null;
        activity.cachedLaunchableAppsAt = 0L;
    }

    private String hiddenAppsKey() {
        List<String> hidden = new ArrayList<>(activity.prefs.getStringSet("hidden_apps", new HashSet<>()));
        Collections.sort(hidden);
        return TextUtils.join("|", hidden);
    }

    private List<MainActivity.AppEntry> buildDisplayEntries(List<MainActivity.AppEntry> apps) {
        Map<String, String> folderByPackage = readPackageFolders();
        Map<String, List<MainActivity.AppEntry>> folderChildren = new LinkedHashMap<>();
        List<MainActivity.AppEntry> display = new ArrayList<>();
        Set<String> addedFolders = new HashSet<>();
        for (MainActivity.AppEntry app : apps) {
            String folder = folderByPackage.get(app.packageName);
            if (TextUtils.isEmpty(folder)) {
                display.add(app);
                continue;
            }
            if (!folderChildren.containsKey(folder)) folderChildren.put(folder, new ArrayList<>());
            folderChildren.get(folder).add(app);
            if (!addedFolders.contains(folder)) {
                display.add(MainActivity.AppEntry.folder(folder, folderChildren.get(folder)));
                addedFolders.add(folder);
            }
        }
        for (Map.Entry<String, List<MainActivity.AppEntry>> entry : folderChildren.entrySet()) {
            applySavedFolderOrder(entry.getKey(), entry.getValue());
        }
        return display;
    }

    private void applySavedOrder(List<MainActivity.AppEntry> apps) {
        List<String> order = readOrder();
        if (order.isEmpty()) return;
        Map<String, Integer> indexByPackage = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) indexByPackage.put(order.get(i), i);
        Collections.sort(apps, (a, b) -> {
            int ai = indexByPackage.containsKey(a.packageName) ? indexByPackage.get(a.packageName) : 100000;
            int bi = indexByPackage.containsKey(b.packageName) ? indexByPackage.get(b.packageName) : 100000;
            if (ai != bi) return ai - bi;
            return Collator.getInstance(Locale.getDefault()).compare(a.label, b.label);
        });
    }

    private void applySavedDisplayOrder(List<MainActivity.AppEntry> entries) {
        applySavedOrder(entries);
    }

    private void applySavedFolderOrder(String folderName, List<MainActivity.AppEntry> entries) {
        List<String> order = readFolderOrder(folderName);
        if (order.isEmpty()) return;
        Map<String, Integer> indexByPackage = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) indexByPackage.put(order.get(i), i);
        Collections.sort(entries, (a, b) -> {
            int ai = indexByPackage.containsKey(a.packageName) ? indexByPackage.get(a.packageName) : 100000;
            int bi = indexByPackage.containsKey(b.packageName) ? indexByPackage.get(b.packageName) : 100000;
            if (ai != bi) return ai - bi;
            return Collator.getInstance(Locale.getDefault()).compare(a.label, b.label);
        });
    }

    private View createAppTile(PackageManager pm, MainActivity.AppEntry entry, int cellWidth) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(activity.dp(8), activity.dp(2), activity.dp(8), activity.dp(10));
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setTag(entry.packageName);

        GridLayout.LayoutParams gridLp = new GridLayout.LayoutParams();
        gridLp.width = cellWidth;
        gridLp.height = activity.dp(activity.prefs.getBoolean("hide_app_titles", false) ? 116 : 150);
        cell.setLayoutParams(gridLp);

        boolean hasBanner = !entry.isFolder && entry.banner != null;
        boolean colorCard = !entry.isFolder && !hasBanner;
        FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setBackgroundResource(hasBanner ? R.drawable.app_card_banner : R.drawable.app_card);
        if (hasBanner) {
            card.setForeground(activity.getResources().getDrawable(R.drawable.app_card_banner_foreground));
        }
        int cardWidth = cellWidth - activity.dp(16);
        int cardHeight = Math.max(activity.dp(78), Math.round(cardWidth * 9f / 16f));
        card.setPadding(colorCard ? activity.dp(18) : 0, colorCard ? activity.dp(8) : 0,
                colorCard ? activity.dp(16) : 0, colorCard ? activity.dp(8) : 0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cell.addView(card, cardLp);

        ImageView icon = new ImageView(activity);
        if (entry.isFolder) {
            addFolderPreview(pm, card, entry, cardWidth, cardHeight);
        } else if (hasBanner) {
            icon.setImageDrawable(entry.banner);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(icon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = loadAppIcon(pm, entry);
            int baseColor = dominantColor(appIcon);
            card.setBackground(buildIconCardBackground(baseColor));
            icon.setImageDrawable(appIcon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Math.min(activity.dp(62), cardHeight - activity.dp(20));
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.addView(icon, iconLp);

            TextView cardLabel = new TextView(activity);
            cardLabel.setText(entry.label);
            cardLabel.setTextColor(readableTextColor(baseColor));
            cardLabel.setTextSize(15);
            cardLabel.setTypeface(Typeface.DEFAULT_BOLD);
            cardLabel.setGravity(Gravity.CENTER_VERTICAL);
            cardLabel.setSingleLine(true);
            cardLabel.setMaxLines(1);
            cardLabel.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = iconSize + activity.dp(18);
            card.addView(cardLabel, textLp);
        }

        TextView label = new TextView(activity);
        boolean showTitleBelow = !activity.prefs.getBoolean("hide_app_titles", false);
        if (showTitleBelow) {
            label.setText(entry.label);
            label.setTextColor(Color.argb(222, 255, 255, 255));
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(cardWidth, activity.dp(25));
            labelLp.topMargin = activity.dp(4);
            cell.addView(label, labelLp);
        }

        card.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(activity.movingPackage)) {
                activity.toast("Position saved");
                activity.movingPackage = null;
                activity.loadApps();
                return;
            }
            if (entry.isFolder) {
                activity.dialogUtils.showFolder(entry);
                return;
            }
            try {
                activity.startExternalActivity(entry.launchIntent);
            } catch (ActivityNotFoundException ex) {
                activity.toast("Unable to open " + entry.label);
            }
        });
        card.setOnLongClickListener(v -> {
            activity.dialogUtils.showAppMenu(pm, entry);
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || !TextUtils.equals(activity.movingPackage, entry.packageName)) {
                return false;
            }
            int delta = 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) delta = -1;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) delta = 1;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) delta = -activity.appsPerRow();
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) delta = activity.appsPerRow();
            else if (keyCode == KeyEvent.KEYCODE_BACK) {
                activity.movingPackage = null;
                activity.loadApps();
                activity.toast("Move cancelled");
                return true;
            }
            if (delta == 0) return false;
            return moveFocusedTileByDelta(v, entry.packageName, delta);
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) keepFocusedCardFullyVisible(v);
            animateFocus(v, hasFocus, 1.08f);
            if (showTitleBelow) {
                label.setTextColor(hasFocus ? Color.WHITE : Color.argb(222, 255, 255, 255));
                label.setShadowLayer(hasFocus ? activity.dp(4) : 0, 0, hasFocus ? activity.dp(2) : 0, Color.argb(180, 0, 0, 0));
            }
        });
        if (TextUtils.equals(activity.movingPackage, entry.packageName)) {
            startHomeMoveWiggle(card, entry.packageName);
        }
        return cell;
    }

    Drawable loadAppIcon(PackageManager pm, MainActivity.AppEntry entry) {
        try {
            if (entry.info != null) return entry.info.loadIcon(pm);
        } catch (Exception ignored) {
        }
        return activity.getResources().getDrawable(R.mipmap.ic_launcher);
    }

    GradientDrawable buildIconCardBackground(int baseColor) {
        int start = adjustColor(baseColor, 1.22f);
        int end = adjustColor(baseColor, 0.92f);
        if (isLowSaturation(baseColor)) {
            start = Color.rgb(238, 244, 251);
            end = Color.rgb(213, 228, 246);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        bg.setCornerRadius(activity.dp(12));
        return bg;
    }

    int dominantColor(Drawable drawable) {
        try {
            int size = activity.dp(56);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            long r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < size; y += 4) {
                for (int x = 0; x < size; x += 4) {
                    int color = bitmap.getPixel(x, y);
                    if (Color.alpha(color) < 80) continue;
                    int cr = Color.red(color);
                    int cg = Color.green(color);
                    int cb = Color.blue(color);
                    int max = Math.max(cr, Math.max(cg, cb));
                    int min = Math.min(cr, Math.min(cg, cb));
                    if (max > 235 && min > 220) continue;
                    if (max - min < 16 && max > 170) continue;
                    r += cr;
                    g += cg;
                    b += cb;
                    count++;
                }
            }
            bitmap.recycle();
            if (count == 0) return Color.rgb(82, 145, 236);
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (Exception ignored) {
            return Color.rgb(82, 145, 236);
        }
    }

    private boolean isLowSaturation(int color) {
        int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
        int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
        return max - min < 24;
    }

    private int adjustColor(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor))));
    }

    int readableTextColor(int color) {
        double luminance = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114;
        return luminance > 145 ? Color.BLACK : Color.WHITE;
    }

    private void keepFocusedCardFullyVisible(View v) {
        int pad = activity.dp(34);
        Rect rect = new Rect(-pad, -pad, v.getWidth() + pad, v.getHeight() + pad);
        v.post(() -> v.requestRectangleOnScreen(rect, false));
    }

    private void addFolderPreview(PackageManager pm, FrameLayout card, MainActivity.AppEntry folder, int cardWidth, int cardHeight) {
        int count = folder.children == null ? 0 : Math.min(9, folder.children.size());
        if (cardWidth <= 0) {
            int measured = card.getWidth();
            int lpWidth = card.getLayoutParams() == null ? 0 : card.getLayoutParams().width;
            cardWidth = measured > 0 ? measured : (lpWidth > 0 ? lpWidth : activity.dp(220));
        }
        int gapX = activity.dp(10);
        int gapY = activity.dp(4);
        int safeWidth = Math.max(activity.dp(90), cardWidth - activity.dp(58));
        int iconWidth = Math.min(activity.dp(58), Math.max(activity.dp(38), (safeWidth - gapX * 2) / 3));
        int maxHeightForNine = Math.max(activity.dp(24), (cardHeight - activity.dp(20) - gapY * 2) / 3);
        int iconHeight = Math.min(maxHeightForNine, Math.min(activity.dp(42), Math.max(activity.dp(30), Math.round(iconWidth * 0.66f))));
        iconWidth = Math.min(iconWidth, Math.max(activity.dp(38), Math.round(iconHeight * 1.55f)));
        int gridWidth = iconWidth * 3 + gapX * 2;
        int left = Math.max(activity.dp(12), (cardWidth - gridWidth) / 2);
        int top = Math.max(activity.dp(12), (cardHeight - iconHeight * 3 - gapY * 2) / 2);
        for (int i = 0; i < count; i++) {
            MainActivity.AppEntry child = folder.children.get(i);
            FrameLayout mini = createFolderMiniCard(pm, child, iconWidth, iconHeight);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconWidth, iconHeight);
            lp.leftMargin = left + (i % 3) * (iconWidth + gapX);
            lp.topMargin = top + (i / 3) * (iconHeight + gapY);
            card.addView(mini, lp);
        }
    }

    private FrameLayout createFolderMiniCard(PackageManager pm, MainActivity.AppEntry entry, int width, int height) {
        FrameLayout mini = new FrameLayout(activity);
        mini.setClipToOutline(true);
        mini.setBackgroundColor(Color.TRANSPARENT);

        ImageView image = new ImageView(activity);
        if (entry.banner != null) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mini.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = loadAppIcon(pm, entry);
            int baseColor = dominantColor(appIcon);
            mini.setBackground(buildMiniIconCardBackground(baseColor));
            mini.setPadding(activity.dp(4), activity.dp(2), activity.dp(3), activity.dp(2));
            image.setImageDrawable(appIcon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int icon = Math.max(activity.dp(12), Math.min(activity.dp(20), height - activity.dp(8)));
            mini.addView(image, new FrameLayout.LayoutParams(icon, icon, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            TextView name = new TextView(activity);
            name.setText(entry.label);
            name.setTextColor(readableTextColor(baseColor));
            name.setTextSize(5);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = icon + activity.dp(4);
            mini.addView(name, textLp);
        }
        return mini;
    }

    private GradientDrawable buildMiniIconCardBackground(int baseColor) {
        int start = adjustColor(baseColor, 1.22f);
        int end = adjustColor(baseColor, 0.92f);
        if (isLowSaturation(baseColor)) {
            start = Color.rgb(238, 244, 251);
            end = Color.rgb(213, 228, 246);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        bg.setCornerRadius(activity.dp(4));
        return bg;
    }

    private boolean moveFocusedTileByDelta(View focusedCard, String packageName, int delta) {
        if (activity.appsGrid == null) return true;
        View cell = (View) focusedCard.getParent();
        int from = activity.appsGrid.indexOfChild(cell);
        int to = Math.max(0, Math.min(activity.appsGrid.getChildCount() - 1, from + delta));
        if (from < 0 || from == to) return true;

        moveAppByDelta(packageName, delta);
        activity.appsGrid.removeViewAt(from);
        activity.appsGrid.addView(cell, to);
        cell.post(() -> {
            if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
                View card = ((LinearLayout) cell).getChildAt(0);
                card.requestFocus();
                card.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80)
                        .withEndAction(() -> card.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start())
                        .start();
            }
        });
        return true;
    }

    private void moveAppByDelta(String packageName, int delta) {
        List<MainActivity.AppEntry> current = buildDisplayEntries(queryLaunchableApps(activity.getPackageManager()));
        applySavedDisplayOrder(current);
        List<String> order = new ArrayList<>();
        for (MainActivity.AppEntry entry : current) {
            if (!order.contains(entry.packageName)) order.add(entry.packageName);
        }
        int from = order.indexOf(packageName);
        if (from < 0) return;
        int to = Math.max(0, Math.min(order.size() - 1, from + delta));
        if (from == to) return;
        order.remove(from);
        order.add(to, packageName);
        saveOrder(order);
    }

    private List<String> readOrder() {
        String raw = activity.prefs.getString("app_order", "");
        List<String> order = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return order;
        for (String part : raw.split(",")) {
            if (!TextUtils.isEmpty(part)) order.add(part);
        }
        return order;
    }

    private void saveOrder(List<String> order) {
        activity.prefs.edit().putString("app_order", TextUtils.join(",", order)).apply();
    }

    private List<String> readFolderOrder(String folderName) {
        String raw = activity.prefs.getString(folderOrderKey(folderName), "");
        List<String> order = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return order;
        for (String part : raw.split(",")) {
            if (!TextUtils.isEmpty(part)) order.add(part);
        }
        return order;
    }

    private void saveFolderOrder(String folderName, List<String> order) {
        activity.prefs.edit().putString(folderOrderKey(folderName), TextUtils.join(",", order)).apply();
    }

    private String folderOrderKey(String folderName) {
        return "folder_order_" + Uri.encode(folderName == null ? "" : folderName);
    }

    Map<String, String> readPackageFolders() {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = activity.prefs.getString("app_folders", "");
        if (TextUtils.isEmpty(raw)) return result;
        for (String item : raw.split("\\|")) {
            int split = item.indexOf('=');
            if (split <= 0) continue;
            result.put(Uri.decode(item.substring(0, split)), Uri.decode(item.substring(split + 1)));
        }
        return result;
    }

    private void savePackageFolders(Map<String, String> folders) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : folders.entrySet()) {
            parts.add(Uri.encode(entry.getKey()) + "=" + Uri.encode(entry.getValue()));
        }
        activity.prefs.edit().putString("app_folders", TextUtils.join("|", parts)).apply();
    }

    void addAppToFolder(MainActivity.AppEntry entry, String folder) {
        Map<String, String> folders = readPackageFolders();
        folders.put(entry.packageName, folder);
        savePackageFolders(folders);
        activity.prefs.edit().putString("last_folder_name", folder).apply();
        activity.loadApps();
    }

    void removeAppFromFolder(String packageName) {
        Map<String, String> folders = readPackageFolders();
        folders.remove(packageName);
        savePackageFolders(folders);
        activity.focusAfterLoadPackage = packageName;
        activity.loadApps();
        activity.toast("Removed from folder");
    }

    void removeFolder(String folderName) {
        Map<String, String> folders = readPackageFolders();
        List<String> removedPackages = new ArrayList<>();
        for (Map.Entry<String, String> entry : new ArrayList<>(folders.entrySet())) {
            if (TextUtils.equals(entry.getValue(), folderName)) {
                removedPackages.add(entry.getKey());
                folders.remove(entry.getKey());
            }
        }
        savePackageFolders(folders);
        if (!removedPackages.isEmpty()) activity.focusAfterLoadPackage = removedPackages.get(0);
        activity.loadApps();
        activity.toast("Folder removed");
    }

    void hideApp(String packageName) {
        Set<String> hidden = new HashSet<>(activity.prefs.getStringSet("hidden_apps", new HashSet<>()));
        hidden.add(packageName);
        activity.prefs.edit().putStringSet("hidden_apps", hidden).apply();
        invalidateAppCache();
        activity.loadApps();
    }

    /** 恢复被隐藏的应用显示 */
    void unhideApp(String packageName) {
        Set<String> hidden = new HashSet<>(activity.prefs.getStringSet("hidden_apps", new HashSet<>()));
        hidden.remove(packageName);
        activity.prefs.edit().putStringSet("hidden_apps", hidden).apply();
        invalidateAppCache();
        activity.loadApps();
    }

    void launchEntry(MainActivity.AppEntry entry, boolean dismissFolder) {
        if (dismissFolder) activity.dismissFolderOverlay();
        try {
            activity.startExternalActivity(entry.launchIntent);
        } catch (ActivityNotFoundException ex) {
            activity.toast("Unable to open " + entry.label);
        }
    }

    void cancelFolderMove(boolean keepFolderOpen) {
        String folderName = activity.movingFolderName;
        String packageName = activity.movingFolderPackage;
        if (!TextUtils.isEmpty(folderName) && activity.movingFolderOriginalOrder != null) {
            activity.prefs.edit().putString(folderOrderKey(folderName), activity.movingFolderOriginalOrder).apply();
        }
        activity.movingFolderName = null;
        activity.movingFolderPackage = null;
        activity.movingFolderOriginalOrder = null;
        if (keepFolderOpen && !TextUtils.isEmpty(folderName)) {
            refreshFolder(folderName, packageName);
        }
        activity.toast("Move cancelled");
    }

    void finishFolderMove(String folderName, String packageName) {
        View focused = activity.getCurrentFocus();
        activity.movingFolderName = null;
        activity.movingFolderPackage = null;
        activity.movingFolderOriginalOrder = null;
        activity.pendingFolderFocusPackage = packageName;
        if (focused != null) {
            focused.animate().cancel();
            focused.setRotation(0f);
            focused.setScaleX(1.07f);
            focused.setScaleY(1.07f);
            focused.requestFocus();
        }
        activity.toast("Position saved");
    }

    private void refreshFolder(String folderName, String focusPackage) {
        activity.pendingFolderFocusPackage = focusPackage;
        MainActivity.AppEntry folder = findFolderEntry(folderName);
        if (folder == null || folder.children == null || folder.children.isEmpty()) {
            activity.dismissFolderOverlay();
            activity.loadApps();
            return;
        }
        activity.dialogUtils.showFolder(folder);
    }

    private MainActivity.AppEntry findFolderEntry(String folderName) {
        List<MainActivity.AppEntry> entries = buildDisplayEntries(queryLaunchableApps(activity.getPackageManager()));
        applySavedDisplayOrder(entries);
        String target = "folder:" + folderName;
        for (MainActivity.AppEntry entry : entries) {
            if (entry.isFolder && TextUtils.equals(entry.packageName, target)) return entry;
        }
        return null;
    }

    void moveFolderChildByDelta(String folderName, String packageName, int delta) {
        List<String> order = currentFolderPackageOrder(folderName);
        int from = order.indexOf(packageName);
        if (from < 0) return;
        int to = Math.max(0, Math.min(order.size() - 1, from + delta));
        if (from == to) return;
        order.remove(from);
        order.add(to, packageName);
        saveFolderOrder(folderName, order);
    }

    private List<String> currentFolderPackageOrder(String folderName) {
        Map<String, String> folders = readPackageFolders();
        List<MainActivity.AppEntry> apps = queryLaunchableApps(activity.getPackageManager());
        List<MainActivity.AppEntry> children = new ArrayList<>();
        for (MainActivity.AppEntry app : apps) {
            if (TextUtils.equals(folderName, folders.get(app.packageName))) {
                children.add(app);
            }
        }
        applySavedFolderOrder(folderName, children);
        List<String> order = new ArrayList<>();
        for (MainActivity.AppEntry child : children) {
            if (!order.contains(child.packageName)) order.add(child.packageName);
        }
        return order;
    }

    void startHomeMoveWiggle(View card, String packageName) {
        card.post(new Runnable() {
            boolean flip;

            @Override
            public void run() {
                if (!TextUtils.equals(activity.movingPackage, packageName) || !card.isAttachedToWindow()) {
                    card.animate().cancel();
                    card.setRotation(0f);
                    card.setScaleX(1f);
                    card.setScaleY(1f);
                    return;
                }
                flip = !flip;
                card.animate()
                        .rotation(flip ? 1.8f : -1.8f)
                        .scaleX(flip ? 1.025f : 0.995f)
                        .scaleY(flip ? 1.025f : 0.995f)
                        .setDuration(145)
                        .withEndAction(this)
                        .start();
            }
        });
    }

    void startFolderMoveWiggle(View card, String folderName, String packageName) {
        card.post(new Runnable() {
            boolean flip;

            @Override
            public void run() {
                boolean stillMoving = TextUtils.equals(activity.movingFolderName, folderName)
                        && TextUtils.equals(activity.movingFolderPackage, packageName);
                if (!stillMoving || !card.isAttachedToWindow()) {
                    card.animate().cancel();
                    card.setRotation(0f);
                    card.setScaleX(1f);
                    card.setScaleY(1f);
                    return;
                }
                flip = !flip;
                card.animate()
                        .rotation(flip ? 1.8f : -1.8f)
                        .scaleX(flip ? 1.025f : 0.995f)
                        .scaleY(flip ? 1.025f : 0.995f)
                        .setDuration(145)
                        .withEndAction(this)
                        .start();
            }
        });
    }

    private void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? activity.dp(12) : 0).setDuration(130).start();
    }

    // ==================== 自定义应用名称相关 ====================

    private Map<String, String> readCustomAppNames() {
        Map<String, String> names = new LinkedHashMap<>();
        String raw = activity.prefs.getString("app_custom_names", "");
        if (TextUtils.isEmpty(raw)) return names;
        for (String item : raw.split("\\|")) {
            int split = item.indexOf('=');
            if (split <= 0) continue;
            names.put(Uri.decode(item.substring(0, split)), Uri.decode(item.substring(split + 1)));
        }
        return names;
    }

    private void saveCustomAppNames(Map<String, String> names) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            parts.add(Uri.encode(entry.getKey()) + "=" + Uri.encode(entry.getValue()));
        }
        activity.prefs.edit().putString("app_custom_names", TextUtils.join("|", parts)).apply();
    }

    private String getCustomAppName(String packageName, String defaultName) {
        Map<String, String> names = readCustomAppNames();
        String custom = names.get(packageName);
        return TextUtils.isEmpty(custom) ? defaultName : custom;
    }

    void renameApp(String packageName, String newName) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(newName)) return;
        Map<String, String> names = readCustomAppNames();
        names.put(packageName, newName.trim());
        saveCustomAppNames(names);
        invalidateAppCache();
        activity.loadApps();
    }

    void renameFolder(String oldName, String newName) {
        if (TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName) || oldName.equals(newName)) return;
        Map<String, String> folders = readPackageFolders();
        boolean changed = false;
        for (Map.Entry<String, String> entry : new ArrayList<>(folders.entrySet())) {
            if (TextUtils.equals(entry.getValue(), oldName)) {
                entry.setValue(newName.trim());
                changed = true;
            }
        }
        if (changed) {
            savePackageFolders(folders);
            activity.prefs.edit().putString("last_folder_name", newName.trim()).apply();
            activity.loadApps();
        }
    }
}