"""Mood cards: download each title's poster and backdrop from TMDB and write the launcher's assets.

Usage (repo root):  tools/venv/Scripts/python.exe -I work/make_moods.py
Reads work/moods/titles.json, writes launcher/app/src/main/assets/moods/ (moods.json, posters/, backdrops/).
Downloads land in work/moods/raw/ and are kept, so a re-run only fetches what is missing.
"""
import json
import os
import re
import urllib.request

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

SRC = "work/moods/titles.json"
RAW = "work/moods/raw"
OUT = "launcher/app/src/main/assets/moods"
TMDB = "https://image.tmdb.org/t/p/"

MOODS = [
    {"name": "Capes and chaos", "color": "#E5484D"},
    {"name": "Comfort sitcom", "color": "#F2B84B"},
    {"name": "Edge of your seat", "color": "#8B5CF6"},
    {"name": "Desi mass", "color": "#FF8A1F"},
    {"name": "Mind games", "color": "#2EC4B6"},
    {"name": "Ghar ki kahaniyan", "color": "#E8618C"},
    {"name": "K-drama nights", "color": "#FF8FC8"},
    {"name": "Reality rumble", "color": "#22C55E"},
    {"name": "Anything at all", "color": "#B8C0CC", "all": True},
]

PACKAGES = {
    "netflix": "com.netflix.ninja",
    "prime": "com.amazon.amazonvideo.livingroom",
    "jiohotstar": "in.startv.hotstar",
    "sonyliv": "com.sonyliv",
    "xstream": "tv.airtel.xstream.tvapp",
}


def link(t):
    """The URI the TV app opens the title with (each format checked on the TV with `am start`)."""
    s = t["service"]
    if s == "netflix" and t.get("netflix_id"):
        return "https://www.netflix.com/title/%s" % t["netflix_id"]
    if s == "prime" and t.get("prime_gti"):
        return "https://app.primevideo.com/detail?gti=%s" % t["prime_gti"]
    return t.get("url", "")


def slug(title):
    return re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")


def fetch(path, size, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return dest
    req = urllib.request.Request(TMDB + size + path, headers={"User-Agent": "cleanyourtv-moods"})
    with urllib.request.urlopen(req, timeout=30) as r, open(dest, "wb") as f:
        f.write(r.read())
    return dest


def poster(src, dest):
    im = ImageOps.fit(Image.open(src).convert("RGB"), (400, 600), Image.LANCZOS)
    im.save(dest, quality=86, optimize=True)


def scene(src, dest):
    """Black and white, darkened, with a vignette: the deck's cards stay readable over it."""
    im = ImageOps.fit(Image.open(src).convert("L"), (960, 540), Image.LANCZOS)
    im = ImageEnhance.Brightness(ImageEnhance.Contrast(im).enhance(1.1)).enhance(0.3)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse((-180, -140, 960 + 180, 540 + 140), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(120))
    im = Image.composite(im, Image.new("L", im.size, 0), mask)
    im.convert("RGB").save(dest, quality=84, optimize=True)


def main():
    titles = json.load(open(SRC, encoding="utf-8"))
    for d in (RAW, OUT + "/posters", OUT + "/backdrops"):
        os.makedirs(d, exist_ok=True)
    out = []
    for t in titles:
        if t.get("service") not in PACKAGES or not t.get("poster_path"):
            print("skip", t["title"], "(no usable service or no poster)")
            continue
        name = slug(t["title"])
        raw_poster = fetch(t["poster_path"], "w500", "%s/%s-poster.jpg" % (RAW, name))
        poster(raw_poster, "%s/posters/%s.jpg" % (OUT, name))
        if t.get("backdrop_path"):
            scene(fetch(t["backdrop_path"], "w1280", "%s/%s-backdrop.jpg" % (RAW, name)), "%s/backdrops/%s.jpg" % (OUT, name))
        else:
            # Some TV serials have no text-free backdrop on TMDB: a blurred poster stands in.
            blurred = Image.open(raw_poster).convert("RGB").filter(ImageFilter.GaussianBlur(18))
            blurred.save("%s/%s-backdrop.jpg" % (RAW, name), quality=90)
            scene("%s/%s-backdrop.jpg" % (RAW, name), "%s/backdrops/%s.jpg" % (OUT, name))
        # "Drishyam 2 (Hindi, 2022)" is the research note; the card shows "Drishyam 2".
        out.append({"title": re.sub(r"\s*\(.*?\)", "", t["title"]), "pkg": PACKAGES[t["service"]], "link": link(t),
                    "moods": t["moods"], "poster": name + ".jpg", "backdrop": name + ".jpg"})
        print("ok", t["title"], PACKAGES[t["service"]], link(t))
    json.dump({"moods": MOODS, "titles": out}, open(OUT + "/moods.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print(len(out), "titles")


main()
