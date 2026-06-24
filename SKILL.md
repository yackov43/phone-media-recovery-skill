---
name: phone-media-recovery
description: Recover missing, partial, or inaccessible Android phone media when moving data between an old and a new Android phone. Use for Android device-to-device recovery projects involving WhatsApp or similar app media, DCIM/gallery files, documents, audio, videos, stickers, voice notes, exact path matching, manifest comparison, verified copy, checksum validation, timestamp preservation, ADB/tar workflows, local phone backups, and deciding what cannot be recovered from files. Android only (no iPhone/iOS).
---

# Phone Media Recovery

## Purpose

Use this skill to investigate and safely restore Android phone media after a migration, backup restore, cable transfer, Smart Switch/vendor transfer, or manual copy left messages or gallery entries pointing to missing files. Scope: Android-to-Android only (iPhone/iOS is out of scope).

The recovery standard is conservative: restore only files that can be matched by exact path and filename, or replace files that exist in the destination but are objectively incomplete or different by size/checksum. Do not guess by visual similarity, display name, thumbnail, date alone, or approximate size.

## Core Rules

1. Preserve user data first. Never delete or overwrite destination files unless the source file has the exact same intended path and a stronger verified signature, or the user explicitly approves a replacement plan.
2. Treat "missing", "partial", and "not downloaded" as different cases:
   - Missing: source has an exact file path; destination does not.
   - Partial/corrupt: both sides have the same path, but size/checksum differs.
   - Not downloaded/cloud-only: the source phone also lacks the file; local file recovery cannot restore it.
3. Compare storage paths, not app display names. Messaging apps often show original names while storing encoded names.
4. Verify every restored file with size plus checksum. Use MD5 for speed when comparing local device files; use SHA-256 when producing audit records or legal/forensic reports.
5. Preserve timestamps when the platform allows it. Pull/push tools often reset modified time.
6. Avoid Windows path normalization hazards. Some phone files may contain names that Windows cannot round-trip safely, such as names ending in a dot or using unusual Unicode. Prefer archive or stream transfer when exact filenames matter.
7. Document non-recoverable cases clearly. A file absent from every accessible source cannot be recreated without server/cloud access, a backup containing it, or another device copy.

## Workflow

### 1. Establish Sources And Scope

Identify every candidate source before copying:

- Old phone current storage.
- New phone current storage.
- Local computer exports.
- Vendor-transfer staging folders.
- iTunes/Finder/iCloud or other phone backups.
- App-specific media folders.
- SD cards or external storage.
- Cloud download surfaces inside the app.

Record the app, media categories, source type, destination type, and whether the user allows replacements for partial files. Keep device model names, serial numbers, phone numbers, and account identifiers out of reusable notes unless the user explicitly asks for a case report.

### 2. Choose Platform Access

Use `references/platform-playbook.md` when selecting an access path.

Common Android access paths:

- ADB with USB debugging (preferred).
- MTP export for simple gallery/DCIM cases.
- Vendor desktop transfer tools when ADB is unavailable.
- App-visible external storage and SD cards.

### 3. Build Manifests

For each source and destination, build a manifest with:

```text
epoch|size|path
```

Use absolute paths while scanning, then compare by a stable relative root such as:

```text
<app-media-root>/<media-category>/<subpath>
```

For Android shell scans, avoid assuming GNU `find` features. Toybox `find` often lacks `-printf`; prefer:

```sh
find "$ROOT" -type f -exec stat -c '%Y|%s|%n' {} +
```

If a full-tree scan cuts off or times out, scan per top-level category and merge results.

### 4. Compare Manifests

Use `scripts/compare_manifests.py` when possible:

```bash
python scripts/compare_manifests.py \
  --old old_manifest.txt \
  --new new_manifest.txt \
  --old-root "/source/root/" \
  --new-root "/destination/root/" \
  --out-dir recovery_plan
```

The script writes:

- `missing.txt`: relative paths present in source and absent in destination.
- `different.txt`: relative paths present on both sides with different sizes.
- `touch.txt`: `epoch|relative_path` for timestamp restoration.
- `summary.json`: counts and bytes by case.

Review `different.txt` before replacing. Size differences can represent corruption, placeholders, app-transcoded files, or a newer legitimate destination file. Only replace when the source path is exact and the recovery goal is to restore the old phone's copy.

### 5. Restore With Filename-Safe Transport

For Android-to-Android recovery, prefer `tar` on device instead of expanding files into Windows directories. Use `scripts/android_tar_restore.ps1` as the starting point:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/android_tar_restore.ps1 `
  -Adb "C:\path\to\adb.exe" `
  -OldSerial "<old-device-serial>" `
  -NewSerial "<new-device-serial>" `
  -Base "/storage/emulated/0/Android/media/<package>/<app-root>" `
  -List recovery_plan\missing.txt `
  -TouchList recovery_plan\touch.txt `
  -Key "media-recovery"
```

This script:

- Pushes the relative-path list to both devices.
- Creates a tar archive on the source device from the app-relative root.
- Pulls and pushes one archive rather than thousands of fragile filenames.
- Extracts on the destination device.
- Restores timestamps.
- Produces old/new verification manifests for the restored list.
- Fails if size/checksum verification differs.

If the target platform cannot run `tar`, use the closest archive-preserving or stream-preserving mechanism available. Avoid any path through a host filesystem that may normalize filenames.

### 6. Verify

Verification must be independent of the copy command:

1. Re-scan the restored relative paths on the source.
2. Re-scan the same relative paths on the destination.
3. Compare checksum and byte size per relative path.
4. Keep the verification output in the case folder.
5. Spot-check a few items in the app UI, especially old files, names without extensions, files ending in dots, stickers, voice notes, and documents.

For app-visible media recovery, a successful filesystem verification means the file landed correctly; it does not guarantee the app will index it immediately. If needed, restart the app, reboot the phone, wait for media indexing, or use the app's built-in download/open behavior.

### 7. Investigate "Still Missing" Reports

If the user says files are still missing after a verified restore:

1. Check for categories that were not in the first scope, such as stickers, profile photos, statuses, wallpapers, AI/editor cache media, or backup-excluded folders.
2. Compare "same path but different size" files, not only missing paths.
3. Rebuild fresh manifests after the restore. Do not rely on pre-restore manifests for final conclusions.
4. Scan per folder if full-tree results disagree with targeted verification.
5. Separate app cache or database differences from user-facing media. Do not copy databases or app working folders unless the user has a specific backup-restore plan and understands the risk.

## What Not To Copy By Default

Do not copy these blindly:

- App databases from one active install into another.
- Encrypted backups without knowing key/version compatibility.
- Cache folders such as temporary downloads, thumbnails, `.tmp`, `.enc.tmp`, `.chk.tmp`, or app work queues.
- Trash folders unless the user explicitly asks to recover deleted media.
- Cloud placeholders that are not complete files.

These may be useful for investigation, but copying them can corrupt state, confuse the app, or replace newer data.

## References

- `references/platform-playbook.md`: choose an access path for Android, iPhone, and mixed migrations.
- `references/recovery-cases.md`: classify missing, partial, cached, cloud-only, and risky cases.
- `references/verification.md`: checksum, timestamp, and audit practices.
- `scripts/compare_manifests.py`: deterministic manifest comparison.
- `scripts/android_tar_restore.ps1`: Android tar-based verified restore workflow.
- `assets/*.svg`: visual explanation diagrams for documentation or presentations.
