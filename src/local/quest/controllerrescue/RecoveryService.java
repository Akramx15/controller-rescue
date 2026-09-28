package local.quest.controllerrescue;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

/** Transient actuator, never restarted automatically. External callers need DEVICE_POWER. */
public class RecoveryService extends Service {
    public static final String POWER_ACTION = "local.quest.controllerrescue.POWER_RESCUE";
    private static final String LOCAL_ACTION = "local.quest.controllerrescue.REPAIR";
    private boolean ownsWorker;

    static void request(Context context, String source) {
        Intent intent = new Intent(context, RecoveryService.class).setAction(LOCAL_ACTION);
        intent.putExtra("source", source);
        try { context.startForegroundService(intent); }
        catch (RuntimeException exception) {
            Rescue.message(context, "Could not start repair service; no restart requested.");
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("recovery", "Controller recovery",
                NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "recovery")
                .setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Controller Rescue")
                .setContentText("Bringing your controllers back…").setOngoing(true).build());
        if (ownsWorker) return START_NOT_STICKY;
        String action = intent == null ? "" : intent.getAction();
        String source;
        if (POWER_ACTION.equals(action)) {
            if (!getSharedPreferences("prefs", 0).getBoolean("powerShortcut", false)
                    || !((PowerManager) getSystemService(POWER_SERVICE)).isInteractive()) {
                finish(); return START_NOT_STICKY;
            }
            source = "double-power";
            if (getSharedPreferences("prefs", 0).getBoolean("test", false)) {
                VolumeService.recordTest(this, source);
                finish(); return START_NOT_STICKY;
            }
        } else if (LOCAL_ACTION.equals(action)) {
            source = intent.getStringExtra("source");
            if (!"manual".equals(source) && !"double-volume-up".equals(source)
                    && !"double-volume-down".equals(source)) {
                finish(); return START_NOT_STICKY;
            }
        } else { finish(); return START_NOT_STICKY; }
        if (!"manual".equals(source) && getSharedPreferences("prefs", 0).getBoolean("test", false)) {
            VolumeService.recordTest(this, source);
            finish(); return START_NOT_STICKY;
        }
        Rescue.recoverInterrupted(this);
        ownsWorker = true;
        if (!Rescue.start(this, source, () -> new android.os.Handler(getMainLooper()).post(this::finish))) {
            finish();
        }
        return START_NOT_STICKY;
    }

    private void finish() {
        ownsWorker = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
