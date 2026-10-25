package com.cleantablet.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HomeActivity extends Activity {
    private static final String PREFS = "home";
    private static final String KEY_GRID_ORDER = "grid_order_list";
    private static final String KEY_DOCK_ORDER = "dock_order_list";
    private static final int REQUEST_PICK_WALLPAPER = 1001;

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
        adapter = new AppGridAdapter(this, new AppGridAdapter.OnAppActionListener() {
            @Override
            public void onAppClick(LaunchableApp app) {
                launch(app);
            }

            @Override
            public void onAppLongClick(LaunchableApp app) {
                showGridAppOptions(app);
            }
        });
        grid.setAdapter(adapter);

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

    private List<String> getSavedOrder(String key) {
        String saved = preferences.getString(key, "");
        List<String> list = new ArrayList<>();
        if (!saved.isEmpty()) {
            for (String pkg : saved.split(",")) {
                String clean = pkg.trim();
                if (!clean.isEmpty() && !list.contains(clean)) {
                    list.add(clean);
                }
            }
        }
        return list;
    }

    private void saveOrder(String key, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        preferences.edit().putString(key, sb.toString()).apply();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        List<String> gridOrder = getSavedOrder(KEY_GRID_ORDER);

        List<LaunchableApp> loaded = new ArrayList<>();
        for (ResolveInfo item : resolved) {
            if (item.activityInfo == null || getPackageName().equals(item.activityInfo.packageName)) continue;
            String pkg = item.activityInfo.packageName;
            if ("com.amazon.kindle.kso".equals(pkg) || "com.amazon.csapp".equals(pkg) || "com.amazon.webapp".equals(pkg)) continue;
            String label = String.valueOf(item.loadLabel(pm));
            loaded.add(new LaunchableApp(label, pkg,
                    new ComponentName(pkg, item.activityInfo.name), item.loadIcon(pm), dockOrder.contains(pkg)));
        }

        // Sort apps according to user-defined grid order; unlisted apps placed at end
        Collator collator = Collator.getInstance();
        Collections.sort(loaded, (a, b) -> {
            int indexA = gridOrder.indexOf(a.packageName);
            int indexB = gridOrder.indexOf(b.packageName);
            if (indexA != -1 && indexB != -1) return Integer.compare(indexA, indexB);
            if (indexA != -1) return -1;
            if (indexB != -1) return 1;
            int labelComp = collator.compare(a.label, b.label);
            return labelComp != 0 ? labelComp : a.packageName.compareTo(b.packageName);
        });

        // Persist complete grid order
        List<String> updatedGridOrder = new ArrayList<>();
        for (LaunchableApp app : loaded) {
            updatedGridOrder.add(app.packageName);
        }
        saveOrder(KEY_GRID_ORDER, updatedGridOrder);

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
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);

        // If dock order is uninitialized, seed with sensible defaults
        if (dockOrder.isEmpty()) {
            String[] defaults = new String[] {
                    "com.android.settings", "com.android.documentsui",
                    "com.android.gallery3d", "com.amazon.avod", "com.amazon.venezia"
            };
            for (String d : defaults) {
                for (LaunchableApp app : allApps) {
                    if (d.equals(app.packageName) && !dockOrder.contains(d)) {
                        dockOrder.add(d);
                        break;
                    }
                }
            }
            if (dockOrder.isEmpty() && !allApps.isEmpty()) {
                for (int i = 0; i < Math.min(5, allApps.size()); i++) {
                    dockOrder.add(allApps.get(i).packageName);
                }
            }
            saveOrder(KEY_DOCK_ORDER, dockOrder);
        }

        // Update favorite flags
        for (LaunchableApp app : allApps) {
            app.favorite = dockOrder.contains(app.packageName);
        }

        // Gather dock apps in exact dockOrder
        List<LaunchableApp> dockApps = new ArrayList<>();
        for (String pkg : dockOrder) {
            for (LaunchableApp app : allApps) {
                if (pkg.equals(app.packageName)) {
                    dockApps.add(app);
                    break;
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
                showDockAppOptions(app);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(54));
            params.setMargins(dp(8), 0, dp(8), 0);
            dock.addView(button, params);
        }
    }

    private void showGridAppOptions(LaunchableApp app) {
        List<String> gridOrder = getSavedOrder(KEY_GRID_ORDER);
        int currentIndex = gridOrder.indexOf(app.packageName);
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        boolean inDock = dockOrder.contains(app.packageName);

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (currentIndex > 0) {
            options.add("◀ Move Left");
            actions.add(() -> moveGridApp(app.packageName, currentIndex - 1));
        }
        if (currentIndex >= 0 && currentIndex < gridOrder.size() - 1) {
            options.add("▶ Move Right");
            actions.add(() -> moveGridApp(app.packageName, currentIndex + 1));
        }
        if (currentIndex > 0) {
            options.add("⬆ Move to Top");
            actions.add(() -> moveGridApp(app.packageName, 0));
        }
        if (currentIndex >= 0 && currentIndex < gridOrder.size() - 1) {
            options.add("⬇ Move to Bottom");
            actions.add(() -> moveGridApp(app.packageName, gridOrder.size() - 1));
        }

        if (inDock) {
            options.add("✖ Remove from Dock");
            actions.add(() -> removeDockApp(app.packageName));
        } else {
            options.add("⭐ Add to Dock");
            actions.add(() -> addDockApp(app.packageName));
        }

        options.add("ℹ App Info");
        actions.add(() -> openAppInfo(app.packageName));

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void showDockAppOptions(LaunchableApp app) {
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        int currentIndex = dockOrder.indexOf(app.packageName);

        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (currentIndex > 0) {
            options.add("◀ Move Left in Dock");
            actions.add(() -> moveDockApp(currentIndex, currentIndex - 1));
        }
        if (currentIndex >= 0 && currentIndex < dockOrder.size() - 1) {
            options.add("▶ Move Right in Dock");
            actions.add(() -> moveDockApp(currentIndex, currentIndex + 1));
        }

        options.add("✖ Remove from Dock");
        actions.add(() -> removeDockApp(app.packageName));

        options.add("ℹ App Info");
        actions.add(() -> openAppInfo(app.packageName));

        new AlertDialog.Builder(this)
                .setTitle(app.label + " (Dock)")
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .show();
    }

    private void moveGridApp(String packageName, int targetIndex) {
        List<String> gridOrder = getSavedOrder(KEY_GRID_ORDER);
        int currentIndex = gridOrder.indexOf(packageName);
        if (currentIndex != -1 && targetIndex >= 0 && targetIndex < gridOrder.size()) {
            gridOrder.remove(currentIndex);
            gridOrder.add(targetIndex, packageName);
            saveOrder(KEY_GRID_ORDER, gridOrder);
            loadApps();
        }
    }

    private void moveDockApp(int currentIndex, int targetIndex) {
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        if (currentIndex >= 0 && currentIndex < dockOrder.size()
                && targetIndex >= 0 && targetIndex < dockOrder.size()) {
            String item = dockOrder.remove(currentIndex);
            dockOrder.add(targetIndex, item);
            saveOrder(KEY_DOCK_ORDER, dockOrder);
            populateDock();
        }
    }

    private void addDockApp(String packageName) {
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        if (!dockOrder.contains(packageName)) {
            dockOrder.add(packageName);
            saveOrder(KEY_DOCK_ORDER, dockOrder);
            loadApps();
            Toast.makeText(this, "Added to dock", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeDockApp(String packageName) {
        List<String> dockOrder = getSavedOrder(KEY_DOCK_ORDER);
        if (dockOrder.remove(packageName)) {
            saveOrder(KEY_DOCK_ORDER, dockOrder);
            loadApps();
            Toast.makeText(this, "Removed from dock", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open app info", Toast.LENGTH_SHORT).show();
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

    private void chooseWallpaper() {
        CharSequence[] options = new CharSequence[] {
                getString(R.string.wallpaper_photos),
                getString(R.string.wallpaper_system)
        };
        new AlertDialog.Builder(this)
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
                InputStream is = getContentResolver().openInputStream(data.getData());
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
