# Keep Android components and AccessibilityService lifecycle methods
-keep public class * extends android.app.Activity {
    public protected *;
}
-keep public class * extends android.app.Service {
    public protected *;
}
-keep public class * extends android.accessibilityservice.AccessibilityService {
    public protected *;
}
