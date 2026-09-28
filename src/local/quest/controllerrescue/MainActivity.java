package local.quest.controllerrescue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** A small manual rescue tool. Settings never start a repair. */
public class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(16, 22, 20);
    private static final int MINT = Color.rgb(195, 243, 210);
    private static final int SURFACE = Color.rgb(28, 40, 33);
    private final android.os.Handler refreshHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean visible;
    private final Runnable cooldownTick = this::refresh;
    private TextView status;
    private TextView testBadge;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (prefs, key) -> runOnUiThread(this::refresh);

    private SharedPreferences prefs() { return getSharedPreferences("prefs", 0); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        Rescue.recoverInterrupted(this);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(32), dp(38), dp(32), dp(30));
        scroll.addView(page);
        setContentView(scroll);

        TextView title = text("Controller Rescue", 22, MINT);
        title.setTypeface(null, Typeface.BOLD);
        add(page, title, 0);
        add(page, text("Controllers on a break?", 17, Color.LTGRAY), 12);
        testBadge = text("Shortcut test mode", 14, MINT);
        add(page, testBadge, 18);

        Button restore = button("Bring them back", true);
        restore.setOnClickListener(view -> RecoveryService.request(this, "manual"));
        add(page, restore, 28);
        status = text("Ready when you need me.", 16, MINT);
        status.setPadding(dp(18), dp(16), dp(18), dp(16));
        status.setBackground(shape(SURFACE));
        add(page, status, 18);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button check = button("Check connection", false);
        check.setOnClickListener(view -> Rescue.checkStatus(this));
        Button settings = button("Settings", false);
        settings.setOnClickListener(view -> showSettings());
        actions.addView(check, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(settings, new LinearLayout.LayoutParams(0, dp(52), 1));
        add(page, actions, 18);
        refresh();
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(10), dp(24), dp(10));
        Switch shortcut = new Switch(this);
        shortcut.setText("Double Volume Up");
        shortcut.setChecked(prefs().getBoolean("shortcut", false));
        shortcut.setOnCheckedChangeListener((view, enabled) ->
                prefs().edit().putBoolean("shortcut", enabled).apply());
        panel.addView(shortcut);
        Switch down = new Switch(this);
        down.setText("Double Volume Down");
        down.setChecked(prefs().getBoolean("shortcutDown", false));
        down.setOnCheckedChangeListener((view, enabled) ->
                prefs().edit().putBoolean("shortcutDown", enabled).apply());
        panel.addView(down);
        Switch power = new Switch(this);
        power.setText("Double Power · LSPosed add-on");
        power.setChecked(prefs().getBoolean("powerShortcut", false));
        bindPowerToggle(power);
        panel.addView(power);
        Button accessibility = button("Enable volume shortcuts", false);
        accessibility.setOnClickListener(view -> {
            accessibility.setEnabled(false);
            AccessibilitySetup.enable(this, () -> accessibility.setEnabled(true));
        });
        panel.addView(accessibility);
        Switch test = new Switch(this);
        test.setText("Test shortcuts · no repair");
        test.setChecked(prefs().getBoolean("test", false));
        test.setOnCheckedChangeListener((view, enabled) -> {
            prefs().edit().putBoolean("test", enabled).commit();
            refresh();
        });
        panel.addView(test);
        panel.addView(text("Root required. Tracking pauses briefly during recovery.", 14, Color.LTGRAY));
        new AlertDialog.Builder(this).setTitle("Settings").setView(panel)
                .setPositiveButton("Done", null).show();
    }

    private void bindPowerToggle(Switch toggle) {
        toggle.setOnCheckedChangeListener((view, enabled) -> {
            toggle.setEnabled(false);
            PowerSettings.set(this, enabled, () -> {
                toggle.setOnCheckedChangeListener(null);
                toggle.setChecked(prefs().getBoolean("powerShortcut", false));
                toggle.setEnabled(true);
                bindPowerToggle(toggle);
            });
        });
    }

    private TextView text(String label, int size, int color) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(primary ? 19 : 15);
        view.setAllCaps(false);
        view.setTextColor(primary ? BACKGROUND : MINT);
        view.setBackground(shape(primary ? MINT : SURFACE));
        view.setMinHeight(dp(primary ? 64 : 48));
        return view;
    }

    private GradientDrawable shape(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        return background;
    }

    private void add(LinearLayout parent, android.view.View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        parent.addView(child, params);
    }

    private void refresh() {
        refreshHandler.removeCallbacks(cooldownTick);
        String raw = prefs().getString("status", "");
        long deadline = UiStatus.cooldownDeadline(raw, prefs().getLong("last", 0));
        long now = System.currentTimeMillis();
        if (status != null) status.setText(deadline > 0 ? UiStatus.remaining(deadline, now) : UiStatus.friendly(raw));
        if (visible && deadline > now) refreshHandler.postDelayed(cooldownTick, 1000);
        if (testBadge != null) testBadge.setVisibility(prefs().getBoolean("test", false) ?
                android.view.View.VISIBLE : android.view.View.GONE);
    }

    @Override public void onStart() {
        super.onStart();
        visible = true;
        prefs().registerOnSharedPreferenceChangeListener(listener);
        refresh();
    }

    @Override public void onStop() {
        visible = false;
        refreshHandler.removeCallbacks(cooldownTick);
        prefs().unregisterOnSharedPreferenceChangeListener(listener);
        super.onStop();
    }
}
