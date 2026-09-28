package local.quest.controllerrescue;

/** Keeps diagnostics out of the main page; original messages stay private. */
final class UiStatus {
    static long cooldownDeadline(String raw, long last) {
        if (raw.startsWith("Cooldown until ")) {
            try { return Long.parseLong(raw.substring("Cooldown until ".length())); }
            catch (NumberFormatException invalid) { return 0; }
        }
        return raw.contains("120-second") && last > 0 ? last + 120000 : 0;
    }

    static String remaining(long deadline, long now) {
        long seconds = Math.max(0, (deadline - now + 999) / 1000);
        if (seconds == 0) return "Ready when you need me.";
        return String.format(java.util.Locale.US, "Try again in %d:%02d.", seconds / 60, seconds % 60);
    }

    static String friendly(String raw) {
        long deadline = cooldownDeadline(raw, 0);
        if (deadline > 0) return remaining(deadline, System.currentTimeMillis());
        if (raw.equals("Enabling volume access")) return "Enabling volume shortcuts…";
        if (raw.equals("Volume access enabled; test delivery")) return "Volume access enabled. Try Test shortcuts.";
        if (raw.equals("Volume access saved; framework pending")) return "Access saved. Waiting for Android; try again shortly.";
        if (raw.startsWith("Volume access setup incomplete")) return "Could not confirm shortcut access. Try again.";
        if (raw.equals("Power shortcut enabled")) return "Power shortcut enabled.";
        if (raw.equals("Power shortcut disabled")) return "Power shortcut disabled.";
        if (raw.isEmpty()) return "Ready when you need me.";
        if (raw.contains("Shortcut detected")) return "Shortcut detected. No repair.";
        if (raw.contains("interrupted")) return "Check interrupted. Try a connection check.";
        if (raw.contains("Root unavailable") || raw.contains("Cannot run program"))
            return "Root needed. Enable root and allow this app, then try again.";
        if (raw.contains("120-second")) return "Wait a moment before trying again.";
        if (raw.contains("already running")) return "Working on it…";
        if (raw.contains("Checking")) return "Checking the connection…";
        if (raw.contains("Restarting")) return "Bringing them back… Tracking may pause briefly.";
        if (raw.contains("both controllers report connected")) return "Both connected.";
        if (raw.contains("Read-only status:")) {
            if (raw.contains("active controllers=2")) return "Both connected.";
            if (raw.contains("active controllers=1")) return "One connected. Waiting for the other.";
            if (raw.contains("active controllers=0")) return "No controllers connected.";
            return "Could not verify the connection.";
        }
        if (raw.contains("awake and mounted")) return "Put on and wake the headset, then try again.";
        if (raw.contains("supports only")) return "This system version is not supported yet.";
        if (raw.contains("Accessibility settings")) return "Shortcut settings are unavailable here.";
        if (raw.contains("Partial recovery")) return "Could not confirm both controllers. Try a connection check.";
        return "Could not finish. Try a connection check.";
    }
}
