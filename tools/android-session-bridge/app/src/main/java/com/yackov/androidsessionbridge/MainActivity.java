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
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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

    private static final String SCAN_PREFS = "android_session_bridge_scan";
    private static final String KEY_SCAN_PENDING = "scan_pending";
    private static final String KEY_SCAN_NONCE = "scan_nonce";
    private static final String KEY_SCAN_STARTED = "scan_started";
    private static final long SCAN_TIMEOUT_MS = 1_800_000L;

    private static final String[] AGENT_IDS = {
            "flow_qa", "ui_ux", "security", "performance",
            "reliability", "artifact_integrity", "regression", "mobile_integration",
            "observability", "release_guard"
    };

    private static final String[] AGENT_TITLES = {
            "Flow QA", "UI / UX", "Security", "Performance",
            "Reliability", "Artifact Integrity", "Regression", "Mobile Integration",
            "Observability", "Release Guard"
    };

    private static final String[] AGENT_DESCRIPTIONS = {
            "עובר על הזרימה מקצה לקצה ומאתר בדיוק איפה היא נעצרת.",
            "בודק מסכים, כפתורים, מצבים, נגישות וחוויית שימוש.",
            "בודק Pairing, הרשאות, פרטיות, סודות ומשטח תקיפה.",
            "בודק latency, polling, שימוש בזיכרון, CPU וסוללה.",
            "בודק retry, timeout, reconnect והתאוששות מתקלות.",
            "בודק שלמות קבצים, תמונות, attachments, metadata ותוצר סופי.",
            "מריץ מחדש תקלות שכבר תוקנו כדי למנוע חזרה לאחור.",
            "בודק Android ו־ChatGPT יחד על המכשיר הפיזי.",
            "בודק logs, correlation IDs, heartbeats, state drift וסימני תקלה.",
            "חוסם Release אם build, signing, E2E או regression gate עדיין לא עברו."
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BridgeApi api = new BridgeApi();
    private final List<CheckBox> agentChecks = new ArrayList<>();

    private LinearLayout sessionCard;
    private LinearLayout codeArea;
    private LinearLayout agentRowsContainer;
    private LinearLayout sessionsRowsContainer;
    private LinearLayout discoveredRowsContainer;
    private TextView sessionsStatus;
    private JSONArray agentRegistryCache = new JSONArray();
    private JSONArray sessionsCache = new JSONArray();
    private JSONArray discoveredChatsCache = new JSONArray();
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
    private Button currentSessionButton;
    private LinearLayout currentSessionActions;
    private Button currentAgentsButton;
    private Button currentRunButton;
    private Button currentRenameButton;
    private Button deviceDisconnectButton;
    private Button accessibilityButton;
    private Button closeButton;
    private LinearLayout scanReceiverContainer;
    private EditText scanImportInput;
    private Button scanImportButton;

    private String currentSessionId = null;
    private String currentSessionTitle = null;
    private String currentSessionChatKey = null;
    private volatile boolean pendingCurrentSessionDiscovery = false;
    private volatile boolean currentSessionPairingInFlight = false;

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
        root.addView(buildMultiSessionCard(), cardLp());

        accessibilityButton = actionButton("הפעל הרשאת שליטה", false);
        accessibilityButton.setOnClickListener(v -> showAccessibilityOnboarding(false));
        root.addView(accessibilityButton, actionLp());

        closeButton = actionButton("הכול מחובר — אפשר לצאת", true);
        closeButton.setOnClickListener(v -> exitToHome());
        LinearLayout.LayoutParams closeLp = actionLp();
        closeLp.setMargins(0, dp(8), 0, dp(14));
        root.addView(closeButton, closeLp);

        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            root.setPadding(dp(18), dp(18), dp(18), dp(28) + bottom);
            return insets;
        });
        scroll.requestApplyInsets();
        syncPendingPairingFromStorage();
        refreshConnectionUi();
        loadAgentRegistry();
        loadSessions();

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

        TextView section = label("Current GPT Session", 13, TEAL, true);
        section.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sectionLp = matchWrap();
        sectionLp.setMargins(0, 0, 0, dp(8));
        sessionCard.addView(section, sectionLp);

        connectionTitle = label("בודק חיבור…", 24, TEXT, true);
        connectionTitle.setGravity(Gravity.CENTER);
        connectionTitle.setMaxLines(2);
        connectionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

        // First-time device pairing only. After the device is paired this area disappears.
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

        connectButton = actionButton("אשר חיבור מכשיר", true);
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

        generateCodeButton = actionButton("חבר את המכשיר בפעם הראשונה", true);
        generateCodeButton.setOnClickListener(v -> generatePairCode());
        LinearLayout.LayoutParams genLp = matchWrap();
        genLp.setMargins(0, dp(12), 0, 0);
        sessionCard.addView(generateCodeButton, genLp);

        currentSessionButton = actionButton("התחבר לסשן הנוכחי", true);
        currentSessionButton.setVisibility(View.GONE);
        currentSessionButton.setOnClickListener(v -> {
            if (currentSessionId != null && !currentSessionId.isEmpty()) {
                updateSessionState(currentSessionId, "disconnected");
            } else {
                beginCurrentSessionConnection();
            }
        });
        LinearLayout.LayoutParams currentLp = matchWrap();
        currentLp.setMargins(0, dp(14), 0, 0);
        sessionCard.addView(currentSessionButton, currentLp);

        currentSessionActions = new LinearLayout(this);
        currentSessionActions.setOrientation(LinearLayout.HORIZONTAL);
        currentSessionActions.setGravity(Gravity.CENTER);
        currentSessionActions.setVisibility(View.GONE);

        currentAgentsButton = compactButton("Agents", false, false);
        currentAgentsButton.setOnClickListener(v -> {
            JSONObject session = findSessionById(currentSessionId);
            if (session != null) configureSessionAgents(session);
        });
        currentSessionActions.addView(currentAgentsButton, weightedButtonLp());

        currentRunButton = compactButton("Run", false, false);
        currentRunButton.setOnClickListener(v -> {
            JSONObject session = findSessionById(currentSessionId);
            if (session != null) requestSessionAgentRun(session);
        });
        currentSessionActions.addView(currentRunButton, weightedButtonLp());

        currentRenameButton = compactButton("Rename", false, false);
        currentRenameButton.setOnClickListener(v -> {
            JSONObject session = findSessionById(currentSessionId);
            if (session != null) promptRenameSession(session);
        });
        currentSessionActions.addView(currentRenameButton, weightedButtonLp());

        LinearLayout.LayoutParams currentActionsLp = matchWrap();
        currentActionsLp.setMargins(0, dp(10), 0, 0);
        sessionCard.addView(currentSessionActions, currentActionsLp);

        deviceDisconnectButton = actionButton("נתק מכשיר", false);
        deviceDisconnectButton.setVisibility(View.GONE);
        deviceDisconnectButton.setOnClickListener(v -> disconnect());
        LinearLayout.LayoutParams deviceLp = matchWrap();
        deviceLp.setMargins(0, dp(8), 0, 0);
        sessionCard.addView(deviceDisconnectButton, deviceLp);

        // Legacy field kept for source compatibility; no longer shown as a second disconnect control.
        disconnectButton = deviceDisconnectButton;
        return sessionCard;
    }

    private View buildMultiSessionCard() {
        LinearLayout card = card();
        card.addView(label("GPT Sessions", 20, TEXT, true), matchWrap());

        TextView info = label(
                "כל שיחת ChatGPT היא ערוץ מבודד. לכל Session יש חיבור משלו, Agents משלו, Run-ים משלו ו־Lease משלו. " +
                "אין צוות גלובלי ואין זליגה בין Sessions.",
                13, MUTED, false);
        info.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams infoLp = matchWrap();
        infoLp.setMargins(0, dp(8), 0, dp(10));
        card.addView(info, infoLp);

        Button scan = actionButton("סרוק ורענן שיחות ChatGPT", true);
        scan.setOnClickListener(v -> scanChatGptSessions());
        card.addView(scan, matchWrap());

        scanReceiverContainer = new LinearLayout(this);
        scanReceiverContainer.setOrientation(LinearLayout.VERTICAL);
        scanReceiverContainer.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable scanReceiverBg = new GradientDrawable();
        scanReceiverBg.setColor(CARD_ALT);
        scanReceiverBg.setCornerRadius(dp(14));
        scanReceiverContainer.setBackground(scanReceiverBg);
        scanReceiverContainer.setVisibility(View.GONE);

        TextView scanReceiverTitle = label("GPT Visual Scan Receiver", 14, TEAL, true);
        scanReceiverContainer.addView(scanReceiverTitle, matchWrap());

        TextView scanReceiverInfo = label(
                "האזור הזה מוזן אוטומטית על ידי GPT לאחר סריקה חזותית של רשימת השיחות. אין צורך להקליד כאן ידנית.",
                11, MUTED, false);
        LinearLayout.LayoutParams scanInfoLp = matchWrap();
        scanInfoLp.setMargins(0, dp(4), 0, dp(8));
        scanReceiverContainer.addView(scanReceiverInfo, scanInfoLp);

        scanImportInput = new EditText(this);
        scanImportInput.setHint("Scan Import JSON");
        scanImportInput.setTextColor(TEXT);
        scanImportInput.setHintTextColor(MUTED);
        scanImportInput.setTextSize(12f);
        scanImportInput.setMinLines(3);
        scanImportInput.setMaxLines(10);
        scanImportInput.setGravity(Gravity.TOP | Gravity.START);
        scanImportInput.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable scanInputBg = new GradientDrawable();
        scanInputBg.setColor(BG);
        scanInputBg.setCornerRadius(dp(10));
        scanImportInput.setBackground(scanInputBg);
        scanReceiverContainer.addView(scanImportInput, matchWrap());

        scanImportButton = compactButton("אשר סריקה", true, false);
        scanImportButton.setOnClickListener(v -> importVisualScan());
        LinearLayout.LayoutParams scanImportLp = matchWrap();
        scanImportLp.setMargins(0, dp(8), 0, 0);
        scanReceiverContainer.addView(scanImportButton, scanImportLp);

        LinearLayout.LayoutParams receiverLp = matchWrap();
        receiverLp.setMargins(0, dp(10), 0, 0);
        card.addView(scanReceiverContainer, receiverLp);

        sessionsRowsContainer = new LinearLayout(this);
        sessionsRowsContainer.setOrientation(LinearLayout.VERTICAL);
        discoveredRowsContainer = sessionsRowsContainer;
        TextView loading = label("טוען את רשימת ה־Sessions…", 13, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams listLp = matchWrap();
        listLp.setMargins(0, dp(12), 0, 0);
        sessionsRowsContainer.addView(loading, matchWrap());
        card.addView(sessionsRowsContainer, listLp);

        sessionsStatus = label("Session Isolation פעיל.", 12, TEAL, false);
        sessionsStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.setMargins(0, dp(10), 0, 0);
        card.addView(sessionsStatus, statusLp);
        return card;
    }

    private void loadSessions() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired || sessionsRowsContainer == null) {
            if (sessionsRowsContainer != null) {
                sessionsRowsContainer.removeAllViews();
                TextView empty = label("חבר קודם את המכשיר כדי לנהל GPT Sessions.", 13, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                sessionsRowsContainer.addView(empty, matchWrap());
            }
            return;
        }

        io.execute(() -> {
            try {
                JSONObject response = api.listSessions(id);
                JSONArray sessions = response.optJSONArray("sessions");
                if (!response.optBoolean("ok", false) || sessions == null) {
                    throw new IllegalStateException(response.optString("error", "session_list_failed"));
                }
                sessionsCache = sessions;
                currentSessionId = response.optString("currentSessionId", null);
                if (currentSessionId != null && currentSessionId.isEmpty()) currentSessionId = null;
                JSONObject current = findSessionById(currentSessionId);
                currentSessionTitle = current == null ? null :
                        current.optString("chat_title", current.optString("label", "GPT Session"));
                currentSessionChatKey = current == null ? null : current.optString("chat_key", null);
                runOnUiThread(() -> {
                    refreshConnectionUi();
                    loadDiscoveredChats();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת טעינת Sessions: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private void scanChatGptSessions() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) {
            sessionsStatus.setText("חבר קודם את המכשיר.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityOnboarding(false);
            return;
        }
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            sessionsStatus.setText("חבר קודם את Current GPT Session.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }

        clearVisualScanState();
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        sessionsStatus.setText("מכין סריקה חזותית מקומית…");
        sessionsStatus.setTextColor(BLUE);

        String instruction =
                "@Android Session Bridge הסריקה המקומית מוכנה. " +
                "אל תבקש מהמשתמש שום פעולה. אל תקרא ל-tap, swipe, open_app או android_session_manager. " +
                "השתמש אך ורק ב-android_control action=screenshot עם sessionId " + currentSessionId + ". " +
                "האפליקציה עצמה פותחת את Sidebar, מחזירה אותו לראש וגוללת לעמוד הבא אחרי כל screenshot. " +
                "בכל תשובת screenshot בדוק result.visualScan. אם ready=false, קרא screenshot שוב. " +
                "כאשר ready=true, קרא חזותית מהתמונה רק כותרות שיחה; אל תשתמש בטקסט Accessibility. " +
                "אל תכלול Chat, Codex, Explore, Scheduled, Search, New chat, Projects, GPTs, Sources, See all, Library, Images או Settings. " +
                "בצע dedupe לפי הכותרת המדויקת והמשך לקרוא screenshot עד result.visualScan.final=true. " +
                "לאחר final=true שמור את הרשימה דרך android_control action=status באותו sessionId. " +
                "אם כל הטקסט קצר מ-3500 תווים, שלח text שמתחיל בדיוק visual_scan_submit:" + nonce +
                " ואז newline ואז כותרת אחת בכל שורה. " +
                "אם הוא ארוך יותר, חלק לעד 2800 תווים בכל חלק ושלח כל חלק עם text שמתחיל visual_scan_chunk:" + nonce +
                ":INDEX:TOTAL ואז newline ותוכן החלק, כאשר INDEX מתחיל ב-0; בסוף שלח status נוסף עם text=visual_scan_finalize:" + nonce + ". " +
                "אל תנווט במכשיר בעצמך ואל תבקש מהמשתמש דבר. לאחר השמירה האפליקציה תחזור לעצמה אוטומטית.";

        BridgeAccessibilityService.queueLocalVisualScan(
                this, nonce, currentSessionId, instruction);

        Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (intent == null) {
            sessionsStatus.setText("ChatGPT לא נמצא במכשיר.");
            sessionsStatus.setTextColor(RED);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void armVisualScanReceiver() {
        if (scanReceiverContainer == null || scanImportInput == null) return;
        boolean pending = getSharedPreferences(SCAN_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_SCAN_PENDING, false);
        long started = getSharedPreferences(SCAN_PREFS, MODE_PRIVATE)
                .getLong(KEY_SCAN_STARTED, 0L);
        if (pending && started > 0L && System.currentTimeMillis() - started > SCAN_TIMEOUT_MS) {
            clearVisualScanState();
            pending = false;
            if (sessionsStatus != null) {
                sessionsStatus.setText("סריקת GPT פגה לפני שהוחזרה תוצאה. אפשר לנסות שוב.");
                sessionsStatus.setTextColor(AMBER);
            }
        }
        scanReceiverContainer.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            scanImportInput.postDelayed(() -> {
                scanImportInput.requestFocus();
                scanImportInput.setSelection(scanImportInput.length());
            }, 250L);
        }
    }

    private void clearVisualScanState() {
        getSharedPreferences(SCAN_PREFS, MODE_PRIVATE)
                .edit()
                .remove(KEY_SCAN_PENDING)
                .remove(KEY_SCAN_NONCE)
                .remove(KEY_SCAN_STARTED)
                .apply();
        if (scanImportInput != null) scanImportInput.setText("");
        if (scanReceiverContainer != null) scanReceiverContainer.setVisibility(View.GONE);
    }

    private void importVisualScan() {
        if (scanImportInput == null) return;
        String raw = scanImportInput.getText() == null ? "" : scanImportInput.getText().toString();
        String nonce = getSharedPreferences(SCAN_PREFS, MODE_PRIVATE)
                .getString(KEY_SCAN_NONCE, "");
        String expectedHeader = "ASB_SCAN_V1:" + nonce;
        String[] lines = raw.replace("\r", "").split("\n");

        if (nonce.isEmpty() || lines.length < 2 || !expectedHeader.equals(lines[0].trim())) {
            sessionsStatus.setText("תוצאת הסריקה נדחתה: nonce או פורמט לא תקינים.");
            sessionsStatus.setTextColor(RED);
            return;
        }

        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (int i = 1; i < lines.length; i++) {
            String title = lines[i] == null ? "" : lines[i].trim();
            if (isValidImportedChatTitle(title)) titles.add(title);
        }
        if (titles.isEmpty()) {
            sessionsStatus.setText("תוצאת הסריקה לא הכילה כותרות שיחה תקינות.");
            sessionsStatus.setTextColor(RED);
            return;
        }

        JSONArray chats = new JSONArray();
        int ordinal = 0;
        for (String title : titles) {
            ordinal++;
            try {
                chats.put(new JSONObject()
                        .put("chatKey", visualScanStableKey(title))
                        .put("title", title)
                        .put("ordinal", ordinal)
                        .put("visibleAtSync", true));
            } catch (Exception ignored) {
            }
        }

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        scanImportButton.setEnabled(false);
        sessionsStatus.setText("שומר את תוצאות הסריקה…");
        sessionsStatus.setTextColor(BLUE);

        io.execute(() -> {
            try {
                JSONObject result = api.syncDiscoveredChats(id, chats);
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("error", "discovery_sync_failed"));
                }
                runOnUiThread(() -> {
                    clearVisualScanState();
                    scanImportButton.setEnabled(true);
                    sessionsStatus.setText("✓ סריקה חזותית הושלמה · " + titles.size() + " Sessions");
                    sessionsStatus.setTextColor(TEAL);
                    loadDiscoveredChats();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    scanImportButton.setEnabled(true);
                    sessionsStatus.setText("שגיאת שמירת סריקה: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private boolean isValidImportedChatTitle(String value) {
        if (value == null) return false;
        String title = value.trim();
        if (title.length() < 2 || title.length() > 180) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        String[] excluded = {
                "chat", "chatgpt", "codex", "explore", "scheduled", "search",
                "new chat", "projects", "gpts", "sources", "see all", "see all…",
                "settings", "images", "plugins", "library",
                "חיפוש", "שיחה חדשה", "פרויקטים", "הגדרות", "תמונות", "תוספים", "ספרייה"
        };
        for (String item : excluded) {
            if (lower.equals(item) || lower.startsWith(item + " ")) return false;
        }
        return true;
    }

    private String visualScanStableKey(String title) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(title.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(title.hashCode()) +
                    Integer.toHexString(("chat:" + title).hashCode());
        }
    }

    private void loadDiscoveredChats() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired || sessionsRowsContainer == null) return;

        io.execute(() -> {
            try {
                JSONObject response = api.listDiscoveredChats(id);
                JSONArray chats = response.optJSONArray("chats");
                if (!response.optBoolean("ok", false) || chats == null) {
                    throw new IllegalStateException(response.optString("error", "discovery_list_failed"));
                }
                discoveredChatsCache = chats;
                runOnUiThread(() -> renderUnifiedSessions(chats));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsRowsContainer.removeAllViews();
                    TextView error = label("שגיאת טעינת השיחות: " + safeMessage(e), 12, RED, false);
                    sessionsRowsContainer.addView(error, matchWrap());
                });
            }
        });
    }

    private void renderUnifiedSessions(JSONArray chats) {
        sessionsRowsContainer.removeAllViews();
        Set<String> renderedSessionIds = new HashSet<>();
        int rendered = 0;

        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.optJSONObject(i);
            if (chat == null) continue;
            String chatKey = chat.optString("chat_key", "");
            String chatTitle = chat.optString("title", "");
            JSONObject session = findSessionByChatKey(chatKey);
            if (session == null && !chatTitle.isEmpty()) {
                session = findUniqueSessionByTitle(chatTitle);
            }

            if (session != null && currentSessionId != null &&
                    currentSessionId.equals(session.optString("session_id", ""))) {
                continue;
            }
            if (currentSessionChatKey != null && !currentSessionChatKey.isEmpty() &&
                    currentSessionChatKey.equals(chatKey)) {
                continue;
            }

            if (session != null) renderedSessionIds.add(session.optString("session_id", ""));
            sessionsRowsContainer.addView(buildUnifiedSessionRow(chat, session), sessionRowLp());
            rendered++;
        }

        // Keep pre-discovery sessions visible once, but never duplicate the Current Session.
        for (int i = 0; i < sessionsCache.length(); i++) {
            JSONObject session = sessionsCache.optJSONObject(i);
            if (session == null) continue;
            String sid = session.optString("session_id", "");
            if (sid.equals(currentSessionId) || renderedSessionIds.contains(sid)) continue;

            JSONObject synthetic = new JSONObject();
            try {
                synthetic.put("chat_key", session.optString("chat_key", sid));
                synthetic.put("title",
                        session.optString("chat_title",
                                session.optString("label", "GPT Session")));
            } catch (Exception ignored) {
            }
            sessionsRowsContainer.addView(buildUnifiedSessionRow(synthetic, session), sessionRowLp());
            rendered++;
        }

        if (rendered == 0) {
            TextView empty = label(
                    "אין כרגע Sessions נוספים. הסשן הנוכחי מוצג למעלה ואינו משוכפל כאן.",
                    13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(10), 0, dp(10));
            sessionsRowsContainer.addView(empty, matchWrap());
        }

        int connected = 0;
        for (int i = 0; i < sessionsCache.length(); i++) {
            JSONObject s = sessionsCache.optJSONObject(i);
            if (s != null && !s.optString("session_id", "").equals(currentSessionId) &&
                    isSessionConnected(s)) connected++;
        }
        sessionsStatus.setText("✓ " + rendered + " Sessions נוספים · " + connected +
                " מחוברים · Session Isolation אטומי");
        sessionsStatus.setTextColor(GREEN);
    }

    private JSONObject findSessionByChatKey(String chatKey) {
        if (chatKey == null || chatKey.isEmpty()) return null;
        for (int i = 0; i < sessionsCache.length(); i++) {
            JSONObject session = sessionsCache.optJSONObject(i);
            if (session != null && chatKey.equals(session.optString("chat_key", ""))) {
                return session;
            }
        }
        return null;
    }

    private JSONObject findUniqueSessionByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return null;
        String wanted = title.trim();
        JSONObject found = null;
        for (int i = 0; i < sessionsCache.length(); i++) {
            JSONObject session = sessionsCache.optJSONObject(i);
            if (session == null) continue;
            String candidate = session.optString(
                    "chat_title",
                    session.optString("label", "")).trim();
            if (!wanted.equals(candidate)) continue;
            if (found != null) return null;
            found = session;
        }
        return found;
    }

    private JSONObject findSessionById(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        for (int i = 0; i < sessionsCache.length(); i++) {
            JSONObject session = sessionsCache.optJSONObject(i);
            if (session != null && sessionId.equals(session.optString("session_id", ""))) {
                return session;
            }
        }
        return null;
    }

    private boolean isSessionConnected(JSONObject session) {
        return session != null
                && session.optBoolean("enabled", true)
                && "connected".equals(session.optString("status", ""));
    }

    private View buildUnifiedSessionRow(JSONObject chat, JSONObject session) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(rounded(CARD_ALT, 16, Color.rgb(35, 53, 80)));

        String titleText = chat.optString(
                "title",
                session == null ? "GPT Session" :
                        session.optString("chat_title", session.optString("label", "GPT Session")));
        String chatKey = chat.optString(
                "chat_key",
                session == null ? "" : session.optString("chat_key", ""));
        boolean connected = isSessionConnected(session);
        JSONArray roles = session == null ? null : session.optJSONArray("agent_suite");
        int roleCount = roles == null ? 0 : roles.length();
        int allAgents = agentRegistryCache == null ? 0 : agentRegistryCache.length();

        TextView title = label(titleText, 16, TEXT, true);
        row.addView(title, matchWrap());

        TextView meta = label(
                (connected ? "● מחובר" : "○ מנותק") +
                        (chatKey.isEmpty() ? "" :
                                " · ID " + chatKey.substring(0, Math.min(10, chatKey.length()))) +
                        (session == null ? "" :
                                " · Agents " + roleCount + "/" + Math.max(allAgents, roleCount)),
                11, connected ? GREEN : MUTED, false);
        LinearLayout.LayoutParams metaLp = matchWrap();
        metaLp.setMargins(0, dp(5), 0, dp(12));
        row.addView(meta, metaLp);

        Button toggle = connected
                ? compactButton("התנתק", false, true)
                : compactButton("התחבר", true, false);
        if (connected) {
            String sessionId = session.optString("session_id", "");
            toggle.setOnClickListener(v -> updateSessionState(sessionId, "disconnected"));
        } else {
            toggle.setOnClickListener(v -> generateSessionPairCode(chat, session));
        }
        LinearLayout.LayoutParams toggleLp = matchWrap();
        toggleLp.setMargins(0, 0, 0, session == null ? 0 : dp(9));
        row.addView(toggle, toggleLp);

        if (session != null) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);

            Button agents = compactButton(
                    "Agents " + roleCount + "/" + Math.max(allAgents, roleCount),
                    false, false);
            agents.setOnClickListener(v -> configureSessionAgents(session));
            actions.addView(agents, weightedButtonLp());

            Button run = compactButton("Run", false, false);
            run.setEnabled(connected && roleCount > 0);
            run.setAlpha(run.isEnabled() ? 1f : 0.40f);
            run.setOnClickListener(v -> requestSessionAgentRun(session));
            actions.addView(run, weightedButtonLp());

            Button rename = compactButton("Rename", false, false);
            rename.setOnClickListener(v -> promptRenameSession(session));
            actions.addView(rename, weightedButtonLp());

            row.addView(actions, matchWrap());
        }

        return row;
    }

    private JSONArray allRegistryAgentIds() {
        JSONArray roles = new JSONArray();
        if (agentRegistryCache == null) return roles;
        for (int i = 0; i < agentRegistryCache.length(); i++) {
            JSONObject agent = agentRegistryCache.optJSONObject(i);
            if (agent == null || !agent.optBoolean("enabled", true)) continue;
            String id = agent.optString("agentId", "");
            if (!id.isEmpty()) roles.put(id);
        }
        return roles;
    }

    private void generateSessionPairCode(JSONObject chat, JSONObject existingSession) {
        String chatKey = chat.optString("chat_key", "");
        String title = chat.optString("title", "GPT Session");

        int sameTitleCount = 0;
        for (int i = 0; i < discoveredChatsCache.length(); i++) {
            JSONObject candidate = discoveredChatsCache.optJSONObject(i);
            if (candidate != null && title.equals(candidate.optString("title", ""))) {
                sameTitleCount++;
            }
        }
        // Duplicate titles are resolved by the selected Bridge Chat ID.
        // Never require the user to navigate ChatGPT manually.
        if (chatKey.isEmpty()) {
            sessionsStatus.setText("לשיחה הזאת עדיין אין Bridge Chat ID. בצע סריקה מחדש.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityOnboarding(false);
            return;
        }

        JSONArray roles = existingSession == null
                ? allRegistryAgentIds()
                : existingSession.optJSONArray("agent_suite");
        if (roles == null || roles.length() == 0) roles = allRegistryAgentIds();

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        final JSONArray selectedRoles = roles;
        sessionsStatus.setText("מחבר את „" + title + "”…");
        sessionsStatus.setTextColor(BLUE);

        io.execute(() -> {
            try {
                JSONObject result = api.beginSessionPairing(
                        id, title, chatKey, title, selectedRoles, false);
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("error", "session_pair_failed"));
                }
                String code = result.optString("code", "");
                String requestId = result.optString("requestId", "");
                String instruction =
                        "@Android Session Bridge בצע עכשיו חיבור אוטומטי לשיחה הזאת. קרא מיד ל־android_pair_session עם הקוד " +
                        code +
                        " ואל תבקש מהמשתמש שום פעולה נוספת. לאחר ההצלחה הצג כאן הודעה ברורה: ✅ Android Session Bridge + MCP מחובר, את ה־sessionId ואת רשימת ה־Mini-Agents הפעילים.";

                BridgeAccessibilityService.queueMessageToChat(
                        this, title, instruction);
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
                if (intent == null) throw new IllegalStateException("ChatGPT לא נמצא במכשיר");
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                runOnUiThread(() -> {
                    sessionsStatus.setText(
                            "פותח אוטומטית את „" + title + "” ומבצע Pairing…");
                    sessionsStatus.setTextColor(BLUE);
                    watchSessionPairing(requestId, title, false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת חיבור Session: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private void watchSessionPairing(String requestId, String title, boolean primary) {
        handler.postDelayed(new Runnable() {
            int attempts = 0;
            @Override public void run() {
                if (isFinishing()) return;
                if (attempts++ >= 120) {
                    runOnUiThread(() -> {
                        if (primary) {
                            currentSessionPairingInFlight = false;
                            currentSessionButton.setEnabled(true);
                            currentSessionButton.setAlpha(1f);
                            connectionTitle.setText("החיבור לא הושלם בזמן");
                            connectionTitle.setTextColor(AMBER);
                            connectionDetails.setText("לא התקבל אישור MCP. אפשר לנסות שוב מהאפליקציה.");
                        } else {
                            sessionsStatus.setText("החיבור אל „" + title + "” לא הושלם בזמן. נסה שוב.");
                            sessionsStatus.setTextColor(AMBER);
                        }
                    });
                    return;
                }
                DeviceIdentity id = DeviceIdentity.getOrCreate(MainActivity.this);
                io.execute(() -> {
                    try {
                        JSONObject r = api.sessionPairStatus(id, requestId);
                        if (r.optBoolean("accepted", false)) {
                            runOnUiThread(() -> {
                                if (primary) {
                                    currentSessionPairingInFlight = false;
                                    currentSessionButton.setEnabled(true);
                                    currentSessionButton.setAlpha(1f);
                                    connectionTitle.setText("✓ " + title);
                                    connectionTitle.setTextColor(GREEN);
                                } else {
                                    sessionsStatus.setText("✓ „" + title + "” מחובר");
                                    sessionsStatus.setTextColor(GREEN);
                                }
                                loadSessions();
                            });
                            return;
                        }
                        if (r.optBoolean("expired", false)) {
                            runOnUiThread(() -> {
                                String message = "קוד החיבור של „" + title + "” פג תוקף.";
                                if (primary) {
                                    currentSessionPairingInFlight = false;
                                    currentSessionButton.setEnabled(true);
                                    currentSessionButton.setAlpha(1f);
                                    connectionDetails.setText(message);
                                } else sessionsStatus.setText(message);
                                if (primary) connectionTitle.setTextColor(AMBER);
                                else sessionsStatus.setTextColor(AMBER);
                            });
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                    handler.postDelayed(this, 2000L);
                });
            }
        }, 1200L);
    }

    private void configureSessionAgents(JSONObject session) {
        if (session == null) return;
        if (agentRegistryCache == null || agentRegistryCache.length() == 0) {
            sessionsStatus.setText("Skill Registry עדיין נטען.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }

        Set<String> selected = new HashSet<>();
        JSONArray current = session.optJSONArray("agent_suite");
        if (current != null) {
            for (int i = 0; i < current.length(); i++) selected.add(current.optString(i));
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(6), dp(10), dp(10));
        scroll.addView(list, matchWrap());

        List<CheckBox> boxes = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < agentRegistryCache.length(); i++) {
            JSONObject agent = agentRegistryCache.optJSONObject(i);
            if (agent == null || !agent.optBoolean("enabled", true)) continue;

            String id = agent.optString("agentId", "");
            if (id.isEmpty()) continue;
            String title = agent.optString("title", id);
            String skill = agent.optString("skillVersion", "");
            String description = hebrewAgentDescription(agent);

            LinearLayout agentCard = new LinearLayout(this);
            agentCard.setOrientation(LinearLayout.VERTICAL);
            agentCard.setPadding(dp(12), dp(10), dp(12), dp(10));

            CheckBox cb = new CheckBox(this);
            cb.setText(title + (skill.isEmpty() ? "" : " · " + skill));
            cb.setTextColor(TEXT);
            cb.setTextSize(14);
            cb.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cb.setChecked(selected.contains(id));
            cb.setTag(id);

            Runnable refreshAgentCard = () -> agentCard.setBackground(
                    rounded(
                            CARD_ALT,
                            14,
                            cb.isChecked() ? TEAL : Color.rgb(51, 65, 85)));
            refreshAgentCard.run();

            agentCard.addView(cb, matchWrap());

            TextView desc = label(description, 12, MUTED, false);
            desc.setLineSpacing(0f, 1.18f);
            LinearLayout.LayoutParams descLp = matchWrap();
            descLp.setMargins(dp(34), dp(3), dp(12), 0);
            agentCard.addView(desc, descLp);

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> refreshAgentCard.run());
            agentCard.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));
            addPressAnimation(agentCard);

            LinearLayout.LayoutParams cardLp = matchWrap();
            cardLp.setMargins(0, dp(5), 0, dp(5));
            list.addView(agentCard, cardLp);

            boxes.add(cb);
            ids.add(id);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("בחירת Mini‑Agents · " + session.optString("label", "Session"))
                .setView(scroll)
                .setPositiveButton("שמור לסשן הזה בלבד", null)
                .setNegativeButton("ביטול", null)
                .create();

        dialog.setOnShowListener(d -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setTextColor(TEAL);
            save.setOnClickListener(v -> {
                JSONArray roles = new JSONArray();
                for (int i = 0; i < boxes.size(); i++) {
                    if (boxes.get(i).isChecked()) roles.put(ids.get(i));
                }
                saveSessionAgents(session.optString("session_id", ""), roles);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private String hebrewAgentDescription(JSONObject agent) {
        String id = agent.optString("agentId", "");
        String description = agent.optString("description", "").trim();
        if (description.matches(".*[\\u0590-\\u05FF].*")) return description;

        for (int i = 0; i < AGENT_IDS.length; i++) {
            if (AGENT_IDS[i].equals(id)) return AGENT_DESCRIPTIONS[i];
        }
        return description.isEmpty()
                ? "סוכן ייעודי עם Skill וצ'קליסט נפרדים לסשן הזה."
                : description;
    }

    private void saveSessionAgents(String sessionId, JSONArray roles) {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                api.updateSession(id, sessionId, null, roles, null);
                runOnUiThread(() -> {
                    sessionsStatus.setText("✓ צוות הסוכנים נשמר רק ל־Session הזה");
                    sessionsStatus.setTextColor(GREEN);
                    loadSessions();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת שמירת Agents: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private void updateSessionState(String sessionId, String status) {
        if (sessionId == null || sessionId.isEmpty()) return;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                api.updateSession(id, sessionId, null, null, status);
                runOnUiThread(() -> {
                    sessionsStatus.setText(
                            "disconnected".equals(status)
                                    ? "✓ ה־Session נותק וה־Lease שלו נסגר"
                                    : "✓ מצב ה־Session עודכן");
                    sessionsStatus.setTextColor(GREEN);
                    loadSessions();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת Session: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private void promptRenameSession(JSONObject session) {
        String sessionId = session.optString("session_id", "");
        if (sessionId.isEmpty()) return;

        EditText input = new EditText(this);
        input.setText(session.optString("label", "GPT Session"));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));

        new AlertDialog.Builder(this)
                .setTitle("שנה שם תצוגה")
                .setMessage("השם הזה שייך רק ל־Bridge ולא משנה את שם השיחה ב־ChatGPT.")
                .setView(input)
                .setPositiveButton("שמור", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) renameSession(sessionId, name);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void renameSession(String sessionId, String name) {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                api.updateSession(id, sessionId, name, null, null);
                runOnUiThread(() -> {
                    sessionsStatus.setText("✓ שם התצוגה עודכן");
                    sessionsStatus.setTextColor(GREEN);
                    loadSessions();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת Rename: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private void requestSessionAgentRun(JSONObject session) {
        if (!isSessionConnected(session)) {
            sessionsStatus.setText("ה־Session מנותק. חבר אותו לפני Run.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }
        String sessionId = session.optString("session_id", "");
        JSONArray roles = session.optJSONArray("agent_suite");
        if (roles == null || roles.length() == 0) {
            sessionsStatus.setText("בחר לפחות Agent אחד ל־Session הזה.");
            sessionsStatus.setTextColor(AMBER);
            return;
        }

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject run = api.requestAgentRun(id, sessionId, roles);
                String runId = run.optString("runId", "");
                runOnUiThread(() -> {
                    sessionsStatus.setText("✓ Run " +
                            (runId.length() >= 8 ? runId.substring(0, 8) : runId) +
                            " נוצר רק עבור „" + session.optString("label", "Session") + "”");
                    sessionsStatus.setTextColor(GREEN);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    sessionsStatus.setText("שגיאת Run: " + safeMessage(e));
                    sessionsStatus.setTextColor(RED);
                });
            }
        });
    }

    private Button compactButton(String text, boolean primary, boolean destructive) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(9), dp(8), dp(9));
        b.setMinHeight(dp(44));

        if (destructive) {
            b.setTextColor(Color.rgb(254, 202, 202));
            b.setBackground(rounded(CARD_ALT, 12, RED));
        } else if (primary) {
            b.setTextColor(Color.rgb(6, 25, 35));
            b.setBackground(rounded(TEAL, 12, TEAL));
        } else {
            b.setTextColor(TEXT);
            b.setBackground(rounded(CARD_ALT, 12, Color.rgb(51, 65, 85)));
        }
        addPressAnimation(b);
        return b;
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private LinearLayout.LayoutParams sessionRowLp() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(9), 0, dp(2));
        return lp;
    }

    private View buildAgentsCard() {
        LinearLayout card = card();

        card.addView(label("GPT Mini‑Agent Crew", 20, TEXT, true), matchWrap());
        TextView p = label(
                "רשימת הסוכנים וה־Skills נטענת דינמית מה־Skill Registry בשרת. " +
                "אותו GPT של הסשן מריץ כל Agent כ־pass נפרד עם Prompt וצ'קליסט משלו.",
                13, MUTED, false);
        p.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams pLp = matchWrap();
        pLp.setMargins(0, dp(8), 0, dp(10));
        card.addView(p, pLp);

        agentRowsContainer = new LinearLayout(this);
        agentRowsContainer.setOrientation(LinearLayout.VERTICAL);
        TextView loading = label("טוען Skill Registry…", 13, MUTED, false);
        loading.setGravity(Gravity.CENTER);
        agentRowsContainer.addView(loading, matchWrap());
        card.addView(agentRowsContainer, matchWrap());

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

        agentStatus = label("ה־Registry מתעדכן מהשרת בלי צורך ב־APK חדש.", 12, MUTED, false);
        agentStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aLp = matchWrap();
        aLp.setMargins(0, dp(10), 0, 0);
        card.addView(agentStatus, aLp);
        return card;
    }

    private void loadAgentRegistry() {
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject response = api.getAgentRegistry(id);
                if (!response.optBoolean("ok", false)) {
                    throw new IllegalStateException(response.optString("error", "registry_failed"));
                }
                JSONArray agents = response.optJSONArray("agents");
                if (agents == null || agents.length() == 0) {
                    throw new IllegalStateException("registry_empty");
                }
                agentRegistryCache = agents;
                runOnUiThread(() -> {
                    if (sessionsRowsContainer != null) loadSessions();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    agentRegistryCache = fallbackAgentRegistry();
                    if (agentStatus != null) {
                        agentStatus.setText("לא ניתן היה לטעון Registry חי; מוצגת רשימת fallback מקומית.");
                        agentStatus.setTextColor(AMBER);
                    }
                });
            }
        });
    }

    private void renderAgentRegistry(JSONArray agents, boolean fallback) {
        if (agentRowsContainer == null) return;
        agentRowsContainer.removeAllViews();
        agentChecks.clear();

        Set<String> persisted = getPreferences(MODE_PRIVATE).getStringSet("agent_roles", null);
        Set<String> saved = persisted == null ? null : new HashSet<>(persisted);

        for (int i = 0; i < agents.length(); i++) {
            JSONObject agent = agents.optJSONObject(i);
            if (agent == null || !agent.optBoolean("enabled", true)) continue;

            String agentId = agent.optString("agentId", "").trim();
            if (agentId.isEmpty()) continue;

            String title = agent.optString("title", agentId);
            String description = agent.optString("description", "");
            String skillVersion = agent.optString("skillVersion", "");
            int checklistCount = agent.optInt("checklistCount", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackground(rounded(CARD_ALT, 12, Color.rgb(35, 53, 80)));

            CheckBox cb = new CheckBox(this);
            cb.setText(title);
            cb.setTextColor(TEXT);
            cb.setTextSize(15);
            cb.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cb.setChecked(saved == null || saved.contains(agentId));
            cb.setTag(agentId);
            row.addView(cb, matchWrap());
            agentChecks.add(cb);

            TextView desc = label(description, 12, MUTED, false);
            LinearLayout.LayoutParams descLp = matchWrap();
            descLp.setMargins(dp(34), 0, dp(34), 0);
            row.addView(desc, descLp);

            String meta = "Skill " + (skillVersion.isEmpty() ? "server" : skillVersion);
            if (checklistCount > 0) meta += " · " + checklistCount + " checks";
            TextView version = label(meta, 11, TEAL, false);
            LinearLayout.LayoutParams verLp = matchWrap();
            verLp.setMargins(dp(34), dp(5), dp(34), 0);
            row.addView(version, verLp);

            LinearLayout.LayoutParams rowLp = matchWrap();
            rowLp.setMargins(0, dp(6), 0, 0);
            agentRowsContainer.addView(row, rowLp);
        }

        if (agentChecks.isEmpty()) {
            TextView empty = label("אין כרגע Mini‑Agents פעילים ב־Registry.", 13, AMBER, false);
            empty.setGravity(Gravity.CENTER);
            agentRowsContainer.addView(empty, matchWrap());
        } else {
            agentStatus.setText(
                    fallback
                            ? "מצב fallback מקומי · " + agentChecks.size() + " סוכנים"
                            : "✓ Skill Registry חי · " + agentChecks.size() + " סוכנים פעילים");
            agentStatus.setTextColor(fallback ? AMBER : GREEN);
        }
        showWithAnimation(agentRowsContainer);
    }

    private JSONArray fallbackAgentRegistry() {
        JSONArray out = new JSONArray();
        for (int i = 0; i < AGENT_IDS.length; i++) {
            JSONObject o = new JSONObject();
            try {
                o.put("agentId", AGENT_IDS[i]);
                o.put("title", AGENT_TITLES[i]);
                o.put("description", AGENT_DESCRIPTIONS[i]);
                o.put("enabled", true);
                o.put("skillVersion", "fallback");
                o.put("checklistCount", 0);
                out.put(o);
            } catch (Exception ignored) {
            }
        }
        return out;
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
                DeviceIdentity.savePendingPairing(
                        this,
                        activePairRequestId,
                        activePairCode,
                        activePairExpiresAt);

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
        if (activePairRequestId == null || activePairRequestId.isEmpty()) {
            generatePairCode();
            return;
        }

        connectionTitle.setText("מתחבר…");
        connectionTitle.setTextColor(BLUE);
        connectionDetails.setText("מאשר את החיבור ישירות מול השרת. אין הודעת קוד בתוך ChatGPT.");
        connectButton.setEnabled(false);

        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        io.execute(() -> {
            try {
                JSONObject result = api.completePairing(id, activePairRequestId);
                if (!result.optBoolean("ok", false) || !result.optBoolean("paired", false)) {
                    throw new IllegalStateException(result.optString("error", "pair_complete_failed"));
                }

                DeviceIdentity.markPaired(this, true);
                DeviceIdentity.clearPendingPairing(this);
                activePairCode = null;
                activePairRequestId = null;
                activePairExpiresAt = 0L;

                runOnUiThread(() -> {
                    pairCode.setText("✓ CONNECTED");
                    pairInstruction.setText("החיבור אושר ישירות. לא נשלחה הודעה לצ'אט.");
                    connectionTitle.setText("✓ מחובר ומוכן");
                    connectionTitle.setTextColor(GREEN);
                    connectButton.setEnabled(true);
                    refreshConnectionUi();
                    loadSessions();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connectionTitle.setText("החיבור נכשל");
                    connectionTitle.setTextColor(RED);
                    connectionDetails.setText(safeMessage(e));
                    connectButton.setEnabled(true);
                });
            }
        });
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
                    DeviceIdentity.clearPendingPairing(this);
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
                    DeviceIdentity.clearPendingPairing(this);
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
        long age = lastContact == 0L ? Long.MAX_VALUE :
                System.currentTimeMillis() - lastContact;
        boolean live = id.paired && accessibility && age < 15_000L;

        accessibilityButton.setVisibility(accessibility ? View.GONE : View.VISIBLE);
        closeButton.setVisibility(View.VISIBLE);

        if (!id.paired) {
            currentSessionButton.setVisibility(View.GONE);
            currentSessionActions.setVisibility(View.GONE);
            deviceDisconnectButton.setVisibility(View.GONE);

            if (activePairCode != null && activePairExpiresAt > 0) {
                long seconds = Math.max(
                        0, (activePairExpiresAt - System.currentTimeMillis()) / 1000L);
                if (seconds <= 0) {
                    activePairCode = null;
                    activePairRequestId = null;
                    activePairExpiresAt = 0L;
                    pairCode.setText("פג תוקף");
                    pairInstruction.setText("צור קוד חדש.");
                    connectButton.setVisibility(View.GONE);
                } else {
                    pairInstruction.setText(
                            "קוד חיבור מכשיר · תקף עוד " +
                                    (seconds / 60) + ":" +
                                    String.format("%02d", seconds % 60));
                }
            }

            if (activePairCode != null) {
                connectionTitle.setText("חיבור מכשיר חד־פעמי");
                connectionTitle.setTextColor(TEAL);
                connectionDetails.setText("אשר את הקוד כדי לאפשר ל־Bridge לעבוד עם המכשיר.");
                technicalStatus.setText(accessibility
                        ? "Accessibility פעיל"
                        : "יש להפעיל Accessibility לפני החיבור");
                generateCodeButton.setVisibility(View.GONE);
                codeArea.setVisibility(View.VISIBLE);
                connectButton.setVisibility(View.VISIBLE);
                newCodeButton.setVisibility(View.VISIBLE);
            } else {
                connectionTitle.setText("המכשיר עדיין לא מחובר");
                connectionTitle.setTextColor(MUTED);
                connectionDetails.setText(
                        "זהו שלב חד־פעמי. לאחריו הכפתור הראשי יחבר את סשן GPT הנוכחי.");
                technicalStatus.setText(accessibility
                        ? "Accessibility פעיל · מוכן לחיבור"
                        : "שלב ראשון: הרשאת Accessibility חד־פעמית");
                codeArea.setVisibility(View.GONE);
                generateCodeButton.setVisibility(View.VISIBLE);
                generateCodeButton.setEnabled(true);
            }
            return;
        }

        // Device is paired. The main card now represents the Current GPT Session.
        generateCodeButton.setVisibility(View.GONE);
        codeArea.setVisibility(View.GONE);
        deviceDisconnectButton.setVisibility(View.VISIBLE);
        currentSessionButton.setVisibility(View.VISIBLE);

        JSONObject current = findSessionById(currentSessionId);
        boolean currentConnected = isSessionConnected(current);

        if (currentConnected) {
            String title = currentSessionTitle == null || currentSessionTitle.isEmpty()
                    ? current.optString("label", "GPT Session")
                    : currentSessionTitle;
            connectionTitle.setText("✓ " + title);
            connectionTitle.setTextColor(GREEN);
            connectionDetails.setText(
                    "זהו הסשן הנוכחי המחובר למכשיר. הוא מוסתר מרשימת שאר ה־Sessions למטה.");
            currentSessionButton.setText("התנתק מהסשן הנוכחי");
            currentSessionButton.setTextColor(Color.rgb(254, 226, 226));
            currentSessionButton.setBackground(
                    rounded(CARD_ALT, 14, RED));

            JSONArray currentRoles = current.optJSONArray("agent_suite");
            int selectedCount = currentRoles == null ? 0 : currentRoles.length();
            int allCount = agentRegistryCache == null ? 0 : agentRegistryCache.length();
            currentAgentsButton.setText("Agents " + selectedCount + "/" + Math.max(allCount, selectedCount));
            currentRunButton.setEnabled(selectedCount > 0);
            currentRunButton.setAlpha(currentRunButton.isEnabled() ? 1f : 0.40f);
            currentSessionActions.setVisibility(View.VISIBLE);
        } else {
            connectionTitle.setText("הסשן הנוכחי לא מחובר");
            connectionTitle.setTextColor(live ? TEAL : AMBER);
            connectionDetails.setText(
                    "לחץ כאן בלבד. האפליקציה תחזור אוטומטית לשיחת ChatGPT הפעילה, תבצע Pairing ותציג אישור בתוך השיחה — בלי פעולה ידנית ב־ChatGPT.");
            currentSessionButton.setText("התחבר לסשן הנוכחי");
            currentSessionButton.setTextColor(Color.rgb(6, 25, 35));
            currentSessionButton.setBackground(rounded(TEAL, 14, TEAL));
            currentSessionActions.setVisibility(View.GONE);
        }
        addPressAnimation(currentSessionButton);

        if (live) {
            technicalStatus.setText(
                    android.os.Build.MODEL +
                            " · Accessibility פעיל · Heartbeat חי · v" +
                            BridgeApi.APP_VERSION);
        } else if (accessibility) {
            technicalStatus.setText("המכשיר מחובר · ממתין ל־heartbeat");
        } else {
            technicalStatus.setText("המכשיר מחובר · Accessibility כבוי");
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
            if (!view.isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate()
                        .scaleX(0.96f).scaleY(0.96f)
                        .alpha(0.72f)
                        .setDuration(70L).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate()
                        .scaleX(1f).scaleY(1f)
                        .alpha(1f)
                        .setDuration(130L).start();
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

    private void syncPendingPairingFromStorage() {
        String requestId = DeviceIdentity.pendingPairRequest(this);
        String code = DeviceIdentity.pendingPairCode(this);
        long expiresAt = DeviceIdentity.pendingPairExpires(this);

        if (requestId == null || requestId.isEmpty() || code == null || code.isEmpty()) {
            if (DeviceIdentity.getOrCreate(this).paired) {
                activePairRequestId = null;
                activePairCode = null;
                activePairExpiresAt = 0L;
            }
            return;
        }

        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            DeviceIdentity.clearPendingPairing(this);
            activePairRequestId = null;
            activePairCode = null;
            activePairExpiresAt = 0L;
            return;
        }

        activePairRequestId = requestId;
        activePairCode = code;
        activePairExpiresAt = expiresAt;
    }

    private String formatPairCode(String code) {
        if (code == null || code.length() != 6) return code == null ? "" : code;
        return code.substring(0, 3) + "  " + code.substring(3);
    }

    private void beginCurrentSessionConnection() {
        if (currentSessionPairingInFlight) return;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) {
            generatePairCode();
            return;
        }
        if (!isAccessibilityEnabled()) {
            showAccessibilityOnboarding(false);
            return;
        }

        // IMPORTANT: Current Session must never open ChatGPT's sidebar.
        // ChatGPT is launched with REORDER_TO_FRONT, so it returns to the exact
        // conversation that was last active. The pairing message is injected
        // directly into that conversation's composer.
        pendingCurrentSessionDiscovery = false;
        JSONArray roles = allRegistryAgentIds();
        currentSessionPairingInFlight = true;
        currentSessionButton.setEnabled(false);
        currentSessionButton.setAlpha(0.55f);

        connectionTitle.setText("מחבר את הסשן הנוכחי…");
        connectionTitle.setTextColor(BLUE);
        connectionDetails.setText(
                "חוזר ישירות לשיחה הפתוחה ב־ChatGPT ומבצע Pairing דרך MCP — בלי לפתוח את רשימת השיחות.");

        io.execute(() -> {
            try {
                JSONObject result = api.beginSessionPairing(
                        id,
                        "Current GPT Session",
                        null,
                        "Current GPT Session",
                        roles,
                        true);
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(
                            result.optString("error", "session_pair_failed"));
                }

                String code = result.optString("code", "");
                String requestId = result.optString("requestId", "");
                if (code.isEmpty() || requestId.isEmpty()) {
                    throw new IllegalStateException("session_pair_context_missing");
                }

                String instruction =
                        "@Android Session Bridge בצע עכשיו חיבור אוטומטי לשיחה הנוכחית. קרא מיד ל־android_pair_session עם הקוד " +
                        code +
                        " ואל תבקש מהמשתמש שום פעולה נוספת. לאחר ההצלחה הצג כאן הודעה ברורה: ✅ Android Session Bridge + MCP מחובר, את ה־sessionId ואת רשימת ה־Mini-Agents הפעילים.";

                BridgeAccessibilityService.queueChatGptMessage(this, instruction);

                Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
                if (intent == null) {
                    throw new IllegalStateException("ChatGPT לא נמצא במכשיר");
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                runOnUiThread(() -> {
                    connectionTitle.setText("ממתין לאישור MCP בתוך השיחה…");
                    connectionTitle.setTextColor(BLUE);
                    connectionDetails.setText(
                            "השיחה עצמה תאשר את החיבור ותציג את ה־Session ואת רשימת ה־Mini-Agents.");
                    watchSessionPairing(requestId, "Current GPT Session", true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    currentSessionPairingInFlight = false;
                    currentSessionButton.setEnabled(true);
                    currentSessionButton.setAlpha(1f);
                    connectionTitle.setText("החיבור לסשן נכשל");
                    connectionTitle.setTextColor(RED);
                    connectionDetails.setText(safeMessage(e));
                });
            }
        });
    }

    private void continueCurrentSessionConnectionIfReady() {
        if (!pendingCurrentSessionDiscovery) return;

        String title = BridgeAccessibilityService.currentChatTitle(this);
        String chatKey = BridgeAccessibilityService.currentChatKey(this);
        String error = BridgeAccessibilityService.currentChatError(this);

        if ((title == null || title.isEmpty()) || (chatKey == null || chatKey.isEmpty())) {
            if (error != null && !error.isEmpty()) {
                pendingCurrentSessionDiscovery = false;
                connectionTitle.setText("לא הצלחתי לזהות את שם הסשן");
                connectionTitle.setTextColor(AMBER);
                connectionDetails.setText("החיבור האוטומטי לא הושלם. אין צורך לפתוח שיחה ידנית; נסה שוב מהאפליקציה.");
            }
            return;
        }

        pendingCurrentSessionDiscovery = false;
        JSONArray roles = allRegistryAgentIds();
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);

        connectionTitle.setText("מחבר את „" + title + "”…");
        connectionTitle.setTextColor(BLUE);
        connectionDetails.setText("ה־Bridge מזווג את השיחה הנוכחית לערוץ מבודד.");

        io.execute(() -> {
            try {
                JSONObject result = api.beginSessionPairing(
                        id, title, chatKey, title, roles, true);
                if (!result.optBoolean("ok", false)) {
                    throw new IllegalStateException(result.optString("error", "session_pair_failed"));
                }
                String code = result.optString("code", "");
                String requestId = result.optString("requestId", "");
                String instruction =
                        "@Android Session Bridge בצע עכשיו חיבור אוטומטי לשיחה הזאת. קרא מיד ל־android_pair_session עם הקוד " +
                        code +
                        " ואל תבקש מהמשתמש שום פעולה נוספת. לאחר ההצלחה הצג כאן הודעה ברורה: ✅ Android Session Bridge + MCP מחובר, את ה־sessionId ואת רשימת ה־Mini-Agents הפעילים.";

                BridgeAccessibilityService.queueMessageToChat(this, title, instruction);
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
                if (intent == null) throw new IllegalStateException("ChatGPT לא נמצא במכשיר");
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);

                runOnUiThread(() -> watchSessionPairing(requestId, title, true));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    connectionTitle.setText("החיבור לסשן נכשל");
                    connectionTitle.setTextColor(RED);
                    connectionDetails.setText(safeMessage(e));
                });
            }
        });
    }

    private void exitToHome() {
        BridgeAccessibilityService.suppressAutoReturn(this, 8000L);
        handler.removeCallbacksAndMessages(null);
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finishAffinity();
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
        syncPendingPairingFromStorage();
        loadAgentRegistry();
        loadSessions();
        continueCurrentSessionConnectionIfReady();
        armVisualScanReceiver();
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
