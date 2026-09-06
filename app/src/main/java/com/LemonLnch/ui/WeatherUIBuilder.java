package com.LemonLnch.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.LemonLnch.R;

import java.util.Locale;

public class WeatherUIBuilder {
    private final MainActivity activity;

    WeatherUIBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build() {
        FrameLayout root = new FrameLayout(activity);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView weatherBackground = new ImageView(activity);
        weatherBackground.setImageResource(R.drawable.weather_scene);
        weatherBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(weatherBackground, new FrameLayout.LayoutParams(-1, -1));

        View weatherWash = new View(activity);
        weatherWash.setBackground(weatherDetailWash());
        root.addView(weatherWash, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        int padH = activity.horizontalPaddingDp();
        page.setPadding(activity.dp(padH), activity.dp(42), activity.dp(padH), activity.dp(34));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, activity.dp(58)));

        TextView back = chip("Back", false);
        back.setOnClickListener(v -> activity.showHome());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(activity.dp(100), activity.dp(46));
        backLp.rightMargin = activity.dp(18);
        top.addView(back, backLp);

        TextView title = new TextView(activity);
        title.setText("Weather");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextClock clock = new TextClock(activity);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour(activity.prefs.getBoolean("use_24h", true) ? "HH:mm" : "h:mm");
        clock.setGravity(Gravity.CENTER);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(24);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setBackgroundResource(R.drawable.glass_chip);
        top.addView(clock, new LinearLayout.LayoutParams(activity.dp(118), activity.dp(46)));

        FrameLayout hero = new FrameLayout(activity);
        hero.setClipChildren(false);
        hero.setClipToPadding(false);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, 0, 1);
        heroLp.topMargin = activity.dp(18);
        page.addView(hero, heroLp);

        LinearLayout summary = new LinearLayout(activity);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setGravity(Gravity.LEFT);
        summary.setPadding(activity.dp(40), activity.dp(24), activity.dp(40), activity.dp(150));
        summary.setClipChildren(false);
        summary.setClipToPadding(false);
        hero.addView(summary, new FrameLayout.LayoutParams(-1, -1));

        TextView city = new TextView(activity);
        city.setText("Batu Pahat, MY");
        city.setTextColor(Color.argb(220, 255, 255, 255));
        city.setTextSize(19);
        city.setIncludeFontPadding(false);
        city.setShadowLayer(activity.dp(2), 0, activity.dp(1), Color.argb(120, 0, 0, 0));
        summary.addView(city, new LinearLayout.LayoutParams(-1, activity.dp(30)));

        LinearLayout headline = new LinearLayout(activity);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        headline.setClipChildren(false);
        headline.setClipToPadding(false);
        summary.addView(headline, new LinearLayout.LayoutParams(-1, activity.dp(110)));

        TextView temp = new TextView(activity);
        temp.setText("31°C");
        temp.setTextColor(Color.WHITE);
        temp.setTextSize(activity.screenWidthDp < 600 ? 60 : 78);
        temp.setTypeface(Typeface.DEFAULT_BOLD);
        temp.setIncludeFontPadding(false);
        temp.setShadowLayer(activity.dp(4), 0, activity.dp(3), Color.argb(120, 0, 0, 0));
        headline.addView(temp, new LinearLayout.LayoutParams(-2, -1));

        ImageView detailIcon = new ImageView(activity);
        detailIcon.setImageResource(weatherIconResource("Broken Clouds"));
        detailIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams detailIconLp = new LinearLayout.LayoutParams(activity.dp(100), activity.dp(100));
        detailIconLp.leftMargin = activity.dp(24);
        detailIconLp.rightMargin = activity.dp(16);
        headline.addView(detailIcon, detailIconLp);

        TextView condition = new TextView(activity);
        condition.setText("Broken Clouds");
        condition.setTextColor(Color.WHITE);
        condition.setTextSize(30);
        condition.setTypeface(Typeface.DEFAULT_BOLD);
        condition.setSingleLine(true);
        condition.setIncludeFontPadding(false);
        condition.setShadowLayer(activity.dp(3), 0, activity.dp(2), Color.argb(120, 0, 0, 0));
        headline.addView(condition, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout metricLine1 = new LinearLayout(activity);
        metricLine1.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(metricLine1, new LinearLayout.LayoutParams(activity.dp(600), activity.dp(36)));
        addWeatherInfo(metricLine1, "Feels like : 35°C");
        addWeatherInfo(metricLine1, "Humidity : 74%");

        LinearLayout metricLine2 = new LinearLayout(activity);
        metricLine2.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(metricLine2, new LinearLayout.LayoutParams(activity.dp(600), activity.dp(36)));
        addWeatherInfo(metricLine2, "Wind : 9 km/h");
        addWeatherInfo(metricLine2, "Visibility : 10 km");

        LinearLayout forecast = new LinearLayout(activity);
        forecast.setGravity(Gravity.CENTER);
        forecast.setPadding(activity.dp(28), activity.dp(8), activity.dp(28), activity.dp(10));
        forecast.setClipChildren(false);
        forecast.setClipToPadding(false);
        forecast.setBackground(weatherForecastBackground());
        FrameLayout.LayoutParams forecastLp = new FrameLayout.LayoutParams(-1, activity.dp(150), Gravity.BOTTOM);
        forecastLp.leftMargin = activity.dp(28);
        forecastLp.rightMargin = activity.dp(28);
        forecastLp.bottomMargin = activity.dp(4);
        hero.addView(forecast, forecastLp);
        addForecastTile(forecast, "Today", "Clouds", "31°C / 25°C");
        addForecastTile(forecast, "Tue", "Rain", "30°C / 24°C");
        addForecastTile(forecast, "Wed", "Partly", "32°C / 25°C");
        addForecastTile(forecast, "Thu", "Clouds", "31°C / 25°C");

        activity.setContentView(root);
        back.requestFocus();
    }

    int weatherIconResource(String condition) {
        String c = condition == null ? "" : condition.toLowerCase(Locale.ROOT);
        if (c.contains("storm") || c.contains("thunder")) return R.drawable.ic_weather_storm;
        if (c.contains("night") && c.contains("rain")) return R.drawable.ic_weather_night_rain;
        if (c.contains("night") || c.contains("moon")) return R.drawable.ic_weather_night;
        if (c.contains("heavy") && c.contains("rain")) return R.drawable.ic_weather_heavy_rain;
        if (c.contains("rain")) return R.drawable.ic_weather_rain;
        if (c.contains("wind") || c.contains("fog") || c.contains("mist")) return R.drawable.ic_weather_wind;
        if (c.contains("sun") || c.contains("clear")) return R.drawable.ic_weather_sun;
        if (c.contains("part")) return R.drawable.ic_weather_partly;
        if (c.contains("broken") || c.contains("clouds")) return R.drawable.ic_weather_clouds;
        if (c.contains("cloud")) return R.drawable.ic_weather_cloud;
        return R.drawable.ic_weather_partly;
    }

    private GradientDrawable weatherDetailWash() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(165, 94, 146, 160),
                        Color.argb(120, 113, 158, 172),
                        Color.argb(75, 44, 72, 82)
                });
        bg.setCornerRadius(activity.dp(0));
        return bg;
    }

    private GradientDrawable weatherForecastBackground() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(128, 130, 174, 188),
                        Color.argb(116, 72, 119, 136)
                });
        bg.setCornerRadius(activity.dp(16));
        bg.setStroke(activity.dp(1), Color.argb(32, 255, 255, 255));
        return bg;
    }

    private void addWeatherInfo(LinearLayout parent, String text) {
        TextView item = new TextView(activity);
        item.setText(text);
        item.setTextColor(Color.argb(230, 255, 255, 255));
        item.setTextSize(18);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setIncludeFontPadding(false);
        item.setShadowLayer(activity.dp(2), 0, activity.dp(1), Color.argb(110, 0, 0, 0));
        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addForecastTile(LinearLayout parent, String day, String condition, String temp) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(activity.dp(10), activity.dp(6), activity.dp(10), activity.dp(6));
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        TextView dayView = new TextView(activity);
        dayView.setText(day);
        dayView.setTextColor(Color.WHITE);
        dayView.setTextSize(17);
        dayView.setTypeface(Typeface.DEFAULT_BOLD);
        dayView.setGravity(Gravity.CENTER);
        dayView.setIncludeFontPadding(false);
        dayView.setShadowLayer(activity.dp(2), 0, activity.dp(1), Color.argb(115, 0, 0, 0));
        tile.addView(dayView, new LinearLayout.LayoutParams(-1, activity.dp(28)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(weatherIconResource(condition));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(-1, activity.dp(78)));
        TextView tempView = new TextView(activity);
        tempView.setText(temp);
        tempView.setTextColor(Color.WHITE);
        tempView.setTextSize(16);
        tempView.setTypeface(Typeface.DEFAULT_BOLD);
        tempView.setGravity(Gravity.CENTER);
        tempView.setSingleLine(true);
        tempView.setIncludeFontPadding(false);
        tempView.setShadowLayer(activity.dp(2), 0, activity.dp(1), Color.argb(115, 0, 0, 0));
        tile.addView(tempView, new LinearLayout.LayoutParams(-1, activity.dp(28)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = activity.dp(8);
        lp.rightMargin = activity.dp(8);
        parent.addView(tile, lp);
    }

    private TextView chip(String text, boolean primary) {
        TextView chip = new TextView(activity);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setTextSize(primary ? 19 : 16);
        chip.setTextColor(primary ? Color.rgb(18, 18, 18) : Color.WHITE);
        chip.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setBackgroundResource(primary ? R.drawable.light_chip : R.drawable.glass_chip);
        chip.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.06f));
        return chip;
    }

    private void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? activity.dp(12) : 0).setDuration(130).start();
    }
}