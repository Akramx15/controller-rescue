package local.quest.controllerrescue;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.KeyEvent;
import android.os.PowerManager;
import android.os.SystemClock;

/** Key releases are observed, never consumed. Each volume key has its own pair. */
public class VolumeService extends AccessibilityService {
    static volatile boolean connected;
    @Override protected void onServiceConnected() { super.onServiceConnected(); connected = true; }
    @Override public boolean onUnbind(android.content.Intent intent) { connected = false; return super.onUnbind(intent); }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
    private final long[] first = {-1, -1};
    private final boolean[] repeated = {false, false};

    @Override public boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        int index = key == KeyEvent.KEYCODE_VOLUME_UP ? 0 : 1;
        String preference = index == 0 ? "shortcut" : "shortcutDown";
        if (!getSharedPreferences("prefs", 0).getBoolean(preference, false)
                || !((PowerManager) getSystemService(POWER_SERVICE)).isInteractive()) {
            first[index] = -1;
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) repeated[index] = false;
        if (event.getRepeatCount() > 0) {
            first[index] = -1;
            repeated[index] = true;
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && (repeated[index] || event.isCanceled())) {
            first[index] = -1;
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            long now = SystemClock.elapsedRealtime();
            if (first[index] >= 0 && now - first[index] <= 450) {
                first[index] = -1;
                String source = index == 0 ? "double-volume-up" : "double-volume-down";
                if (getSharedPreferences("prefs", 0).getBoolean("test", false)) recordTest(this, source);
                else RecoveryService.request(this, source);
            } else first[index] = now;
        }
        return false;
    }

    static void recordTest(android.content.Context context, String source) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("prefs", 0);
        String key = "testCount-" + source;
        int count = prefs.getInt(key, 0) + 1;
        prefs.edit().putInt(key, count).putString("status", "Shortcut detected " + source
                + " #" + count + " at " + new java.util.Date()
                + ". Test only: no repair command executed.").apply();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { first[0] = first[1] = -1; }
}
