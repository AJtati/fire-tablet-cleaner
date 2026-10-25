package com.cleantablet.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(8), dp(10), dp(8), dp(8));
            card.setMinimumHeight(dp(118));
            ImageView icon = new ImageView(context);
            card.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            TextView label = new TextView(context);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setTextColor(Color.WHITE);
            label.setTextSize(13);
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
        holder.label.setText(app.favorite ? "★ " + app.label : app.label);
        holder.label.setTypeface(null, app.favorite ? Typeface.BOLD : Typeface.NORMAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(app.favorite ? 0xE6334A4A : 0xD92A2E37);
        background.setCornerRadius(dp(18));
        recycled.setBackground(background);
        recycled.setContentDescription(app.label + (app.favorite ? ", favourite" : ""));
        return recycled;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label;
        Holder(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }
}
