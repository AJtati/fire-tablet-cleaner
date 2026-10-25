package com.cleantablet.launcher;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class AppGridAdapter extends BaseAdapter {
    interface OnAppActionListener {
        void onAppClick(LaunchableApp app);
        void onAppLongClick(LaunchableApp app);
    }

    private final Context context;
    private final List<LaunchableApp> apps = new ArrayList<>();
    private final int rippleResId;
    private final OnAppActionListener listener;

    AppGridAdapter(Context context, OnAppActionListener listener) {
        this.context = context;
        this.listener = listener;
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
            rippleResId = value.resourceId;
        } else {
            rippleResId = 0;
        }
    }

    void replace(List<LaunchableApp> next) {
        apps.clear();
        apps.addAll(next);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return apps.size(); }
    @Override public LaunchableApp getItem(int position) { return apps.get(position); }
    @Override public long getItemId(int position) { return position; }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        Holder holder;
        if (recycled == null) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(4), dp(8), dp(4), dp(6));
            card.setMinimumHeight(dp(92));
            if (rippleResId != 0) {
                card.setBackgroundResource(rippleResId);
            }

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView label = new TextView(context);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(Color.WHITE);
            label.setTextSize(11.5f);
            label.setShadowLayer(3f, 0f, dp(1), 0xAA000000);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dp(6);
            card.addView(label, textParams);

            holder = new Holder(icon, label);
            card.setTag(holder);
            recycled = card;
        } else {
            holder = (Holder) recycled.getTag();
        }

        LaunchableApp app = getItem(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        recycled.setContentDescription(app.label + (app.favorite ? ", favourite" : ""));

        if (listener != null) {
            recycled.setOnClickListener(v -> listener.onAppClick(app));
            recycled.setOnLongClickListener(v -> {
                listener.onAppLongClick(app);
                return true;
            });
        }

        return recycled;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label;
        Holder(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }
}
