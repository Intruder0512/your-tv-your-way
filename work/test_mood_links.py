"""Open every mood-card title on the TV the way the launcher does, and screenshot what came up.

Usage (repo root):  TV_HOST=<tv ip> tools/venv/Scripts/python.exe -I work/test_mood_links.py
Writes shots/links/<n>.png and prints the activity in front after each launch.
"""
import json
import os
import subprocess
import sys

PY = sys.executable
TV = [PY, "-I", "tools/tv.py"]
titles = json.load(open("launcher/app/src/main/assets/moods/moods.json", encoding="utf-8"))["titles"]
only = sys.argv[1:]
os.makedirs("shots/links", exist_ok=True)
env = dict(os.environ, MSYS_NO_PATHCONV="1")


def tv(*args):
    return subprocess.run(TV + list(args), capture_output=True, text=True, env=env, timeout=200).stdout.strip()


for i, t in enumerate(titles):
    if only and str(i) not in only:
        continue
    # -f 0x10008000 = NEW_TASK | CLEAR_TASK, as MoodDeck.open: a running app must not keep its last page.
    front = tv("am start -f 0x10008000 -a android.intent.action.VIEW -d '%s' -p %s >/dev/null 2>&1; sleep 9; "
               "dumpsys activity activities | grep mResumedActivity | head -1" % (t["link"], t["pkg"]))
    tv("--screenshot", "shots/links/%02d.png" % i)
    tv("input keyevent HOME; sleep 2")
    print("%02d %-30s %s" % (i, t["title"][:30], front.split(" u0 ")[-1].split(" ")[0] if front else "?"), flush=True)
