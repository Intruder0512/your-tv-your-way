#!/usr/bin/env bash
# Undo everything: re-enable every app this session disabled and give Home back to the stock
# Google TV launcher. Run from Git Bash in the repo folder:  work/restore.sh [tv ip]
# Needs USB debugging switched on again on the TV. Wait a minute before rebooting the TV afterwards.
set -euo pipefail
cd "$(dirname "$0")/.."
# work/local.env (not committed) can hold TV_HOST=<tv ip>, so the address need not be typed each time.
if [ -f work/local.env ]; then . work/local.env; fi
export TV_HOST="${1:-${TV_HOST:?give the TV's IP address: $0 <tv ip>}}"
tools/restore.sh work/debloat.txt com.google.android.tvlauncher
# Optional: also remove the new launcher (it is a normal app, so this is a plain uninstall):
#   tools/venv/Scripts/python.exe tools/tv.py 'pm uninstall com.gathin.tvhome'
