package local.quest.controllerrescue;

import java.util.LinkedHashSet;
import java.util.Set;

/** Strict component-list parsing prevents settings text becoming shell code. */
final class AccessibilityPolicy {
    static final String COMPONENT = "local.quest.controllerrescue/local.quest.controllerrescue.VolumeService";

    static Set<String> parse(String value) {
        Set<String> entries = new LinkedHashSet<>();
        String text = value.trim();
        if (text.isEmpty() || text.equals("null")) return entries;
        for (String entry : text.split(":", -1)) {
            if (!entry.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) {
                throw new IllegalArgumentException("Unexpected accessibility service list; no changes made.");
            }
            entries.add(entry);
        }
        return entries;
    }

    static Set<String> canonical(Set<String> entries) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            int slash = entry.indexOf('/');
            String packageName = entry.substring(0, slash);
            String className = entry.substring(slash + 1);
            normalized.add(packageName + "/" + (className.startsWith(".") ? packageName + className : className));
        }
        return normalized;
    }

    static boolean preserves(Set<String> before, Set<String> after) {
        return canonical(after).containsAll(canonical(before));
    }

    static String append(String value) {
        Set<String> entries = parse(value);
        if (!entries.contains("local.quest.controllerrescue/.VolumeService")) entries.add(COMPONENT);
        return String.join(":", entries);
    }

    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static String user(String value) {
        String user = value.trim();
        if (!user.matches("[0-9]{1,6}")) throw new IllegalArgumentException("Cannot identify current Android user.");
        return user;
    }
}
