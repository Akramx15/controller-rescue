# Optional power shortcut

Adds double power to Controller Rescue through LSPosed's **Android** scope. The companion must be installed. The module is inactive until `controller_rescue_double_power` is enabled by the companion; it defaults to off.

Compatibility is restricted to Quest 3, Android 14, system build `52433670036000520`. This module detects the gesture; Controller Rescue still uses its existing root grant for recovery.

The native power detector waits briefly (normally 300ms) before normal single-press sleep. Double power requests recovery while the headset is already awake. Normal single, long and very-long power actions remain in Android's original handlers. An existing camera shortcut, another double-power action, or an early Meta system-key handler blocks activation rather than being overridden. Unsupported builds and unavailable state leave the original methods unchanged.

Press and release power twice quickly, with less than 300ms between the two down events. Both holds must be shorter than 500ms and shorter than the native long-press threshold. The module waits 500ms after the second release before requesting recovery. Any third press, key combination, canceled/held key, sleep, or disabled shortcut cancels the request. A first press that wakes the headset never triggers recovery.

When Android's emergency gesture handler is present, it is left untouched and still receives every event. Third and later presses cancel the repair candidate. The live handler's cooldown is also respected. The module observes completed presses after native gesture processing because that handler may consume the second press. Only the independent quiet-period timer can request recovery; the native double callback cannot.

On the tested Quest build, the optional gesture service may be absent even when its capability resources are enabled. A confirmed null service follows Android's existing native-detector path; failed state reads still block the shortcut. If a live service appears, its camera and emergency cooldown checks apply immediately.

The module starts the explicit `local.quest.controllerrescue.RecoveryService` with action `local.quest.controllerrescue.POWER_RESCUE` from system_server. Both APKs must have the same signing certificate. The module checks that signature and the enabled/exported service's `android.permission.DEVICE_POWER` protection. The companion validates its own power setting and owns the foreground notification, root execution and cooldown. No arbitrary shell command travels through this interface.

## Build

Requires JDK, Android SDK platform/build-tools 34, and Python 3.9+. Run `ANDROID_HOME=/path/to/android-sdk python3 build.py`. The script runs policy and event-sequence tests and builds an unsigned APK. To sign, set `RESCUE_KEYSTORE` to a keystore outside this repository, `RESCUE_KEY_PASSWORD`, and optionally `RESCUE_KEY_ALIAS`. It never creates or copies signing keys into the repository.

The compile-only Xposed API 82 dependency is checked against SHA-256 `f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25` from `https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar`; it is not packaged into the module.

Activate only **System Framework / Android System** in LSPosed, reboot the headset normally, then enable the shortcut in Controller Rescue. Vector's CLI names this scope `system/0`; `android/0` does not load the module into system_server on the tested Vector build. Xposed logs tagged `ControllerRescuePower` report installation and any blocked configuration. A successful build does not establish physical button behavior: use the companion's shortcut test before trying recovery.

Physical double-power detection passed on the tested headset after a normal reboot: the screen stayed awake, the companion recorded the gesture, and a later real recovery request reached the sensor-service restart. Both controllers were subsequently observed connected. App interruption during a restart can still prevent its own final verification; use **Check connection** if that is reported.

During development, repeated partial framework restarts were followed by a kernel mount stall, a blocked sensor HAL restart and a watchdog timeout in Android's native sensor service. The exact initiating mount was not captured. Use a normal headset reboot for module activation; do not restart zygote directly.
