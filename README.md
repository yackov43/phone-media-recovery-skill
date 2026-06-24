<div align="center">

<img src="assets/recovery-cover.png" alt="Phone Media Recovery" width="840">

# 📱 Phone Media Recovery

### Safely recover missing, partial, or inaccessible phone media after a device migration — with exact path matching, manifest comparison, verified transfers, and checksum validation.

<br>

![Type](https://img.shields.io/badge/type-AI%20Agent%20Skill-0F766E?style=for-the-badge)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iPhone-0F766E?style=for-the-badge)
![Transfer](https://img.shields.io/badge/transfer-ADB%20%2B%20tar-0F766E?style=for-the-badge)
![Verify](https://img.shields.io/badge/verify-MD5%20%2F%20SHA--256-0F766E?style=for-the-badge)
![Safety](https://img.shields.io/badge/data-additive%20%C2%B7%20never%20deletes-16A34A?style=for-the-badge)

</div>

---

## 📖 What is this?

When you move a phone — **Samsung Smart Switch, Move to iOS, a cable transfer, a backup restore, or a manual copy** — the chat and gallery *database* usually moves, but the actual **media files often do not fully copy across**.

The result is painful and confusing: WhatsApp shows a document, a photo appears in a chat, a video sits in the conversation — but when you tap it you get **“File not found”** or a stuck **download arrow**.

**Phone Media Recovery** is a disciplined methodology *plus* tooling that:

1. **Finds exactly which files are missing** — by comparing the two phones file-by-file.
2. **Restores only those files** — to their exact original path, byte-for-byte.
3. **Proves it worked** — with size + checksum verification and original-timestamp preservation.

It is built around one strict promise: **no guessing.** Files are matched by *exact path and filename* — never by “looks similar”, display name, thumbnail, or approximate size. This is what makes the recovery trustworthy.

> This skill was forged from a real-world recovery: ~12,000 WhatsApp media files that Smart Switch silently dropped during a phone upgrade, restored and verified with zero data loss.

---

## 📑 Table of Contents

- [Who it's for](#-who-its-for)
- [The recovery standard](#-the-recovery-standard)
- [How it works](#-how-it-works)
- [Recovery case classification](#-recovery-case-classification)
- [Verification &amp; audit](#-verification--audit)
- [What it will *not* copy](#-what-it-will-not-copy)
- [Installation](#-installation)
- [Quick start](#-quick-start)
- [Scripts](#-scripts)
- [Repository structure](#-repository-structure)
- [References](#-references)
- [Disclaimer](#-disclaimer)

---

## 👥 Who it's for

| You are… | This helps you… |
|----------|-----------------|
| **Someone who switched phones** | Get back WhatsApp documents/photos/videos that show “file not found” after the transfer. |
| **A technician / repair shop** | Run an auditable, conservative recovery between two Android phones. |
| **A power user / forensic context** | Produce checksum-verified, timestamp-accurate restoration records. |

**Media categories covered:** documents · images · videos · audio · voice notes · video notes · stickers · GIFs · DCIM/gallery · downloads — and anything stored under an app's media root.

---

## 🧭 The recovery standard

The core philosophy is **conservative and verifiable**. Seven rules drive every decision:

1. **Preserve user data first.** Never delete or overwrite a destination file unless it is the exact same intended path *and* the source is a stronger, verified copy — or the user explicitly approves.
2. **Distinguish the three states:** `missing` (source has it, destination doesn't) vs. `partial/corrupt` (both have it, sizes differ) vs. `not-downloaded/cloud-only` (the source phone lacks it too → not recoverable from files).
3. **Compare storage paths, not display names.** Messaging apps show friendly names but store encoded ones (`DOC-20260601-WA0038`, `IMG-…`, sometimes *no extension*).
4. **Verify every restored file** with byte size **and** checksum (MD5 for speed, SHA-256 for audit).
5. **Preserve timestamps** — most copy tools reset modified-time; this restores the original.
6. **Avoid Windows path-normalization hazards** — names ending in a dot or with unusual Unicode can't round-trip through a Windows folder, so transfers use **archives/streams** (`tar`), not loose files.
7. **Document the non-recoverable cases** clearly instead of faking a result.

---

## 🔁 How it works

<div align="center">
<img src="assets/recovery-workflow.svg" alt="Recovery workflow" width="820">
</div>

The skill follows a repeatable, seven-stage pipeline:

| # | Stage | What happens |
|---|-------|--------------|
| **1** | **Establish sources & scope** | Inventory every source: old phone, new phone, computer exports, vendor staging, backups, SD cards, app cloud surfaces. |
| **2** | **Choose platform access** | Pick the safest path (ADB / MTP / backup parser / cloud). See [`platform-playbook.md`](references/platform-playbook.md). |
| **3** | **Build manifests** | For each side, list every file as `epoch\|size\|path`. On Android use `find … -exec stat -c '%Y\|%s\|%n' {} +` (Toybox has no `-printf`). |
| **4** | **Compare manifests** | Deterministic diff → `missing.txt`, `different.txt`, `touch.txt`, `summary.json`. |
| **5** | **Restore (filename-safe)** | Transfer via on-device `tar` so exact names survive — not loose files through Windows. |
| **6** | **Verify** | Re-scan both sides independently; compare size + checksum per path; restore timestamps; spot-check in the app. |
| **7** | **Investigate “still missing”** | Widen scope (stickers, statuses, profile photos, no-extension files), and re-check *partial* files, not only missing ones. |

---

## 🗂️ Recovery case classification

Not every “missing” file is the same. The skill sorts each item into a case and acts accordingly:

<div align="center">
<img src="assets/case-classification.svg" alt="Recovery case classification" width="820">
</div>

| Case | Situation | Action |
|------|-----------|--------|
| **A — Missing** | Source has the exact path; destination doesn't. | Restore exact path, preserve timestamp, verify. |
| **B — Partial / corrupt** | Same path on both, but size/checksum differ. | Confirm intent → replace via filename-safe transfer → verify. |
| **C — Download button** | App expects a server/cloud download; no local file anywhere. | Use the app's own download/cloud restore — **not** recoverable from files. |
| **D — Name mismatch** | App shows a friendly name; disk uses an encoded name. | Match by manifest metadata + app-relative path, never by visible name. |
| **E — Out-of-scope categories** | Stickers, voice notes, statuses, profile photos, no-extension files. | Rescan each category independently and compare. |
| **F — Cache / DB / backup diffs** | Temp, thumbnails, databases, trash. | **Do not copy by default** — investigation only. |

---

## ✅ Verification &amp; audit

Verification is **independent of the copy command** — a transfer is only “done” when a fresh, separate scan proves it.

<div align="center">
<img src="assets/verification-loop.svg" alt="Verification loop" width="760">
</div>

- **Minimum check per file:** relative path · byte size · checksum · timestamp.
- **Checksum choice:** `MD5` for fast same-session comparison · `SHA-256` for audit / legal / long-term records.
- **Timestamp preservation:** record epoch in the manifest, then re-apply after restore:
  ```sh
  touch -d "@1712345678" "$DEST/relative/path/file.ext"
  ```
- **Red flags that stop the process:** a restored file is `0` bytes while the source isn't · a filename changed (trailing dot/space, odd Unicode) · full-tree `find` totals disagree with per-folder scans · the app still shows the file missing after a verified restore + reboot (points to DB/cloud/indexing, not the file).

See [`references/verification.md`](references/verification.md) for the full audit layout.

---

## 🚫 What it will *not* copy

To protect the destination phone, these are **never** copied blindly:

- App **databases** from one active install into another.
- **Encrypted backups** without known key/version compatibility.
- **Cache** folders — temporary downloads, thumbnails, `.tmp`, `.enc.tmp`, work queues.
- **Trash** folders (unless you explicitly ask to recover deleted media).
- **Cloud placeholders** that are not complete files.

---

## ⚙️ Installation

### Prerequisites

| Tool | Needed for |
|------|-----------|
| **[ADB](https://developer.android.com/tools/adb)** (Android Platform Tools) | Android device access & transfer |
| **Python 3.8+** | `scripts/compare_manifests.py` |
| **PowerShell 7+** | `scripts/android_tar_restore.ps1` |
| **ffmpeg / ffprobe** *(optional)* | Inspecting media (duration, frames) during identification |

### Option A — As a Claude Code skill

```bash
# user-level (available in every project)
git clone https://github.com/yackov43/phone-media-recovery-skill.git \
  ~/.claude/skills/phone-media-recovery

# …or project-level
git clone https://github.com/yackov43/phone-media-recovery-skill.git \
  .claude/skills/phone-media-recovery
```

Then invoke it naturally, e.g. *“Use the phone-media-recovery skill to find what didn't transfer between my old and new phone.”*

### Option B — As a Codex / OpenAI agent skill

The skill ships an agent manifest at [`agents/openai.yaml`](agents/openai.yaml). Place the folder in your agent's skills directory and register it; the default invocation is:

```text
Use $phone-media-recovery to diagnose and safely restore missing phone media between devices.
```

### Option C — Standalone tooling

```bash
git clone https://github.com/yackov43/phone-media-recovery-skill.git
cd phone-media-recovery-skill
python scripts/compare_manifests.py --help
```

---

## 🚀 Quick start

A typical Android-to-Android recovery:

```bash
# 1) Confirm both phones are visible
adb devices -l

# 2) Build a manifest per media category on EACH phone
#    (run for old and new; repeat per top-level folder)
adb -s <SERIAL> shell "find '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents' \
  -type f -exec stat -c '%Y|%s|%n' {} +" > old_documents.txt

# 3) Compare → produces missing.txt / different.txt / touch.txt / summary.json
python scripts/compare_manifests.py \
  --old old_documents.txt --new new_documents.txt \
  --old-root "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/" \
  --new-root "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/" \
  --out-dir recovery_plan
```

```powershell
# 4) Restore exactly the missing files, filename-safe, with verification
powershell -ExecutionPolicy Bypass -File scripts/android_tar_restore.ps1 `
  -Adb "C:\path\to\adb.exe" `
  -OldSerial "<old-serial>" -NewSerial "<new-serial>" `
  -Base "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents" `
  -List recovery_plan\missing.txt `
  -TouchList recovery_plan\touch.txt `
  -Key "documents"
```

The restore is **additive** — it only adds files the destination is missing. It never deletes or overwrites your existing data.

---

## 📜 Scripts

| Script | Purpose |
|--------|---------|
| [`scripts/compare_manifests.py`](scripts/compare_manifests.py) | Deterministic manifest diff → `missing.txt`, `different.txt`, `touch.txt`, `summary.json`. |
| [`scripts/android_tar_restore.ps1`](scripts/android_tar_restore.ps1) | Tar-based verified restore: stage list → archive on source → pull/push one archive → extract on destination → restore timestamps → emit old/new verification manifests; **fails** if size/checksum differ. |

---

## 📁 Repository structure

```text
phone-media-recovery-skill/
├── SKILL.md                       # The skill definition (rules + workflow)
├── README.md                      # You are here
├── agents/
│   └── openai.yaml                # Codex / OpenAI agent manifest
├── references/
│   ├── platform-playbook.md       # Android / iPhone / mixed access paths
│   ├── recovery-cases.md          # Case A–F classification
│   └── verification.md            # Checksum, timestamp & audit practices
├── scripts/
│   ├── compare_manifests.py       # Manifest comparison
│   └── android_tar_restore.ps1    # Verified tar restore
└── assets/
    ├── recovery-cover.png         # Cover illustration
    ├── recovery-workflow.svg      # Workflow diagram
    ├── case-classification.svg    # Case diagram
    ├── verification-loop.svg      # Verification diagram
    └── icon-small.svg             # Skill icon
```

---

## 📚 References

- **[`SKILL.md`](SKILL.md)** — the full skill definition (core rules + 7-stage workflow).
- **[`references/platform-playbook.md`](references/platform-playbook.md)** — choosing an access path for Android, iPhone, and mixed migrations.
- **[`references/recovery-cases.md`](references/recovery-cases.md)** — classifying missing, partial, cached, cloud-only, and risky cases.
- **[`references/verification.md`](references/verification.md)** — checksum, timestamp, and audit practices.

---

## ⚠️ Disclaimer

This skill works on **user-accessible storage, official backups, app exports, and verified copies** — it assumes **no root/jailbreak** unless you explicitly choose otherwise. It is conservative by design and additive by default, but you are responsible for backing up important data before any device operation. Files that were *never downloaded* to any available source (cloud-only) cannot be reconstructed from local files.

<div align="center">
<br>
<sub>Built for trustworthy, verifiable phone-media recovery · no guessing, only exact matches.</sub>
</div>
