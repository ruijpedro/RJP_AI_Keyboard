#!/usr/bin/env python3
"""Convert a Hunspell .dic file to the simple RJP dictionary TSV format.
Usage: python tools/import_hunspell.py input.dic output.tsv
Affix expansion is intentionally not performed; roots are imported and can be combined
with an externally expanded word list if desired.
"""
import sys, re
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("Usage: import_hunspell.py input.dic output.tsv")
src, dst = map(Path, sys.argv[1:])
lines = src.read_text(encoding="utf-8", errors="ignore").splitlines()
if lines and lines[0].strip().isdigit():
    lines = lines[1:]
words=[]
for line in lines:
    raw=line.split()[0] if line.split() else ""
    word=raw.split("/",1)[0].strip()
    if len(word)>=2 and not re.search(r"\d", word): words.append(word)
seen=set(); out=[]
for i,w in enumerate(words):
    key=w.casefold()
    if key in seen: continue
    seen.add(key)
    out.append(f"{w}\t{max(100, 20000-i)}")
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text("# word\tfrequency\n"+"\n".join(out)+"\n", encoding="utf-8")
print(f"Wrote {len(out)} words to {dst}")
