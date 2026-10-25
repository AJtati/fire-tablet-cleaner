package com.cleantablet.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;

public final class LauncherAccessibilityService extends AccessibilityService {
    private static final String TAG = "LauncherA11y";
    private static final String FIRE_LAUNCHER = "com.amazon.firelauncher";
    private static volatile LauncherAccessibilityService sInstance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int redirectAttempts = 0;

    private WindowManager windowManager;
    private View navBarOverlay;
    private View buttonsContainer;
    private View handleContainer;
    private WindowManager.LayoutParams overlayParams;

    private boolean isHomeScreen = true;
    private boolean isExpanded = true;
    private int expandedHeightPx;
    private int collapsedHeightPx;

    private final Runnable autoCollapseRunnable = this::collapseNavBar;

    private final Runnable redirectWatchdog = new Runnable() {
        @Override
        public void run() {
            launchCleanHome();
            redirectAttempts++;
            if (redirectAttempts < 10) {
                handler.postDelayed(this, 60L);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.i(TAG, "LauncherAccessibilityService connected");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 20;
        setServiceInfo(info);

        handler.post(this::setupNavBarOverlay);
    }

    private void setupNavBarOverlay() {
        if (navBarOverlay != null) return;
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;

            LayoutInflater inflater = LayoutInflater.from(this);
            navBarOverlay = inflater.inflate(R.layout.overlay_nav_bar, null);

            buttonsContainer = navBarOverlay.findViewById(R.id.nav_buttons_container);
            handleContainer = navBarOverlay.findViewById(R.id.nav_handle_container);

            int overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

            expandedHeightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 46, getResources().getDisplayMetrics());
            collapsedHeightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());

            overlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    expandedHeightPx,
                    overlayType,
                    flags,
                    PixelFormat.TRANSLUCENT);

            overlayParams.gravity = Gravity.BOTTOM;

            ImageButton btnBack = navBarOverlay.findViewById(R.id.btn_nav_back);
            ImageButton btnHome = navBarOverlay.findViewById(R.id.btn_nav_home);
            ImageButton btnRecents = navBarOverlay.findViewById(R.id.btn_nav_recents);

            btnBack.setOnClickListener(v -> {
                triggerBack();
                if (!isHomeScreen) {
                    scheduleAutoCollapse(1200L);
                }
            });

            btnHome.setOnClickListener(v -> {
                isHomeScreen = true;
                expandNavBar();
                launchCleanHome();
            });

            btnRecents.setOnClickListener(v -> {
                triggerRecents();
                if (!isHomeScreen) {
                    scheduleAutoCollapse(3000L);
                }
            });

            // Swipe up or tap on the bottom handle to reveal full navigation bar
            handleContainer.setOnTouchListener(new View.OnTouchListener() {
                private float startY = 0;
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            if (startY - event.getRawY() > 8) { // Swiped upwards
                                expandNavBar();
                                return true;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                            expandNavBar();
                            return true;
                    }
                    return true;
                }
            });

            // Tap outside the expanded bar collapses it back in other apps
            navBarOverlay.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    if (!isHomeScreen && isExpanded) {
                        collapseNavBar();
                        return true;
                    }
                }
                return false;
            });

            windowManager.addView(navBarOverlay, overlayParams);
            Log.i(TAG, "Custom navigation bar overlay added successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error displaying navigation bar overlay", e);
        }
    }

    private void expandNavBar() {
        if (windowManager == null || navBarOverlay == null || overlayParams == null) return;
        handler.removeCallbacks(autoCollapseRunnable);
        isExpanded = true;
        if (buttonsContainer != null) buttonsContainer.setVisibility(View.VISIBLE);
        if (handleContainer != null) handleContainer.setVisibility(View.GONE);
        overlayParams.height = expandedHeightPx;
        try {
            windowManager.updateViewLayout(navBarOverlay, overlayParams);
        } catch (Exception ignored) {}

        if (!isHomeScreen) {
            scheduleAutoCollapse(3500L);
        }
    }

    private void collapseNavBar() {
        if (windowManager == null || navBarOverlay == null || overlayParams == null) return;
        if (isHomeScreen) return; // Keep visible on home screen
        handler.removeCallbacks(autoCollapseRunnable);
        isExpanded = false;
        if (buttonsContainer != null) buttonsContainer.setVisibility(View.GONE);
        if (handleContainer != null) handleContainer.setVisibility(View.VISIBLE);
        overlayParams.height = collapsedHeightPx;
        try {
            windowManager.updateViewLayout(navBarOverlay, overlayParams);
        } catch (Exception ignored) {}
    }

    private void scheduleAutoCollapse(long delayMs) {
        handler.removeCallbacks(autoCollapseRunnable);
        handler.postDelayed(autoCollapseRunnable, delayMs);
    }

    private void removeNavBarOverlay() {
        if (windowManager != null && navBarOverlay != null) {
            try {
                windowManager.removeView(navBarOverlay);
            } catch (Exception ignored) {}
            navBarOverlay = null;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        handler.removeCallbacksAndMessages(null);
        removeNavBarOverlay();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        handler.removeCallbacksAndMessages(null);
        removeNavBarOverlay();
        super.onDestroy();
    }

    public static boolean triggerRecents() {
        LauncherAccessibilityService service = sInstance;
        if (service != null) {
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS);
        }
        return false;
    }

    public static boolean triggerHome() {
        LauncherAccessibilityService service = sInstance;
        if (service != null) {
            return service.performGlobalAction(GLOBAL_ACTION_HOME);
        }
        return false;
    }

    public static boolean triggerBack() {
        LauncherAccessibilityService service = sInstance;
        if (service != null) {
            return service.performGlobalAction(GLOBAL_ACTION_BACK);
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        if (FIRE_LAUNCHER.contentEquals(pkg)) {
            Log.i(TAG, "Detected Fire Launcher (" + event.getEventType() + "), redirecting to Clean Home...");
            redirectAttempts = 0;
            handler.removeCallbacks(redirectWatchdog);
            redirectWatchdog.run();
            return;
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            boolean nowHome = getPackageName().contentEquals(pkg);
            if (nowHome != isHomeScreen) {
                isHomeScreen = nowHome;
                if (isHomeScreen) {
                    expandNavBar();
                } else {
                    collapseNavBar();
                }
            }
        }
    }

    private void launchCleanHome() {
        Intent home = new Intent(this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Bundle opts = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();

        try {
            startActivity(home, opts);
        } catch (Exception ignored) {}

        try {
            PendingIntent pi = PendingIntent.getActivity(
                    this, 0, home, PendingIntent.FLAG_UPDATE_CURRENT, opts);
            pi.send();
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }
}
