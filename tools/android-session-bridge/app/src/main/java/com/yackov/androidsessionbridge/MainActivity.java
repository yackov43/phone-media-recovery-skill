package com.yackov.androidsessionbridge;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout pairSection;
    private TextView connectionTitle;
    private TextView connectionDetails;
    private TextView technicalStatus;
    private EditText code;
    private Button accessibilityButton;
    private Button closeButton;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshConnectionUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = new TextView(this);
        title.setText("Android Session Bridge");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("שליטה מאובטחת מהסשן שלך ב-ChatGPT");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(71, 85, 105));
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.setMargins(0, dp(8), 0, dp(22));
        root.addView(subtitle, subLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setGravity(Gravity.CENTER);
        card.setBackground(rounded(Color.WHITE, 24));
        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.setMargins(0, 0, 0, dp(18));
        root.addView(card, cardLp);

        connectionTitle = new TextView(this);
        connectionTitle.setTextSize(25);
        connectionTitle.setGravity(Gravity.CENTER);
        connectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(connectionTitle, matchWrap());

        connectionDetails = new TextView(this);
        connectionDetails.setTextSize(16);
        connectionDetails.setGravity(Gravity.CENTER);
        connectionDetails.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams detailsLp = matchWrap();
        detailsLp.setMargins(0, dp(10), 0, 0);
        card.addView(connectionDetails, detailsLp);

        technicalStatus = new TextView(this);
        technicalStatus.setTextSize(13);
        technicalStatus.setGravity(Gravity.CENTER);
        technicalStatus.setTextColor(Color.rgb(100, 116, 139));
        LinearLayout.LayoutParams techLp = matchWrap();
        techLp.setMargins(0, dp(12), 0, 0);
        card.addView(technicalStatus, techLp);

        pairSection = new LinearLayout(this);
        pairSection.setOrientation(LinearLayout.VERTICAL);
        pairSection.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(pairSection, matchWrap());

        TextView pairHelp = new TextView(this);
        pairHelp.setText("הזן את קוד הצימוד שמופיע ב-ChatGPT");
        pairHelp.setTextSize(15);
        pairHelp.setGravity(Gravity.CENTER);
        pairSection.addView(pairHelp, matchWrap());

        code = new EditText(this);
        code.setHint("קוד צימוד בן 6 ספרות");
        code.setGravity(Gravity.CENTER);
        code.setTextSize(24);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams codeLp = matchWrap();
        codeLp.setMargins(0, dp(8), 0, dp(8));
        pairSection.addView(code, codeLp);

        Button pair = new Button(this);
        pair.setText("חבר את הטלפון");
        pair.setOnClickListener(v -> pair());
        pairSection.addView(pair, matchWrap());

        accessibilityButton = new Button(this);
        accessibilityButton.setText("פתח הגדרות נגישות");
        accessibilityButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams accessLp = matchWrap();
        accessLp.setMargins(0, dp(8), 0, 0);
        root.addView(accessibilityButton, accessLp);

        closeButton = new Button(this);
        closeButton.setText("הכול מחובר — אפשר לצאת");
        closeButton.setOnClickListener(v -> finishAndRemoveTask());
        LinearLayout.LayoutParams closeLp = matchWrap();
        closeLp.setMargins(0, dp(10), 0, 0);
        root.addView(closeButton, closeLp);

        setContentView(root);
        refreshConnectionUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    private void pair() {
        String pairingCode = code.getText().toString().trim();
        if (pairingCode.length() < 6) {
            connectionTitle.setText("חסר קוד צימוד");
            connectionTitle.setTextColor(Color.rgb(185, 28, 28));
            connectionDetails.setText("הזן את קוד הצימוד בן 6 הספרות שמופיע בשיחה.");
            return;
        }

        connectionTitle.setText("מתחבר…");
        connectionTitle.setTextColor(Color.rgb(2, 132, 199));
        connectionDetails.setText("מבצע צימוד מאובטח עם הסשן.");

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject r = new BridgeApi().enroll(pairingCode, id, android.os.Build.MODEL);
                boolean ok = r.optBoolean("ok", false);
                if (ok) DeviceIdentity.markPaired(this, true);
                runOnUiThread(() -> {
                    if (ok) {
                        refreshConnectionUi();
                        if (!isAccessibilityEnabled()) {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        }
                    } else {
                        connectionTitle.setText("הצימוד נכשל");
                        connectionTitle.setTextColor(Color.rgb(185, 28, 28));
                        connectionDetails.setText(r.optString("error", "שגיאה לא ידועה"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connectionTitle.setText("שגיאת חיבור");
                    connectionTitle.setTextColor(Color.rgb(185, 28, 28));
                    connectionDetails.setText(e.getMessage() == null ? "לא ניתן להתחבר" : e.getMessage());
                });
            }
        });
    }

    private void refreshConnectionUi() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        boolean accessibility = isAccessibilityEnabled();
        long lastContact = DeviceIdentity.lastContact(this);
        long age = lastContact == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastContact;
        boolean live = id.paired && accessibility && age < 10000L;

        pairSection.setVisibility(id.paired ? View.GONE : View.VISIBLE);
        accessibilityButton.setVisibility(accessibility ? View.GONE : View.VISIBLE);
        closeButton.setVisibility(live ? View.VISIBLE : View.GONE);

        if (live) {
            connectionTitle.setText("✓ מחובר");
            connectionTitle.setTextColor(Color.rgb(5, 150, 105));
            connectionDetails.setText("החיבור לסשן פעיל.\nאפשר לצאת מהאפליקציה — השירות ימשיך לעבוד ברקע.");
            technicalStatus.setText("SM-S908E • Accessibility פעיל • ערוץ השליטה חי");
        } else if (id.paired && accessibility) {
            connectionTitle.setText("מתחבר לסשן…");
            connectionTitle.setTextColor(Color.rgb(2, 132, 199));
            connectionDetails.setText("הצימוד והנגישות פעילים. ממתין לאישור קשר מהשרת.");
            technicalStatus.setText("Accessibility פעיל • ממתין ל-heartbeat");
        } else if (id.paired) {
            connectionTitle.setText("צומד — חסרה הרשאת נגישות");
            connectionTitle.setTextColor(Color.rgb(217, 119, 6));
            connectionDetails.setText("הטלפון כבר צומד. הפעל את Android Session Bridge במסך הנגישות.");
            technicalStatus.setText("Pairing פעיל • Accessibility כבוי");
        } else {
            connectionTitle.setText("לא מחובר עדיין");
            connectionTitle.setTextColor(Color.rgb(71, 85, 105));
            connectionDetails.setText("בצע צימוד חד-פעמי ולאחר מכן הפעל נגישות.");
            technicalStatus.setText("גרסה " + BridgeApi.APP_VERSION);
        }
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;

        ComponentName mine = new ComponentName(this, BridgeAccessibilityService.class);
        String[] parts = enabled.split(":");
        for (String p : parts) {
            ComponentName c = ComponentName.unflattenFromString(p);
            if (mine.equals(c)) return true;
        }
        return false;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), Color.rgb(226, 232, 240));
        return d;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresher);
        io.shutdownNow();
        super.onDestroy();
    }
}
