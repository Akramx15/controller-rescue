# Double Power

The LSPosed module is included in **Controller Rescue** starting with v0.3.0. No second APK is needed. The repair button and volume shortcuts work without LSPosed.

## Enable

1. Install Controller Rescue and grant root access.
2. In LSPosed/Vector, enable **Controller Rescue** for **System Framework / Android System** only.
3. Reboot the headset normally.
4. In the app, turn on **Double Power**. Use **Test shortcuts** to check detection without running recovery.

If your root is temporary, restore root and LSPosed through your usual rooting tool after the full reboot.

For Vector CLI, the framework scope is `system/0`. The APK's legacy Xposed scope resource remains `android`, which the manager normalizes.

## Upgrade from two APKs

1. Turn off **Double Power** in the old app.
2. Disable **Controller Rescue · Power** in LSPosed and uninstall that old companion.
3. Update the main app with the v0.3.0 APK. Its package and signing certificate are unchanged, so settings are preserved.
4. Enable **Controller Rescue** for the framework scope and reboot normally.
5. Turn on Double Power and test it.

Disabling or uninstalling a module does not unload hooks from a running system process. Keep Double Power off until the reboot is complete. The new hook also refuses to act while the old companion package is installed; this guard does not replace the reboot.

## Behavior

Supported only on Quest 3, Android 14, build `52433670036000520`. Unsupported builds, conflicting camera/power actions, and unreadable system state leave normal power handling in place.

Press and release twice quickly, with less than 300 ms between the two down events. Both holds must be shorter than 500 ms and Android's long-press threshold. The module waits 500 ms after the second release before requesting recovery. A third press, held/canceled key, key combination, sleep, or disabled shortcut cancels the candidate. A press that wakes the headset never starts recovery.

Single-press sleep is delayed briefly; long-press actions and the native emergency gesture handler remain in Android's handlers. On the tested firmware, an absent optional gesture service is a known normal state; failed reads still block the shortcut.

The hook runs in system_server and starts the app's explicit `RecoveryService`. That service must be enabled, exported, and protected by `android.permission.DEVICE_POWER`. It checks the app's opt-in setting and owns the foreground notification, root execution, cooldown, and verification. No shell command travels through the hook interface. Logs tagged `ControllerRescuePower` record module installation and blocked configurations.

During development, repeated partial framework restarts were followed by a kernel mount stall and a native sensor-service watchdog timeout. Use a normal full reboot for activation; never restart zygote directly.
