# Recovery Cases

## Case A: Missing Destination File

Definition: the source contains `relative/path/name.ext`, and the destination does not.

Action: restore the exact relative path, preserve timestamp, verify size and checksum.

## Case B: Partial Or Corrupt Destination File

Definition: both devices contain the same relative path, but byte size or checksum differs.

Action:

1. Confirm the user wants the source version.
2. Confirm the source file opens or at least has a valid nonzero signature.
3. Replace destination through a filename-safe workflow.
4. Verify size and checksum.

Common causes:

- Interrupted migration.
- Placeholder file.
- Windows path normalization.
- App-generated thumbnail or preview in the place of the original.
- Partial cloud download.

## Case C: App Shows A Download Button

Definition: the app does not have a local file and expects server/cloud download.

Action: try the app's own download path or cloud restore. Local recovery cannot reconstruct a file absent from all sources.

## Case D: Display Name Does Not Match Disk Name

Definition: the app UI shows a friendly file name, but disk storage uses encoded or generated names.

Action: search by manifest metadata and app-relative paths, not only by visible name. Avoid guessing from similar dates or sizes unless the user explicitly allows a non-exact investigation.

## Case E: Extra Categories Outside The First Scope

Examples:

- Stickers.
- Backup-excluded stickers.
- Voice notes.
- Video notes.
- Profile photos.
- Statuses.
- Wallpapers.
- App AI/editor media.
- Files without extensions.

Action: rescan top-level app media folders and compare each category independently.

## Case F: Cache, Trash, Database, And Backup Differences

Action: do not copy by default. These folders may help explain behavior, but they are not safe user-media restoration targets without a case-specific plan.
