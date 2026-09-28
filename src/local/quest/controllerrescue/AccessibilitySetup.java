package local.quest.controllerrescue;

import android.content.Context;
import java.io.File;
import java.util.Set;

/** Explicit user-requested opt-in; never changes shortcut toggles or sensor state. */
final class AccessibilitySetup {
    private static boolean inFlight;

    static synchronized void enable(Context context, Runnable completion) {
        Context app = context.getApplicationContext();
        if (inFlight) { completion.run(); return; }
        inFlight = true;
        Rescue.message(app, "Enabling volume access");
        new Thread(() -> {
            try {
                File dir = new File(app.getFilesDir(), "accessibility-setup");
                dir.mkdirs();
                Rescue.Result uid = Rescue.run(dir, "root", "id -u");
                if (uid.exit != 0 || !uid.text.trim().equals("0")) throw new Exception("Root unavailable");
                String user = AccessibilityPolicy.user(read(dir, "current-user", "am get-current-user"));
                if (Integer.parseInt(user) != android.os.Process.myUid() / 100000) {
                    throw new Exception("Current user differs from this app user; no changes made.");
                }
                String prefix = "settings --user " + user;
                String original = read(dir, "before", prefix + " get secure enabled_accessibility_services");
                Set<String> previous = AccessibilityPolicy.parse(original);
                String merged = AccessibilityPolicy.append(original);
                // Recheck immediately before writing so an observed concurrent change is not overwritten.
                if (!original.equals(read(dir, "recheck", prefix + " get secure enabled_accessibility_services"))) {
                    throw new Exception("Accessibility changed during setup; try again.");
                }
                read(dir, "write-list", prefix + " put secure enabled_accessibility_services " + AccessibilityPolicy.quote(merged));
                read(dir, "enable", prefix + " put secure accessibility_enabled 1");
                Set<String> actual = AccessibilityPolicy.parse(read(dir, "after", prefix + " get secure enabled_accessibility_services"));
                String enabled = read(dir, "enabled-after", prefix + " get secure accessibility_enabled");
                if (!AccessibilityPolicy.preserves(previous, actual) || !(actual.contains(AccessibilityPolicy.COMPONENT)
                        || actual.contains("local.quest.controllerrescue/.VolumeService")) || !enabled.equals("1")) {
                    throw new Exception("Accessibility verification failed; inspect setup logs.");
                }
                for (int attempt = 0; attempt < 10 && !VolumeService.connected; attempt++) {
                    Thread.sleep(500);
                }
                try { Rescue.run(dir, "framework", "dumpsys accessibility"); }
                catch (Exception optionalDiagnosticFailure) { /* Binding is independently verified above. */ }
                Rescue.message(app, VolumeService.connected ? "Volume access enabled; test delivery" : "Volume access saved; framework pending");
            } catch (Exception exception) {
                String detail = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                Rescue.message(app, detail.contains("Root unavailable") || detail.contains("Cannot run program")
                        ? "Root unavailable" : "Volume access setup incomplete: " + detail);
            } finally {
                synchronized (AccessibilitySetup.class) { inFlight = false; }
                new android.os.Handler(app.getMainLooper()).post(completion);
            }
        }, "accessibility-setup").start();
    }

    private static String read(File dir, String label, String command) throws Exception {
        Rescue.Result result = Rescue.run(dir, label, command);
        if (result.exit != 0) throw new Exception("Command failed: " + label);
        return result.text.trim();
    }
}
