package com.cleantablet.launcher;

import android.content.Context;
import android.graphics.Color;
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
    private final Context context;
    private final List<LaunchableApp> apps = new ArrayList<>();

    AppGridAdapter(Context context) {
        this.context = context;
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
            card.setPadding(dp(4), dp(6), dp(4), dp(4));
            card.setMinimumHeight(dp(104));
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));
            TextView label = new TextView(context);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setTextColor(Color.WHITE);
            label.setTextSize(12);
            label.setShadowLayer(3f, 0f, dp(1), 0xCC000000);
            LinearLayout.LayoutParams text = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            text.topMargin = dp(6);
            card.addView(label, text);
            holder = new Holder(icon, label);
            card.setTag(holder);
            recycled = card;
        } else {
            holder = (Holder) recycled.getTag();
        }
        LaunchableApp app = getItem(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        recycled.setBackgroundColor(Color.TRANSPARENT);
        recycled.setContentDescription(app.label + (app.favorite ? ", favourite" : ""));
        return recycled;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label;
        Holder(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }
}
