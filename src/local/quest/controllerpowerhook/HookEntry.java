package local.quest.controllerpowerhook;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Observes native power gestures; all privileged repair work stays in the app service. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[ControllerRescuePower] ";
    private static final String SETTING = "controller_rescue_double_power";
    private static final String ACTION = "local.quest.controllerrescue.POWER_RESCUE";
    private static boolean installed;
    private static volatile boolean armed;
    private static String lastState = "";
    private static Class<?> detectorClass;
    private static long settingReadAt = -1;
    private static boolean cachedEnabled;
    private static long legacyReadAt = -1;
    private static boolean legacyInstalled = true;
    private static final PowerSequence sequence = new PowerSequence();

    @Override public synchronized void handleLoadPackage(LoadPackageParam loaded) {
        if (!"android".equals(loaded.packageName)
                || !("android".equals(loaded.processName) || "system_server".equals(loaded.processName))
                || installed) return;
        installed = true;
        XposedBridge.log(TAG + "model=" + Build.MODEL + " sdk=" + Build.VERSION.SDK_INT
                + " build=" + Build.VERSION.INCREMENTAL);
        if (!PowerPolicy.supported(Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL, Build.MODEL)) {
            state("unsupported build; hooks=0");
            return;
        }
        XC_MethodHook.Unhook maxHook = null;
        XC_MethodHook.Unhook pressHook = null;
        XC_MethodHook.Unhook observeHook = null;
        try {
            Class<?> pwm = XposedHelpers.findClass("com.android.server.policy.PhoneWindowManager", loaded.classLoader);
            detectorClass = XposedHelpers.findClass("com.android.server.policy.SingleKeyGestureDetector", loaded.classLoader);
            Method max = pwm.getDeclaredMethod("getMaxMultiPressPowerCount");
            Method press = pwm.getDeclaredMethod("powerPress", long.class, int.class, boolean.class);
            Method observe = pwm.getDeclaredMethod("handleKeyGesture", KeyEvent.class, boolean.class);
            if (max.getReturnType() != int.class || press.getReturnType() != void.class
                    || observe.getReturnType() != void.class)
                throw new NoSuchMethodException("power method return types");
            // Resolve all accessors before activating either hook.
            for (String name : new String[]{"mContext", "mHandler", "mAllKeysAreSystemKeys",
                    "mSystemKeyEventHandler", "mDoublePressOnPowerBehavior", "mGestureLauncherService",
                    "mKeyCombinationManager", "mLongPressOnPowerAssistantTimeoutMs"})
                XposedHelpers.findField(pwm, name);
            XposedHelpers.findField(detectorClass, "sDefaultLongPressTimeout");
            pwm.getDeclaredMethod("getResolvedLongPressOnPowerBehavior");
            maxHook = XposedBridge.hookMethod(max, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!armed || p.hasThrowable()) return;
                    if (eligible(p.thisObject) != null)
                        p.setResult(Math.max((Integer) p.getResult(), 2));
                }
            });
            pressHook = XposedBridge.hookMethod(press, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!armed || (Integer) p.args[1] != 2) return;
                    // Only the independent completed-press observer can dispatch recovery.
                    if (eligible(p.thisObject) != null) p.setResult(null);
                }
            });
            observeHook = XposedBridge.hookMethod(observe, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!armed) return;
                    if (p.hasThrowable()) { sequence.cancel(); return; }
                    try {
                        KeyEvent event = (KeyEvent) p.args[0];
                        if (event.getKeyCode() != KeyEvent.KEYCODE_POWER) {
                            sequence.cancel();
                            return;
                        }
                        Context context = eligible(p.thisObject);
                        Object combinations = XposedHelpers.getObjectField(p.thisObject, "mKeyCombinationManager");
                        if (context == null || combinations == null
                                || (Boolean) XposedHelpers.callMethod(combinations, "isPowerKeyIntercepted")
                                || (Boolean) XposedHelpers.callMethod(combinations, "isKeyConsumed", event)) {
                            sequence.cancel();
                            return;
                        }
                        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) {
                            sequence.cancel();
                            return;
                        }
                        long nativeLongPress = (Integer) XposedHelpers.callMethod(p.thisObject,
                                "getResolvedLongPressOnPowerBehavior") == 5
                                ? XposedHelpers.getLongField(p.thisObject, "mLongPressOnPowerAssistantTimeoutMs")
                                : XposedHelpers.getStaticLongField(detectorClass, "sDefaultLongPressTimeout");
                        long token = sequence.event(event.getAction() == KeyEvent.ACTION_DOWN,
                                event.getRepeatCount(), event.isCanceled() || event.isLongPress(), event.getDownTime(),
                                event.getEventTime(), (Boolean) p.args[1], true, nativeLongPress);
                        if (token < 0) return;
                        Handler handler = (Handler) XposedHelpers.getObjectField(p.thisObject, "mHandler");
                        final Object owner = p.thisObject;
                        if (handler == null || !handler.postDelayed(() -> complete(owner, token),
                                PowerSequence.QUIET_MS)) sequence.cancel();
                    } catch (Throwable error) {
                        sequence.cancel();
                        state("power callback blocked: " + error.getClass().getSimpleName());
                    }
                }
            });
            armed = true;
            XposedBridge.log(TAG + "installed hooks=3; default disabled; emergency handling unchanged");
        } catch (Throwable error) {
            armed = false;
            sequence.cancel();
            unhook(observeHook);
            unhook(pressHook);
            unhook(maxHook);
            state("hook installation failed: " + error.getClass().getSimpleName());
        }
    }

    private static void complete(Object pwm, long token) {
        try {
            Context context = armed ? eligible(pwm) : null;
            if (context == null) { sequence.cancel(); return; }
            Intent request = new Intent(ACTION).setComponent(new ComponentName(
                    "local.quest.controllerrescue", "local.quest.controllerrescue.RecoveryService"));
            PackageManager packages = context.getPackageManager();
            ServiceInfo service = packages.getServiceInfo(request.getComponent(), 0);
            if (!"local.quest.controllerrescue".equals(service.packageName)
                    || !"local.quest.controllerrescue.RecoveryService".equals(service.name)
                    || !service.enabled || !service.applicationInfo.enabled || !service.exported
                    || !"android.permission.DEVICE_POWER".equals(service.permission)) {
                state("blocked: recovery service contract mismatch");
                return;
            }
            // Slow package lookups precede the final cancellation point, so an intervening
            // third press can still invalidate this request. Never retain a recycled KeyEvent.
            context = armed ? eligible(pwm) : null;
            PowerManager power = context == null ? null
                    : (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (!sequence.take(token, SystemClock.uptimeMillis(), context != null,
                    power != null && power.isInteractive())) return;
            context.startForegroundService(request);
            XposedBridge.log(TAG + "quiet double-power request dispatched");
        } catch (Throwable error) {
            sequence.cancel();
            state("dispatch failed: " + error.getClass().getSimpleName());
        }
    }

    private static void unhook(XC_MethodHook.Unhook hook) {
        if (hook == null) return;
        try { hook.unhook(); } catch (Throwable ignored) {
            // Any remaining callback is inert because armed was cleared first.
        }
    }

    private static Context eligible(Object pwm) {
        try {
            Context context = (Context) XposedHelpers.getObjectField(pwm, "mContext");
            if (context == null) return null;
            if (hasLegacyModule(context)) {
                sequence.cancel();
                state("blocked: remove old Controller Rescue Power and reboot");
                return null;
            }
            boolean enabled = enabled(context);
            if (!enabled) { sequence.cancel(); state("disabled"); return null; }
            boolean early = XposedHelpers.getBooleanField(pwm, "mAllKeysAreSystemKeys")
                    && XposedHelpers.getObjectField(pwm, "mSystemKeyEventHandler") != null;
            int user = (Integer) XposedHelpers.callStaticMethod(ActivityManager.class, "getCurrentUser");
            if (user != 0) { state("blocked: secondary headset user"); return null; }
            boolean camera = false;
            Object live = XposedHelpers.getObjectField(pwm, "mGestureLauncherService");
            if (live != null) {
                camera = XposedHelpers.getBooleanField(live, "mCameraDoubleTapPowerEnabled");
                if (XposedHelpers.getBooleanField(live, "mEmergencyGestureEnabled")) {
                    long last = XposedHelpers.getLongField(live, "mLastEmergencyGestureTriggered");
                    int cooldown = XposedHelpers.getIntField(live, "mEmergencyGesturePowerButtonCooldownPeriodMs");
                    if (cooldown >= 0 && SystemClock.uptimeMillis() - last < cooldown) {
                        state("blocked: emergency power cooldown");
                        return null;
                    }
                }
            }
            // A successfully read null matches the installed PWM.handleCameraGesture path:
            // it returns false and uses the native key detector. Capability resources alone
            // do not prove that SystemServer started this optional service. Reflection failures
            // still reach the fail-closed catch, and every event/dispatch rereads the live field.
            String reason = PowerPolicy.blocked(enabled, early, camera,
                    XposedHelpers.getIntField(pwm, "mDoublePressOnPowerBehavior"));
            state(reason == null
                    ? (live == null ? "enabled; quiet double-power ready; native detector (known-null gesture service)"
                    : "enabled; quiet double-power ready; live gesture service preserved")
                    : "blocked: " + reason);
            return reason == null ? context : null;
        } catch (Throwable error) {
            state("state unavailable: " + error.getClass().getSimpleName());
            return null;
        }
    }

    /** Old and unified modules have independent classloaders and must not both hook power. */
    private static synchronized boolean hasLegacyModule(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (legacyReadAt < 0 || now - legacyReadAt >= 5000) {
            legacyInstalled = true;
            try {
                context.getPackageManager().getPackageInfo("local.quest.controllerpowerhook", 0);
            } catch (PackageManager.NameNotFoundException absent) {
                legacyInstalled = false;
            }
            // Other lookup errors propagate to eligible's fail-closed handler.
            legacyReadAt = now;
        }
        return legacyInstalled;
    }

    private static synchronized boolean enabled(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (settingReadAt < 0 || now - settingReadAt >= 500) {
            cachedEnabled = Settings.Global.getInt(context.getContentResolver(), SETTING, 0) == 1;
            settingReadAt = now;
        }
        return cachedEnabled;
    }

    private static synchronized void state(String value) {
        if (!value.equals(lastState)) {
            lastState = value;
            XposedBridge.log(TAG + value);
        }
    }
}
