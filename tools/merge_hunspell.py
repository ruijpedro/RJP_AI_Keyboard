#!/usr/bin/env python3
"""Merge Hunspell root words into an existing RJP TSV dictionary without losing curated frequencies."""
import sys, re
from pathlib import Path
if len(sys.argv) != 3:
    raise SystemExit("Usage: merge_hunspell.py input.dic dictionary.tsv")
src, dst = map(Path, sys.argv[1:])
existing=[]; seen=set()
if dst.exists():
    for line in dst.read_text(encoding='utf-8', errors='ignore').splitlines():
        if not line or line.startswith('#'): continue
        p=line.split('\t'); w=p[0].strip()
        if w and w.casefold() not in seen:
            seen.add(w.casefold()); existing.append((w, int(p[1]) if len(p)>1 and p[1].isdigit() else 1000))
lines=src.read_text(encoding='utf-8', errors='ignore').splitlines()
if lines and lines[0].strip().isdigit(): lines=lines[1:]
added=0
for line in lines:
    parts=line.split()
    if not parts: continue
    word=parts[0].split('/',1)[0].strip()
    if len(word)<2 or re.search(r'\d', word): continue
    k=word.casefold()
    if k in seen: continue
    seen.add(k); existing.append((word, 2500)); added+=1
dst.write_text('# word\tfrequency\n'+'\n'.join(f'{w}\t{f}' for w,f in existing)+'\n', encoding='utf-8')
print(f'{dst.name}: +{added} Hunspell roots, total {len(existing)}')
