package com.yackov.androidsessionbridge;

import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class BridgeApi {
    private static final String BASE = "https://dwwsjglbhzmxspjogjvq.supabase.co";
    private static final String API_KEY = "sb_publishable_ySwK4qDT578YBoXudnFNCQ_3xs3pkBx";
    public static final String APP_VERSION = "0.1.0";

    private JSONObject postRpc(String rpc, JSONObject body) throws Exception {
        URL url = new URL(BASE + "/rest/v1/rpc/" + rpc);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("apikey", API_KEY);
        c.setRequestProperty("Authorization", "Bearer " + API_KEY);

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) {
            os.write(payload);
        }

        int status = c.getResponseCode();
        InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + ": " + sb);
        }
        return new JSONObject(sb.toString());
    }

    public JSONObject enroll(String code, DeviceIdentity id, String label) throws Exception {
        JSONObject info = deviceInfo();
        JSONObject body = new JSONObject()
                .put("p_code", code)
                .put("p_device", id.deviceId)
                .put("p_secret", id.secret)
                .put("p_label", label)
                .put("p_app_version", APP_VERSION)
                .put("p_device_info", info);
        return postRpc("android_bridge_enroll", body);
    }

    public JSONObject poll(DeviceIdentity id) throws Exception {
        JSONObject body = new JSONObject()
                .put("p_device", id.deviceId)
                .put("p_secret", id.secret)
                .put("p_app_version", APP_VERSION)
                .put("p_device_info", deviceInfo());
        return postRpc("android_bridge_poll", body);
    }

    public JSONObject report(DeviceIdentity id, String commandId, boolean ok,
                             JSONObject result, String error) throws Exception {
        JSONObject body = new JSONObject()
                .put("p_device", id.deviceId)
                .put("p_secret", id.secret)
                .put("p_command", commandId)
                .put("p_ok", ok)
                .put("p_result", result == null ? new JSONObject() : result);
        if (error == null) body.put("p_error", JSONObject.NULL);
        else body.put("p_error", error);
        return postRpc("android_bridge_report", body);
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
