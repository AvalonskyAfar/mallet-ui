#!/usr/bin/env python3
"""Copies the pictures the unit tests draw into docs/images.

    ./gradlew :sancai:testDebugUnitTest :showcase:testDebugUnitTest
    python tools/pictures.py            (run from the project root; needs Pillow)

The library tests draw a gallery of every component in the light and in the dark theme
(sancai/build/snapshots); the showcase test draws every page of the demo app
(showcase/build/snapshots). Both run on the JVM with Robolectric and Android's own native
graphics, so the pictures show what the code draws, but they are not photographs of a device:
there is no status bar in them, and fonts are the ones bundled with Robolectric.

- docs/images/components/<name>_light.png, <name>_dark.png   the galleries, 720 px wide
- docs/images/pages/<page>_<variant>.png                     the top of each demo page

Pictures taken on an emulator (docs/images/device) are made by tools/device.py instead.
"""
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is needed: pip install pillow")

COMPONENTS_FROM = "sancai/build/snapshots"
PAGES_FROM = "showcase/build/snapshots"
COMPONENTS_TO = "docs/images/components"
PAGES_TO = "docs/images/pages"
GALLERY_WIDTH = 720
PAGE_WIDTH = 540
PAGE_HEIGHT_DP = 860          # one screen of a page; the pictures are 2.625 px per dp
DENSITY = 2.625
PAGE_VARIANTS = ("light", "dark", "zh", "rtl", "large")


def save(image, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, optimize=True)
    return os.path.getsize(path)


def scaled(image, width):
    if image.width <= width:
        return image
    height = max(1, round(image.height * width / image.width))
    return image.resize((width, height), Image.LANCZOS)


def main():
    if not os.path.isdir(COMPONENTS_FROM):
        sys.exit("no pictures in %s: run the unit tests first" % COMPONENTS_FROM)
    total = 0
    count = 0
    for name in sorted(os.listdir(COMPONENTS_FROM)):
        if not name.endswith(".png"):
            continue
        with Image.open(os.path.join(COMPONENTS_FROM, name)) as image:
            total += save(scaled(image.convert("RGB"), GALLERY_WIDTH), os.path.join(COMPONENTS_TO, name))
            count += 1
    print("%d component pictures" % count)

    pages = 0
    if os.path.isdir(PAGES_FROM):
        for name in sorted(os.listdir(PAGES_FROM)):
            stem, extension = os.path.splitext(name)
            if extension != ".png" or not stem.startswith("page_"):
                continue
            if not stem.endswith(tuple("_" + variant for variant in PAGE_VARIANTS)):
                continue
            with Image.open(os.path.join(PAGES_FROM, name)) as image:
                screen = image.convert("RGB").crop((0, 0, image.width, min(image.height, round(PAGE_HEIGHT_DP * DENSITY))))
                total += save(scaled(screen, PAGE_WIDTH), os.path.join(PAGES_TO, stem[len("page_"):] + ".png"))
                pages += 1
    print("%d page pictures" % pages)
    print("%.1f MB in docs/images" % (total / 1e6))


if __name__ == "__main__":
    main()
