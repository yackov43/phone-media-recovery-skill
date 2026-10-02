package com.yackov.androidsessionbridge;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
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
            "עובר על הזרימה מקצה לקצה ומאתר בדיוק איפה היא נעצרת.",
            "בודק מסכים, כפתורים, מצבים, נגישות וחוויית שימוש.",
            "בודק Pairing, הרשאות, פרטיות, סודות ומשטח תקיפה.",
            "בודק latency, polling, שימוש בזיכרון, CPU וסוללה.",
            "בודק retry, timeout, reconnect והתאוששות מתקלות.",
            "בודק נאמנות למקור, Fit, פיקסלים, DPI ו־PNG סופי.",
            "מריץ מחדש תקלות שכבר תוקנו כדי למנוע חזרה לאחור.",
            "בודק Android ו־ChatGPT יחד על המכשיר הפיזי."
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BridgeApi api = new BridgeApi();
    private final List<CheckBox> agentChecks = new ArrayList<>();

    private LinearLayout sessionCard;
    private LinearLayout codeArea;
    private TextView connectionTitle;
    private TextView connectionDetails;
    private TextView technicalStatus;
    private TextView pairCode;
    private TextView pairInstruction;
    private TextView agentStatus;
    private Button generateCodeButton;
    private Button connectButton;
    private Button newCodeButton;
    private Button disconnectButton;
    private Button accessibilityButton;
    private Button closeButton;

    private volatile boolean serverCheckInFlight = false;
    private long lastServerCheckMs = 0L;
    private String activePairCode = null;
    private String activePairRequestId = null;
    private long activePairExpiresAt = 0L;

    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refreshConnectionUi();
            long now = System.currentTimeMillis();
            if (now - lastServerCheckMs > 3000L && !serverCheckInFlight) {
                lastServerCheckMs = now;
                verifyServerPairing();
            }
            handler.postDelayed(this, 1000L);
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
        root.setAlpha(0f);
        scroll.addView(root, matchWrap());

        root.addView(buildHeader());
        root.addView(buildAboutCard(), cardLp());
        root.addView(buildSessionCard(), cardLp());
        root.addView(buildAgentsCard(), cardLp());

        accessibilityButton = actionButton("הפעל הרשאת שליטה", false);
        accessibilityButton.setOnClickListener(v -> showAccessibilityOnboarding(false));
        root.addView(accessibilityButton, actionLp());

        closeButton = actionButton("הכול מחובר — אפשר לצאת", true);
        closeButton.setOnClickListener(v -> finishAndRemoveTask());
        root.addView(closeButton, actionLp());

        setContentView(scroll);
        refreshConnectionUi();

        root.animate()
                .alpha(1f)
                .setDuration(420L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        handler.postDelayed(() -> {
            if (!isAccessibilityEnabled()) showAccessibilityOnboarding(true);
        }, 550L);
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
        LinearLayout.LayoutParams copyLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLp.setMargins(dp(14), 0, dp(14), 0);

        copy.addView(label("Android Session Bridge", 24, TEXT, true), matchWrap());
        copy.addView(label("Session Control Center · v" + BridgeApi.APP_VERSION, 13, MUTED, false),
                matchWrap());
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
        card.addView(label("מה האפליקציה עושה?", 19, TEXT, true), matchWrap());

        TextView p = label(
                "גשר פרטי בין הסשן שלך ב־ChatGPT לבין הטלפון. " +
                "הוא מאפשר ל־GPT לבצע QA אמיתי על מכשיר פיזי: צילום מסך, קריאת UI, " +
                "לחיצות, הקלדה, בדיקות זרימה ואימות תוצאות.",
                14, MUTED, false);
        p.setLineSpacing(0f, 1.24f);
        LinearLayout.LayoutParams pLp = matchWrap();
        pLp.setMargins(0, dp(9), 0, 0);
        card.addView(p, pLp);

        TextView note = label(
                "אותו GPT של הסשן מפעיל גם את Mini‑Agents. אין OpenAI API ואין מפתח נוסף בתוך ה־APK.",
                12, TEAL, false);
        LinearLayout.LayoutParams nLp = matchWrap();
        nLp.setMargins(0, dp(12), 0, 0);
        card.addView(note, nLp);
        return card;
    }

    private View buildSessionCard() {
        sessionCard = card();

        connectionTitle = label("בודק חיבור…", 24, TEXT, true);
        connectionTitle.setGravity(Gravity.CENTER);
        sessionCard.addView(connectionTitle, matchWrap());

        connectionDetails = label("", 15, MUTED, false);
        connectionDetails.setGravity(Gravity.CENTER);
        connectionDetails.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams dLp = matchWrap();
        dLp.setMargins(0, dp(8), 0, 0);
        sessionCard.addView(connectionDetails, dLp);

        technicalStatus = label("", 12, MUTED, false);
        technicalStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = matchWrap();
        tLp.setMargins(0, dp(9), 0, dp(8));
        sessionCard.addView(technicalStatus, tLp);

        codeArea = new LinearLayout(this);
        codeArea.setOrientation(LinearLayout.VERTICAL);
        codeArea.setVisibility(View.GONE);
        LinearLayout.LayoutParams codeAreaLp = matchWrap();
        codeAreaLp.setMargins(0, dp(4), 0, 0);
        sessionCard.addView(codeArea, codeAreaLp);

        pairCode = label("— — — — — —", 34, TEXT, true);
        pairCode.setGravity(Gravity.CENTER);
        pairCode.setTextIsSelectable(true);
        pairCode.setLetterSpacing(0.08f);
        pairCode.setPadding(0, dp(8), 0, dp(6));
        codeArea.addView(pairCode, matchWrap());

        pairInstruction = label("", 12, MUTED, false);
        pairInstruction.setGravity(Gravity.CENTER);
        pairInstruction.setTextIsSelectable(true);
        codeArea.addView(pairInstruction, matchWrap());

        connectButton = actionButton("התחבר לסשן GPT", true);
        connectButton.setVisibility(View.GONE);
        connectButton.setOnClickListener(v -> connectToChatGptSession());
        LinearLayout.LayoutParams connectLp = matchWrap();
        connectLp.setMargins(0, dp(14), 0, 0);
        codeArea.addView(connectButton, connectLp);

        newCodeButton = actionButton("צור קוד חדש", false);
        newCodeButton.setVisibility(View.GONE);
        newCodeButton.setOnClickListener(v -> generatePairCode());
        LinearLayout.LayoutParams newLp = matchWrap();
        newLp.setMargins(0, dp(8), 0, 0);
        codeArea.addView(newCodeButton, newLp);

        generateCodeButton = actionButton("צור קוד חיבור", true);
        generateCodeButton.setOnClickListener(v -> generatePairCode());
        LinearLayout.LayoutParams genLp = matchWrap();
        genLp.setMargins(0, dp(12), 0, 0);
        sessionCard.addView(generateCodeButton, genLp);

        disconnectButton = actionButton("התנתק מהסשן", false);
        disconnectButton.setTextColor(Color.rgb(254, 202, 202));
        disconnectButton.setBackground(rounded(Color.rgb(72, 24, 34), 14, Color.rgb(127, 29, 29)));
        addPressAnimation(disconnectButton);
        disconnectButton.setVisibility(View.GONE);
        disconnectButton.setOnClickListener(v -> disconnect());
        LinearLayout.LayoutParams discLp = matchWrap();
        discLp.setMargins(0, dp(12), 0, 0);
        sessionCard.addView(disconnectButton, discLp);

        return sessionCard;
    }

    private View buildAgentsCard() {
        LinearLayout card = card();

        card.addView(label("GPT Mini‑Agent Crew", 20, TEXT, true), matchWrap());
        TextView p = label(
                "אלה לא מודלים חיצוניים. אותו GPT של הסשן שלך מריץ כמה passes עצמאיים — " +
                "כל pass מקבל תפקיד, צ'קליסט ודוח נפרד. בחר מי משתתף בסבב.",
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

        Button run = actionButton("הפעל את ה־Mini‑Agents בסשן הזה", true);
        run.setOnClickListener(v -> saveAgentSuite(true));
        LinearLayout.LayoutParams rLp = matchWrap();
        rLp.setMargins(0, dp(8), 0, 0);
        card.addView(run, rLp);

        agentStatus = label("הסוכנים משתמשים באותו GPT ובאותו חיבור של הסשן.", 12, MUTED, false);
        agentStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aLp = matchWrap();
        aLp.setMargins(0, dp(10), 0, 0);
        card.addView(agentStatus, aLp);
        return card;
    }

    private void generatePairCode() {
        generateCodeButton.setEnabled(false);
        newCodeButton.setEnabled(false);
        connectionTitle.setText("יוצר קוד…");
        connectionTitle.setTextColor(BLUE);
        connectionDetails.setText("מכין בקשת Pairing חד־פעמית לסשן.");

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
                long expiry = parseIsoMillis(r.optString("expiresAt", ""));

                activePairCode = code;
                activePairRequestId = requestId.isEmpty() ? null : requestId;
                activePairExpiresAt = expiry > 0 ? expiry : System.currentTimeMillis() + 10 * 60_000L;

                runOnUiThread(() -> {
                    connectionTitle.setText("קוד מוכן");
                    connectionTitle.setTextColor(TEAL);
                    connectionDetails.setText("עכשיו לחץ על „התחבר לסשן GPT”.");
                    pairCode.setText(formatPairCode(code));
                    pairInstruction.setText("הקוד תקף לכ־10 דקות. כפתור ההתחברות שולח אותו ישירות לסשן הפעיל.");
                    generateCodeButton.setVisibility(View.GONE);
                    codeArea.setVisibility(View.VISIBLE);
                    connectButton.setVisibility(View.VISIBLE);
                    newCodeButton.setVisibility(View.VISIBLE);
                    showWithAnimation(codeArea);
                    showWithAnimation(connectButton);
                    newCodeButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connectionTitle.setText("שגיאת יצירת קוד");
                    connectionTitle.setTextColor(RED);
                    connectionDetails.setText(safeMessage(e));
                    generateCodeButton.setEnabled(true);
                });
            }
        });
    }

    private void connectToChatGptSession() {
        if (activePairCode == null || activePairCode.isEmpty()) {
            generatePairCode();
            return;
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityOnboarding(false);
            return;
        }

        String message = "חבר את Android Session Bridge עם הקוד " + activePairCode;
        BridgeAccessibilityService.queueChatGptMessage(this, message);

        connectionTitle.setText("מתחבר לסשן…");
        connectionTitle.setTextColor(BLUE);
        connectionDetails.setText("פותח את ChatGPT ושולח את קוד החיבור לסשן הפעיל.");
        connectButton.setEnabled(false);

        Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (intent == null) {
            connectButton.setEnabled(true);
            connectionDetails.setText("ChatGPT לא נמצא במכשיר.");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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
                    DeviceIdentity.markPaired(this, true);
                    runOnUiThread(() -> {
                        pairCode.setText("✓ CONNECTED");
                        pairInstruction.setText("הסשן אישר את הקוד והחיבור פעיל.");
                        connectButton.setEnabled(true);
                        refreshConnectionUi();
                    });
                } else if (exactRequestExpired) {
                    activePairCode = null;
                    activePairRequestId = null;
                    activePairExpiresAt = 0L;
                    runOnUiThread(() -> {
                        pairCode.setText("פג תוקף");
                        pairInstruction.setText("קוד החיבור לא אושר בזמן. צור קוד חדש.");
                        connectButton.setEnabled(true);
                    });
                }
            } catch (Exception ignored) {
                // Transient network errors must not erase local pairing state.
            } finally {
                serverCheckInFlight = false;
                runOnUiThread(this::refreshConnectionUi);
            }
        });
    }

    private void disconnect() {
        disconnectButton.setEnabled(false);
        connectionTitle.setText("מתנתק…");
        connectionTitle.setTextColor(BLUE);
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
                    pairInstruction.setText("");
                    codeArea.setVisibility(View.GONE);
                    disconnectButton.setVisibility(View.GONE);
                    generateCodeButton.setVisibility(View.VISIBLE);
                    generateCodeButton.setEnabled(true);
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
            agentStatus.setText("בחר לפחות Mini‑Agent אחד.");
            agentStatus.setTextColor(AMBER);
            return;
        }

        Set<String> saved = new HashSet<>();
        for (int i = 0; i < roles.length(); i++) saved.add(roles.optString(i));
        getPreferences(MODE_PRIVATE).edit().putStringSet("agent_roles", saved).apply();

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) {
            agentStatus.setText("הצוות נשמר. חבר קודם את הסשן כדי להפעיל אותו.");
            agentStatus.setTextColor(AMBER);
            return;
        }

        agentStatus.setText(requestRun ? "פותח סבב Mini‑Agents…" : "שומר את צוות הסוכנים…");
        agentStatus.setTextColor(MUTED);

        io.execute(() -> {
            try {
                api.setAgentSuite(id, roles);
                if (!requestRun) {
                    runOnUiThread(() -> {
                        agentStatus.setText("✓ צוות ה־Mini‑Agents נשמר.");
                        agentStatus.setTextColor(GREEN);
                    });
                    return;
                }

                JSONObject run = api.requestAgentRun(id, roles);
                String runId = run.optString("runId", "");
                String message = "הפעל את צוות הבדיקות של Android Session Bridge בסשן הזה" +
                        (runId.isEmpty() ? "" : " עבור Run " + runId);

                BridgeAccessibilityService.queueChatGptMessage(this, message);

                runOnUiThread(() -> {
                    agentStatus.setText("✓ הסבב נוצר. שולח אותו לאותו GPT בסשן הנוכחי…");
                    agentStatus.setTextColor(GREEN);
                    Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    agentStatus.setText("שגיאת Mini‑Agents: " + safeMessage(e));
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
        boolean live = id.paired && accessibility && age < 15_000L;

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
                connectButton.setVisibility(View.GONE);
            } else {
                pairInstruction.setText(
                        "הקוד תקף עוד " + (seconds / 60) + ":" + String.format("%02d", seconds % 60) +
                        ". לחץ „התחבר לסשן GPT”.");
            }
        }

        if (live) {
            connectionTitle.setText("✓ מחובר ומוכן");
            connectionTitle.setTextColor(GREEN);
            connectionDetails.setText(
                    "הסשן יכול לשלוט ולבדוק את המכשיר.\n" +
                    "אפשר לצאת מהאפליקציה — השירות ממשיך לעבוד ברקע.");
            technicalStatus.setText(
                    android.os.Build.MODEL + " · Accessibility פעיל · Heartbeat חי · v" +
                            BridgeApi.APP_VERSION);
            generateCodeButton.setVisibility(View.GONE);
            codeArea.setVisibility(View.GONE);
            disconnectButton.setVisibility(View.VISIBLE);
            disconnectButton.setEnabled(true);
        } else if (id.paired && accessibility) {
            connectionTitle.setText("מחובר · ממתין ל־heartbeat");
            connectionTitle.setTextColor(BLUE);
            connectionDetails.setText("Pairing ונגישות פעילים. ממתין לקשר הבא מהסשן.");
            technicalStatus.setText("ערוץ השליטה מאושר · Accessibility פעיל");
            generateCodeButton.setVisibility(View.GONE);
            codeArea.setVisibility(View.GONE);
            disconnectButton.setVisibility(View.VISIBLE);
            disconnectButton.setEnabled(true);
        } else if (id.paired) {
            connectionTitle.setText("מחובר · צריך הרשאת שליטה");
            connectionTitle.setTextColor(AMBER);
            connectionDetails.setText("החיבור קיים. הפעל את Accessibility פעם אחת כדי לאפשר QA ושליטה.");
            technicalStatus.setText("Pairing פעיל · Accessibility כבוי");
            generateCodeButton.setVisibility(View.GONE);
            codeArea.setVisibility(View.GONE);
            disconnectButton.setVisibility(View.VISIBLE);
            disconnectButton.setEnabled(true);
        } else if (activePairCode != null) {
            connectionTitle.setText("קוד מוכן לחיבור");
            connectionTitle.setTextColor(TEAL);
            connectionDetails.setText("לחץ על כפתור ההתחברות שמופיע מתחת לקוד.");
            technicalStatus.setText(accessibility
                    ? "Accessibility פעיל · מוכן לשלוח את הקוד לסשן"
                    : "יש להפעיל Accessibility לפני החיבור");
            generateCodeButton.setVisibility(View.GONE);
            codeArea.setVisibility(View.VISIBLE);
            connectButton.setVisibility(View.VISIBLE);
            newCodeButton.setVisibility(View.VISIBLE);
            disconnectButton.setVisibility(View.GONE);
        } else {
            connectionTitle.setText("לא מחובר");
            connectionTitle.setTextColor(MUTED);
            connectionDetails.setText("צור קוד חיבור. מיד אחריו יופיע כפתור „התחבר לסשן GPT”.");
            technicalStatus.setText(accessibility
                    ? "Accessibility פעיל · מוכן לחיבור"
                    : "שלב ראשון: הרשאת Accessibility חד־פעמית");
            codeArea.setVisibility(View.GONE);
            generateCodeButton.setVisibility(View.VISIBLE);
            generateCodeButton.setEnabled(true);
            disconnectButton.setVisibility(View.GONE);
        }
    }

    private void showAccessibilityOnboarding(boolean firstLaunch) {
        if (isAccessibilityEnabled()) return;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("הרשאת שליטה חד־פעמית")
                .setMessage(
                        "כדי שהסשן ו־Mini‑Agents יוכלו לבצע QA אמיתי — צילום מסך, " +
                        "קריאת מסכים ולחיצות — צריך להפעיל פעם אחת את Android Session Bridge " +
                        "במסך הנגישות.\n\nAndroid לא מאפשר לאפליקציה לאשר את ההרשאה בעצמה; " +
                        "אתה מאשר אותה פעם אחת במסך המערכת.")
                .setPositiveButton("פתח והפעל הרשאה", (d, which) -> openAccessibilitySettings())
                .setNegativeButton(firstLaunch ? "אחר כך" : "ביטול", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setTextColor(TEAL);
        });
        dialog.show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private JSONArray selectedAgents() {
        JSONArray arr = new JSONArray();
        for (CheckBox cb : agentChecks) if (cb.isChecked()) arr.put(String.valueOf(cb.getTag()));
        return arr;
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
        addPressAnimation(b);
        return b;
    }

    private void addPressAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(90L).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(150L).start();
            }
            return false;
        });
    }

    private void showWithAnimation(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(10));
        v.setVisibility(View.VISIBLE);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
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
