#!/usr/bin/env python3
"""Compare source and destination phone-media manifests.

Input format: epoch|size|path
Output files:
  missing.txt    relative paths present only in source
  different.txt  relative paths present on both sides with different sizes
  touch.txt      epoch|relative_path for missing + different source files
  summary.json   counts and bytes
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Entry:
    epoch: int
    size: int
    relpath: str


def normalize_path(path: str, root: str | None) -> str:
    path = path.strip().replace("\\", "/")
    if root:
        root = root.strip().replace("\\", "/")
        if root and not root.endswith("/"):
            root += "/"
        if path.startswith(root):
            path = path[len(root) :]
    return path.lstrip("/")


def load_manifest(path: Path, root: str | None) -> dict[str, Entry]:
    entries: dict[str, Entry] = {}
    with path.open("r", encoding="utf-8-sig", errors="replace") as handle:
        for line_no, line in enumerate(handle, 1):
            line = line.rstrip("\n\r")
            if not line:
                continue
            parts = line.split("|", 2)
            if len(parts) != 3:
                raise ValueError(f"{path}:{line_no}: expected epoch|size|path")
            epoch_s, size_s, raw_path = parts
            relpath = normalize_path(raw_path, root)
            entries[relpath] = Entry(int(epoch_s), int(size_s), relpath)
    return entries


def write_lines(path: Path, lines: list[str]) -> None:
    path.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--old", required=True, type=Path)
    parser.add_argument("--new", required=True, type=Path)
    parser.add_argument("--old-root")
    parser.add_argument("--new-root")
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--include-different-in-touch", action="store_true", default=True)
    args = parser.parse_args()

    old = load_manifest(args.old, args.old_root)
    new = load_manifest(args.new, args.new_root)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    missing = [old[p] for p in sorted(old) if p not in new]
    different = [old[p] for p in sorted(old) if p in new and old[p].size != new[p].size]
    new_only = [new[p] for p in sorted(new) if p not in old]

    write_lines(args.out_dir / "missing.txt", [e.relpath for e in missing])
    write_lines(
        args.out_dir / "different.txt",
        [f"{e.relpath}|old_size={e.size}|new_size={new[e.relpath].size}" for e in different],
    )
    touch_entries = missing + (different if args.include_different_in_touch else [])
    write_lines(args.out_dir / "touch.txt", [f"{e.epoch}|{e.relpath}" for e in touch_entries])

    summary = {
        "old_count": len(old),
        "new_count": len(new),
        "missing_count": len(missing),
        "missing_bytes": sum(e.size for e in missing),
        "different_count": len(different),
        "different_old_bytes": sum(e.size for e in different),
        "new_only_count": len(new_only),
        "new_only_bytes": sum(e.size for e in new_only),
    }
    (args.out_dir / "summary.json").write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
