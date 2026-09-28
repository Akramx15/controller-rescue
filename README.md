# Controller Rescue

Controllers on a break? Bring them back.

One small app for rooted Quest 3. One button to recover the connection.

**[Download the APK](https://github.com/Akramx15/controller-rescue/releases)**

## A little help

- Recover your controllers without clearing pairing.
- Check whether both are connected.
- Double-press Volume Up, Volume Down, or Power to run a repair.
- Test shortcuts without running a repair.

## Set it up

Install **Controller Rescue** and grant it root access.

For volume shortcuts, open **Settings → Enable volume shortcuts**, then choose your buttons.

For Power, enable **Controller Rescue** in LSPosed/Vector for **System Framework / Android System**, reboot the headset normally, then turn on **Double Power** in the app. Two quick presses keep the screen awake and request recovery. LSPosed is only needed for this shortcut.

**Everything is in one APK.** If you used v0.2.x, turn off Double Power, disable and uninstall **Controller Rescue · Power**, then activate the updated app in LSPosed and reboot normally before turning Power back on. Your main app settings stay with you. [Power setup details](docs/power.md).

## Before you tap

Requires root. Currently restricted to Quest 3 on Quest OS build **52433670036000520**.

Recovery briefly pauses tracking. It restarts the existing sensor service; it does not change firmware or clear pairing. This is a recovery tool, not a proven cure for the underlying fault.

Repairs have a two-minute cooldown, with a countdown when you try again early. Nothing runs automatically when the timer ends. If verification is interrupted, use **Check connection**.

No network permission, analytics, or uploads. Up to ten recovery logs stay on your headset.

[Build from source](docs/build.md) · [MIT license](LICENSE)

Unofficial. Not affiliated with Meta. Controllers deserve shorter breaks.
