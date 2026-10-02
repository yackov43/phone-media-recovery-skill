package com.yackov.androidsessionbridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ContentValues;
import android.content.Intent;
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
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BridgeAccessibilityService extends AccessibilityService {
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final BridgeApi api = new BridgeApi();
    private volatile boolean busy = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        worker.scheduleWithFixedDelay(this::pollOnce, 250, 700, TimeUnit.MILLISECONDS);
    }

    private void pollOnce() {
        if (busy) return;
        DeviceIdentity id = DeviceIdentity.getOrCreate(this);
        if (!id.paired) return;

        try {
            JSONObject response = api.poll(id);
            if (!response.optBoolean("ok", false)) return;
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
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshotResult) {
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
