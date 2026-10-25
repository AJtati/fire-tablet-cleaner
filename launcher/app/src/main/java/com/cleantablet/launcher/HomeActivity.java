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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    private TextView clock;
    private TextView date;
    private LinearLayout dock;
    private GridView grid;
    private SharedPreferences preferences;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            clockHandler.postDelayed(this, 10_000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        dock = findViewById(R.id.dock);
        grid = findViewById(R.id.apps);
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
    public void onBackPressed() {
        if (search != null && search.getText().length() > 0) {
            search.setText("");
            return;
        }
        if (grid != null) {
            grid.smoothScrollToPosition(0);
        }
        // Do not call super.onBackPressed() so pressing Back stays on this launcher
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (search != null && search.getText().length() > 0) {
            search.setText("");
        }
        if (grid != null) {
            grid.smoothScrollToPosition(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockTick);
        clockTick.run();
        loadApps();
    }

    @Override
    protected void onPause() {
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
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
            if ("com.amazon.kindle.kso".equals(pkg) || "com.amazon.csapp".equals(pkg) || "com.amazon.webapp".equals(pkg)) continue;
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
        populateDock();
        filter(search.getText().toString());
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now));
    }

    private void populateDock() {
        dock.removeAllViews();
        List<LaunchableApp> dockApps = new ArrayList<>();
        for (LaunchableApp app : allApps) {
            if (app.favorite) {
                dockApps.add(app);
            }
        }
        if (dockApps.isEmpty()) {
            for (LaunchableApp app : allApps) {
                if ("com.amazon.avod".equals(app.packageName)) {
                    dockApps.add(app);
                    break;
                }
            }
            for (LaunchableApp app : allApps) {
                if (dockApps.size() >= 5) break;
                if (!dockApps.contains(app)) {
                    dockApps.add(app);
                }
            }
        }

        for (LaunchableApp app : dockApps) {
            ImageButton button = new ImageButton(this);
            button.setImageDrawable(app.icon);
            button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            button.setPadding(dp(6), dp(6), dp(6), dp(6));
            button.setContentDescription(getString(R.string.open_favorite, app.label));
            TypedValue value = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)) {
                button.setBackgroundResource(value.resourceId);
            }
            button.setOnClickListener(view -> launch(app));
            button.setOnLongClickListener(view -> {
                toggleFavorite(app);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(54));
            params.setMargins(dp(8), 0, dp(8), 0);
            dock.addView(button, params);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void filter(String text) {
        String needle = text.trim().toLowerCase(Locale.getDefault());
        List<LaunchableApp> shown = new ArrayList<>();
        for (LaunchableApp app : allApps) {
            if (needle.isEmpty() || app.label.toLowerCase(Locale.getDefault()).contains(needle)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) shown.add(app);
        }
        adapter.replace(shown);
        boolean hasApps = !shown.isEmpty();
        grid.setVisibility(hasApps ? View.VISIBLE : View.GONE);
        empty.setVisibility(hasApps ? View.GONE : View.VISIBLE);
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

    private static final int REQUEST_PICK_WALLPAPER = 1001;

    private void chooseWallpaper() {
        CharSequence[] options = new CharSequence[] {
                getString(R.string.wallpaper_photos),
                getString(R.string.wallpaper_system)
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                            intent.setType("image/*");
                            startActivityForResult(Intent.createChooser(intent, getString(R.string.wallpaper)), REQUEST_PICK_WALLPAPER);
                        } catch (Exception e) {
                            Toast.makeText(this, "No image picker app found", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        try {
                            startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(this, "System wallpaper picker unavailable", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                if (is != null) {
                    WallpaperManager wm = WallpaperManager.getInstance(getApplicationContext());
                    wm.setStream(is);
                    is.close();
                    Toast.makeText(this, R.string.wallpaper_success, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Could not set wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
