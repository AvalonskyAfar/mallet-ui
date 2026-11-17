#!/usr/bin/env python3
"""Turns the films the showcase tests draw into GIFs.

    ./gradlew :showcase:testDebugUnitTest --tests "*ShowcaseFilmTest" -PsancaiFilms
    python tools/films.py                 (run from the project root)

ShowcaseFilmTest starts the real activity of the demo app on the JVM, touches it and draws
it frame by frame, 50 pictures a second, into showcase/build/films/<name>/0001.png and so on
(see showcase/src/test/.../Film.kt). This script makes docs/images/films/<name>.gif of each
sequence: every second picture, 25 a second, 360 px wide.

These films are not screen recordings: there is no device, status bar or system font in them.
Clips recorded on an emulator are made by tools/device.py instead.

It uses ffmpeg when there is one (FFMPEG, PATH or the imageio-ffmpeg package) and Pillow
otherwise; ffmpeg gives the smaller files.
"""
import argparse
import os
import shutil
import subprocess
import sys

FROM = os.path.join("showcase", "build", "films")
TO = os.path.join("docs", "images", "films")


def find_ffmpeg():
    if os.environ.get("FFMPEG"):
        return os.environ["FFMPEG"]
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return None


def with_ffmpeg(ffmpeg, frames, target, width, fps):
    chain = "fps=%d,scale=%d:-1:flags=lanczos" % (fps, width)
    palette = "split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle"
    command = [
        ffmpeg, "-y", "-loglevel", "error", "-framerate", "50", "-i", os.path.join(frames, "%04d.png"),
        "-vf", chain + "," + palette, "-loop", "0", target,
    ]
    if subprocess.call(command) != 0:
        sys.exit("ffmpeg failed on " + frames)


def with_pillow(frames, target, width, fps):
    try:
        from PIL import Image
    except ImportError:
        sys.exit("neither ffmpeg nor Pillow is available: pip install pillow")
    step = max(1, round(50 / fps))
    names = sorted(name for name in os.listdir(frames) if name.endswith(".png"))[::step]
    pictures = []
    for name in names:
        with Image.open(os.path.join(frames, name)) as picture:
            height = round(picture.height * width / picture.width)
            pictures.append(picture.convert("RGB").resize((width, height), Image.LANCZOS)
                            .quantize(colors=128, method=Image.MEDIANCUT, dither=Image.NONE))
    pictures[0].save(target, save_all=True, append_images=pictures[1:], duration=round(1000 / fps), loop=0, optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--width", type=int, default=360)
    parser.add_argument("--fps", type=int, default=25)
    parser.add_argument("--only", help="make only the film with this name")
    options = parser.parse_args()
    if not os.path.isdir(FROM):
        sys.exit("no films in %s: run ShowcaseFilmTest with -PsancaiFilms first" % FROM)
    os.makedirs(TO, exist_ok=True)
    ffmpeg = find_ffmpeg()
    total = 0
    for name in sorted(os.listdir(FROM)):
        frames = os.path.join(FROM, name)
        if not os.path.isdir(frames) or (options.only and name != options.only):
            continue
        count = len([f for f in os.listdir(frames) if f.endswith(".png")])
        if count == 0:
            continue
        target = os.path.join(TO, name + ".gif")
        if ffmpeg:
            with_ffmpeg(ffmpeg, frames, target, options.width, options.fps)
        else:
            with_pillow(frames, target, options.width, options.fps)
        size = os.path.getsize(target)
        total += size
        print("%-28s %4d frames  %5.1f s  %6d KB" % (name + ".gif", count, count / 50.0, size // 1024))
    print("%.1f MB in %s (%s)" % (total / 1e6, TO, "ffmpeg" if ffmpeg else "Pillow"))


if __name__ == "__main__":
    main()
