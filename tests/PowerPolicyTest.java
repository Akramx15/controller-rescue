import local.quest.controllerpowerhook.PowerPolicy;

public final class PowerPolicyTest {
    private static int checks;
    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        expect(PowerPolicy.supported(34, PowerPolicy.BUILD, "Quest 3"), "verified target");
        expect(!PowerPolicy.supported(35, PowerPolicy.BUILD, "Quest 3"), "reject OS drift");
        expect(!PowerPolicy.supported(34, "other", "Quest 3"), "reject build drift");
        expect(!PowerPolicy.supported(34, PowerPolicy.BUILD, "Quest Pro"), "reject different model");
        for (int mask = 0; mask < 16; mask++) {
            boolean enabled = (mask & 1) != 0;
            boolean early = (mask & 2) != 0;
            boolean camera = (mask & 4) != 0;
            int originalDouble = (mask & 8) != 0 ? 1 : 0;
            boolean permitted = PowerPolicy.blocked(enabled, early, camera, originalDouble) == null;
            expect(permitted == (mask == 1), "only enabled without conflicts may alter power: " + mask);
        }
        for (int count = 0; count <= 6; count++) {
            for (int state = 0; state < 4; state++) {
                boolean beganAsleep = (state & 1) != 0;
                boolean interactive = (state & 2) != 0;
                expect(PowerPolicy.shouldRescue(count, beganAsleep, interactive)
                        == (count == 2 && state == 2), "preserve all other native power actions");
            }
        }
        System.out.println("Power policy: " + checks + " checks passed");
    }
}
