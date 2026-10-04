package com.yackov.androidsessionbridge;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public final class BridgeApi {
    private static final String ENDPOINT =
            "https://dwwsjglbhzmxspjogjvq.supabase.co/functions/v1/android-session-bridge";
    public static final String APP_VERSION = "1.0.8";

    private JSONObject request(String query, String method, DeviceIdentity id, JSONObject body)
            throws Exception {
        URL url = new URL(ENDPOINT + query);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-android-bridge-token", id.secret);
        c.setRequestProperty("x-android-bridge-version", APP_VERSION);
        c.setUseCaches(false);

        if (body != null && !"GET".equals(method)) {
            c.setDoOutput(true);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = c.getOutputStream()) {
                os.write(payload);
            }
        }

        int status = c.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();

        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + sb);
        }
        return sb.length() == 0 ? new JSONObject() : new JSONObject(sb.toString());
    }

    public JSONObject beginPairing(DeviceIdentity id, String label, JSONArray agentSuite)
            throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("label", label)
                .put("appVersion", APP_VERSION)
                .put("deviceInfo", deviceInfo())
                .put("agentSuite", agentSuite == null ? new JSONArray() : agentSuite);
        return request("?android_bridge=pair_begin", "POST", id, body);
    }

    public JSONObject pairStatus(DeviceIdentity id) throws Exception {
        return pairStatus(id, null);
    }

    public JSONObject pairStatus(DeviceIdentity id, String requestId) throws Exception {
        String query = "?android_bridge=pair_status&device_id=" +
                URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name());
        if (requestId != null && !requestId.isEmpty()) {
            query += "&request_id=" +
                    URLEncoder.encode(requestId, StandardCharsets.UTF_8.name());
        }
        return request(query, "GET", id, null);
    }

    public JSONObject completePairing(DeviceIdentity id, String requestId) throws Exception {
        return request("?android_bridge=pair_complete", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("requestId", requestId == null ? "" : requestId));
    }


    public JSONObject disconnect(DeviceIdentity id) throws Exception {
        return request("?android_bridge=disconnect", "POST", id,
                new JSONObject().put("deviceId", id.deviceId));
    }

    public JSONObject setAgentSuite(DeviceIdentity id, JSONArray roles) throws Exception {
        return request("?android_bridge=agent_settings", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("roles", roles == null ? new JSONArray() : roles));
    }

    public JSONObject getAutonomy(DeviceIdentity id, String sessionId) throws Exception {
        String query = "?android_bridge=autonomy_get&device_id=" +
                java.net.URLEncoder.encode(id.deviceId, "UTF-8") +
                "&session_id=" + java.net.URLEncoder.encode(sessionId, "UTF-8");
        return request(query, "GET", id, null);
    }

    public JSONObject setAutonomy(
            DeviceIdentity id, String sessionId, boolean enabled, JSONObject policy) throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("sessionId", sessionId)
                .put("enabled", enabled);
        if (policy != null) body.put("policy", policy);
        return request("?android_bridge=autonomy_set", "POST", id, body);
    }

    public JSONObject resolveAutonomyDecision(
            DeviceIdentity id,
            String sessionId,
            String decisionId,
            String resolution,
            String comment) throws Exception {
        return request("?android_bridge=autonomy_decision", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("sessionId", sessionId)
                        .put("decisionId", decisionId == null ? "" : decisionId)
                        .put("resolution", resolution)
                        .put("comment", comment == null ? "" : comment));
    }

    public JSONObject getAgentRegistry(DeviceIdentity id) throws Exception {
        return request(
                "?android_bridge=agent_registry&device_id=" +
                        URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name()),
                "GET", id, null);
    }


    public JSONObject beginSessionPairing(DeviceIdentity id, String label, JSONArray agentSuite)
            throws Exception {
        return beginSessionPairing(id, label, null, null, agentSuite);
    }

    public JSONObject beginSessionPairing(
            DeviceIdentity id,
            String label,
            String chatKey,
            String chatTitle,
            JSONArray agentSuite) throws Exception {
        return beginSessionPairing(id, label, chatKey, chatTitle, agentSuite, false);
    }

    public JSONObject beginSessionPairing(
            DeviceIdentity id,
            String label,
            String chatKey,
            String chatTitle,
            JSONArray agentSuite,
            boolean isPrimary) throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("label", label == null ? "GPT Session" : label)
                .put("agentSuite", agentSuite == null ? new JSONArray() : agentSuite)
                .put("isPrimary", isPrimary);
        if (chatKey != null && !chatKey.isEmpty()) body.put("chatKey", chatKey);
        if (chatTitle != null && !chatTitle.isEmpty()) body.put("chatTitle", chatTitle);
        return request("?android_bridge=session_pair_begin", "POST", id, body);
    }

    public JSONObject sessionPairStatus(DeviceIdentity id, String requestId) throws Exception {
        String query = "?android_bridge=session_pair_status&device_id=" +
                URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name()) +
                "&request_id=" +
                URLEncoder.encode(requestId == null ? "" : requestId, StandardCharsets.UTF_8.name());
        return request(query, "GET", id, null);
    }

    public JSONObject listSessions(DeviceIdentity id) throws Exception {
        return request(
                "?android_bridge=session_list&device_id=" +
                        URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name()),
                "GET", id, null);
    }

    public JSONObject syncDiscoveredChats(DeviceIdentity id, JSONArray chats) throws Exception {
        return request("?android_bridge=discovery_sync", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("chats", chats == null ? new JSONArray() : chats));
    }

    public JSONObject listDiscoveredChats(DeviceIdentity id) throws Exception {
        return request(
                "?android_bridge=discovery_list&device_id=" +
                        URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name()),
                "GET", id, null);
    }

    public JSONObject registerDiscoveredChatSession(
            DeviceIdentity id, String chatKey, String title, JSONArray roles) throws Exception {
        return request("?android_bridge=session_register_discovered", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("chatKey", chatKey == null ? "" : chatKey)
                        .put("title", title == null ? "GPT Session" : title)
                        .put("roles", roles == null ? new JSONArray() : roles));
    }


    public JSONObject updateSession(DeviceIdentity id, String sessionId, String label,
                                    JSONArray roles, String status) throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("sessionId", sessionId);
        if (label != null) body.put("label", label);
        if (roles != null) body.put("roles", roles);
        if (status != null) body.put("status", status);
        return request("?android_bridge=session_update", "POST", id, body);
    }

    public JSONObject requestAgentRun(DeviceIdentity id, JSONArray roles) throws Exception {
        return requestAgentRun(id, null, roles);
    }

    public JSONObject requestAgentRun(DeviceIdentity id, String sessionId, JSONArray roles)
            throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("roles", roles == null ? new JSONArray() : roles);
        if (sessionId != null && !sessionId.isEmpty()) body.put("sessionId", sessionId);
        return request("?android_bridge=agent_run_request", "POST", id, body);
    }

    public JSONObject poll(DeviceIdentity id) throws Exception {
        JSONObject raw = request(
                "?android_bridge=poll&device_id=" +
                        URLEncoder.encode(id.deviceId, StandardCharsets.UTF_8.name()),
                "GET", id, null);

        JSONObject normalized = new JSONObject().put("ok", true);
        JSONObject command = raw.optJSONObject("command");
        if (command == null) {
            normalized.put("command", JSONObject.NULL);
            return normalized;
        }

        JSONObject sourcePayload = command.optJSONObject("payload");
        JSONObject payload = new JSONObject();
        if (sourcePayload != null) {
            Iterator<String> keys = sourcePayload.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                payload.put(key, sourcePayload.opt(key));
            }
        }
        payload.put("op", command.optString("action", ""));

        normalized.put("command", new JSONObject()
                .put("id", command.optString("id", ""))
                .put("payload", payload));
        return normalized;
    }

    public JSONObject report(DeviceIdentity id, String commandId, boolean ok,
                             JSONObject result, String error) throws Exception {
        JSONObject clean = result == null ? new JSONObject() :
                new JSONObject(result.toString());

        String imageBase64 = clean.optString("base64", null);
        String imageMime = clean.optString("mime", null);
        clean.remove("base64");
        clean.remove("mime");

        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("commandId", commandId)
                .put("ok", ok)
                .put("result", clean);

        if (imageBase64 != null && !imageBase64.isEmpty()) {
            body.put("imageBase64", imageBase64);
            body.put("imageMime", imageMime == null || imageMime.isEmpty()
                    ? "image/jpeg" : imageMime);
        }
        if (error != null) body.put("error", error);

        return request("?android_bridge=result", "POST", id, body);
    }


    public JSONObject uploadVisualScanFrame(
            DeviceIdentity id,
            String sessionId,
            String nonce,
            int index,
            String hash,
            int width,
            int height,
            String imageMime,
            String imageBase64) throws Exception {
        JSONObject body = new JSONObject()
                .put("deviceId", id.deviceId)
                .put("sessionId", sessionId == null ? "" : sessionId)
                .put("nonce", nonce == null ? "" : nonce)
                .put("index", index)
                .put("hash", hash == null ? "" : hash)
                .put("width", width)
                .put("height", height)
                .put("imageMime", imageMime == null || imageMime.isEmpty() ? "image/jpeg" : imageMime)
                .put("imageBase64", imageBase64 == null ? "" : imageBase64);
        return request("?android_bridge=visual_scan_frame", "POST", id, body);
    }

    public JSONObject completeVisualScanFrames(
            DeviceIdentity id,
            String sessionId,
            String nonce,
            int frameCount) throws Exception {
        return request("?android_bridge=visual_scan_complete", "POST", id,
                new JSONObject()
                        .put("deviceId", id.deviceId)
                        .put("sessionId", sessionId == null ? "" : sessionId)
                        .put("nonce", nonce == null ? "" : nonce)
                        .put("frameCount", frameCount));
    }

    public static JSONObject deviceInfo() {
        JSONObject o = new JSONObject();
        try {
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("model", Build.MODEL);
            o.put("device", Build.DEVICE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("release", Build.VERSION.RELEASE);
        } catch (Exception ignored) {
        }
        return o;
    }
}
