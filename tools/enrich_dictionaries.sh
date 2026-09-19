#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/app/src/main/assets/licenses"

# code:npm-package
PACKS=(
  "pt_PT:dictionary-pt-pt"
  "en_GB:dictionary-en-gb"
  "it_IT:dictionary-it"
  "es_ES:dictionary-es"
  "fr_FR:dictionary-fr"
  "de_DE:dictionary-de"
)

for item in "${PACKS[@]}"; do
  code="${item%%:*}"
  pkg="${item##*:}"
  echo "Enriching $code from $pkg"
  work="$TMP/$code"; mkdir -p "$work"
  if tgz=$(cd "$work" && npm pack "$pkg" --silent 2>/dev/null | tail -n1) && [ -n "$tgz" ]; then
    tar -xzf "$work/$tgz" -C "$work"
    dic="$work/package/index.dic"
    if [ -f "$dic" ]; then
      python3 "$ROOT/tools/merge_hunspell.py" "$dic" "$ROOT/app/src/main/assets/dictionaries/$code.tsv"
      for lic in license LICENSE license.md LICENSE.md; do
        if [ -f "$work/package/$lic" ]; then
          cp "$work/package/$lic" "$ROOT/app/src/main/assets/licenses/${code}_${pkg}.txt"
          break
        fi
      done
    else
      echo "WARN: $pkg has no index.dic; using bundled starter dictionary"
    fi
  else
    echo "WARN: could not download $pkg; using bundled starter dictionary"
  fi
done
