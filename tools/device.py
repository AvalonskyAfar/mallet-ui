#!/usr/bin/env python3
"""Runs the showcase on a connected device or emulator: tests, pictures and clips.

    python tools/device.py build              debug APK and the APK of the device tests
    python tools/device.py install            install both on the connected device
    python tools/device.py test               run the device tests, write build/device/tests.txt
    python tools/device.py shots              run the screenshot tour, pull build/device/shots
    python tools/device.py motion [--slow N]  run the motion tour, pull build/device/motion, make GIFs
    python tools/device.py all                everything above, in this order
    python tools/device.py smoke [--apk F]    install the shrunk release APK, open every page, look for a crash

Run it from the project root. It needs `adb` (from ANDROID_HOME, local.properties or PATH) and
one device in `adb devices`. `motion` also needs ffmpeg (FFMPEG, PATH, or the imageio-ffmpeg
package) to turn the recordings into GIFs.

The device tests live in showcase/src/androidTest. They drive the real app with real touch
events (UiAutomator) and read the state of the views. The two tours are not tests: they walk
through the app and leave pictures and clips behind. In the clips a dot marks where the
window of the app is touched; the tour draws it, because Android's own "show taps" does not
show injected touches.

--slow N stretches every gesture and animation of the motion tour by N and plays the result
back N times faster. An emulator without a graphics card draws only a few frames per second;
slowed down, it has the time to draw them all.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time

APP = "io.github.avalonskyafar.mallet.showcase"
TEST = APP + ".test"
RUNNER = TEST + "/androidx.test.runner.AndroidJUnitRunner"
REMOTE = "/data/local/tmp/mallet"
OUT = os.path.join("build", "device")
APK = os.path.join("showcase", "build", "outputs", "apk", "debug", "showcase-debug.apk")
TEST_APK = os.path.join("showcase", "build", "outputs", "apk", "androidTest", "debug", "showcase-debug-androidTest.apk")
RELEASE_APK = os.path.join("showcase", "build", "outputs", "apk", "release", "showcase-release.apk")

STATUS = {0: "passed", -1: "ERROR", -2: "FAILED", -3: "ignored", -4: "skipped"}
LEAD_CUT_S = 1.1


def find_adb():
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    name = "adb.exe" if os.name == "nt" else "adb"
    homes = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")]
    if os.path.isfile("local.properties"):
        with open("local.properties", encoding="utf-8") as properties:
            for line in properties:
                if line.startswith("sdk.dir="):
                    homes.append(line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\"))
    for home in homes:
        if home and os.path.isfile(os.path.join(home, "platform-tools", name)):
            return os.path.join(home, "platform-tools", name)
    return shutil.which("adb") or sys.exit("adb not found: set ANDROID_HOME or ADB")


ADB = None


def adb(*arguments, check=True, timeout=None):
    result = subprocess.run([ADB, *arguments], capture_output=True, timeout=timeout)
    output = result.stdout.decode("utf-8", "replace") + result.stderr.decode("utf-8", "replace")
    if check and result.returncode != 0:
        sys.exit("adb %s failed:\n%s" % (" ".join(arguments), output))
    return output


def shell(command, check=True, timeout=None):
    return adb("shell", command, check=check, timeout=timeout)


def gradle(*tasks):
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    wrapper = os.path.join(root, "gradlew.bat" if os.name == "nt" else "gradlew")
    code = subprocess.call([wrapper, *tasks, "--console=plain", "-q"])
    if code != 0:
        sys.exit("gradle %s failed with exit code %d" % (" ".join(tasks), code))


def device_description():
    def prop(name):
        return shell("getprop " + name).strip()
    size = shell("wm size").strip().replace("\n", ", ")
    density = shell("wm density").strip().replace("\n", ", ")
    return "%s %s, Android %s (API %s), %s, %s, %s" % (
        prop("ro.product.manufacturer"), prop("ro.product.model"), prop("ro.build.version.release"),
        prop("ro.build.version.sdk"), prop("ro.product.cpu.abi"), size, density,
    )


def require_device():
    lines = [line for line in adb("devices").splitlines()[1:] if line.strip().endswith("device")]
    if len(lines) != 1:
        sys.exit("exactly one device must be connected; adb devices shows %d" % len(lines))


def build(_):
    gradle(":showcase:assembleDebug", ":showcase:assembleDebugAndroidTest")
    for path in (APK, TEST_APK):
        print("%-78s %6d KB" % (path, os.path.getsize(path) // 1024))


def install(_):
    require_device()
    for path in (APK, TEST_APK):
        if not os.path.isfile(path):
            sys.exit("%s is missing: run `python tools/device.py build` first" % path)
        print(adb("install", "-r", "-t", path, timeout=600).strip().splitlines()[-1], path)
    shell("mkdir -p %s/shots %s/motion" % (REMOTE, REMOTE))


def instrument(extras, log_name, timeout):
    """Runs the instrumentation with [extras]; returns (tests, raw output)."""
    arguments = " ".join("-e %s %s" % (key, value) for key, value in extras.items())
    started = time.time()
    raw = shell("am instrument -w -r %s %s" % (arguments, RUNNER), check=False, timeout=timeout)
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, log_name), "w", encoding="utf-8") as log:
        log.write(raw)
    tests = []
    current = {}
    stack = None
    for line in raw.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            stack = None
            if key == "stack":
                stack = [value]
                current["stack"] = stack
            else:
                current[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(": ")[1])
            stack = None
            if code != 1:
                tests.append((current.get("class", "?"), current.get("test", "?"), code, "\n".join(current.get("stack", []))))
            current = {}
        elif stack is not None:
            stack.append(line)
    if "INSTRUMENTATION_CODE: -1" not in raw and not tests:
        sys.exit("the instrumentation did not run:\n" + raw[-3000:])
    return tests, raw, time.time() - started


def report(tests, seconds, title, file_name):
    lines = [title, "device: " + device_description(), "date:   " + time.strftime("%Y-%m-%d %H:%M %z"), ""]
    for klass, test, code, stack in tests:
        lines.append("%-8s %s.%s" % (STATUS.get(code, str(code)), klass.rsplit(".", 1)[-1], test))
        if code in (-1, -2):
            lines.extend("             " + part for part in stack.strip().splitlines()[:12])
    counts = {}
    for _, _, code, _ in tests:
        counts[STATUS.get(code, str(code))] = counts.get(STATUS.get(code, str(code)), 0) + 1
    lines.append("")
    lines.append("%d tests in %.0f s: %s" % (len(tests), seconds, ", ".join("%d %s" % (n, s) for s, n in sorted(counts.items()))))
    text = "\n".join(lines) + "\n"
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, file_name), "w", encoding="utf-8") as out:
        out.write(text)
    print(text)
    return not any(code in (-1, -2) for _, _, code, _ in tests)


def test(options):
    require_device()
    extras = {"package": APP + ".device"}
    if options.only:
        extras = {"class": APP + ".device." + options.only}
    tests, _, seconds = instrument(extras, "tests-raw.txt", 3600)
    if not report(tests, seconds, "Mallet showcase: device tests", "tests.txt"):
        sys.exit(1)


def pull(remote, local):
    if os.path.isdir(local):
        shutil.rmtree(local)
    os.makedirs(os.path.dirname(local), exist_ok=True)
    adb("pull", remote, local, timeout=1800)
    return sorted(os.listdir(local))


def shots(options):
    require_device()
    shell("rm -rf %s/shots; mkdir -p %s/shots" % (REMOTE, REMOTE))
    extras = {"class": APP + ".tour.ScreenshotTour" + ("#" + options.only if options.only else ""), "tour": "true"}
    tests, _, seconds = instrument(extras, "shots-raw.txt", 3600)
    ok = report(tests, seconds, "Mallet showcase: screenshot tour", "shots.txt")
    names = pull(REMOTE + "/shots", os.path.join(OUT, "shots"))
    print("%d pictures in %s" % (len(names), os.path.join(OUT, "shots")))
    if not ok:
        sys.exit(1)


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


def to_gif(ffmpeg, source, target, slow, width, fps):
    chain = "setpts=PTS/%s,fps=%d,scale=%d:-1:flags=lanczos" % (slow, fps, width)
    # Flat colours without dithering: a dithered picture of a calm interface is several times larger.
    palette = "split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle"
    # The tour starts the recorder 1.8 s before each scene; most of that still lead is cut off.
    lead = "%.2f" % (LEAD_CUT_S * float(slow))
    command = [ffmpeg, "-y", "-loglevel", "error", "-ss", lead, "-i", source, "-vf", chain + "," + palette, "-loop", "0", target]
    if subprocess.call(command) != 0:
        sys.exit("ffmpeg failed on " + source)


def set_animation_scale(value):
    for name in ("animator_duration_scale", "transition_animation_scale", "window_animation_scale"):
        shell("settings put global %s %s" % (name, value))


def motion(options):
    require_device()
    slow = options.slow
    shell("rm -rf %s/motion; mkdir -p %s/motion" % (REMOTE, REMOTE))
    extras = {
        "class": APP + ".tour.MotionTour" + ("#" + options.only if options.only else ""),
        "tour": "true", "slow": slow, "size": options.size,
    }
    set_animation_scale(slow)
    try:
        tests, _, seconds = instrument(extras, "motion-raw.txt", 3600)
    finally:
        set_animation_scale(1)
    ok = report(tests, seconds, "Mallet showcase: motion tour (slowed %sx)" % slow, "motion.txt")
    names = pull(REMOTE + "/motion", os.path.join(OUT, "motion"))
    ffmpeg = find_ffmpeg()
    if ffmpeg is None:
        print("ffmpeg not found: the recordings stay MP4 (%s)" % ", ".join(names))
    else:
        for name in names:
            if name.endswith(".mp4"):
                source = os.path.join(OUT, "motion", name)
                target = source[:-4] + ".gif"
                to_gif(ffmpeg, source, target, slow, options.width, options.fps)
                print("%-40s %6d KB" % (target, os.path.getsize(target) // 1024))
    if not ok:
        sys.exit(1)


def page_keys():
    """The keys of the demo pages, read from the sources of the showcase."""
    keys = {"home"}
    for directory, _, files in os.walk(os.path.join("showcase", "src", "main", "kotlin")):
        for name in files:
            if name.endswith(".kt"):
                with open(os.path.join(directory, name), encoding="utf-8") as source:
                    keys.update(re.findall(r'DemoPage\(\s*"([a-z_0-9]+)"', source.read()))
    return sorted(keys)


def smoke(options):
    """Opens every page of an installed build that the device tests cannot drive: the release APK.

    The release build is shrunk and optimised by R8. A class or resource it removed by mistake
    shows as a crash when the page that needs it is built, laid out or drawn.
    """
    require_device()
    apk = options.apk or RELEASE_APK
    if not os.path.isfile(apk):
        sys.exit("%s is missing: run `./gradlew :showcase:assembleRelease` (or `./gradlew dist`) first" % apk)
    print(adb("install", "-r", "-t", apk, timeout=600).strip().splitlines()[-1], apk)
    shell("logcat -b crash -c", check=False)
    keys = page_keys()
    lines = ["Mallet showcase: every page of %s" % apk.replace(os.sep, "/"), "device: " + device_description(),
             "date:   " + time.strftime("%Y-%m-%d %H:%M %z"), ""]
    failed = 0
    for key in keys:
        started = shell("am start -W --activity-clear-task -n %s/.MainActivity --es page %s" % (APP, key), check=False)
        time.sleep(1.0)
        # Two swipes, so that more than the first screen is laid out and drawn.
        shell("input swipe 540 1300 540 500 250", check=False)
        shell("input swipe 540 1300 540 500 250", check=False)
        time.sleep(0.4)
        crash = shell("logcat -b crash -d", check=False).strip()
        alive = APP in shell("ps -A 2>/dev/null | grep %s || ps | grep %s" % (APP, APP), check=False)
        ok = "Status: ok" in started and alive and "FATAL EXCEPTION" not in crash
        failed += 0 if ok else 1
        lines.append("%-8s %s" % ("opened" if ok else "CRASHED", key))
        if not ok:
            lines.extend("             " + part for part in crash.splitlines()[:14])
            shell("logcat -b crash -c", check=False)
    lines += ["", "%d pages: %d opened, %d crashed" % (len(keys), len(keys) - failed, failed)]
    text = "\n".join(lines) + "\n"
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "smoke.txt"), "w", encoding="utf-8") as out:
        out.write(text)
    print(text)
    if failed:
        sys.exit(1)


def everything(options):
    build(options)
    install(options)
    test(options)
    shots(options)
    motion(options)


def main():
    global ADB
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("command", choices=["build", "install", "test", "shots", "motion", "all", "smoke"])
    parser.add_argument("--apk", help="smoke: the APK to install instead of the release build of the showcase")
    parser.add_argument("--only", help="one test class (test) or one method of the tour (shots, motion)")
    parser.add_argument("--slow", default="1", help="motion: stretch gestures and animations by this factor")
    parser.add_argument("--size", default="720x1600", help="motion: size of the recording")
    parser.add_argument("--width", type=int, default=320, help="motion: width of the GIFs")
    parser.add_argument("--fps", type=int, default=25, help="motion: frames per second of the GIFs")
    options = parser.parse_args()
    if not re.fullmatch(r"\d+(\.\d+)?", options.slow):
        sys.exit("--slow takes a number")
    if not os.path.isfile("settings.gradle.kts"):
        sys.exit("run this from the project root")
    ADB = find_adb() if options.command != "build" else None
    commands = {"build": build, "install": install, "test": test, "shots": shots, "motion": motion, "all": everything, "smoke": smoke}
    commands[options.command](options)


if __name__ == "__main__":
    main()
