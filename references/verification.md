# Verification And Audit

## Minimum Verification

For every restored file, verify:

- Relative path.
- Byte size.
- Checksum.
- Timestamp when required.

Store verification files beside the recovery plan.

## Suggested File Layout

```text
case/
  manifests/
    old_<category>.txt
    new_<category>.txt
  plan/
    missing.txt
    different.txt
    touch.txt
    summary.json
  verify/
    restored_old.txt
    restored_new.txt
  logs/
    commands.txt
    notes.md
```

## Checksum Choice

- Use MD5 for fast same-session source/destination comparison when collision resistance is not the issue.
- Use SHA-256 for audit reports, legal contexts, or long-term records.

## Timestamp Preservation

Many copy commands do not preserve modified time. Preserve timestamps by recording epoch in the manifest and applying it after restore.

Android example:

```sh
touch -d "@1712345678" "$DEST/relative/path/file.ext"
```

## Validation Red Flags

Stop and investigate when:

- A restored file verifies as size `0` but source is nonzero.
- A filename changed, especially trailing dot, trailing space, non-ASCII, or unusual punctuation.
- Full-tree `find` totals disagree with targeted folder scans.
- The app still shows missing files after filesystem verification and reboot. This may indicate database references, cloud-only items, or app indexing behavior.
