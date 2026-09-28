# Controller Rescue

Controllers taking a break? Bring them back.

A small recovery tool for rooted Meta Quest 3 headsets. One repair button, a connection check, and optional double-press shortcuts, with a quiet interface and short, friendly messages.

[Download APKs](https://github.com/Akramx15/controller-rescue/releases/latest)

## What it does

Restarts the existing sensor service that recovered both controllers during a recorded connection failure. Pairing and firmware stay intact. Tracking pauses briefly while the shared service restarts.

This is a recovery tool, not a proven fix for the underlying firmware fault. The current build is restricted to the tested Quest OS build **52433670036000520**. Root access must be available and granted to the app.

## Shortcuts

- Double Volume Up.
- Double Volume Down.
- Double Power, through the optional LSPosed/Vector companion.

Volume shortcuts require the app's Accessibility service. In Settings, select **Enable volume shortcuts** and allow root access. The app enables its own service directly while keeping existing accessibility services enabled. Then switch on the volume shortcuts you want. It observes volume keys without consuming them, so normal volume changes still happen. It does not request screen-content access.

The power companion defers single-press sleep briefly and observes two quick completed presses. It waits another 500 ms without a third press before requesting recovery. The module checks the tested system build and conflicting power-key features before changing behavior. It is scoped only to **System Framework / android**. See [power-hook](power-hook/) for setup and limitations.

Shortcuts are opt-in. A test mode checks the keys without running a repair. Repairs have a two-minute cooldown and are never triggered simply because a controller is asleep or searching.

## Root and privacy

Root is used for enabling shortcut settings, fixed service-state checks, diagnostic dumps and the sensor-service restart. The app does not accept arbitrary shell commands, download firmware, clear pairing, or automatically reboot the headset.

Up to ten recovery logs stay in the app's private storage. There is no network permission, analytics or upload endpoint. If the app is stopped during recovery, it reports that verification was interrupted instead of silently trying again.

## Build

Sources use Java and the Android SDK directly, without Gradle. Android API 34, Build Tools 34.0.0, a JDK and Python 3.9+ are required.

Set `ANDROID_HOME` to your SDK directory, `RESCUE_KEYSTORE` to an existing signing keystore **outside** this repository, and `RESCUE_KEY_PASSWORD` to its password. Set `RESCUE_KEY_ALIAS` to the key alias if it differs from `controller-rescue-local`. Both APKs must use the same certificate.

```sh
python3 build.py
python3 power-hook/build.py
```

APKs are written to `build/` and `power-hook/build/`. The optional companion downloads a pinned, compile-only Xposed API dependency on its first build. It is not included in the APK.

Device logs, proprietary system binaries and signing keys are excluded from this repository.

MIT licensed. Unofficial; not affiliated with Meta.
