#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""CI gate: verify the in-app qBittorrent preferences editor implements the
COMPLETE set of qBittorrent-Enhanced WebUI Options keys.

The reference list `config/qb_enhanced_prefs_required.txt` holds every
preference key that the bundled engine (pinned ref in
.github/workflows/android.yml, QBT_REF) both serializes in
AppController::preferencesAction() (GET /api/v2/app/preferences) and accepts
in AppController::setPreferencesAction() (POST setPreferences) — i.e. every
key that is round-trippable over the WebUI Options API.

This script extracts the keys written by the app's preferences editor
(ui/qbsettings/*Fragment.kt collectValues: out.put("key", ...) and
out.add("key", ...)) and fails the build when any required key is missing.

Regenerating the reference list when QBT_REF changes:
    git clone https://github.com/c0re100/qBittorrent-Enhanced-Edition
    python3 - <<'EOF'
    import re
    src = open('qBittorrent-Enhanced-Edition/src/webui/api/appcontroller.cpp').read()
    g = src.index('void AppController::preferencesAction()')
    s = src.index('void AppController::setPreferencesAction()')
    get = set(re.findall(r'data\[u"([a-z_0-9]+)"_s\]', src[g:s]))
    put = set(re.findall(r'hasKey\(u"([a-z_0-9]+)"_s\)', src[s:]))
    open('config/qb_enhanced_prefs_required.txt', 'w').write(
        '\\n'.join(sorted(get & put)) + '\\n')
    EOF
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FRAGMENTS = ROOT / "app/src/main/java/io/github/xixka/qbittorrent/ui/qbsettings"
REQUIRED_FILE = ROOT / "config/qb_enhanced_prefs_required.txt"


def keys_written() -> set:
    """Preference keys the editor writes (out.put / out.add, incl. multiline)."""
    keys = set()
    for frag in sorted(FRAGMENTS.glob("*.kt")):
        src = frag.read_text(encoding="utf-8")
        for m in re.finditer(r'out\.(?:put|add)\(\s*"([a-z_0-9]+)"', src):
            keys.add(m.group(1))
    return keys


def required_keys() -> set:
    return {line.strip() for line in REQUIRED_FILE.read_text().splitlines() if line.strip()}


def main() -> int:
    written = keys_written()
    required = required_keys()
    if not required:
        print(f"FATAL: empty/missing {REQUIRED_FILE}")
        return 2

    missing = sorted(required - written)
    print(f"preference keys written by the app: {len(written)}")
    print(f"required engine keys ({REQUIRED_FILE.name}): {len(required)}")

    if missing:
        print("\nMISSING preference keys (qBittorrent Enhanced config not "
              "implemented by the in-app editor):")
        for k in missing:
            print(f"  - {k}")
        return 1

    print("OK: the settings editor covers the complete qBittorrent Enhanced "
          "WebUI Options key set.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
