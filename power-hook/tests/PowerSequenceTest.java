import local.quest.controllerpowerhook.PowerSequence;

public final class PowerSequenceTest {
    private static int checks;
    private static void expect(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static long key(PowerSequence s, boolean down, long start, long time) {
        return s.event(down, 0, false, start, time, true, true, 500);
    }
    private static long pair(PowerSequence s) {
        key(s, true, 1000, 1000); key(s, false, 1000, 1060);
        key(s, true, 1200, 1200); return key(s, false, 1200, 1260);
    }
    public static void main(String[] args) {
        PowerSequence s = new PowerSequence();
        long token = pair(s);
        expect(token >= 0, "fast double starts quiet wait");
        expect(!s.take(token, 1759, true, true), "500ms quiet is required");
        expect(s.take(token, 1760, true, true), "double completes after quiet");
        expect(!s.take(token, 1900, true, true), "at most one dispatch per token");

        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1060);
        key(s, true, 1350, 1350);
        expect(key(s, false, 1350, 1410) < 0, "350ms down interval is not a fast pair");

        s = new PowerSequence(); token = pair(s);
        key(s, true, 1400, 1400); key(s, false, 1400, 1460);
        expect(!s.take(token, 2000, true, true), "third press cancels second-UP candidate");
        key(s, true, 1600, 1600);
        expect(key(s, false, 1600, 1660) < 0, "press4 must not start new pair");
        key(s, true, 1800, 1800);
        expect(key(s, false, 1800, 1860) < 0, "press5 must not trigger recovery");
        expect(!s.take(token, 2400, true, true), "five-press sequence never dispatches old token");
        key(s, true, 2500, 2500); key(s, false, 2500, 2560);
        key(s, true, 2700, 2700); long fresh = key(s, false, 2700, 2760);
        expect(s.take(fresh, 3260, true, true), "fresh pair after quiet is allowed");

        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1500);
        key(s, true, 1600, 1600);
        expect(key(s, false, 1600, 1650) < 0, "first long hold is rejected");
        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1050);
        key(s, true, 1200, 1200);
        expect(key(s, false, 1200, 1700) < 0, "second long hold is rejected");
        s = new PowerSequence();
        s.event(true, 0, false, 1000, 1000, true, true, 200);
        s.event(false, 0, false, 1000, 1200, true, true, 200);
        s.event(true, 0, false, 1250, 1250, true, true, 200);
        expect(s.event(false, 0, false, 1250, 1300, true, true, 200) < 0,
                "shorter native long threshold is respected");

        s = new PowerSequence(); token = pair(s);
        expect(!s.take(token, 1760, false, true), "disable while pending cancels");
        expect(!s.take(token, 1800, true, true), "reenable cannot resurrect canceled request");
        s = new PowerSequence(); token = pair(s);
        expect(!s.take(token, 1760, true, false), "headset sleeping cancels pending request");
        s = new PowerSequence();
        s.event(true, 0, false, 1000, 1000, false, true, 500);
        key(s, false, 1000, 1050); key(s, true, 1200, 1200);
        expect(key(s, false, 1200, 1250) < 0, "first press asleep never repairs");

        s = new PowerSequence(); token = pair(s);
        s.cancel();
        expect(!s.take(token, 1760, true, true), "key chord or cooldown cancellation invalidates");
        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1050);
        s.event(true, 1, false, 1200, 1200, true, true, 500);
        expect(key(s, false, 1200, 1250) < 0, "repeated key cannot complete pair");
        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1050); key(s, true, 1200, 1200);
        expect(s.event(false, 0, true, 1200, 1250, true, true, 500) < 0, "canceled UP rejected");
        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1050); key(s, true, 1200, 1200);
        expect(key(s, false, 1199, 1250) < 0, "unmatched DOWN/UP rejected");
        s = new PowerSequence();
        key(s, true, 1000, 1000); key(s, false, 1000, 1050);
        key(s, true, 1300, 1300);
        expect(key(s, false, 1300, 1350) < 0, "300ms boundary rejected");
        System.out.println("Power sequence: " + checks + " checks passed");
    }
}
