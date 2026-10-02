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
    private static final String KEY_LAST_CONTACT = "last_contact_ms";
    private static final String KEY_PENDING_PAIR_REQUEST = "pending_pair_request";
    private static final String KEY_PENDING_PAIR_CODE = "pending_pair_code";
    private static final String KEY_PENDING_PAIR_EXPIRES = "pending_pair_expires";

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

    public static void markContact(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CONTACT, System.currentTimeMillis()).apply();
    }

    public static long lastContact(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_CONTACT, 0L);
    }

    public static void clearContact(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_LAST_CONTACT).apply();
    }

    public static void savePendingPairing(Context context, String requestId, String code, long expiresAt) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PAIR_REQUEST, requestId)
                .putString(KEY_PENDING_PAIR_CODE, code)
                .putLong(KEY_PENDING_PAIR_EXPIRES, expiresAt)
                .apply();
    }

    public static String pendingPairRequest(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_PAIR_REQUEST, null);
    }

    public static String pendingPairCode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_PAIR_CODE, null);
    }

    public static long pendingPairExpires(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_PENDING_PAIR_EXPIRES, 0L);
    }

    public static void clearPendingPairing(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_PAIR_REQUEST)
                .remove(KEY_PENDING_PAIR_CODE)
                .remove(KEY_PENDING_PAIR_EXPIRES)
                .apply();
    }

    public static void markDisconnected(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PAIRED, false)
                .remove(KEY_LAST_CONTACT)
                .remove(KEY_PENDING_PAIR_REQUEST)
                .remove(KEY_PENDING_PAIR_CODE)
                .remove(KEY_PENDING_PAIR_EXPIRES)
                .apply();
    }
}
