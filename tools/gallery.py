#!/usr/bin/env python3
"""Puts the pictures and clips taken on a device into docs/images.

    python tools/device.py shots          (leaves the screenshots in build/device/shots)
    python tools/device.py motion         (leaves the clips in build/device/motion)
    python tools/gallery.py               (run from the project root; needs Pillow)

- docs/images/device/<name>.png   every screenshot of the tour except the screen-by-screen
                                  scrolls, 480 px wide
- docs/images/motion/<name>.gif   the clips of the motion tour, as they are
- docs/images/hero.png            five screens side by side, for the top of the README

The screenshots are photographs of the demo app running on the device named in
docs/VERIFICATION.md; nothing is drawn into them here. The hero only rounds the corners of
each screen and sets the screens on a plain ground with the short downward shadow of the
design language.
"""
import os
import shutil
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:
    sys.exit("Pillow is needed: pip install pillow")

FROM = os.path.join("build", "device", "shots")
TO = os.path.join("docs", "images", "device")
CLIPS_FROM = os.path.join("build", "device", "motion")
CLIPS_TO = os.path.join("docs", "images", "motion")
HERO = os.path.join("docs", "images", "hero.png")
WIDTH = 480

HERO_SCREENS = [
    "page_home_light", "gesture_track_light", "overlay_dialog_light", "page_c_tracks_dark", "gesture_between_pages_dark",
]
HERO_SCREEN_WIDTH = 330
HERO_GAP = 34
HERO_MARGIN = 48
HERO_RADIUS = 30
HERO_GROUND = (236, 233, 227)


def scaled(image, width):
    height = max(1, round(image.height * width / image.width))
    return image.resize((width, height), Image.LANCZOS)


def rounded(image, radius):
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), radius, fill=255)
    out = image.convert("RGBA")
    out.putalpha(mask)
    return out


def hero(names):
    screens = []
    for name in names:
        path = os.path.join(FROM, name + ".png")
        if not os.path.isfile(path):
            sys.exit("the hero needs %s: run the screenshot tour first" % path)
        with Image.open(path) as picture:
            screens.append(rounded(scaled(picture.convert("RGB"), HERO_SCREEN_WIDTH), HERO_RADIUS))
    height = max(screen.height for screen in screens)
    width = 2 * HERO_MARGIN + len(screens) * HERO_SCREEN_WIDTH + (len(screens) - 1) * HERO_GAP
    canvas = Image.new("RGBA", (width, height + 2 * HERO_MARGIN + 16), HERO_GROUND + (255,))
    # One short soft shadow below each screen, then a hairline rim: depth as the library paints it.
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow)
    x = HERO_MARGIN
    for screen in screens:
        draw.rounded_rectangle((x + 2, HERO_MARGIN + 10, x + screen.width - 3, HERO_MARGIN + screen.height + 9),
                               HERO_RADIUS, fill=(40, 34, 26, 70))
        x += HERO_SCREEN_WIDTH + HERO_GAP
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(9)))
    rim = ImageDraw.Draw(canvas)
    x = HERO_MARGIN
    for screen in screens:
        canvas.alpha_composite(screen, (x, HERO_MARGIN))
        rim.rounded_rectangle((x, HERO_MARGIN, x + screen.width - 1, HERO_MARGIN + screen.height - 1),
                              HERO_RADIUS, outline=(0, 0, 0, 46), width=1)
        x += HERO_SCREEN_WIDTH + HERO_GAP
    os.makedirs(os.path.dirname(HERO), exist_ok=True)
    canvas.convert("RGB").save(HERO, optimize=True)
    print("%s  %d x %d  %d KB" % (HERO, canvas.width, canvas.height, os.path.getsize(HERO) // 1024))


def main():
    if not os.path.isdir(FROM):
        sys.exit("no screenshots in %s: run `python tools/device.py shots` first" % FROM)
    os.makedirs(TO, exist_ok=True)
    for old in os.listdir(TO):
        if old.endswith(".png"):
            os.remove(os.path.join(TO, old))
    total = 0
    count = 0
    for name in sorted(os.listdir(FROM)):
        if not name.endswith(".png") or name.startswith("scroll_"):
            continue
        with Image.open(os.path.join(FROM, name)) as picture:
            target = os.path.join(TO, name)
            scaled(picture.convert("RGB"), WIDTH).save(target, optimize=True)
            total += os.path.getsize(target)
            count += 1
    print("%d pictures, %.1f MB in %s" % (count, total / 1e6, TO))
    hero(sys.argv[1:] or HERO_SCREENS)

    if os.path.isdir(CLIPS_FROM):
        os.makedirs(CLIPS_TO, exist_ok=True)
        size = 0
        clips = sorted(name for name in os.listdir(CLIPS_FROM) if name.endswith(".gif"))
        for name in clips:
            shutil.copyfile(os.path.join(CLIPS_FROM, name), os.path.join(CLIPS_TO, name))
            size += os.path.getsize(os.path.join(CLIPS_TO, name))
        print("%d clips, %.1f MB in %s" % (len(clips), size / 1e6, CLIPS_TO))


if __name__ == "__main__":
    main()
