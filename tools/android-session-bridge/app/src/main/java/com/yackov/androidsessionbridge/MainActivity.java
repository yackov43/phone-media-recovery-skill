package com.yackov.androidsessionbridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 18, 32);
    private static final int CARD = Color.rgb(17, 28, 46);
    private static final int CARD_ALT = Color.rgb(23, 36, 58);
    private static final int TEXT = Color.rgb(248, 250, 252);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int TEAL = Color.rgb(45, 212, 191);
    private static final int BLUE = Color.rgb(56, 189, 248);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int AMBER = Color.rgb(245, 158, 11);
    private static final int RED = Color.rgb(239, 68, 68);

    private static final String[] AGENT_IDS = {
            "flow_qa", "ui_ux", "security", "performance",
            "reliability", "print_fidelity", "regression", "mobile_integration"
    };

    private static final String[] AGENT_TITLES = {
            "Flow QA", "UI / UX", "Security", "Performance",
            "Reliability", "Print Fidelity", "Regression", "Mobile Integration"
    };

    private static final String[] AGENT_DESCRIPTIONS = {
            "עובר על כל הזרימה מקצה לקצה ומאתר נקודות עצירה.",
            "בודק מסכים, כפתורים, שגיאות, נגישות וחוויית שימוש.",
            "בודק הרשאות, Pairing, סודות, פרטיות ומשטח תקיפה.",
            "בודק latency, polling, שימוש בזיכרון, CPU וסוללה.",
            "בודק reconnect, timeout, retry והתאוששות מתקלות.",
            "בודק נאמנות למקור, Fit, פיקסלים, DPI ו־PNG סופי.",
            "מריץ מחדש תקלות שכבר נפתרו כדי למנוע חזרה לאחור.",
            "בודק התנהגות ספציפית של ChatGPT ו־Android במכשיר אמיתי."
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BridgeApi api = new BridgeApi();
    private final List<CheckBox> agentChecks = new ArrayList<>();

    private TextView connectionTitle;
    private TextView connectionDetails;
    private TextView technicalStatus;
    private TextView pairCode;
    private TextView pairInstruction;
    private TextView agentStatus;
    private Button accessibilityButton;
    private Button disconnectButton;
    private Button closeButton;
    private Button generateCodeButton;

    private volatile boolean serverCheckInFlight = false;
    private long lastServerCheckMs = 0L;
    private String activePairCode = null;
    private String activePairRequestId = null;
    private long activePairExpiresAt = 0L;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshConnectionUi();
            long now = System.currentTimeMillis();
            if (now - lastServerCheckMs > 3500L && !serverCheckInFlight) {
                lastServerCheckMs = now;
                verifyServerPairing();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, matchWrap());

        root.addView(buildHeader());
        root.addView(buildAboutCard(), cardLp());
        root.addView(buildConnectionCard(), cardLp());
        root.addView(buildPairingCard(), cardLp());
        root.addView(buildAgentsCard(), cardLp());

        accessibilityButton = actionButton("פתח הגדרות נגישות", false);
        accessibilityButton.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton, actionLp());

        closeButton = actionButton("הכול מחובר — אפשר לצאת", true);
        closeButton.setOnClickListener(v -> finishAndRemoveTask());
        root.addView(closeButton, actionLp());

        setContentView(scroll);
        refreshConnectionUi();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(18));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        icon.setBackground(rounded(CARD_ALT, 18, Color.rgb(35, 53, 80)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(66), dp(66)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.setMargins(dp(14), 0, dp(14), 0);

        TextView title = label("Android Session Bridge", 24, TEXT, true);
        copy.addView(title, matchWrap());

        TextView sub = label("Control Center · v" + BridgeApi.APP_VERSION, 13, MUTED, false);
        copy.addView(sub, matchWrap());
        header.addView(copy, copyLp);

        TextView live = label("PRIVATE", 11, TEAL, true);
        live.setGravity(Gravity.CENTER);
        live.setPadding(dp(9), dp(6), dp(9), dp(6));
        live.setBackground(rounded(Color.rgb(15, 62, 67), 99, Color.rgb(28, 91, 91)));
        header.addView(live, wrapWrap());
        return header;
    }

    private View buildAboutCard() {
        LinearLayout card = card();
        TextView h = label("מה האפליקציה עושה?", 19, TEXT, true);
        card.addView(h, matchWrap());

        TextView p = label(
                "האפליקציה יוצרת ערוץ פרטי ומאושר בין הסשן שלך ב־ChatGPT לבין הטלפון. " +
                "היא מאפשרת צילום מסך, קריאת UI, לחיצות, הקלדה ובדיקות QA על מכשיר אמיתי — " +
                "בלי Termux, בלי SSH ובלי tunnel נכנס.",
                14, MUTED, false);
        p.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams pLp = matchWrap();
        pLp.setMargins(0, dp(10), 0, 0);
        card.addView(p, pLp);

        TextView note = label("מפתח OpenAI אינו נשמר ב־APK. הסשן המחובר הוא מנוע ה־GPT.", 12, TEAL, false);
        LinearLayout.LayoutParams nLp = matchWrap();
        nLp.setMargins(0, dp(12), 0, 0);
        card.addView(note, nLp);
        return card;
    }

    private View buildConnectionCard() {
        LinearLayout card = card();

        connectionTitle = label("בודק חיבור…", 24, TEXT, true);
        connectionTitle.setGravity(Gravity.CENTER);
        card.addView(connectionTitle, matchWrap());

        connectionDetails = label("", 15, MUTED, false);
        connectionDetails.setGravity(Gravity.CENTER);
        connectionDetails.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams dLp = matchWrap();
        dLp.setMargins(0, dp(9), 0, 0);
        card.addView(connectionDetails, dLp);

        technicalStatus = label("", 12, MUTED, false);
        technicalStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = matchWrap();
        tLp.setMargins(0, dp(10), 0, 0);
        card.addView(technicalStatus, tLp);

        disconnectButton = actionButton("התנתק מהמכשיר", false);
        disconnectButton.setTextColor(Color.rgb(254, 202, 202));
        disconnectButton.setBackground(rounded(Color.rgb(72, 24, 34), 14, Color.rgb(127, 29, 29)));
        disconnectButton.setOnClickListener(v -> disconnect());
        LinearLayout.LayoutParams bLp = matchWrap();
        bLp.setMargins(0, dp(14), 0, 0);
        card.addView(disconnectButton, bLp);

        return card;
    }

    private View buildPairingCard() {
        LinearLayout card = card();

        card.addView(label("Pairing מתוך האפליקציה", 19, TEXT, true), matchWrap());
        TextView info = label(
                "אין צורך לבקש מ־GPT שימציא קוד. לחץ כאן והאפליקציה תקבל קוד חד־פעמי מהשרת. " +
                "את הקוד הזה מוסרים לסשן שרוצים לחבר.",
                13, MUTED, false);
        info.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams iLp = matchWrap();
        iLp.setMargins(0, dp(8), 0, dp(12));
        card.addView(info, iLp);

        pairCode = label("— — — — — —", 34, TEXT, true);
        pairCode.setGravity(Gravity.CENTER);
        pairCode.setTextIsSelectable(true);
        pairCode.setLetterSpacing(0.08f);
        pairCode.setPadding(0, dp(10), 0, dp(8));
        card.addView(pairCode, matchWrap());

        pairInstruction = label("צור קוד כדי לחבר סשן חדש.", 12, MUTED, false);
        pairInstruction.setGravity(Gravity.CENTER);
        pairInstruction.setTextIsSelectable(true);
        card.addView(pairInstruction, matchWrap());

        generateCodeButton = actionButton("צור קוד חיבור", true);
        generateCodeButton.setOnClickListener(v -> generatePairCode());
        LinearLayout.LayoutParams gLp = matchWrap();
        gLp.setMargins(0, dp(14), 0, 0);
        card.addView(generateCodeButton, gLp);

        Button copy = actionButton("העתק הוראת חיבור", false);
        copy.setOnClickListener(v -> copyPairInstruction());
        LinearLayout.LayoutParams cLp = matchWrap();
        cLp.setMargins(0, dp(8), 0, 0);
        card.addView(copy, cLp);

        return card;
    }

    private View buildAgentsCard() {
        LinearLayout card = card();

        card.addView(label("GPT Agent Crew", 20, TEXT, true), matchWrap());
        TextView p = label(
                "כל Agent מקבל תפקיד קבוע ומדווח בנפרד. האפליקציה שומרת את ההרכב; " +
                "הסשן המחובר מבצע את הבדיקות בפועל על המכשיר ועל PrintMaster.",
                13, MUTED, false);
        p.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams pLp = matchWrap();
        pLp.setMargins(0, dp(8), 0, dp(10));
        card.addView(p, pLp);

        Set<String> saved = getPreferences(MODE_PRIVATE)
                .getStringSet("agent_roles", new HashSet<>(Arrays.asList(AGENT_IDS)));

        for (int i = 0; i < AGENT_IDS.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackground(rounded(CARD_ALT, 12, Color.rgb(35, 53, 80)));

            CheckBox cb = new CheckBox(this);
            cb.setText(AGENT_TITLES[i]);
            cb.setTextColor(TEXT);
            cb.setTextSize(15);
            cb.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cb.setChecked(saved.contains(AGENT_IDS[i]));
            cb.setTag(AGENT_IDS[i]);
            row.addView(cb, matchWrap());
            agentChecks.add(cb);

            TextView desc = label(AGENT_DESCRIPTIONS[i], 12, MUTED, false);
            LinearLayout.LayoutParams descLp = matchWrap();
            descLp.setMargins(dp(34), 0, dp(34), 0);
            row.addView(desc, descLp);

            LinearLayout.LayoutParams rowLp = matchWrap();
            rowLp.setMargins(0, dp(6), 0, 0);
            card.addView(row, rowLp);
        }

        Button save = actionButton("שמור צוות סוכנים", false);
        save.setOnClickListener(v -> saveAgentSuite(false));
        LinearLayout.LayoutParams sLp = matchWrap();
        sLp.setMargins(0, dp(14), 0, 0);
        card.addView(save, sLp);

        Button run = actionButton("הפעל צוות בדיקות ב־ChatGPT", true);
        run.setOnClickListener(v -> saveAgentSuite(true));
        LinearLayout.LayoutParams rLp = matchWrap();
        rLp.setMargins(0, dp(8), 0, 0);
        card.addView(run, rLp);

        agentStatus = label("כל הסוכנים מסומנים כברירת מחדל.", 12, MUTED, false);
        agentStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aLp = matchWrap();
        aLp.setMargins(0, dp(10), 0, 0);
        card.addView(agentStatus, aLp);
        return card;
    }

    private void generatePairCode() {
        generateCodeButton.setEnabled(false);
        pairCode.setText("מייצר…");
        pairInstruction.setText("יוצר בקשת Pairing מאובטחת…");
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        JSONArray agents = selectedAgents();

        io.execute(() -> {
            try {
                JSONObject r = api.beginPairing(id, android.os.Build.MODEL, agents);
                if (!r.optBoolean("ok", false)) {
                    throw new IllegalStateException(r.optString("error", "pairing_failed"));
                }
                String code = r.optString("code", "");
                String requestId = r.optString("requestId", "");
                String instruction = r.optString("instruction",
                        "ב־ChatGPT כתוב: חבר את Android Session Bridge עם הקוד " + code);
                long expiry = parseIsoMillis(r.optString("expiresAt", ""));
                activePairCode = code;
                activePairRequestId = requestId.isEmpty() ? null : requestId;
                activePairExpiresAt = expiry > 0 ? expiry : System.currentTimeMillis() + 10 * 60_000L;
                copyToClipboard("Android Session Bridge", instruction);

                runOnUiThread(() -> {
                    pairCode.setText(formatPairCode(code));
                    pairInstruction.setText(instruction + "\nהקוד הועתק ללוח והוא תקף לכ־10 דקות.");
                    generateCodeButton.setText("צור קוד חדש");
                    generateCodeButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pairCode.setText("שגיאה");
                    pairInstruction.setText(safeMessage(e));
                    generateCodeButton.setEnabled(true);
                });
            }
        });
    }

    private void copyPairInstruction() {
        if (activePairCode == null || activePairCode.isEmpty()) {
            pairInstruction.setText("צור קודם קוד חיבור.");
            return;
        }
        String instruction = "ב־ChatGPT כתוב: חבר את Android Session Bridge עם הקוד " + activePairCode;
        copyToClipboard("Android Session Bridge", instruction);
        pairInstruction.setText(instruction + "\n✓ הועתק ללוח");
    }

    private void verifyServerPairing() {
        serverCheckInFlight = true;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject r = api.pairStatus(id, activePairRequestId);
                boolean paired = r.optBoolean("paired", false);
                boolean exactRequestAccepted = activePairRequestId == null
                        ? paired
                        : r.optBoolean("accepted", false);
                boolean exactRequestExpired = activePairRequestId != null
                        && r.optBoolean("expired", false);

                if (paired && !id.paired) DeviceIdentity.markPaired(this, true);
                if (!paired && id.paired && activePairRequestId == null) {
                    DeviceIdentity.markDisconnected(this);
                }

                if (exactRequestAccepted && activePairCode != null) {
                    activePairCode = null;
                    activePairRequestId = null;
                    activePairExpiresAt = 0L;
                    runOnUiThread(() -> {
                        pairCode.setText("✓ CONNECTED");
                        pairInstruction.setText("הקוד הספציפי אושר והטלפון מחובר לסשן.");
                    });
                } else if (exactRequestExpired) {
                    activePairCode = null;
                    activePairRequestId = null;
                    activePairExpiresAt = 0L;
                    runOnUiThread(() -> {
                        pairCode.setText("פג תוקף");
                        pairInstruction.setText("קוד החיבור לא אושר בזמן. צור קוד חדש.");
                    });
                }
            } catch (Exception ignored) {
                // Keep the local state on transient network errors.
            } finally {
                serverCheckInFlight = false;
                runOnUiThread(this::refreshConnectionUi);
            }
        });
    }

    private void disconnect() {
        disconnectButton.setEnabled(false);
        connectionTitle.setText("מתנתק…");
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);

        io.execute(() -> {
            try {
                api.disconnect(id);
                DeviceIdentity.markDisconnected(this);
                runOnUiThread(() -> {
                    activePairCode = null;
                    activePairRequestId = null;
                    activePairExpiresAt = 0L;
                    pairCode.setText("— — — — — —");
                    pairInstruction.setText("החיבור נותק. צור קוד חדש כדי לחבר סשן.");
                    refreshConnectionUi();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connectionDetails.setText("ההתנתקות נכשלה: " + safeMessage(e));
                    disconnectButton.setEnabled(true);
                });
            }
        });
    }

    private void saveAgentSuite(boolean requestRun) {
        JSONArray roles = selectedAgents();
        if (roles.length() == 0) {
            agentStatus.setText("בחר לפחות Agent אחד.");
            agentStatus.setTextColor(AMBER);
            return;
        }

        Set<String> saved = new HashSet<>();
        for (int i = 0; i < roles.length(); i++) saved.add(roles.optString(i));
        getPreferences(MODE_PRIVATE).edit().putStringSet("agent_roles", saved).apply();

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) {
            agentStatus.setText("הצוות נשמר מקומית. חבר את הטלפון כדי להפעיל אותו.");
            agentStatus.setTextColor(AMBER);
            return;
        }

        agentStatus.setText(requestRun ? "מכין סבב בדיקות…" : "שומר את צוות הסוכנים…");
        agentStatus.setTextColor(MUTED);

        io.execute(() -> {
            try {
                api.setAgentSuite(id, roles);
                if (!requestRun) {
                    runOnUiThread(() -> {
                        agentStatus.setText("✓ צוות הסוכנים נשמר בשרת.");
                        agentStatus.setTextColor(GREEN);
                    });
                    return;
                }

                JSONObject run = api.requestAgentRun(id, roles);
                String runId = run.optString("runId", "");
                String command = "הפעל את צוות הבדיקות של Android Session Bridge";
                copyToClipboard("Agent Crew", command);
                runOnUiThread(() -> {
                    agentStatus.setText("✓ נפתחה בקשת Agent Crew" +
                            (runId.isEmpty() ? "" : " · " + runId.substring(0, Math.min(8, runId.length()))) +
                            "\nפותח את ChatGPT. הפקודה הועתקה ללוח.");
                    agentStatus.setTextColor(GREEN);
                    openChatGpt();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    agentStatus.setText("שגיאת Agent Crew: " + safeMessage(e));
                    agentStatus.setTextColor(RED);
                });
            }
        });
    }

    private void refreshConnectionUi() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        boolean accessibility = isAccessibilityEnabled();
        long lastContact = DeviceIdentity.lastContact(this);
        long age = lastContact == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - lastContact;
        boolean live = id.paired && accessibility && age < 12_000L;

        disconnectButton.setVisibility(id.paired ? View.VISIBLE : View.GONE);
        disconnectButton.setEnabled(true);
        accessibilityButton.setVisibility(accessibility ? View.GONE : View.VISIBLE);
        closeButton.setVisibility(live ? View.VISIBLE : View.GONE);

        if (activePairCode != null && activePairExpiresAt > 0) {
            long seconds = Math.max(0, (activePairExpiresAt - System.currentTimeMillis()) / 1000L);
            if (seconds <= 0) {
                activePairCode = null;
                activePairRequestId = null;
                activePairExpiresAt = 0L;
                pairCode.setText("פג תוקף");
                pairInstruction.setText("צור קוד חדש.");
            } else {
                pairInstruction.setText(
                        "ב־ChatGPT כתוב: חבר את Android Session Bridge עם הקוד " + activePairCode +
                        "\nתוקף: " + (seconds / 60) + ":" + String.format("%02d", seconds % 60));
            }
        }

        if (live) {
            connectionTitle.setText("✓ מחובר ומוכן");
            connectionTitle.setTextColor(GREEN);
            connectionDetails.setText(
                    "הסשן יכול לבצע בדיקות ושליטה על המכשיר.\n" +
                    "אפשר לצאת מהאפליקציה — השירות ממשיך לעבוד ברקע.");
            technicalStatus.setText(
                    android.os.Build.MODEL + " · Accessibility פעיל · Heartbeat חי · v" +
                            BridgeApi.APP_VERSION);
        } else if (id.paired && accessibility) {
            connectionTitle.setText("מחובר · ממתין ל־heartbeat");
            connectionTitle.setTextColor(BLUE);
            connectionDetails.setText("Pairing ונגישות פעילים. ממתין לקשר הבא מהסשן.");
            technicalStatus.setText("ערוץ השליטה מאושר · Accessibility פעיל");
        } else if (id.paired) {
            connectionTitle.setText("מחובר · חסרה נגישות");
            connectionTitle.setTextColor(AMBER);
            connectionDetails.setText("ה־Pairing קיים, אבל צריך להפעיל את שירות הנגישות.");
            technicalStatus.setText("Pairing פעיל · Accessibility כבוי");
        } else {
            connectionTitle.setText("לא מחובר");
            connectionTitle.setTextColor(MUTED);
            connectionDetails.setText("צור קוד חיבור מתוך האפליקציה וחבר אותו לסשן ChatGPT.");
            technicalStatus.setText("מזהה המכשיר נשמר מקומית · אין סשן פעיל");
        }
    }

    private JSONArray selectedAgents() {
        JSONArray arr = new JSONArray();
        for (CheckBox cb : agentChecks) if (cb.isChecked()) arr.put(String.valueOf(cb.getTag()));
        return arr;
    }

    private void openChatGpt() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } else {
            agentStatus.setText("ChatGPT לא נמצא במכשיר. בקשת ה־Agent Crew נשמרה בשרת.");
            agentStatus.setTextColor(AMBER);
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

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(rounded(CARD, 20, Color.rgb(35, 53, 80)));
        return card;
    }

    private TextView label(String value, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button actionButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(primary ? Color.rgb(6, 25, 35) : TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(11), dp(12), dp(11));
        b.setBackground(primary
                ? rounded(TEAL, 14, TEAL)
                : rounded(CARD_ALT, 14, Color.rgb(51, 65, 85)));
        return b;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(8));
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private String formatPairCode(String code) {
        if (code == null || code.length() != 6) return code == null ? "" : code;
        return code.substring(0, 3) + "  " + code.substring(3);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private long parseIsoMillis(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresher);
        io.shutdownNow();
        super.onDestroy();
    }
}
