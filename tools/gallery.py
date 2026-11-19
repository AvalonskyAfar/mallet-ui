#!/usr/bin/env python3
"""Puts the pictures and clips taken on a device into docs, and writes docs/GALLERY.md.

    python tools/device.py shots          (leaves the screenshots in build/device/shots)
    python tools/device.py motion         (leaves the clips in build/device/motion)
    python tools/gallery.py               (run from the project root; needs Pillow)

- docs/images/device/<name>.png   every screenshot of the tour except the screen-by-screen
                                  scrolls, 480 px wide
- docs/images/motion/<name>.gif   the clips of the motion tour, as they are
- docs/images/hero.png            five screens side by side, for the top of the README
- docs/GALLERY.md                 a page that shows all of them, in order

The screenshots are photographs of the demo app running on the device named in
docs/VERIFICATION.md; nothing is drawn into them here. They are scaled down and stored with
a palette of 256 colours, which keeps the repository small and cannot be told from the
original at this size. The hero rounds the corners of five of them and sets them on a
plain ground with the short downward shadow of the design language.
"""
import os
import re
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
GALLERY = os.path.join("docs", "GALLERY.md")
STRINGS = os.path.join("showcase", "src", "main", "res", "values", "strings.xml")
WIDTH = 480
SHOWN = 230

HERO_SCREENS = [
    "page_home_light", "gesture_track_light", "overlay_dialog_light", "page_c_tracks_dark", "page_c_rows_light",
]
HERO_SCREEN_WIDTH = 330
HERO_GAP = 34
HERO_MARGIN = 48
HERO_RADIUS = 30
HERO_GROUND = (236, 233, 227)

# (heading, the lines of a note under it, the keys of its pages)
PAGE_GROUPS = [
    ("Home and the four pages around it", [
        "Home shows nothing that points at its neighbours: no arrows, no page names at the edges. It",
        "names the pages around it once, in a row of four entries at its bottom, one for each",
        "first-level page. A first-level page shows no way back: it is left by dragging back towards",
        "Home, or with the system's Back.",
    ], ["home", "foundations", "components", "interactions", "appearance"]),
    ("Foundations", [], ["f_colour", "f_type", "f_material", "f_icons"]),
    ("Components", [], ["c_tracks", "c_choice", "c_numbers", "c_buttons", "c_fields", "c_rows", "c_nav", "c_overlays",
                        "c_states", "c_reading", "c_xml"]),
    ("Interactions", [], ["i_press", "i_follow", "i_spatial", "i_reveal", "i_pager", "i_arbitrate", "i_motion"]),
]
OVERLAYS = [
    ("overlay_dialog", "A dialog"),
    ("overlay_dialog_danger", "A dialog whose action destroys something"),
    ("overlay_dialog_three", "A dialog with three actions"),
    ("overlay_sheet", "A sheet of actions"),
    ("overlay_sheet_custom", "A sheet with content of its own"),
    ("overlay_menu", "A menu"),
    ("overlay_menu_checked", "A menu with a checked and a disabled item"),
    ("overlay_toast", "A toast with an action"),
]
GESTURES = [
    ("gesture_between_pages", "Between two places: Home leaves upwards while Components arrives from below"),
    ("gesture_track", "A thumb between two options: the hue under it is a blend of the two"),
    ("gesture_power", "The power switch half-way to off"),
    ("gesture_row_open", "A row that has slid aside and uncovered its action from the rim of the card"),
]
CLIPS = [
    ("places", "Pages are places: to the page beside Home and back, to the page below, and from one of its rows into "
               "a deeper page"),
    ("tracks", "A thumb follows the finger; the track takes the hue of where the thumb is"),
    ("switches", "A tap, a drag, and a drag that is taken back"),
    ("sliders", "Sliders: continuous, in steps, and through the hue scale"),
    ("reveal", "Rows slide aside and uncover one action from the rim of the card; sections open in place"),
    ("pager", "Two pagers that turn together"),
    ("overlays", "A dialog, a sheet dragged down, a menu, a toast"),
    ("appearance", "The theme changes while the app runs"),
]


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


def page_titles():
    titles = {}
    if os.path.isfile(STRINGS):
        with open(STRINGS, encoding="utf-8") as source:
            for key, title in re.findall(r'<string name="page_(\w+)_title">([^<]*)</string>', source.read()):
                titles[key] = title.replace("\\'", "'").replace("&amp;", "&")
    return titles


def picture(name, alt):
    return '<img src="images/device/%s.png" width="%d" alt="%s">' % (name, SHOWN, alt)


def pair(lines, stem, caption, have):
    cells = [picture(stem + "_" + theme, "%s, %s theme" % (caption, theme)) if (stem + "_" + theme) in have else ""
             for theme in ("light", "dark")]
    lines.append("| %s | %s | %s |" % (caption, cells[0], cells[1]))


def row_of(lines, names, captions):
    lines.append("| " + " | ".join(picture(name, caption) for name, caption in zip(names, captions)) + " |")
    lines.append("|" + ":---:|" * len(names))
    lines.append("| " + " | ".join(captions) + " |")
    lines.append("")


def write_gallery(have, clips):
    titles = page_titles()
    out = [
        "# Gallery",
        "",
        "The showcase app, photographed and recorded while it ran on an Android 16 emulator.",
        "The pictures are screenshots taken by the screenshot tour (`python tools/device.py shots`);",
        "the clips are screen recordings made by the motion tour (`python tools/device.py motion`),",
        "at normal speed, driven by real touch events. The dot that marks the finger in the clips is",
        "drawn by the tour. [VERIFICATION.md](VERIFICATION.md) says on which device and how.",
        "",
        "> This file is written by `tools/gallery.py`. Do not edit it by hand.",
        "",
        "Contents: [In motion](#in-motion) · [Pages](#pages) · [Overlays](#overlays) ·",
        "[Under the finger](#under-the-finger) · [Other appearances](#other-appearances) ·",
        "[Chinese](#chinese) · [Right to left](#right-to-left)",
        "",
        "## In motion",
        "",
    ]
    shown = [(name, caption) for name, caption in CLIPS if name + ".gif" in clips]
    for start in range(0, len(shown), 4):
        part = shown[start:start + 4]
        out.append("| " + " | ".join('<img src="images/motion/%s.gif" width="%d" alt="%s">' % (name, SHOWN, caption)
                                    for name, caption in part) + " |")
        out.append("|" + ":---:|" * len(part))
        out.append("| " + " | ".join(caption for _, caption in part) + " |")
        out.append("")

    out += ["## Pages", "", "Every page of the app as it opens, in both themes.", ""]
    for heading, note, keys in PAGE_GROUPS:
        out += ["### " + heading, ""]
        if note:
            out += note + [""]
        out += ["| | Light | Dark |", "|---|:---:|:---:|"]
        for key in keys:
            pair(out, "page_" + key, titles.get(key, key), have)
        out.append("")

    out += ["## Overlays", "", "| | Light | Dark |", "|---|:---:|:---:|"]
    for stem, caption in OVERLAYS:
        pair(out, stem, caption, have)
    if "overlay_dialog_input_error_light" in have:
        out.append("| An input dialog that refused a name | %s | |"
                   % picture("overlay_dialog_input_error_light", "An input dialog showing its error"))
    out.append("")

    out += ["## Under the finger", "", "Taken while the finger was still on the glass.", "",
            "| | Light | Dark |", "|---|:---:|:---:|"]
    for stem, caption in GESTURES:
        pair(out, stem, caption, have)
    out.append("")

    out += ["## Other appearances", "", "### Another set of hues", "", "| | Light | Dark |", "|---|:---:|:---:|"]
    for key in ("appearance", "c_tracks", "home"):
        pair(out, "look_plum_" + key, titles.get(key, key), have)
    out += ["", "### High contrast", "", "| | Light | Dark |", "|---|:---:|:---:|"]
    for key in ("c_tracks", "c_rows"):
        pair(out, "look_contrast_" + key, titles.get(key, key), have)
    large = [key for key in ("home", "c_tracks", "c_rows", "c_buttons") if "look_large_" + key in have]
    if large:
        out += ["", "### Larger text", ""]
        row_of(out, ["look_large_" + key for key in large], [titles.get(key, key) for key in large])

    chinese = sorted(name for name in have if name.startswith("zh_"))
    if chinese:
        out += ["## Chinese", "", "The app in Simplified Chinese.", ""]
        for start in range(0, len(chinese), 4):
            part = chinese[start:start + 4]
            captions = ["A dialog" if name == "zh_overlay_dialog" else titles.get(name[3:], name[3:]) for name in part]
            row_of(out, part, captions)

    mirrored = sorted(name for name in have if name.startswith("rtl_"))
    if mirrored:
        out += ["## Right to left", "",
                "The app has no strings in a right-to-left language; these show the layout direction.", ""]
        row_of(out, mirrored, [titles.get(name[4:], name[4:]) for name in mirrored])

    with open(GALLERY, "w", encoding="utf-8", newline="\n") as target:
        target.write("\n".join(out).rstrip("\n") + "\n")
    print("%s: %d pictures and %d clips" % (GALLERY, sum(line.count("images/device/") for line in out), len(shown)))


def main():
    if not os.path.isdir(FROM):
        sys.exit("no screenshots in %s: run `python tools/device.py shots` first" % FROM)
    os.makedirs(TO, exist_ok=True)
    for old in os.listdir(TO):
        if old.endswith(".png"):
            os.remove(os.path.join(TO, old))
    total = 0
    have = set()
    for name in sorted(os.listdir(FROM)):
        if not name.endswith(".png") or name.startswith("scroll_"):
            continue
        with Image.open(os.path.join(FROM, name)) as shot:
            target = os.path.join(TO, name)
            small = scaled(shot.convert("RGB"), WIDTH)
            small.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(target, optimize=True)
            total += os.path.getsize(target)
            have.add(name[:-4])
    print("%d pictures, %.1f MB in %s" % (len(have), total / 1e6, TO))
    hero(sys.argv[1:] or HERO_SCREENS)

    clips = []
    if os.path.isdir(CLIPS_FROM):
        os.makedirs(CLIPS_TO, exist_ok=True)
        size = 0
        clips = sorted(name for name in os.listdir(CLIPS_FROM) if name.endswith(".gif"))
        for name in clips:
            shutil.copyfile(os.path.join(CLIPS_FROM, name), os.path.join(CLIPS_TO, name))
            size += os.path.getsize(os.path.join(CLIPS_TO, name))
        print("%d clips, %.1f MB in %s" % (len(clips), size / 1e6, CLIPS_TO))
    write_gallery(have, clips)


if __name__ == "__main__":
    main()
