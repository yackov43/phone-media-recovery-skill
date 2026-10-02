package com.yackov.androidsessionbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.UUID;

public final class DeviceIdentity {
    private static final String PREFS = "bridge_identity";
    private static final String KEY_DEVICE = "device_id";
    private static final String KEY_SECRET = "device_secret";
    private static final String KEY_PAIRED = "paired";

    public final String deviceId;
    public final String secret;
    public final boolean paired;

    private DeviceIdentity(String deviceId, String secret, boolean paired) {
        this.deviceId = deviceId;
        this.secret = secret;
        this.paired = paired;
    }

    public static DeviceIdentity getOrCreate(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = p.getString(KEY_DEVICE, null);
        String secret = p.getString(KEY_SECRET, null);
        if (id == null || secret == null) {
            id = UUID.randomUUID().toString();
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.URL_SAFE);
            p.edit().putString(KEY_DEVICE, id).putString(KEY_SECRET, secret).apply();
        }
        return new DeviceIdentity(id, secret, p.getBoolean(KEY_PAIRED, false));
    }

    public static void markPaired(Context context, boolean paired) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PAIRED, paired).apply();
    }
}
