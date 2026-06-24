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

## Out Of Scope: iPhone / Cross-Platform

This skill targets **Android-to-Android** recovery only. iPhone/iOS access (Finder/iTunes/iCloud backups, app containers) and cross-platform migrations are out of scope.

## Important Distinction

Separate "file recovery" from "database/chat index repair". Restoring a file does not always make a chat database reference it immediately; the app may need a restart, reboot, or media re-index.

## No-Root Principle

This skill assumes no root unless the user explicitly says otherwise. Use user-accessible storage, official backups, app exports, and verified copies first.
