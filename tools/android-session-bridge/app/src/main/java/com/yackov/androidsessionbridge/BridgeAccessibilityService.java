package com.yackov.androidsessionbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeAccessibilityService extends AccessibilityService {
    private static final String LOCAL_PREFS = "bridge_local_automation";
    private static final String KEY_AUTOMATION_SCHEMA_VERSION = "automation_schema_version";
    private static final int AUTOMATION_SCHEMA_VERSION = 2;
    private static final String KEY_PENDING_CHATGPT_MESSAGE = "pending_chatgpt_message";
    private static final String KEY_PENDING_CHATGPT_CREATED = "pending_chatgpt_created";
    private static final String KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS = "pending_chatgpt_focus_attempts";
    private static final String KEY_PENDING_CHATGPT_FILLED = "pending_chatgpt_filled";
    private static final String KEY_PENDING_CHATGPT_SEND_ATTEMPTS = "pending_chatgpt_send_attempts";
    private static final String KEY_DISCOVERY_ACTIVE = "discovery_active";
    private static final String KEY_DISCOVERY_PASS = "discovery_pass";
    private static final String KEY_DISCOVERY_TITLES = "discovery_titles";
    private static final String KEY_DISCOVERY_SIDEBAR_OPENED = "discovery_sidebar_opened";
    private static final String KEY_TARGET_CHAT_TITLE = "target_chat_title";
    private static final String KEY_TARGET_CHAT_MESSAGE = "target_chat_message";
    private static final String KEY_TARGET_CHAT_CREATED = "target_chat_created";
    private static final String KEY_TARGET_CHAT_SEARCH_MODE = "target_chat_search_mode";
    private static final String KEY_TARGET_CHAT_SEARCH_ATTEMPTS = "target_chat_search_attempts";
    private static final String KEY_CURRENT_CHAT_DISCOVERY = "current_chat_discovery";
    private static final String KEY_CURRENT_CHAT_TITLE = "current_chat_title";
    private static final String KEY_CURRENT_CHAT_KEY = "current_chat_key";
    private static final String KEY_CURRENT_CHAT_ERROR = "current_chat_error";
    private static final String KEY_SUPPRESS_BRIDGE_RETURN_UNTIL = "suppress_bridge_return_until";
    private static final String KEY_VISUAL_SCAN_ACTIVE = "visual_scan_active";
    private static final String KEY_VISUAL_SCAN_NONCE = "visual_scan_nonce";
    private static final String KEY_VISUAL_SCAN_SESSION_ID = "visual_scan_session_id";
    private static final String KEY_VISUAL_SCAN_PHASE = "visual_scan_phase";
    private static final String KEY_VISUAL_SCAN_RESET_COUNT = "visual_scan_reset_count";
    private static final String KEY_VISUAL_SCAN_RESET_HASH = "visual_scan_reset_hash";
    private static final String KEY_VISUAL_SCAN_RESET_SAME = "visual_scan_reset_same";
    private static final String KEY_VISUAL_SCAN_PAGE = "visual_scan_page";
    private static final String KEY_VISUAL_SCAN_FRAME_COUNT = "visual_scan_frame_count";
    private static final String KEY_VISUAL_SCAN_LAST_HASH = "visual_scan_last_hash";
    private static final String KEY_VISUAL_SCAN_SAME_COUNT = "visual_scan_same_count";
    private static final String KEY_VISUAL_SCAN_STARTED = "visual_scan_started";
    private static final String KEY_VISUAL_SCAN_CANCEL_REASON = "visual_scan_cancel_reason";
    private static final int VISUAL_SCAN_MAX_RESET_STEPS = 40;
    private static final int VISUAL_SCAN_MAX_FRAMES = 96;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final BridgeApi api = new BridgeApi();
    private volatile boolean busy = false;
    private volatile boolean localAutomationBusy = false;
    private volatile boolean localVisualCaptureBusy = false;
    private volatile long localVisualInternalGestureUntil = 0L;

    public static void queueChatGptMessage(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_CHATGPT_MESSAGE, message.trim())
                .putLong(KEY_PENDING_CHATGPT_CREATED, System.currentTimeMillis())
                .putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0)
                .putBoolean(KEY_PENDING_CHATGPT_FILLED, false)
                .putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0)
                .apply();
    }

    public static void queueLocalVisualScan(
            Context context, String nonce, String sessionId, String instruction) {
        if (context == null || nonce == null || sessionId == null ||
                instruction == null || instruction.trim().isEmpty()) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VISUAL_SCAN_ACTIVE, true)
                .putString(KEY_VISUAL_SCAN_NONCE, nonce)
                .putString(KEY_VISUAL_SCAN_SESSION_ID, sessionId)
                .putString(KEY_VISUAL_SCAN_PHASE, "waiting_message")
                .putInt(KEY_VISUAL_SCAN_RESET_COUNT, 0)
                .putString(KEY_VISUAL_SCAN_RESET_HASH, "")
                .putInt(KEY_VISUAL_SCAN_RESET_SAME, 0)
                .putInt(KEY_VISUAL_SCAN_PAGE, 0)
                .putInt(KEY_VISUAL_SCAN_FRAME_COUNT, 0)
                .putString(KEY_VISUAL_SCAN_LAST_HASH, "")
                .putInt(KEY_VISUAL_SCAN_SAME_COUNT, 0)
                .putLong(KEY_VISUAL_SCAN_STARTED, System.currentTimeMillis())
                .putString(KEY_PENDING_CHATGPT_MESSAGE, instruction.trim())
                .putLong(KEY_PENDING_CHATGPT_CREATED, System.currentTimeMillis())
                .putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0)
                .putBoolean(KEY_PENDING_CHATGPT_FILLED, false)
                .putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0)
                .remove(KEY_VISUAL_SCAN_CANCEL_REASON)
                .apply();
    }

    public static void queueSessionDiscovery(Context context) {
        if (context == null) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DISCOVERY_ACTIVE, true)
                .putInt(KEY_DISCOVERY_PASS, 0)
                .putString(KEY_DISCOVERY_TITLES, "[]")
                .putBoolean(KEY_DISCOVERY_SIDEBAR_OPENED, false)
                .apply();
    }

    public static void queueMessageToChat(Context context, String title, String message) {
        if (context == null || title == null || title.trim().isEmpty() ||
                message == null || message.trim().isEmpty()) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TARGET_CHAT_TITLE, title.trim())
                .putString(KEY_TARGET_CHAT_MESSAGE, message.trim())
                .putLong(KEY_TARGET_CHAT_CREATED, System.currentTimeMillis())
                .putBoolean(KEY_TARGET_CHAT_SEARCH_MODE, false)
                .putInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, 0)
                .apply();
    }

    public static void requestCurrentChatIdentity(Context context) {
        if (context == null) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CURRENT_CHAT_DISCOVERY, true)
                .remove(KEY_CURRENT_CHAT_TITLE)
                .remove(KEY_CURRENT_CHAT_KEY)
                .remove(KEY_CURRENT_CHAT_ERROR)
                .apply();
    }

    public static String currentChatTitle(Context context) {
        return context == null ? null :
                context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_CURRENT_CHAT_TITLE, null);
    }

    public static String currentChatKey(Context context) {
        return context == null ? null :
                context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_CURRENT_CHAT_KEY, null);
    }

    public static String currentChatError(Context context) {
        return context == null ? null :
                context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_CURRENT_CHAT_ERROR, null);
    }

    public static void suppressAutoReturn(Context context, long millis) {
        if (context == null) return;
        context.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SUPPRESS_BRIDGE_RETURN_UNTIL,
                        System.currentTimeMillis() + Math.max(1000L, millis))
                .putBoolean(KEY_DISCOVERY_ACTIVE, false)
                .putBoolean(KEY_CURRENT_CHAT_DISCOVERY, false)
                .remove(KEY_TARGET_CHAT_TITLE)
                .remove(KEY_TARGET_CHAT_MESSAGE)
                .remove(KEY_TARGET_CHAT_CREATED)
                .remove(KEY_TARGET_CHAT_SEARCH_MODE)
                .remove(KEY_TARGET_CHAT_SEARCH_ATTEMPTS)
                .apply();
    }

    private boolean bridgeAutoReturnAllowed() {
        long until = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_SUPPRESS_BRIDGE_RETURN_UNTIL, 0L);
        return System.currentTimeMillis() >= until;
    }




    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        migrateAutomationState();
        worker.scheduleWithFixedDelay(this::pollOnce, 250, 1200, TimeUnit.MILLISECONDS);
        worker.schedule(this::attemptPendingChatGptMessage, 700, TimeUnit.MILLISECONDS);
        worker.schedule(this::attemptTargetedChatMessage, 760, TimeUnit.MILLISECONDS);
        worker.schedule(this::attemptSessionDiscovery, 900, TimeUnit.MILLISECONDS);
        worker.schedule(this::attemptLocalVisualScan, 1000, TimeUnit.MILLISECONDS);
        worker.scheduleWithFixedDelay(this::pollLocalVisualScanCompletion, 1800, 1800, TimeUnit.MILLISECONDS);
    }

    private void migrateAutomationState() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        int version = prefs.getInt(KEY_AUTOMATION_SCHEMA_VERSION, 0);
        if (version >= AUTOMATION_SCHEMA_VERSION) return;

        prefs.edit()
                .putInt(KEY_AUTOMATION_SCHEMA_VERSION, AUTOMATION_SCHEMA_VERSION)
                .putBoolean(KEY_VISUAL_SCAN_ACTIVE, false)
                .putString(KEY_VISUAL_SCAN_PHASE, "cancelled")
                .putString(KEY_VISUAL_SCAN_CANCEL_REASON, "upgrade_cleanup")
                .putBoolean(KEY_DISCOVERY_ACTIVE, false)
                .putBoolean(KEY_CURRENT_CHAT_DISCOVERY, false)
                .remove(KEY_PENDING_CHATGPT_MESSAGE)
                .remove(KEY_PENDING_CHATGPT_CREATED)
                .remove(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS)
                .remove(KEY_PENDING_CHATGPT_FILLED)
                .remove(KEY_PENDING_CHATGPT_SEND_ATTEMPTS)
                .remove(KEY_TARGET_CHAT_TITLE)
                .remove(KEY_TARGET_CHAT_MESSAGE)
                .remove(KEY_TARGET_CHAT_CREATED)
                .remove(KEY_TARGET_CHAT_SEARCH_MODE)
                .remove(KEY_TARGET_CHAT_SEARCH_ATTEMPTS)
                .apply();
    }

    private void pollOnce() {
        if (busy) return;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) {
            checkPendingPairingInBackground(id);
            id = DeviceIdentity.getOrCreate(this);
            if (!id.paired) return;
        }

        try {
            JSONObject response = api.poll(id);
            if (!response.optBoolean("ok", false)) return;
            DeviceIdentity.markContact(this);
            Object commandObj = response.opt("command");
            if (!(commandObj instanceof JSONObject)) return;

            JSONObject wrapper = (JSONObject) commandObj;
            String commandId = wrapper.optString("id", "");
            JSONObject payload = wrapper.optJSONObject("payload");
            if (commandId.isEmpty() || payload == null) return;

            busy = true;
            boolean async = executeCommand(id, commandId, payload);
            if (!async) busy = false;
        } catch (Exception ignored) {
            busy = false;
        }
    }

    private void checkPendingPairingInBackground(DeviceIdentity id) {
        String requestId = DeviceIdentity.pendingPairRequest(this);
        if (requestId == null || requestId.isEmpty()) return;

        long expiresAt = DeviceIdentity.pendingPairExpires(this);
        if (expiresAt > 0L && System.currentTimeMillis() >= expiresAt) {
            DeviceIdentity.clearPendingPairing(this);
            return;
        }

        try {
            JSONObject status = api.pairStatus(id, requestId);
            if (status.optBoolean("accepted", false)) {
                DeviceIdentity.markPaired(this, true);
                DeviceIdentity.clearPendingPairing(this);
                DeviceIdentity.markContact(this);
            } else if (status.optBoolean("expired", false)) {
                DeviceIdentity.clearPendingPairing(this);
            }
        } catch (Exception ignored) {
            // Keep the exact pending request across transient network failures.
        }
    }

    private boolean executeCommand(DeviceIdentity id, String commandId, JSONObject cmd) {
        String op = cmd.optString("op", "").toLowerCase(Locale.ROOT);
        try {
            switch (op) {
                case "ping":
                    report(id, commandId, true,
                            new JSONObject().put("pong", true).put("device", BridgeApi.deviceInfo()), null);
                    return false;

                case "snapshot":
                    report(id, commandId, true,
                            new JSONObject().put("tree", snapshotTree()), null);
                    return false;

                case "tap": {
                    float x = (float) cmd.getDouble("x");
                    float y = (float) cmd.getDouble("y");
                    boolean accepted = tap(x, y);
                    report(id, commandId, accepted,
                            new JSONObject().put("accepted", accepted).put("x", x).put("y", y),
                            accepted ? null : "GESTURE_REJECTED");
                    return false;
                }

                case "swipe": {
                    float x1 = (float) cmd.getDouble("x1");
                    float y1 = (float) cmd.getDouble("y1");
                    float x2 = (float) cmd.getDouble("x2");
                    float y2 = (float) cmd.getDouble("y2");
                    long duration = cmd.optLong("duration_ms", 350);
                    boolean accepted = swipe(x1, y1, x2, y2, duration);
                    report(id, commandId, accepted,
                            new JSONObject().put("accepted", accepted),
                            accepted ? null : "GESTURE_REJECTED");
                    return false;
                }

                case "click_text": {
                    String text = cmd.getString("text");
                    boolean exact = cmd.optBoolean("exact", false);
                    boolean clicked = clickText(text, exact);
                    report(id, commandId, clicked,
                            new JSONObject().put("clicked", clicked).put("text", text),
                            clicked ? null : "TEXT_NOT_FOUND");
                    return false;
                }

                case "set_text": {
                    String value = cmd.getString("text");
                    String field = cmd.optString("field_text", null);
                    boolean ok = setText(value, field);
                    report(id, commandId, ok,
                            new JSONObject().put("set", ok),
                            ok ? null : "EDITABLE_FIELD_NOT_FOUND");
                    return false;
                }

                case "global": {
                    String action = cmd.getString("action");
                    int code = globalAction(action);
                    boolean ok = code != -1 && performGlobalAction(code);
                    report(id, commandId, ok,
                            new JSONObject().put("action", action).put("performed", ok),
                            ok ? null : "GLOBAL_ACTION_FAILED");
                    return false;
                }

                case "launch": {
                    String pkg = cmd.getString("package");
                    boolean ok = launchPackage(pkg);
                    report(id, commandId, ok,
                            new JSONObject().put("package", pkg).put("launched", ok),
                            ok ? null : "PACKAGE_NOT_LAUNCHABLE");
                    return false;
                }

                case "open_chatgpt": {
                    boolean ok = launchPackage("com.openai.chatgpt");
                    report(id, commandId, ok,
                            new JSONObject().put("package", "com.openai.chatgpt").put("launched", ok),
                            ok ? null : "CHATGPT_NOT_LAUNCHABLE");
                    return false;
                }

                case "make_qa_image": {
                    Uri uri = makeQaImage();
                    boolean ok = uri != null;
                    report(id, commandId, ok,
                            new JSONObject().put("uri", ok ? uri.toString() : JSONObject.NULL),
                            ok ? null : "QA_IMAGE_CREATE_FAILED");
                    return false;
                }

                case "screenshot":
                    takeBridgeScreenshot(id, commandId);
                    return true;

                default:
                    report(id, commandId, false, new JSONObject(), "UNKNOWN_OP:" + op);
                    return false;
            }
        } catch (Exception e) {
            report(id, commandId, false, new JSONObject(), e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private void report(DeviceIdentity id, String commandId, boolean ok, JSONObject result, String error) {
        try {
            api.report(id, commandId, ok, result, error);
        } catch (Exception ignored) {
        }
    }

    private boolean tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 80);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private boolean swipe(float x1, float y1, float x2, float y2, long duration) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(100, duration));
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    private boolean clickText(String wanted, boolean exact) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            String ts = t == null ? "" : t.toString();
            String ds = d == null ? "" : d.toString();
            boolean match = exact
                    ? ts.equals(wanted) || ds.equals(wanted)
                    : ts.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT))
                      || ds.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT));
            if (match) {
                AccessibilityNodeInfo clickable = n;
                while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (!r.isEmpty()) return tap(r.exactCenterX(), r.exactCenterY());
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    private boolean setText(String value, String fieldText) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        if (fieldText == null || fieldText.isEmpty()) {
            try {
                AccessibilityNodeInfo focused =
                        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null && setNodeText(focused, value)) return true;
            } catch (Exception ignored) {}

            try {
                List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                        if (window == null || window.getRoot() == null) continue;
                        AccessibilityNodeInfo focused =
                                window.getRoot().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                        if (focused != null && setNodeText(focused, value)) return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo fallback = null;
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isEditable()) {
                if (fallback == null) fallback = n;
                if (fieldText == null || fieldText.isEmpty()) {
                    if (n.isFocused()) return setNodeText(n, value);
                } else {
                    String t = n.getText() == null ? "" : n.getText().toString();
                    String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
                    if (t.contains(fieldText) || d.contains(fieldText)) return setNodeText(n, value);
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return fallback != null && setNodeText(fallback, value);
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String value) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private int globalAction(String action) {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "back": return GLOBAL_ACTION_BACK;
            case "home": return GLOBAL_ACTION_HOME;
            case "recents": return GLOBAL_ACTION_RECENTS;
            case "notifications": return GLOBAL_ACTION_NOTIFICATIONS;
            case "quick_settings": return GLOBAL_ACTION_QUICK_SETTINGS;
            default: return -1;
        }
    }

    private boolean launchPackage(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return true;
    }

    private JSONArray snapshotTree() {
        JSONArray out = new JSONArray();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return out;
        ArrayDeque<NodeDepth> q = new ArrayDeque<>();
        q.add(new NodeDepth(root, 0));
        int count = 0;
        while (!q.isEmpty() && count < 450) {
            NodeDepth nd = q.removeFirst();
            AccessibilityNodeInfo n = nd.node;
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            JSONObject j = new JSONObject();
            try {
                j.put("depth", nd.depth);
                j.put("text", n.getText() == null ? JSONObject.NULL : n.getText().toString());
                j.put("desc", n.getContentDescription() == null ? JSONObject.NULL : n.getContentDescription().toString());
                j.put("view_id", n.getViewIdResourceName() == null ? JSONObject.NULL : n.getViewIdResourceName());
                j.put("class", n.getClassName() == null ? JSONObject.NULL : n.getClassName().toString());
                j.put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
                j.put("clickable", n.isClickable());
                j.put("editable", n.isEditable());
                j.put("focused", n.isFocused());
                j.put("enabled", n.isEnabled());
                out.put(j);
            } catch (Exception ignored) {}
            count++;
            if (nd.depth < 14) {
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo child = n.getChild(i);
                    if (child != null) q.add(new NodeDepth(child, nd.depth + 1));
                }
            }
        }
        return out;
    }

    private void takeBridgeScreenshot(DeviceIdentity id, String commandId) {
        final AtomicBoolean finished = new AtomicBoolean(false);

        worker.schedule(() -> {
            if (!finished.compareAndSet(false, true)) return;
            report(id, commandId, false, new JSONObject(),
                    "SCREENSHOT_WATCHDOG_TIMEOUT");
            busy = false;
        }, 8, TimeUnit.SECONDS);

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshotResult) {
                if (!finished.compareAndSet(false, true)) {
                    try {
                        HardwareBuffer stale = screenshotResult.getHardwareBuffer();
                        if (stale != null) stale.close();
                    } catch (Exception ignored) {}
                    return;
                }
                try {
                    HardwareBuffer hb = screenshotResult.getHardwareBuffer();
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb, screenshotResult.getColorSpace());
                    if (hw == null) throw new IllegalStateException("Bitmap unavailable");
                    Bitmap soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                    hb.close();

                    int targetW = Math.min(720, soft.getWidth());
                    int targetH = Math.max(1, Math.round(soft.getHeight() * (targetW / (float) soft.getWidth())));
                    Bitmap scaled = targetW == soft.getWidth()
                            ? soft
                            : Bitmap.createScaledBitmap(soft, targetW, targetH, true);

                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    scaled.compress(Bitmap.CompressFormat.JPEG, 48, bytes);
                    String b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);

                    JSONObject result = new JSONObject()
                            .put("width", scaled.getWidth())
                            .put("height", scaled.getHeight())
                            .put("mime", "image/jpeg")
                            .put("base64", b64)
                            .put("tree", snapshotTree());
                    JSONObject visualScan = decorateLocalVisualScan(scaled);
                    if (visualScan != null) result.put("visualScan", visualScan);
                    if (scaled != soft) scaled.recycle();
                    soft.recycle();

                    worker.execute(() -> {
                        report(id, commandId, true, result, null);
                        busy = false;
                    });
                } catch (Exception e) {
                    worker.execute(() -> {
                        report(id, commandId, false, new JSONObject(), "SCREENSHOT_ERROR:" + e.getMessage());
                        busy = false;
                    });
                }
            }

            @Override
            public void onFailure(int errorCode) {
                if (!finished.compareAndSet(false, true)) return;
                worker.execute(() -> {
                    report(id, commandId, false, new JSONObject(), "SCREENSHOT_FAILURE:" + errorCode);
                    busy = false;
                });
            }
        });
    }

    private Uri makeQaImage() {
        try {
            Bitmap b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(Color.rgb(238, 238, 238));

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(210, 35, 35));
            c.drawCircle(256, 245, 145, p);

            p.setColor(Color.rgb(75, 40, 20));
            p.setStrokeWidth(18);
            c.drawLine(270, 105, 292, 60, p);

            p.setColor(Color.rgb(30, 145, 75));
            c.drawOval(287, 66, 360, 110, p);

            p.setColor(Color.BLACK);
            p.setTextSize(34);
            p.setFakeBoldText(true);
            c.drawText("PM-QA", 374, 480, p);

            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "PM-QA-" + System.currentTimeMillis() + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AndroidSessionBridge");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null || !b.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
            }
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.openai.chatgpt".contentEquals(event.getPackageName())) return;

        int type = event.getEventType();

        if ((type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) &&
                System.currentTimeMillis() > localVisualInternalGestureUntil) {
            SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
            String phase = prefs.getString(KEY_VISUAL_SCAN_PHASE, "");
            if (prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false) &&
                    ("opening".equals(phase) || "resetting".equals(phase) ||
                            "capturing".equals(phase))) {
                cancelLocalVisualScan(prefs, "user_interaction");
                return;
            }
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            worker.schedule(this::attemptPendingChatGptMessage, 350, TimeUnit.MILLISECONDS);
            worker.schedule(this::attemptTargetedChatMessage, 390, TimeUnit.MILLISECONDS);
            worker.schedule(this::attemptSessionDiscovery, 420, TimeUnit.MILLISECONDS);
            worker.schedule(this::attemptLocalVisualScan, 460, TimeUnit.MILLISECONDS);
        }
    }

    private void markLocalVisualGesture() {
        localVisualInternalGestureUntil = System.currentTimeMillis() + 1400L;
    }

    private void cancelLocalVisualScan(SharedPreferences prefs, String reason) {
        prefs.edit()
                .putBoolean(KEY_VISUAL_SCAN_ACTIVE, false)
                .putString(KEY_VISUAL_SCAN_PHASE, "cancelled")
                .putString(KEY_VISUAL_SCAN_CANCEL_REASON, reason == null ? "cancelled" : reason)
                .remove(KEY_PENDING_CHATGPT_MESSAGE)
                .remove(KEY_PENDING_CHATGPT_CREATED)
                .remove(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS)
                .remove(KEY_PENDING_CHATGPT_FILLED)
                .remove(KEY_PENDING_CHATGPT_SEND_ATTEMPTS)
                .apply();
        localVisualCaptureBusy = false;
    }

    private void attemptTargetedChatMessage() {
        if (localAutomationBusy) return;
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        String title = prefs.getString(KEY_TARGET_CHAT_TITLE, null);
        String message = prefs.getString(KEY_TARGET_CHAT_MESSAGE, null);
        if (title == null || title.isEmpty() || message == null || message.isEmpty()) return;

        long created = prefs.getLong(KEY_TARGET_CHAT_CREATED, 0L);
        if (created > 0L && System.currentTimeMillis() - created > 120_000L) {
            clearTargetedChatRequest(prefs);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) return;

        localAutomationBusy = true;
        try {
            boolean searchMode = prefs.getBoolean(KEY_TARGET_CHAT_SEARCH_MODE, false);
            int attempts = prefs.getInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, 0);

            // First use semantic nodes when ChatGPT exposes them.
            AccessibilityNodeInfo target = findChatTitleNode(root, title);
            if (target != null && openTargetChatAndQueueMessage(target, message, prefs)) return;

            Rect screen = new Rect();
            root.getBoundsInScreen(screen);
            float width = Math.max(1, screen.width());
            float height = Math.max(1, screen.height());

            if (searchMode) {
                // Search input becomes focusable even on Compose builds whose normal
                // conversation tree is opaque to Accessibility.
                boolean typed = setText(title, null);
                attempts++;
                prefs.edit().putInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, attempts).apply();

                // Give ChatGPT enough time to populate results. If semantic result
                // nodes remain hidden, select the first exact search result by the
                // live-verified result row position.
                if (typed && attempts >= 2) {
                    boolean opened = tap(
                            screen.left + width * 0.50f,
                            screen.top + height * 0.145f);
                    if (opened) {
                        prefs.edit()
                                .remove(KEY_TARGET_CHAT_TITLE)
                                .remove(KEY_TARGET_CHAT_MESSAGE)
                                .remove(KEY_TARGET_CHAT_CREATED)
                                .remove(KEY_TARGET_CHAT_SEARCH_MODE)
                                .remove(KEY_TARGET_CHAT_SEARCH_ATTEMPTS)
                                .putString(KEY_PENDING_CHATGPT_MESSAGE, message)
                                .putLong(KEY_PENDING_CHATGPT_CREATED, System.currentTimeMillis())
                                .putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0)
                                .putBoolean(KEY_PENDING_CHATGPT_FILLED, false)
                                .putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0)
                                .apply();
                        worker.schedule(this::attemptPendingChatGptMessage, 850, TimeUnit.MILLISECONDS);
                        return;
                    }
                }

                if (attempts >= 8) {
                    clearTargetedChatRequest(prefs);
                    return;
                }
                worker.schedule(this::attemptTargetedChatMessage, 500, TimeUnit.MILLISECONDS);
                return;
            }

            // App-only navigation contract: the user never has to open the sidebar.
            // First invocation starts from the last active ChatGPT conversation.
            if (attempts == 0) {
                AccessibilityNodeInfo opener = findSidebarOpener(root);
                boolean opened = false;
                if (opener != null) {
                    AccessibilityNodeInfo clickable = clickableAncestor(opener);
                    if (clickable != null) markLocalVisualGesture();
                    opened = clickable != null &&
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (!opened) {
                    // Verified on the current ChatGPT Android Compose layout.
                    opened = tap(
                            screen.left + width * 0.895f,
                            screen.top + height * 0.072f);
                }
                prefs.edit().putInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, 1).apply();
                worker.schedule(this::attemptTargetedChatMessage, 650, TimeUnit.MILLISECONDS);
                return;
            }

            // Sidebar is now expected to be open. Prefer its semantic Search control,
            // then use the live-verified Search-button coordinate when Compose hides it.
            boolean searchOpened =
                    clickText("חיפוש", true) || clickText("Search", true);
            if (!searchOpened) {
                searchOpened = tap(
                        screen.left + width * 0.32f,
                        screen.top + height * 0.075f);
            }

            if (searchOpened) {
                prefs.edit()
                        .putBoolean(KEY_TARGET_CHAT_SEARCH_MODE, true)
                        .putInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, 0)
                        .apply();
                worker.schedule(this::attemptTargetedChatMessage, 700, TimeUnit.MILLISECONDS);
                return;
            }

            attempts++;
            if (attempts >= 4) {
                clearTargetedChatRequest(prefs);
                return;
            }
            prefs.edit().putInt(KEY_TARGET_CHAT_SEARCH_ATTEMPTS, attempts).apply();
            worker.schedule(this::attemptTargetedChatMessage, 550, TimeUnit.MILLISECONDS);
        } finally {
            localAutomationBusy = false;
        }
    }

    private boolean openTargetChatAndQueueMessage(
            AccessibilityNodeInfo target, String message, SharedPreferences prefs) {
        AccessibilityNodeInfo clickable = clickableAncestor(target);
        boolean opened = clickable != null &&
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (!opened) {
            Rect r = new Rect();
            target.getBoundsInScreen(r);
            if (!r.isEmpty()) opened = tap(r.exactCenterX(), r.exactCenterY());
        }
        if (!opened) return false;

        prefs.edit()
                .remove(KEY_TARGET_CHAT_TITLE)
                .remove(KEY_TARGET_CHAT_MESSAGE)
                .remove(KEY_TARGET_CHAT_CREATED)
                .remove(KEY_TARGET_CHAT_SEARCH_MODE)
                .remove(KEY_TARGET_CHAT_SEARCH_ATTEMPTS)
                .putString(KEY_PENDING_CHATGPT_MESSAGE, message)
                .putLong(KEY_PENDING_CHATGPT_CREATED, System.currentTimeMillis())
                .putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0)
                .putBoolean(KEY_PENDING_CHATGPT_FILLED, false)
                .putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0)
                .apply();

        worker.schedule(this::attemptPendingChatGptMessage, 650, TimeUnit.MILLISECONDS);
        return true;
    }

    private void clearTargetedChatRequest(SharedPreferences prefs) {
        prefs.edit()
                .remove(KEY_TARGET_CHAT_TITLE)
                .remove(KEY_TARGET_CHAT_MESSAGE)
                .remove(KEY_TARGET_CHAT_CREATED)
                .remove(KEY_TARGET_CHAT_SEARCH_MODE)
                .remove(KEY_TARGET_CHAT_SEARCH_ATTEMPTS)
                .apply();
    }

    private void attemptPendingChatGptMessage() {
        if (localAutomationBusy) return;

        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        String message = prefs.getString(KEY_PENDING_CHATGPT_MESSAGE, null);
        if (message == null || message.isEmpty()) return;

        long created = prefs.getLong(KEY_PENDING_CHATGPT_CREATED, 0L);
        if (created > 0L && System.currentTimeMillis() - created > 120_000L) {
            prefs.edit()
                    .remove(KEY_PENDING_CHATGPT_MESSAGE)
                    .remove(KEY_PENDING_CHATGPT_CREATED)
                    .remove(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS)
                    .remove(KEY_PENDING_CHATGPT_FILLED)
                    .remove(KEY_PENDING_CHATGPT_SEND_ATTEMPTS)
                    .apply();
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) {
            return;
        }

        localAutomationBusy = true;
        try {
            AccessibilityNodeInfo editor = findComposer(root);

            // Compose may hide the editor until it receives a real touch. The
            // composer center was verified live; focus it and retry automatically.
            if (editor == null) {
                int focusAttempts = prefs.getInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0);
                if (focusAttempts >= 4) return;

                Rect screen = new Rect();
                root.getBoundsInScreen(screen);
                float width = Math.max(1, screen.width());
                float height = Math.max(1, screen.height());
                tap(
                        screen.left + width * 0.50f,
                        screen.top + height * 0.900f);
                prefs.edit()
                        .putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, focusAttempts + 1)
                        .apply();
                worker.schedule(this::attemptPendingChatGptMessage, 450, TimeUnit.MILLISECONDS);
                return;
            }

            prefs.edit().putInt(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS, 0).apply();

            boolean alreadyFilled = prefs.getBoolean(KEY_PENDING_CHATGPT_FILLED, false);
            if (!alreadyFilled) {
                if (!setNodeText(editor, message)) return;
                prefs.edit()
                        .putBoolean(KEY_PENDING_CHATGPT_FILLED, true)
                        .putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0)
                        .apply();
            }

            try {
                Thread.sleep(320L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            AccessibilityNodeInfo latestRoot = getRootInActiveWindow();
            if (latestRoot == null) return;

            AccessibilityNodeInfo send = findSendButton(latestRoot);
            boolean sent = false;
            if (send != null) {
                AccessibilityNodeInfo clickable = send;
                while (clickable != null && !clickable.isClickable()) {
                    clickable = clickable.getParent();
                }
                sent = clickable != null &&
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }

            if (!sent) {
                int sendAttempts = prefs.getInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, 0) + 1;
                prefs.edit().putInt(KEY_PENDING_CHATGPT_SEND_ATTEMPTS, sendAttempts).apply();
                if (sendAttempts >= 4) {
                    prefs.edit()
                            .remove(KEY_PENDING_CHATGPT_MESSAGE)
                            .remove(KEY_PENDING_CHATGPT_CREATED)
                            .remove(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS)
                            .remove(KEY_PENDING_CHATGPT_FILLED)
                            .remove(KEY_PENDING_CHATGPT_SEND_ATTEMPTS)
                            .apply();
                    if (prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false)) {
                        cancelLocalVisualScan(prefs, "send_button_unavailable");
                    }
                    return;
                }
                worker.schedule(this::attemptPendingChatGptMessage, 500, TimeUnit.MILLISECONDS);
                return;
            }

            if (sent) {
                SharedPreferences.Editor done = prefs.edit()
                        .remove(KEY_PENDING_CHATGPT_MESSAGE)
                        .remove(KEY_PENDING_CHATGPT_CREATED)
                        .remove(KEY_PENDING_CHATGPT_FOCUS_ATTEMPTS)
                        .remove(KEY_PENDING_CHATGPT_FILLED)
                        .remove(KEY_PENDING_CHATGPT_SEND_ATTEMPTS);
                if (prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false) &&
                        "waiting_message".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, ""))) {
                    done.putString(KEY_VISUAL_SCAN_PHASE, "opening")
                            .putInt(KEY_VISUAL_SCAN_RESET_COUNT, 0)
                            .putString(KEY_VISUAL_SCAN_RESET_HASH, "")
                            .putInt(KEY_VISUAL_SCAN_RESET_SAME, 0)
                            .putInt(KEY_VISUAL_SCAN_PAGE, 0)
                            .putInt(KEY_VISUAL_SCAN_FRAME_COUNT, 0)
                            .putString(KEY_VISUAL_SCAN_LAST_HASH, "")
                            .putInt(KEY_VISUAL_SCAN_SAME_COUNT, 0);
                }
                done.apply();
                worker.schedule(this::attemptLocalVisualScan, 650, TimeUnit.MILLISECONDS);
            }
        } finally {
            localAutomationBusy = false;
        }
    }


    private void attemptLocalVisualScan() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false)) return;

        long started = prefs.getLong(KEY_VISUAL_SCAN_STARTED, 0L);
        if (started > 0L && System.currentTimeMillis() - started > 1_800_000L) {
            prefs.edit()
                    .putBoolean(KEY_VISUAL_SCAN_ACTIVE, false)
                    .putString(KEY_VISUAL_SCAN_PHASE, "timeout")
                    .apply();
            launchPackage("com.yackov.androidsessionbridge");
            return;
        }

        String phase = prefs.getString(KEY_VISUAL_SCAN_PHASE, "waiting_message");
        if ("waiting_message".equals(phase) || "await_submit".equals(phase) ||
                "done".equals(phase) || "timeout".equals(phase) || "error".equals(phase)) return;
        if (localAutomationBusy || localVisualCaptureBusy) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) {
            worker.schedule(this::attemptLocalVisualScan, 600, TimeUnit.MILLISECONDS);
            return;
        }

        localAutomationBusy = true;
        try {
            Rect screen = new Rect();
            root.getBoundsInScreen(screen);
            if (screen.isEmpty()) return;

            if ("opening".equals(phase)) {
                boolean opened = false;
                AccessibilityNodeInfo opener = findSidebarOpener(root);
                if (opener != null) {
                    AccessibilityNodeInfo clickable = clickableAncestor(opener);
                    opened = clickable != null &&
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (!opened) {
                    markLocalVisualGesture();
                    opened = tap(
                            screen.left + screen.width() * 0.895f,
                            screen.top + screen.height() * 0.072f);
                }
                if (opened) {
                    prefs.edit()
                            .putString(KEY_VISUAL_SCAN_PHASE, "resetting")
                            .putInt(KEY_VISUAL_SCAN_RESET_COUNT, 0)
                            .putString(KEY_VISUAL_SCAN_RESET_HASH, "")
                            .putInt(KEY_VISUAL_SCAN_RESET_SAME, 0)
                            .apply();
                    worker.schedule(this::resetLocalVisualScanStep, 700, TimeUnit.MILLISECONDS);
                } else {
                    worker.schedule(this::attemptLocalVisualScan, 700, TimeUnit.MILLISECONDS);
                }
                return;
            }

            if ("resetting".equals(phase)) {
                worker.schedule(this::resetLocalVisualScanStep, 50, TimeUnit.MILLISECONDS);
                return;
            }

            if ("capturing".equals(phase)) {
                worker.schedule(this::captureAndUploadLocalVisualFrame, 50, TimeUnit.MILLISECONDS);
            }
        } finally {
            localAutomationBusy = false;
        }
    }

    private void resetLocalVisualScanStep() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false) ||
                !"resetting".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, "")) ||
                localVisualCaptureBusy) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) {
            worker.schedule(this::resetLocalVisualScanStep, 600, TimeUnit.MILLISECONDS);
            return;
        }

        localVisualCaptureBusy = true;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshotResult) {
                try {
                    HardwareBuffer hb = screenshotResult.getHardwareBuffer();
                    Bitmap hw = Bitmap.wrapHardwareBuffer(hb, screenshotResult.getColorSpace());
                    if (hw == null) throw new IllegalStateException("Bitmap unavailable");
                    Bitmap soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                    hb.close();

                    String hash = visualSidebarHash(soft);
                    soft.recycle();

                    String last = prefs.getString(KEY_VISUAL_SCAN_RESET_HASH, "");
                    int same = prefs.getInt(KEY_VISUAL_SCAN_RESET_SAME, 0);
                    int count = prefs.getInt(KEY_VISUAL_SCAN_RESET_COUNT, 0);
                    same = !hash.isEmpty() && hash.equals(last) ? same + 1 : 0;

                    if (same >= 2 || count >= VISUAL_SCAN_MAX_RESET_STEPS) {
                        prefs.edit()
                                .putString(KEY_VISUAL_SCAN_PHASE, "capturing")
                                .putInt(KEY_VISUAL_SCAN_PAGE, 0)
                                .putInt(KEY_VISUAL_SCAN_FRAME_COUNT, 0)
                                .putString(KEY_VISUAL_SCAN_LAST_HASH, "")
                                .putInt(KEY_VISUAL_SCAN_SAME_COUNT, 0)
                                .apply();
                        worker.schedule(BridgeAccessibilityService.this::captureAndUploadLocalVisualFrame,
                                250, TimeUnit.MILLISECONDS);
                        return;
                    }

                    Rect screen = new Rect();
                    AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
                    if (currentRoot != null) currentRoot.getBoundsInScreen(screen);
                    if (!screen.isEmpty()) markLocalVisualGesture();
                    boolean accepted = !screen.isEmpty() && swipe(
                            screen.left + screen.width() * 0.70f,
                            screen.top + screen.height() * 0.28f,
                            screen.left + screen.width() * 0.70f,
                            screen.top + screen.height() * 0.86f,
                            360L);

                    prefs.edit()
                            .putString(KEY_VISUAL_SCAN_RESET_HASH, hash)
                            .putInt(KEY_VISUAL_SCAN_RESET_SAME, same)
                            .putInt(KEY_VISUAL_SCAN_RESET_COUNT, accepted ? count + 1 : count)
                            .apply();
                    worker.schedule(BridgeAccessibilityService.this::resetLocalVisualScanStep,
                            accepted ? 470 : 750, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    worker.schedule(BridgeAccessibilityService.this::resetLocalVisualScanStep,
                            850, TimeUnit.MILLISECONDS);
                } finally {
                    localVisualCaptureBusy = false;
                }
            }

            @Override
            public void onFailure(int errorCode) {
                localVisualCaptureBusy = false;
                worker.schedule(BridgeAccessibilityService.this::resetLocalVisualScanStep,
                        900, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void captureAndUploadLocalVisualFrame() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false) ||
                !"capturing".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, "")) ||
                localVisualCaptureBusy) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) {
            worker.schedule(this::captureAndUploadLocalVisualFrame, 600, TimeUnit.MILLISECONDS);
            return;
        }

        localVisualCaptureBusy = true;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshotResult) {
                worker.execute(() -> {
                    Bitmap soft = null;
                    Bitmap scaled = null;
                    try {
                        HardwareBuffer hb = screenshotResult.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, screenshotResult.getColorSpace());
                        if (hw == null) throw new IllegalStateException("Bitmap unavailable");
                        soft = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hb.close();

                        String hash = visualSidebarHash(soft);
                        String lastHash = prefs.getString(KEY_VISUAL_SCAN_LAST_HASH, "");
                        int same = prefs.getInt(KEY_VISUAL_SCAN_SAME_COUNT, 0);
                        int frameCount = prefs.getInt(KEY_VISUAL_SCAN_FRAME_COUNT, 0);
                        same = !hash.isEmpty() && hash.equals(lastHash) ? same + 1 : 0;

                        if (same >= 2 || frameCount >= VISUAL_SCAN_MAX_FRAMES) {
                            String nonce = prefs.getString(KEY_VISUAL_SCAN_NONCE, "");
                            String sessionId = prefs.getString(KEY_VISUAL_SCAN_SESSION_ID, "");
                            DeviceIdentity id = DeviceIdentity.getOrCreate(BridgeAccessibilityService.this);
                            api.completeVisualScanFrames(id, sessionId, nonce, frameCount);
                            prefs.edit()
                                    .putString(KEY_VISUAL_SCAN_PHASE, "await_submit")
                                    .putInt(KEY_VISUAL_SCAN_SAME_COUNT, same)
                                    .apply();
                            return;
                        }

                        if (same == 0) {
                            int targetW = Math.min(720, soft.getWidth());
                            int targetH = Math.max(1,
                                    Math.round(soft.getHeight() * (targetW / (float) soft.getWidth())));
                            scaled = targetW == soft.getWidth()
                                    ? soft
                                    : Bitmap.createScaledBitmap(soft, targetW, targetH, true);

                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            scaled.compress(Bitmap.CompressFormat.JPEG, 48, bytes);
                            String b64 = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);

                            String nonce = prefs.getString(KEY_VISUAL_SCAN_NONCE, "");
                            String sessionId = prefs.getString(KEY_VISUAL_SCAN_SESSION_ID, "");
                            DeviceIdentity id = DeviceIdentity.getOrCreate(BridgeAccessibilityService.this);
                            api.uploadVisualScanFrame(
                                    id, sessionId, nonce, frameCount, hash,
                                    scaled.getWidth(), scaled.getHeight(),
                                    "image/jpeg", b64);

                            prefs.edit()
                                    .putString(KEY_VISUAL_SCAN_LAST_HASH, hash)
                                    .putInt(KEY_VISUAL_SCAN_SAME_COUNT, 0)
                                    .putInt(KEY_VISUAL_SCAN_PAGE, frameCount)
                                    .putInt(KEY_VISUAL_SCAN_FRAME_COUNT, frameCount + 1)
                                    .apply();
                        } else {
                            prefs.edit().putInt(KEY_VISUAL_SCAN_SAME_COUNT, same).apply();
                        }

                        Rect screen = new Rect();
                        AccessibilityNodeInfo currentRoot = getRootInActiveWindow();
                        if (currentRoot != null) currentRoot.getBoundsInScreen(screen);
                        if (!screen.isEmpty()) markLocalVisualGesture();
                        boolean accepted = !screen.isEmpty() && swipe(
                                screen.left + screen.width() * 0.70f,
                                screen.top + screen.height() * 0.84f,
                                screen.left + screen.width() * 0.70f,
                                screen.top + screen.height() * 0.28f,
                                520L);

                        worker.schedule(
                                BridgeAccessibilityService.this::captureAndUploadLocalVisualFrame,
                                accepted ? 650 : 900, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        worker.schedule(
                                BridgeAccessibilityService.this::captureAndUploadLocalVisualFrame,
                                1000, TimeUnit.MILLISECONDS);
                    } finally {
                        if (scaled != null && scaled != soft) scaled.recycle();
                        if (soft != null && !soft.isRecycled()) soft.recycle();
                        localVisualCaptureBusy = false;
                    }
                });
            }

            @Override
            public void onFailure(int errorCode) {
                localVisualCaptureBusy = false;
                worker.schedule(BridgeAccessibilityService.this::captureAndUploadLocalVisualFrame,
                        1000, TimeUnit.MILLISECONDS);
            }
        });
    }

    private String visualSidebarHash(Bitmap bitmap) {
        try {
            int left = 0;
            int top = Math.min(bitmap.getHeight() - 1, Math.max(0, Math.round(bitmap.getHeight() * 0.08f)));
            int right = Math.max(1, Math.min(bitmap.getWidth(), Math.round(bitmap.getWidth() * 0.90f)));
            int bottom = Math.max(top + 1, Math.min(bitmap.getHeight(), Math.round(bitmap.getHeight() * 0.93f)));
            Bitmap crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
            Bitmap tiny = Bitmap.createScaledBitmap(crop, 24, 24, true);
            StringBuilder out = new StringBuilder(24 * 24);
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < 24; x++) {
                    int pixel = tiny.getPixel(x, y);
                    int lum = (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100;
                    out.append(Integer.toHexString(Math.min(15, Math.max(0, lum / 16))));
                }
            }
            if (tiny != crop) tiny.recycle();
            crop.recycle();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private JSONObject decorateLocalVisualScan(Bitmap bitmap) {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false)) return null;

        JSONObject meta = new JSONObject();
        try {
            meta.put("nonce", prefs.getString(KEY_VISUAL_SCAN_NONCE, ""));
            meta.put("phase", prefs.getString(KEY_VISUAL_SCAN_PHASE, ""));
            meta.put("frameCount", prefs.getInt(KEY_VISUAL_SCAN_FRAME_COUNT, 0));
            meta.put("autonomous", true);
            meta.put("ready", "await_submit".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, "")));
            meta.put("final", "await_submit".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, "")));
        } catch (Exception ignored) {}
        return meta;
    }

    private void pollLocalVisualScanCompletion() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_VISUAL_SCAN_ACTIVE, false) ||
                !"await_submit".equals(prefs.getString(KEY_VISUAL_SCAN_PHASE, ""))) return;

        String nonce = prefs.getString(KEY_VISUAL_SCAN_NONCE, "");
        if (nonce == null || nonce.isEmpty()) return;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) return;

        try {
            JSONObject response = api.listDiscoveredChats(id);
            JSONArray chats = response.optJSONArray("chats");
            boolean matched = false;
            if (chats != null) {
                for (int i = 0; i < chats.length(); i++) {
                    JSONObject row = chats.optJSONObject(i);
                    JSONObject metadata = row == null ? null : row.optJSONObject("metadata");
                    if (metadata != null && nonce.equals(metadata.optString("nonce", ""))) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                prefs.edit()
                        .putBoolean(KEY_VISUAL_SCAN_ACTIVE, false)
                        .putString(KEY_VISUAL_SCAN_PHASE, "done")
                        .apply();
                launchPackage("com.yackov.androidsessionbridge");
            }
        } catch (Exception ignored) {
        }
    }

    private void attemptSessionDiscovery() {
        SharedPreferences prefs = getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DISCOVERY_ACTIVE, false)) return;
        if (localAutomationBusy) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.openai.chatgpt".contentEquals(root.getPackageName())) {
            return;
        }

        localAutomationBusy = true;
        try {
            int pass = prefs.getInt(KEY_DISCOVERY_PASS, 0);
            boolean sidebarOpened = prefs.getBoolean(KEY_DISCOVERY_SIDEBAR_OPENED, false);
            boolean sidebarDetected = looksLikeChatSidebar(root);

            // Never collect titles from the conversation body. Discovery must first
            // open ChatGPT's sidebar; otherwise ordinary message/action text can be
            // mistaken for a chat title.
            if (!sidebarOpened && !sidebarDetected) {
                boolean opened = false;
                AccessibilityNodeInfo opener = findSidebarOpener(root);
                if (opener != null) {
                    AccessibilityNodeInfo clickable = opener;
                    while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                    opened = clickable != null &&
                            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                if (!opened) {
                    Rect screen = new Rect();
                    root.getBoundsInScreen(screen);
                    if (!screen.isEmpty()) {
                        opened = tap(
                                screen.left + screen.width() * 0.895f,
                                screen.top + screen.height() * 0.072f);
                    }
                }
                if (opened) {
                    prefs.edit()
                            .putBoolean(KEY_DISCOVERY_SIDEBAR_OPENED, true)
                            .putInt(KEY_DISCOVERY_PASS, pass + 1)
                            .apply();
                    worker.schedule(this::attemptSessionDiscovery, 750, TimeUnit.MILLISECONDS);
                } else {
                    prefs.edit()
                            .putInt(KEY_DISCOVERY_PASS, pass + 1)
                            .apply();
                    if (pass < 4) {
                        worker.schedule(this::attemptSessionDiscovery, 650, TimeUnit.MILLISECONDS);
                    } else {
                        finishSessionDiscovery(readSavedDiscoveryTitles(prefs));
                    }
                }
                return;
            }

            // If ChatGPT exposes normal sidebar markers, keep the flag truthful too.
            if (sidebarDetected && !sidebarOpened) {
                prefs.edit().putBoolean(KEY_DISCOVERY_SIDEBAR_OPENED, true).apply();
            }

            AccessibilityNodeInfo selectedNow = findSelectedChatTitleNode(root);
            if (selectedNow != null && selectedNow.getText() != null) {
                String selectedTitle = normalizeChatTitle(selectedNow.getText().toString());
                if (!selectedTitle.isEmpty()) {
                    LinkedHashSet<String> before = readSavedDiscoveryTitles(prefs);
                    int selectedOrdinal = 1;
                    int idx = 0;
                    for (String item : before) {
                        idx++;
                        if (normalizeChatTitle(item).equals(selectedTitle)) {
                            selectedOrdinal = idx;
                            break;
                        }
                    }
                    prefs.edit()
                            .putString(KEY_CURRENT_CHAT_TITLE, selectedTitle)
                            .putString(KEY_CURRENT_CHAT_KEY, stableChatKey(selectedTitle, selectedOrdinal))
                            .apply();
                }
            }

            LinkedHashSet<String> titles = readSavedDiscoveryTitles(prefs);
            titles.addAll(collectVisibleChatTitles(root));
            prefs.edit()
                    .putString(KEY_DISCOVERY_TITLES, jsonArrayString(titles))
                    .putInt(KEY_DISCOVERY_PASS, pass + 1)
                    .apply();

            AccessibilityNodeInfo scrollable = findLargestScrollable(root);
            boolean scrolled = false;
            if (scrollable != null && pass < 28) {
                scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            }

            if (scrolled) {
                worker.schedule(this::attemptSessionDiscovery, 520, TimeUnit.MILLISECONDS);
                return;
            }

            finishSessionDiscovery(titles);
        } finally {
            localAutomationBusy = false;
        }
    }

    private void finishSessionDiscovery(LinkedHashSet<String> titles) {
        try {
            JSONArray chats = new JSONArray();
            int ordinal = 0;
            for (String title : titles) {
                String clean = normalizeChatTitle(title);
                if (clean.isEmpty()) continue;
                ordinal++;
                chats.put(new JSONObject()
                        .put("chatKey", stableChatKey(clean, ordinal))
                        .put("title", clean)
                        .put("ordinal", ordinal)
                        .put("visibleAtSync", true));
            }

            DeviceIdentity id = DeviceIdentity.getOrCreate(this);
            if (id.paired && chats.length() > 0) {
                api.syncDiscoveredChats(id, chats);
            }

            getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_DISCOVERY_ACTIVE, false)
                    .putInt(KEY_DISCOVERY_PASS, 0)
                    .putBoolean(KEY_DISCOVERY_SIDEBAR_OPENED, false)
                    .putString(KEY_DISCOVERY_TITLES, chats.toString())
                    .apply();

            if (bridgeAutoReturnAllowed()) {
                Intent bridge = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (bridge != null) {
                    bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(bridge);
                }
            }
        } catch (Exception ignored) {
            getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DISCOVERY_ACTIVE, false).apply();
        }
    }

    private AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        int hops = 0;
        while (current != null && hops++ < 6) {
            if (current.isClickable()) return current;
            current = current.getParent();
        }
        return null;
    }

    private AccessibilityNodeInfo findChatTitleNode(AccessibilityNodeInfo root, String wanted) {
        if (wanted == null) return null;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence raw = n.getText();
            if (raw != null) {
                String title = normalizeChatTitle(raw.toString());
                if (title.equals(wanted) && isLikelyChatTitle(title, n)) return n;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findSelectedChatTitleNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo accessibilityFocusedCandidate = null;
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence raw = n.getText();
            if (raw != null) {
                String title = normalizeChatTitle(raw.toString());
                if (isLikelyChatTitle(title, n)) {
                    if (n.isSelected()) return n;
                    String desc = n.getContentDescription() == null ? "" :
                            n.getContentDescription().toString().toLowerCase(Locale.ROOT);
                    if (desc.contains("selected") || desc.contains("current") ||
                            desc.contains("נבחר") || desc.contains("נוכחי")) return n;
                    if (n.isAccessibilityFocused()) accessibilityFocusedCandidate = n;
                    AccessibilityNodeInfo p = n.getParent();
                    int hops = 0;
                    while (p != null && hops++ < 4) {
                        if (p.isSelected()) return n;
                        String pd = p.getContentDescription() == null ? "" :
                                p.getContentDescription().toString().toLowerCase(Locale.ROOT);
                        if (pd.contains("selected") || pd.contains("current") ||
                                pd.contains("נבחר") || pd.contains("נוכחי")) return n;
                        p = p.getParent();
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return accessibilityFocusedCandidate;
    }

    private AccessibilityNodeInfo findSidebarOpener(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String text = n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(Locale.ROOT);
            String desc = n.getContentDescription() == null ? "" :
                    n.getContentDescription().toString().trim().toLowerCase(Locale.ROOT);

            boolean match =
                    desc.contains("open sidebar") ||
                    desc.contains("sidebar") ||
                    desc.equals("menu") ||
                    desc.contains("navigation") ||
                    desc.contains("פתח סרגל") ||
                    desc.contains("תפריט") ||
                    text.equals("menu");

            if (match && n.isEnabled()) return n;

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    private boolean looksLikeChatSidebar(AccessibilityNodeInfo root) {
        int markers = 0;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String t = n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (t.equals("new chat") || t.equals("search") || t.equals("chats") ||
                    t.equals("שיחה חדשה") || t.equals("חיפוש") || t.equals("שיחות")) {
                markers++;
                if (markers >= 2) return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return false;
    }

    private List<String> collectVisibleChatTitles(AccessibilityNodeInfo root) {
        List<String> out = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence raw = n.getText();
            if (raw != null) {
                String t = normalizeChatTitle(raw.toString());
                if (isLikelyChatTitle(t, n)) out.add(t);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return out;
    }

    private boolean isLikelyChatTitle(String t, AccessibilityNodeInfo node) {
        if (t == null || t.length() < 2 || t.length() > 180) return false;
        String lower = t.toLowerCase(Locale.ROOT);

        String[] excluded = new String[] {
                "chatgpt","new chat","search","images","plugins","projects","library",
                "settings","help","upgrade","log out","share","rename","delete","archive",
                "codex","explore","scheduled","see all","see all…","sources",
                "שיחה חדשה","חיפוש","תמונות","תוספים","פרויקטים","ספרייה","הגדרות",
                "עזרה","שיתוף","שנה שם","מחק","ארכיון","gpt-5","gpt-4","temporary chat"
        };
        for (String x : excluded) {
            if (lower.equals(x) || lower.startsWith(x + " ")) return false;
        }

        if (node.isEditable()) return false;
        if (lower.matches("^\\d{1,2}:\\d{2}$")) return false;

        AccessibilityNodeInfo p = node;
        int hops = 0;
        while (p != null && hops++ < 4) {
            if (p.isClickable()) return true;
            p = p.getParent();
        }
        return false;
    }

    private AccessibilityNodeInfo findLargestScrollable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo best = null;
        int bestArea = -1;

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isScrollable()) {
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                int area = Math.max(0, r.width()) * Math.max(0, r.height());
                if (area > bestArea) {
                    bestArea = area;
                    best = n;
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return best;
    }

    private LinkedHashSet<String> readSavedDiscoveryTitles(SharedPreferences prefs) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_DISCOVERY_TITLES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                if (v instanceof String) out.add((String) v);
                else if (v instanceof JSONObject) {
                    String title = ((JSONObject) v).optString("title", "");
                    if (!title.isEmpty()) out.add(title);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String jsonArrayString(Set<String> titles) {
        JSONArray arr = new JSONArray();
        for (String title : titles) arr.put(title);
        return arr.toString();
    }

    private String normalizeChatTitle(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ").trim();
    }

    private String stableChatKey(String title, int ordinal) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Discovery stores titles in a LinkedHashSet, so duplicate titles are
            // already collapsed. Including the global list position made the same
            // conversation receive a different key whenever recency reordered it.
            byte[] bytes = digest.digest(title.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(title.hashCode()) +
                    Integer.toHexString(("chat:" + title).hashCode());
        }
    }

    private AccessibilityNodeInfo findComposer(AccessibilityNodeInfo root) {
        try {
            AccessibilityNodeInfo focused =
                    root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.isEnabled()) return focused;
        } catch (Exception ignored) {}

        try {
            List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    if (window == null || window.getRoot() == null) continue;
                    AccessibilityNodeInfo focused =
                            window.getRoot().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    if (focused != null && focused.isEnabled()) return focused;
                }
            }
        } catch (Exception ignored) {}

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo fallback = null;

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isEditable() && n.isEnabled()) {
                if (fallback == null) fallback = n;
                if (n.isFocused()) return n;

                String hint = "";
                try {
                    CharSequence h = n.getHintText();
                    if (h != null) hint = h.toString().toLowerCase(Locale.ROOT);
                } catch (Exception ignored) {}

                String desc = n.getContentDescription() == null
                        ? "" : n.getContentDescription().toString().toLowerCase(Locale.ROOT);

                if (hint.contains("message") || hint.contains("chatgpt") ||
                        hint.contains("הודעה") || desc.contains("message") ||
                        desc.contains("הודעה")) {
                    return n;
                }
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return fallback;
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String text = n.getText() == null ? "" : n.getText().toString().toLowerCase(Locale.ROOT);
            String desc = n.getContentDescription() == null
                    ? "" : n.getContentDescription().toString().toLowerCase(Locale.ROOT);

            boolean sendLike =
                    text.equals("send") || text.equals("שלח") ||
                    desc.equals("send") || desc.equals("שלח") ||
                    desc.contains("send message") ||
                    desc.contains("send prompt") ||
                    desc.contains("שליחת הודעה");

            if (sendLike && n.isEnabled()) return n;

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.add(child);
            }
        }
        return null;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private static final class NodeDepth {
        final AccessibilityNodeInfo node;
        final int depth;
        NodeDepth(AccessibilityNodeInfo node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
