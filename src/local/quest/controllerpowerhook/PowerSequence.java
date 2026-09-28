package local.quest.controllerpowerhook;

/** Counts completed short presses without changing Android's emergency gesture detector. */
public final class PowerSequence {
    public static final long PAIR_INTERVAL_MS = 300;
    public static final long QUIET_MS = 500;
    public static final long HOLD_MS = 500;
    private long generation;
    private long lastEvent = -1;
    private long firstDown;
    private long heldDown;
    private long due;
    private long holdLimit;
    private int count;
    private boolean pressed;
    private boolean firstUp;
    private boolean beganInteractive;
    private boolean rejected;
    private boolean candidate;

    /** Returns a generation token only when a second short UP starts the quiet interval. */
    public synchronized long event(boolean down, int repeats, boolean canceled,
                                   long downTime, long eventTime,
                                   boolean interactive, boolean enabled, long nativeLongPressMs) {
        if (!enabled || canceled || repeats != 0 || nativeLongPressMs <= 0 || eventTime < downTime
                || (lastEvent >= 0 && eventTime < lastEvent)) {
            cancel();
            return -1;
        }
        if (down) {
            boolean fresh = count == 0 || (!pressed && lastEvent >= 0
                    && eventTime - lastEvent >= QUIET_MS);
            if (fresh) clear();
            generation++;
            candidate = false; // Every subsequent DOWN invalidates the scheduled request.
            count++;
            if (count == 1) {
                beganInteractive = interactive;
                firstDown = eventTime;
                rejected = !interactive;
            } else if (count == 2) {
                rejected |= pressed || !firstUp || eventTime - firstDown >= PAIR_INTERVAL_MS;
            } else {
                // Stay rejected through presses3/4/5, until a complete quiet interval.
                rejected = true;
            }
            pressed = true;
            heldDown = downTime;
            holdLimit = Math.min(HOLD_MS, nativeLongPressMs);
            lastEvent = eventTime;
            return -1;
        }
        generation++;
        candidate = false;
        if (!pressed || heldDown != downTime) {
            cancel();
            return -1;
        }
        pressed = false;
        lastEvent = eventTime;
        rejected |= eventTime - heldDown >= Math.min(holdLimit, nativeLongPressMs);
        if (count == 1) firstUp = !rejected;
        if (count == 2 && !rejected
                && PowerPolicy.shouldRescue(count, !beganInteractive, interactive)) {
            due = eventTime + QUIET_MS;
            candidate = true;
            return generation;
        }
        return -1;
    }

    /** Consumes a ready token once. Disabling or sleeping during the wait cancels it. */
    public synchronized boolean take(long token, long now, boolean enabled, boolean interactive) {
        if (token != generation || !candidate) return false;
        if (!enabled || !interactive) { cancel(); return false; }
        if (now < due) return false;
        candidate = false;
        generation++;
        return true;
    }

    public synchronized void cancel() {
        generation++;
        clear();
    }

    private void clear() {
        lastEvent = -1;
        count = 0;
        pressed = firstUp = beganInteractive = rejected = candidate = false;
    }
}
