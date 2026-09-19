#!/usr/bin/env python3
"""Publish app dictionaries to the runtime update feed and generate a SHA-256 manifest.

Safety rule: never replace an existing feed dictionary with a smaller candidate. This
prevents a temporary upstream/npm failure from downgrading a user's dictionary.
"""
import hashlib, json, shutil
from datetime import datetime, timezone
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "app" / "src" / "main" / "assets" / "dictionaries"
licenses_src = root / "app" / "src" / "main" / "assets" / "licenses"
out = root / "dictionary-feed"
out.mkdir(parents=True, exist_ok=True)
(out / "licenses").mkdir(exist_ok=True)

codes = ["pt_PT", "en_GB", "it_IT", "es_ES", "fr_FR", "de_DE"]

def count_entries(path: Path) -> int:
    return sum(1 for line in path.read_text(encoding="utf-8", errors="ignore").splitlines() if line and not line.startswith("#"))

items = {}
for code in codes:
    source = src / f"{code}.tsv"
    target = out / source.name
    source_entries = count_entries(source)
    existing_entries = count_entries(target) if target.exists() else 0

    if existing_entries > source_entries:
        print(f"{code}: keeping existing feed ({existing_entries}) instead of smaller candidate ({source_entries})")
    else:
        shutil.copy2(source, target)

    entries = count_entries(target)
    if entries < 100:
        raise SystemExit(f"Refusing to publish {code}: only {entries} valid entries")
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    items[code] = {
        "version": digest[:16],
        "file": target.name,
        "sha256": digest,
        "entries": entries,
    }

# Keep license notices next to the runtime feed when upstream packages provide them.
if licenses_src.exists():
    for license_file in licenses_src.iterdir():
        if license_file.is_file():
            shutil.copy2(license_file, out / "licenses" / license_file.name)

manifest_path = out / "manifest.json"
old_manifest = {}
if manifest_path.exists():
    try:
        old_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception:
        old_manifest = {}
unchanged = old_manifest.get("schema") == 1 and old_manifest.get("dictionaries") == items
manifest = {
    "schema": 1,
    "generatedAt": old_manifest.get("generatedAt") if unchanged else datetime.now(timezone.utc).isoformat(),
    "dictionaries": items,
}
manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Published {len(items)} dictionaries to {out}")
for code, item in items.items():
    print(f"{code}: {item['entries']} entries, {item['version']}")
