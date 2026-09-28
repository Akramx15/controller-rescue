package local.quest.controllerpowerhook;

/** Build boundary and fail-closed decisions, independent of Android for local verification. */
public final class PowerPolicy {
    public static final String BUILD = "52433670036000520";
    private PowerPolicy() { }

    public static boolean supported(int sdk, String incremental, String model) {
        return sdk == 34 && BUILD.equals(incremental) && "Quest 3".equals(model);
    }

    public static String blocked(boolean enabled, boolean earlyHandler,
                                 boolean camera, int doubleAction) {
        if (!enabled) return "disabled";
        if (earlyHandler) return "OEM early system-key handler";
        if (camera) return "camera double-power gesture";
        if (doubleAction != 0) return "existing double-power action";
        return null;
    }

    public static boolean shouldRescue(int count, boolean beganAsleep, boolean interactive) {
        return count == 2 && !beganAsleep && interactive;
    }
}
