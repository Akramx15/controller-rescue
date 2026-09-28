package local.quest.controllerrescue;

import android.content.Context;
import java.io.File;

/** Only the fixed opt-in flag can be changed here, never a supplied command. */
final class PowerSettings {
    private static boolean inFlight;
    static synchronized void set(Context context, boolean enabled, Runnable complete) {
        if (inFlight) {
            Rescue.message(context, "A controller operation is already running.");
            complete.run();
            return;
        }
        inFlight = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                File dir = new File(app.getFilesDir(), "power-settings");
                dir.mkdirs();
                Rescue.Result uid = Rescue.run(dir, "root", "id -u");
                if (uid.exit != 0 || !uid.text.trim().equals("0")) throw new Exception("Root unavailable");
                String value = enabled ? "1" : "0";
                Rescue.Result write = Rescue.run(dir, "write", enabled
                        ? "settings put global controller_rescue_double_power 1"
                        : "settings put global controller_rescue_double_power 0");
                Rescue.Result read = Rescue.run(dir, "read", "settings get global controller_rescue_double_power");
                if (write.exit != 0 || read.exit != 0 || !value.equals(read.text.trim())) {
                    throw new Exception("Power shortcut setting verification failed");
                }
                app.getSharedPreferences("prefs", 0).edit().putBoolean("powerShortcut", enabled).commit();
                Rescue.message(app, enabled ? "Power shortcut enabled" : "Power shortcut disabled");
            } catch (Exception exception) {
                Rescue.message(app, exception.getMessage() == null ? "Root unavailable" : exception.getMessage());
            } finally {
                synchronized (PowerSettings.class) { inFlight = false; }
                new android.os.Handler(app.getMainLooper()).post(complete);
            }
        }, "power-shortcut-settings").start();
    }
}
