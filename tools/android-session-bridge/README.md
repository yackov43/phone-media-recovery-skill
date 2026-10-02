# Android Session Bridge

Private, outbound-only control bridge for the user's own Android device.

## Goal

After one-time APK installation, pairing, and explicit Accessibility enablement:

ChatGPT session -> existing Supabase project -> Android Session Bridge -> Android UI

There is no Termux, Pinggy, inbound tunnel, SSH server, or ADB-over-network in the runtime path.

## Pairing

The app generates its own random device UUID and 256-bit secret locally. The secret is never committed to GitHub and is never shown to ChatGPT. ChatGPT creates a short-lived enrollment code in Supabase; the user enters that code once in the app. The database stores only SHA-256(secret).

## Supported commands

- ping
- snapshot (Accessibility tree)
- screenshot (compressed screenshot + tree)
- tap
- swipe
- click_text
- set_text
- global: back/home/recents/notifications/quick_settings
- launch(package)
- open_chatgpt
- make_qa_image

## Security

The Android client can only poll/report through three SECURITY DEFINER RPCs. Direct anon access to the bridge tables is revoked. Each poll/report must prove possession of the per-device secret.

This is intended for private QA on a device owned and explicitly authorized by the user. Android requires the user to enable the Accessibility service manually.
