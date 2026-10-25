package com.cleantablet.launcher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HomeActivity extends Activity {
    private static final String PREFS = "home";
    private static final String FAVORITES = "favorites";
    private final List<LaunchableApp> allApps = new ArrayList<>();
    private AppGridAdapter adapter;
    private EditText search;
    private TextView empty;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        GridView grid = findViewById(R.id.apps);
        adapter = new AppGridAdapter(this);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(adapter.getItem(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleFavorite(adapter.getItem(position));
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.wallpaper).setOnClickListener(view -> chooseWallpaper());
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        Set<String> favorites = preferences.getStringSet(FAVORITES, new HashSet<>());
        List<LaunchableApp> loaded = new ArrayList<>();
        for (ResolveInfo item : resolved) {
            if (item.activityInfo == null || getPackageName().equals(item.activityInfo.packageName)) continue;
            String pkg = item.activityInfo.packageName;
            String label = String.valueOf(item.loadLabel(pm));
            loaded.add(new LaunchableApp(label, pkg,
                    new ComponentName(pkg, item.activityInfo.name), item.loadIcon(pm), favorites.contains(pkg)));
        }
        Collator collator = Collator.getInstance();
        Collections.sort(loaded, (a, b) -> {
            if (a.favorite != b.favorite) return a.favorite ? -1 : 1;
            int label = collator.compare(a.label, b.label);
            return label != 0 ? label : a.packageName.compareTo(b.packageName);
        });
        allApps.clear();
        allApps.addAll(loaded);
        filter(search.getText().toString());
    }

    private void filter(String text) {
        String needle = text.trim().toLowerCase(Locale.getDefault());
        List<LaunchableApp> shown = new ArrayList<>();
        for (LaunchableApp app : allApps) {
            if (needle.isEmpty() || app.label.toLowerCase(Locale.getDefault()).contains(needle)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) shown.add(app);
        }
        adapter.replace(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void launch(LaunchableApp app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Could not open " + app.label, Toast.LENGTH_SHORT).show();
            loadApps();
        }
    }

    private void toggleFavorite(LaunchableApp app) {
        Set<String> favorites = new HashSet<>(preferences.getStringSet(FAVORITES, new HashSet<>()));
        boolean added;
        if (favorites.contains(app.packageName)) {
            favorites.remove(app.packageName);
            added = false;
        } else {
            favorites.add(app.packageName);
            added = true;
        }
        preferences.edit().putStringSet(FAVORITES, favorites).apply();
        Toast.makeText(this, getString(added ? R.string.favorite_added : R.string.favorite_removed, app.label),
                Toast.LENGTH_SHORT).show();
        loadApps();
    }

    private void chooseWallpaper() {
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Wallpaper settings are unavailable on this firmware", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
