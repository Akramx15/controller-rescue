package local.quest.controllerrescue;
public class AccessibilityPolicyTest {
    public static void main(String[] args) {
        String qgo="com.anagan.qgo/com.anagan.qgo.service.AppChangeDetectionService";
        if(!AccessibilityPolicy.append(qgo).equals(qgo+":"+AccessibilityPolicy.COMPONENT)) throw new AssertionError("QGO preserved");
        if(!AccessibilityPolicy.append("null").equals(AccessibilityPolicy.COMPONENT)) throw new AssertionError("unset");
        String shortName="local.quest.controllerrescue/.VolumeService";
        if(!AccessibilityPolicy.append(shortName).equals(shortName)) throw new AssertionError("no alias duplicate");
        if(!AccessibilityPolicy.append(AccessibilityPolicy.COMPONENT).equals(AccessibilityPolicy.COMPONENT)) throw new AssertionError("idempotent");
        for(String bad:new String[]{"foo/bar;reboot","foo/$(id)","foo/bar:'x'","foo/bar\nreboot"}) {
            try { AccessibilityPolicy.append(bad); throw new AssertionError("unsafe accepted"); } catch(IllegalArgumentException expected) { }
        }
        try { AccessibilityPolicy.user("0;reboot"); throw new AssertionError("bad user"); } catch(IllegalArgumentException expected) { }
        if(!AccessibilityPolicy.user("10\n").equals("10")) throw new AssertionError("user trim");
        String shortQgo="com.anagan.qgo/.service.AppChangeDetectionService";
        if(!AccessibilityPolicy.preserves(AccessibilityPolicy.parse(shortQgo), AccessibilityPolicy.parse(qgo))) throw new AssertionError("normalized QGO retained");
        if(!AccessibilityPolicy.preserves(AccessibilityPolicy.parse(qgo), AccessibilityPolicy.parse(shortQgo))) throw new AssertionError("reverse alias equivalence");
        if(AccessibilityPolicy.preserves(AccessibilityPolicy.parse(qgo), AccessibilityPolicy.parse(AccessibilityPolicy.COMPONENT))) throw new AssertionError("removed QGO must fail");
        if(!AccessibilityPolicy.append(shortQgo).startsWith(shortQgo+":")) throw new AssertionError("raw write spelling preserved");
        System.out.println("14 accessibility policy cases passed");
    }
}
