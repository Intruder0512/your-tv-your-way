"""White-on-transparent dock logos cut from the launcher's own tiles in a TV screenshot.

Usage (repo root):  tools/venv/Scripts/python.exe -I work/make_logos.py shots/11-down.png
Writes launcher/app/src/main/res/drawable-nodpi/logo_*.png and copies to work/logos/ for review.
"""
import colorsys
import os
import sys

from PIL import Image, ImageDraw, ImageFont, ImageFilter

SHOT = sys.argv[1] if len(sys.argv) > 1 else "shots/11-down.png"
OUT = "launcher/app/src/main/res/drawable-nodpi"
REVIEW = "work/logos"
TILE_W, TILE_H, TILE_Y = 354, 199, 402
TILES = {"netflix": 162, "youtube": 576, "prime": 990, "jiohotstar": 1404}
CANVAS = (720, 405)          # 16:9, twice the on-screen tile, so the logo stays sharp when focused
INK_TARGET = 0.085           # share of the canvas a logo's ink covers: equal visual weight
MAX_BOX = (0.86, 0.62)       # never wider/taller than this share of the canvas


def mask_for(name, img):
    """Alpha 0..255 per pixel: what belongs to the logo on this banner."""
    w, h = img.size
    px = img.load()
    out = Image.new("L", img.size, 0)
    po = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            hh, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if name == "netflix":          # red letters on white
                a = max(0.0, min(1.0, (s - 0.25) / 0.45))
            elif name == "youtube":        # red button and dark wordmark on light grey; the play triangle stays a hole
                a = max(max(0.0, min(1.0, (s - 0.3) / 0.4)), max(0.0, min(1.0, (0.55 - v) / 0.35)))
            elif name == "airtel":         # the swirl, white to mid grey, on red
                a = max(0.0, min(1.0, (0.45 - s) / 0.2)) * max(0.0, min(1.0, (v - 0.25) / 0.2))
            else:                          # white lettering on a coloured field
                a = max(0.0, min(1.0, (v - 0.72) / 0.2)) * max(0.0, min(1.0, (0.35 - s) / 0.25))
            po[x, y] = int(a * 255)
    return out


def place(alpha):
    """Trim, scale to the shared ink target and centre on the canvas; returns a white RGBA logo."""
    box = alpha.getbbox()
    alpha = alpha.crop(box)
    ink = sum(alpha.getdata()) / 255.0
    cw, ch = CANVAS
    scale = (INK_TARGET * cw * ch / max(ink, 1)) ** 0.5
    scale = min(scale, MAX_BOX[0] * cw / alpha.width, MAX_BOX[1] * ch / alpha.height)
    alpha = alpha.resize((max(1, round(alpha.width * scale)), max(1, round(alpha.height * scale))), Image.LANCZOS)
    logo = Image.new("RGBA", CANVAS, (255, 255, 255, 0))
    white = Image.new("RGBA", alpha.size, (255, 255, 255, 255))
    white.putalpha(alpha)
    # Sit low in the tile: the name that fades in under the focused logo then stays close to it.
    logo.paste(white, ((cw - alpha.width) // 2, min(ch - alpha.height, round(ch * 0.7 - alpha.height / 2))), white)
    return logo


def ps5():
    font = ImageFont.truetype("launcher/app/src/main/res/font/outfit.ttf", 200)
    font.set_variation_by_axes([600])   # Outfit is a variable font; its default instance is the thinnest
    img = Image.new("L", (900, 300), 0)
    d = ImageDraw.Draw(img)
    d.text((20, 20), "PS5", font=font, fill=255)
    return img


AIRTEL_BANNER = os.path.expanduser("~/.cleanyourtv/tmp/xs/res/drawable-xhdpi-v4/ic_launcher_leanback.png")


def airtel():
    """The Airtel swirl from the Xstream app's own banner, with "airtel" set beside it: the set-top box on HDMI 1."""
    banner = Image.open(AIRTEL_BANNER).convert("RGB")
    banner = banner.resize((banner.width * 4, banner.height * 4), Image.LANCZOS)
    # The banner shades the swirl grey towards its middle, so take everything that is not red, as one solid white.
    swirl = mask_for("airtel", banner.crop((440, 60, 840, 380)))
    swirl = swirl.crop(swirl.getbbox()).filter(ImageFilter.GaussianBlur(1.2))
    font = ImageFont.truetype("launcher/app/src/main/res/font/outfit.ttf", 230)
    font.set_variation_by_axes([600])
    text = Image.new("L", (900, 320), 0)
    ImageDraw.Draw(text).text((10, 10), "airtel", font=font, fill=255)
    text = text.crop(text.getbbox())
    swirl = swirl.resize((round(swirl.width * text.height * 1.35 / swirl.height), round(text.height * 1.35)), Image.LANCZOS)
    out = Image.new("L", (swirl.width + 40 + text.width, swirl.height), 0)
    out.paste(swirl, (0, 0))
    out.paste(text, (swirl.width + 40, swirl.height - text.height - round(swirl.height * 0.08)))
    return out


def main():
    shot = Image.open(SHOT).convert("RGB")
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(REVIEW, exist_ok=True)
    logos = {}
    for name, x in TILES.items():
        inset = 10   # keep the rounded corners and the band out of the crop
        tile = shot.crop((x + inset, TILE_Y + inset, x + TILE_W - inset, TILE_Y + TILE_H - inset))
        tile = tile.resize((tile.width * 2, tile.height * 2), Image.LANCZOS)
        alpha = mask_for(name, tile).filter(ImageFilter.GaussianBlur(0.6))
        logos[name] = place(alpha)
    logos["ps5"] = place(ps5())
    if os.path.exists(AIRTEL_BANNER):
        logos["airtel"] = place(airtel())
    for name, logo in logos.items():
        logo.save(os.path.join(OUT, "banner_logo_%s.png" % name))
        preview = Image.new("RGBA", CANVAS, (20, 20, 28, 255))
        preview.alpha_composite(logo)
        preview.save(os.path.join(REVIEW, "%s.png" % name))
        print(name, logo.getbbox())


main()
