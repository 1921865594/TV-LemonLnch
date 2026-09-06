package com.LemonLnch.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class GradientPicker {
    private final MainActivity activity;

    GradientPicker(MainActivity activity) {
        this.activity = activity;
    }

    void show() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout dim = new FrameLayout(activity);
        dim.setBackgroundColor(Color.argb(165, 0, 0, 0));
        dim.setPadding(activity.dp(54), activity.dp(38), activity.dp(54), activity.dp(38));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(activity.dp(30), activity.dp(24), activity.dp(30), activity.dp(24));
        panel.setBackground(activity.dialogUtils.actionPanelBackground());
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(activity.dp(1080), screenW - activity.dp(activity.screenWidthDp < 600 ? 40 : 108));
        int panelHeight = Math.min(activity.dp(616), screenH - activity.dp(activity.screenWidthDp < 600 ? 50 : 64));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.CENTER);
        dim.addView(panel, panelLp);

        TextView title = new TextView(activity);
        title.setText("Custom Gradient");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(42)));

        TextView hint = new TextView(activity);
        hint.setText("D-pad to preview - OK to apply - Back to cancel");
        hint.setTextColor(Color.argb(150, 255, 255, 255));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, activity.dp(28)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, activity.dp(10), 0, activity.dp(18));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        GridLayout grid = new GridLayout(activity);
        int columns = panelWidth >= activity.dp(980) ? 5 : 4;
        grid.setColumnCount(columns);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, activity.dp(28));
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));

        List<View> previews = new ArrayList<>();
        String selectedId = activity.prefs.getString("gradient_id", "emerald_night");
        String originalMode = activity.prefs.getString("bg_mode", "light");
        String originalId = selectedId;
        boolean[] applied = new boolean[]{false};
        int cardWidth = Math.max(activity.dp(160), (panelWidth - activity.dp(60) - activity.dp(16) * columns) / columns);
        int cardHeight = Math.round(cardWidth * 0.52f);
        View[] selectedView = new View[1];

        for (MainActivity.GradientPreset preset : gradientPresets()) {
            boolean selected = preset.id.equals(selectedId);
            FrameLayout preview = new FrameLayout(activity);
            preview.setBackground(gradientDrawable(preset, selected, false));
            preview.setFocusable(true);
            preview.setClickable(true);
            preview.setClipChildren(false);
            preview.setClipToPadding(false);

            TextView name = new TextView(activity);
            name.setText(preset.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(13);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.BOTTOM | Gravity.LEFT);
            name.setPadding(activity.dp(12), 0, activity.dp(38), activity.dp(10));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            preview.addView(name, new FrameLayout.LayoutParams(-1, -1));

            TextView check = new TextView(activity);
            check.setText(selected ? String.valueOf((char) 0x2713) : "");
            check.setTextColor(Color.WHITE);
            check.setTextSize(22);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(activity.dp(34), activity.dp(34), Gravity.RIGHT | Gravity.TOP);
            checkLp.setMargins(0, activity.dp(5), activity.dp(6), 0);
            preview.addView(check, checkLp);
            if (selected) selectedView[0] = preview;

            preview.setOnFocusChangeListener((v, hasFocus) -> {
                v.setBackground(gradientDrawable(preset, selected, hasFocus));
                v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(130).start();
                if (hasFocus) {
                    scroll.post(() -> scroll.requestChildRectangleOnScreen(grid,
                            new android.graphics.Rect(v.getLeft() - activity.dp(12), v.getTop() - activity.dp(12),
                                    v.getRight() + activity.dp(12), v.getBottom() + activity.dp(28)), false));
                    if (activity.launcherRoot != null) activity.launcherRoot.setBackground(gradientDrawable(preset, false, false));
                }
            });
            preview.setOnClickListener(v -> {
                applied[0] = true;
                activity.prefs.edit().putString("bg_mode", "gradient").putString("gradient_id", preset.id).apply();
                dialog.dismiss();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardWidth;
            lp.height = cardHeight;
            lp.setMargins(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8));
            grid.addView(preview, lp);
            previews.add(preview);
        }

        dialog.setContentView(dim);
        dialog.setOnShowListener(d -> {
            View first = selectedView[0] != null ? selectedView[0] : (previews.isEmpty() ? null : previews.get(0));
            if (first != null) first.requestFocus();
        });
        dialog.setOnDismissListener(d -> {
            if (!applied[0]) {
                activity.prefs.edit().putString("bg_mode", originalMode).putString("gradient_id", originalId).apply();
            }
            activity.showSettings("简介");
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    GradientDrawable currentGradientDrawable() {
        String id = activity.prefs.getString("gradient_id", "emerald_night");
        for (MainActivity.GradientPreset preset : gradientPresets()) {
            if (preset.id.equals(id)) return gradientDrawable(preset, false, false);
        }
        return gradientDrawable(gradientPresets().get(0), false, false);
    }

    private GradientDrawable gradientDrawable(MainActivity.GradientPreset preset, boolean selected, boolean focused) {
        GradientDrawable.Orientation orientation = preset.vertical
                ? GradientDrawable.Orientation.TOP_BOTTOM
                : GradientDrawable.Orientation.TL_BR;
        GradientDrawable bg = new GradientDrawable(orientation, preset.colors);
        bg.setCornerRadius(activity.dp(16));
        bg.setStroke(focused ? activity.dp(3) : (selected ? activity.dp(2) : activity.dp(1)),
                focused ? Color.WHITE : (selected ? Color.argb(220, 255, 255, 255) : Color.argb(55, 255, 255, 255)));
        return bg;
    }

    private List<MainActivity.GradientPreset> gradientPresets() {
        List<MainActivity.GradientPreset> items = new ArrayList<>();
        items.add(new MainActivity.GradientPreset("emerald_night", "Emerald Night", false, "#102B24", "#185E4C", "#6BD08D"));
        items.add(new MainActivity.GradientPreset("ocean_blue", "Ocean Blue", false, "#0C2B5E", "#1D79D8", "#7FD9FF"));
        items.add(new MainActivity.GradientPreset("midnight_purple", "Midnight Purple", false, "#17132F", "#4B2B84", "#A765FF"));
        items.add(new MainActivity.GradientPreset("sunset_orange", "Sunset Orange", false, "#32170E", "#E75E35", "#FFD37A"));
        items.add(new MainActivity.GradientPreset("aurora_green", "Aurora Green", false, "#062819", "#35B978", "#B5F37D"));
        items.add(new MainActivity.GradientPreset("deep_space", "Deep Space", false, "#050816", "#132B54", "#6D4BD8"));
        items.add(new MainActivity.GradientPreset("rose_night", "Rose Night", false, "#27101A", "#8D3159", "#F08AB5"));
        items.add(new MainActivity.GradientPreset("cyber_blue", "Cyber Blue", false, "#061B2F", "#0877B8", "#4FF3E8"));
        items.add(new MainActivity.GradientPreset("forest_mist", "Forest Mist", true, "#173522", "#5B8C63", "#C7E5C2"));
        items.add(new MainActivity.GradientPreset("black_gold", "Black Gold", false, "#090806", "#53431C", "#F2C64E"));
        items.add(new MainActivity.GradientPreset("arctic_blue", "Arctic Blue", true, "#0D3556", "#83C8E8", "#F4FCFF"));
        items.add(new MainActivity.GradientPreset("lavender_dream", "Lavender Dream", false, "#282045", "#8B79D9", "#F0D6FF"));
        items.add(new MainActivity.GradientPreset("teal_shadow", "Teal Shadow", false, "#08272B", "#1E8085", "#85E0D8"));
        items.add(new MainActivity.GradientPreset("crimson_night", "Crimson Night", false, "#240909", "#8F1C28", "#F06666"));
        items.add(new MainActivity.GradientPreset("indigo_glow", "Indigo Glow", false, "#10163D", "#395BE7", "#86C4FF"));
        items.add(new MainActivity.GradientPreset("bronze_dark", "Bronze Dark", false, "#20120B", "#8C5732", "#DFA66B"));
        items.add(new MainActivity.GradientPreset("sky_horizon", "Sky Horizon", true, "#215A93", "#B9E4FF", "#F5D497"));
        items.add(new MainActivity.GradientPreset("mint_glass", "Mint Glass", false, "#163B39", "#80D5BB", "#E8FFF5"));
        items.add(new MainActivity.GradientPreset("volcano", "Volcano", false, "#1B0804", "#B52A19", "#FFAB40"));
        items.add(new MainActivity.GradientPreset("moonlight", "Moonlight", true, "#0B1020", "#354C6F", "#C6D4EF"));
        items.add(new MainActivity.GradientPreset("tropical_sea", "Tropical Sea", false, "#063E4B", "#13B5A7", "#B6F78E"));
        items.add(new MainActivity.GradientPreset("graphite", "Graphite", false, "#111111", "#3B4650", "#9BA8B4"));
        items.add(new MainActivity.GradientPreset("neon_purple", "Neon Purple", false, "#16072C", "#9C27FF", "#00D5FF"));
        items.add(new MainActivity.GradientPreset("deep_emerald", "Deep Emerald", false, "#041B16", "#0E5A49", "#2DE0A5"));
        return items;
    }
}