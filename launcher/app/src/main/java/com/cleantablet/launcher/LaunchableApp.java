package com.cleantablet.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

final class LaunchableApp {
    final String label;
    final String packageName;
    final ComponentName component;
    final Drawable icon;
    boolean favorite;

    LaunchableApp(String label, String packageName, ComponentName component, Drawable icon, boolean favorite) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
        this.favorite = favorite;
    }
}
