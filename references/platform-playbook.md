# Platform Playbook

## Android

Preferred access paths:

1. ADB with USB debugging enabled.
2. MTP export for simple gallery/DCIM cases.
3. Vendor desktop transfer tools when ADB is unavailable.
4. SD card or external storage if the app stored media there.

Recommended Android media workflow:

1. Confirm devices with `adb devices -l`.
2. Locate the app-visible media root. Common examples:
   - `/storage/emulated/0/DCIM`
   - `/storage/emulated/0/Pictures`
   - `/storage/emulated/0/Movies`
   - `/storage/emulated/0/Download`
   - `/storage/emulated/0/Android/media/<package>/<app-root>`
3. Build manifests per top-level folder.
4. Compare by relative path.
5. Use on-device `tar` for exact filename preservation.
6. Verify with size and checksum after extraction.

Notes:

- `/sdcard` is commonly a symlink; `/storage/emulated/0` is usually safer for `find`.
- Android `toybox` tools vary by version. Test `stat`, `md5sum`, `sha256sum`, and `tar` before relying on them.
- Some app folders are inaccessible without root. If the needed data is inside private app storage, use app export, cloud restore, or a backup route instead of forcing filesystem access.

## iPhone

Direct filesystem access is limited. Use these routes:

1. Finder/iTunes encrypted backup, then inspect media inside the backup with a trusted parser.
2. iCloud Photos or iCloud Drive download for gallery/files cases.
3. DCIM import for camera media.
4. App-native export, chat export, or cloud restore when the app supports it.
5. Device-to-device migration logs or staging files only when available and trusted.

Important constraints:

- App media inside iOS app containers is usually not accessible directly from USB.
- Encrypted backups preserve more app data than unencrypted backups.
- A backup may contain files that are no longer visible in the current app UI, but it may also omit cloud-only placeholders.
- Do not modify an iPhone backup in place unless the user explicitly wants a backup surgery workflow and accepts the risk.

## Mixed Phone Migrations

When moving between Android and iPhone:

1. Do not assume filesystem paths will match exactly across platforms.
2. Compare within equivalent app export roots, not full device roots.
3. Prefer app-native migration records if available.
4. Separate "file recovery" from "database/chat index repair". Restoring a file does not always make a chat database reference it.

## No-Root Principle

This skill assumes no root/jailbreak unless the user explicitly says otherwise. Use user-accessible storage, official backups, app exports, and verified copies first.
