# Build

One command produces one APK, including the optional LSPosed entry point.

Requires Android API 34, Build Tools 34.0.0, a JDK, and Python 3.9+.

Set `ANDROID_HOME` to your SDK directory, `RESCUE_KEYSTORE` to an existing signing keystore **outside** this repository, and `RESCUE_KEY_PASSWORD` to its password. Set `RESCUE_KEY_ALIAS` if the alias differs from `controller-rescue-local`.

```sh
python3 build.py
```

The signed APK is written to `build/controller-rescue-0.3.0.apk`. Use the same signing key to update an existing installation without losing its settings or root grant.

The build runs the recovery, accessibility, and power policy/sequence tests. On its first run it downloads the compile-only [Xposed API 82](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar), checked against SHA-256 `f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`. Xposed API classes are not bundled. The regular app does not load the hook classes; recovery and volume shortcuts can run without LSPosed.

Root is used for shortcut settings, fixed service-state checks, private diagnostic dumps, and the sensor-service restart. Recovery runs only after a user request. The app accepts no arbitrary shell command and does not reboot the headset automatically.

Device logs, proprietary system binaries, build output, dependencies, and signing keys are excluded from the repository.
