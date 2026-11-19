#!/usr/bin/env python3
"""Generates docs/COMPONENT_CATALOG.md from the sources of the library.

    python tools/catalog.py            (run from the project root)

Nothing in the catalog is written by hand except the group introductions in this file:

- what a component is, how it behaves and its examples come from its KDoc;
- its XML attributes come from res/values/mallet_attrs_*.xml, with their comments;
- its public members come from the Kotlin source, with the first sentence of their KDoc;
- whether it was extracted from the original app or is new comes from the tables of
  docs/DESIGN_DECISIONS.md;
- its pictures are the ones the unit tests draw (see tools/pictures.py).

So the catalog cannot say something the code does not do. Run it again after changing a
component; `--check` exits with an error when the file on disk is out of date.
"""
import io
import os
import re
import sys

SOURCES = "mallet/src/main/kotlin/io/github/avalonskyafar/mallet"
ATTRS = "mallet/src/main/res/values"
DECISIONS = "docs/DESIGN_DECISIONS.md"
PICTURES = "docs/images/components"
TARGET = "docs/COMPONENT_CATALOG.md"

# (group title, introduction, [(class, package directory, picture stem or None, showcase page)])
GROUPS = [
    ("Surfaces and text", "What everything else stands on: the card, the divider, text in the type scale and icons.", [
        ("MalletSurface", "widget", "foundation", "Foundations › Materials and depth"),
        ("MalletDivider", "widget", "foundation", "Foundations › Materials and depth"),
        ("MalletText", "widget", "foundation", "Foundations › Typography"),
        ("MalletIconView", "icon", "glyphs", "Foundations › Icons"),
    ]),
    ("Actions", "Things that are pressed. A page has at most one primary capsule; everything else is neutral.", [
        ("MalletButton", "widget", "foundation", "Components › Buttons and chips"),
        ("MalletChip", "widget", "chip", "Components › Buttons and chips"),
        ("MalletChipGroup", "widget", "chip", "Components › Buttons and chips"),
        ("MalletTag", "widget", "chip", "Components › Buttons and chips"),
        ("MalletBadge", "widget", None, "Components › Buttons and chips"),
    ]),
    ("Choice", "Controls whose state is a colour in a recessed bed. The thumb or bead that moves over it stays neutral.", [
        ("MalletSegmentedTrack", "widget", "segmented_track", "Components › Tracks and switches"),
        ("MalletSwitch", "widget", "switch", "Components › Tracks and switches"),
        ("MalletPowerSwitch", "widget", "power", "Components › Tracks and switches"),
        ("MalletCheckbox", "widget", "checkbox", "Components › Checkboxes and radios"),
        ("MalletRadioButton", "widget", "radio", "Components › Checkboxes and radios"),
        ("MalletRadioGroup", "widget", "radio", "Components › Checkboxes and radios"),
    ]),
    ("Numbers and progress", "An exact number, an approximate amount, and the two ways of saying that something is going on.", [
        ("MalletStepper", "widget", "stepper", "Components › Numbers and progress"),
        ("MalletSlider", "widget", "slider", "Components › Numbers and progress"),
        ("MalletProgressBar", "widget", "progress", "Components › Numbers and progress"),
        ("MalletSpinner", "widget", "progress", "Components › Numbers and progress"),
    ]),
    ("Text input", "One component: a label, a recessed well and a support line.", [
        ("MalletTextField", "widget", "field", "Components › Text fields"),
    ]),
    ("Rows and pages", "A page is a scrolling column of headings and cards; a card is a column of rows.", [
        ("MalletPage", "widget", "page", "Components › Built from XML"),
        ("MalletHeading", "widget", "headinglink", "Components › Rows and lists"),
        ("MalletEntryRow", "widget", "entry", "Components › Rows and lists"),
        ("MalletControlRow", "widget", "controlrow", "Components › Rows and lists"),
        ("MalletLinkRow", "widget", "headinglink", "Components › Rows and lists"),
        ("MalletKeyValueList", "widget", "controlrow", "Components › Rows and lists"),
    ]),
    ("Reading", "Long-form content: explanations, notes, steps and pictures.", [
        ("MalletParagraph", "widget", "media", "Components › Reading"),
        ("MalletCallout", "widget", "callout", "Components › Reading"),
        ("MalletFlowRow", "widget", "callout", "Components › Reading"),
        ("MalletMediaFrame", "widget", "media", "Components › Reading"),
    ]),
    ("Navigation", "Bars and tabs for apps with a few sections, and the spatial host for apps whose pages are places.", [
        ("MalletNavBar", "widget", "navbar", "Components › Navigation"),
        ("MalletTopBar", "widget", "topbar", "Components › Navigation"),
        ("MalletTabs", "widget", "tabs", "Components › Navigation"),
        ("MalletPager", "interaction", "pager", "Interactions › Pager"),
        ("MalletPageIndicator", "widget", "pager", "Interactions › Pager"),
        ("MalletSpatialGraph", "interaction", None, "Interactions › Pages as places"),
        ("MalletSpatialHost", "interaction", "spatial_drag", "Interactions › Pages as places"),
        ("MalletBack", "interaction", None, "Interactions › Pages as places"),
        ("MalletSpatialTransition", "interaction", "transition", "Interactions › Pages as places"),
    ]),
    ("Overlays", "What appears above a page: a question, a set of actions, a small menu, a passing message, a floating panel.", [
        ("MalletDialog", "overlay", "dialog", "Components › Dialogs and sheets"),
        ("MalletBottomSheet", "overlay", "sheet", "Components › Dialogs and sheets"),
        ("MalletMenu", "overlay", "menu", "Components › Dialogs and sheets"),
        ("MalletToast", "overlay", "toast", "Components › Dialogs and sheets"),
        ("MalletFloatingPanel", "overlay", "panel", "Components › Dialogs and sheets"),
    ]),
    ("States", "What an area shows when it has nothing, is waiting, or has failed.", [
        ("MalletStateView", "widget", "stateview", "Components › Empty, loading, error"),
        ("MalletStateLayout", "widget", "statelayout", "Components › Empty, loading, error"),
        ("MalletSkeleton", "widget", "skeleton", "Components › Empty, loading, error"),
        ("MalletBanner", "widget", "banner", "Components › Empty, loading, error"),
    ]),
    ("Interaction", "The behaviour behind the components, usable on views of your own.", [
        ("MalletPress", "interaction", None, "Interactions › Press and haptics"),
        ("MalletHaptics", "interaction", None, "Interactions › Press and haptics"),
        ("MalletRevealLayout", "interaction", "reveal", "Interactions › Reveal and expand"),
        ("MalletExpandableLayout", "interaction", "expandable", "Interactions › Reveal and expand"),
        ("MalletThemeTransition", "interaction", "themetransition", "Appearance"),
        ("MalletHorizontalDragOwner", "interaction", None, "Interactions › Who owns the gesture"),
        ("MalletControl", "widget", None, "Interactions › Who owns the gesture"),
    ]),
]


def read(path):
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


# ---------------------------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------------------------

DECLARATION = re.compile(r"^public (?:abstract |open |data |fun |enum |sealed )*(class|object|interface) (\w+)")


def kdoc_before(lines, index):
    """The KDoc that ends right above line [index] (annotations and comments in between allowed)."""
    i = index - 1
    while i >= 0 and (lines[i].strip().startswith("@") or lines[i].strip().startswith("//")):
        i -= 1
    if i < 0 or not lines[i].strip().endswith("*/"):
        return []
    end = i
    while i >= 0 and not lines[i].strip().startswith("/**"):
        i -= 1
    if i < 0:
        return []
    out = []
    for raw in lines[i:end + 1]:
        text = raw.strip()
        text = re.sub(r"^/\*\*", "", text)
        text = re.sub(r"\*/$", "", text)
        text = re.sub(r"^\* ?", "", text)
        out.append(text.rstrip())
    while out and not out[0].strip():
        out.pop(0)
    while out and not out[-1].strip():
        out.pop()
    return out


def first_sentence(doc_lines):
    # A tag line and the indented lines that continue it are parameter documentation, not
    # summary; only the prose before the first tag is kept.
    prose = []
    in_tag = False
    for raw in doc_lines:
        stripped = raw.strip()
        if stripped.startswith("@"):
            in_tag = True
            continue
        if in_tag and (raw.startswith(" ") or raw.startswith("\t") or not stripped):
            continue
        prose.append(stripped)
    text = re.sub(r"\s+", " ", " ".join(prose)).strip()
    # A sentence ends at a full stop that is followed by a space and a capital letter or a
    # bracket; "e.g. " and "1.5 dp" are not ends.
    cut = re.search(r"(?<=[a-z0-9)\]`'\"])\.\s+(?=[A-Z\[`(])", text)
    sentence = text[: cut.start() + 1] if cut else text
    return sentence.rstrip(":")


def find_class(name, directory):
    """Returns (file lines, index of the declaration, fully qualified name) or None."""
    folder = os.path.join(SOURCES, directory)
    for file_name in sorted(os.listdir(folder)):
        if not file_name.endswith(".kt"):
            continue
        lines = read(os.path.join(folder, file_name)).split("\n")
        for index, line in enumerate(lines):
            match = DECLARATION.match(line)
            if match and match.group(2) == name:
                return lines, index, "io.github.avalonskyafar.mallet.%s.%s" % (directory, name)
    return None


def members(lines, start):
    """Public declarations nested in the type declared at [start]: (depth, signature, summary)."""
    out = []
    depth = 0
    opened = False
    index = start
    while index < len(lines):
        raw = lines[index]
        line = raw.strip()
        if index > start and opened and line.startswith("public ") and not line.startswith("public constructor"):
            signature = line
            balance = signature.count("(") - signature.count(")")
            follow = index
            while balance > 0 and follow + 1 < len(lines):
                follow += 1
                more = lines[follow].strip()
                signature += " " + more
                balance += more.count("(") - more.count(")")
            signature = re.sub(r"\s*\{\s*$", "", signature)
            signature = re.sub(r"\s*=\s*[^=>].*$", "", signature) if re.match(r"public (va[lr]|const|fun [^(]*\([^)]*\)[^{=]*=)", signature) else signature
            signature = re.sub(r"\s+", " ", signature).replace("public ", "", 1)
            signature = signature.replace("( ", "(").replace(", )", ")")
            if signature != "companion object":
                out.append((depth, signature, first_sentence(kdoc_before(lines, index))))
        # Braces outside strings and comments are good enough for these sources.
        code = re.sub(r'"(?:\\.|[^"\\])*"', '""', line.split("//")[0])
        depth += code.count("{") - code.count("}")
        if "{" in code:
            opened = True
        if opened and depth <= 0:
            break
        index += 1
    return out


def link_names(text):
    """KDoc references such as [setChecked] become code spans; Markdown links stay."""
    return re.sub(r"\[([A-Za-z_][\w.]*)\](?!\()", r"`\1`", text)


def split_kdoc(doc):
    """Returns (prose lines, [(language, code lines)]). Code blocks are taken out of the prose."""
    prose, blocks = [], []
    current = None
    for line in doc:
        fence = re.match(r"^```(\w*)\s*$", line.strip())
        if fence and current is None:
            current = (fence.group(1) or "text", [])
            continue
        if fence and current is not None:
            blocks.append(current)
            current = None
            continue
        if current is not None:
            current[1].append(line)
        elif not line.strip().startswith("@"):
            prose.append(link_names(line))
    # Collapse the blank lines the removed blocks leave behind.
    cleaned = []
    for line in prose:
        if not line.strip() and (not cleaned or not cleaned[-1].strip()):
            continue
        cleaned.append(line)
    while cleaned and not cleaned[-1].strip():
        cleaned.pop()
    return cleaned, blocks


# ---------------------------------------------------------------------------------------------
# Attributes
# ---------------------------------------------------------------------------------------------

TOKEN = re.compile(
    r"<!--(?P<comment>.*?)-->"
    r"|<declare-styleable\s+name=\"(?P<styleable>\w+)\"\s*>"
    r"|(?P<end_styleable></declare-styleable>)"
    r"|<attr\s+name=\"(?P<attr>[\w:]+)\"(?:\s+format=\"(?P<format>[\w|]+)\")?\s*(?P<empty>/)?>"
    r"|<(?:enum|flag)\s+name=\"(?P<enum>\w+)\"[^>]*/>"
    r"|(?P<end_attr></attr>)",
    re.S,
)


def load_attributes():
    """Returns (styleables, shared): styleable name -> [attr]; attr name -> attr declared outside a styleable."""
    styleables, shared = {}, {}
    for file_name in sorted(os.listdir(ATTRS)):
        if not file_name.startswith("mallet_attrs"):
            continue
        text = read(os.path.join(ATTRS, file_name))
        current, comment, open_attr = None, None, None
        for match in TOKEN.finditer(text):
            if match.group("comment") is not None:
                comment = re.sub(r"\s+", " ", match.group("comment")).strip()
            elif match.group("styleable"):
                current = match.group("styleable")
                styleables.setdefault(current, [])
                comment = None
            elif match.group("end_styleable"):
                current, comment = None, None
            elif match.group("attr"):
                attr = {"name": match.group("attr"), "format": match.group("format"), "values": [], "comment": comment}
                comment = None
                if current is None:
                    shared[attr["name"]] = attr
                else:
                    styleables[current].append(attr)
                open_attr = None if match.group("empty") else attr
            elif match.group("enum") and open_attr is not None:
                open_attr["values"].append(match.group("enum"))
            elif match.group("end_attr"):
                open_attr = None
    return styleables, shared


PLATFORM = {
    "android:text": ("string", "The text."),
    "android:enabled": ("boolean", "Whether the component can be operated. Disabled, it pales as a whole."),
    "android:hint": ("string", "The hint shown while the field is empty."),
    "android:inputType": ("input type", "The kind of text the keyboard is set up for."),
    "android:maxLength": ("integer", "Hard limit of characters."),
    "android:minLines": ("integer", "Lines the field is at least high for."),
    "android:maxLines": ("integer", "Lines after which the text scrolls."),
    "android:src": ("drawable", "The picture."),
    "android:scaleType": ("scale type", "How the picture is fitted."),
    "android:orientation": ("horizontal / vertical", "The direction children are laid out in."),
    "android:gravity": ("gravity", "How the content is placed."),
    "android:focusable": ("boolean", "Whether the component takes keyboard focus. True unless the layout says otherwise."),
    "android:clickable": ("boolean", "Whether the component answers taps and keys. True unless the layout says otherwise."),
}


def describe(attr, shared):
    base = shared.get(attr["name"], {})
    fmt = attr["format"] or base.get("format")
    values = attr["values"] or base.get("values") or []
    comment = attr["comment"] or base.get("comment")
    if attr["name"].startswith("android:"):
        platform = PLATFORM.get(attr["name"], ("", ""))
        kind = platform[0]
        comment = comment or platform[1]
    elif values:
        listed = values
        # An icon attribute lists its glyphs plus `none`; the names, not the entries, are what
        # the docs count, so say which is which instead of churning out one more than the icons.
        if "none" in values and len(values) > 8:
            names = [value for value in values if value != "none"]
            kind = "one of %d names, or `none` (see below)" % len(names)
        else:
            kind = " / ".join("`%s`" % value for value in values) if len(listed) <= 8 else "one of %d names (see below)" % len(listed)
    else:
        kind = (fmt or "").replace("|", " or ")
    prefix = "" if attr["name"].startswith("android:") else "app:"
    return "`%s%s`" % (prefix, attr["name"]), kind, comment or ""


# ---------------------------------------------------------------------------------------------
# Provenance
# ---------------------------------------------------------------------------------------------

def load_marks():
    """Component name -> (mark, origin) from the provenance tables of DESIGN_DECISIONS.md."""
    marks = {}
    if not os.path.exists(DECISIONS):
        return marks
    for line in read(DECISIONS).split("\n"):
        cells = [cell.strip() for cell in line.split("|")]
        if len(cells) < 6 or cells[2] not in ("Extracted", "Extended", "New"):
            continue
        for name in re.findall(r"`(Mallet\w+)`", cells[1]):
            marks[name] = (cells[2], cells[3])
    return marks


# ---------------------------------------------------------------------------------------------
# Writing
# ---------------------------------------------------------------------------------------------

def anchor(name):
    return name.lower()


def cell(text):
    return text.replace("|", "\\|").replace("\n", " ")


def render():
    styleables, shared = load_attributes()
    marks = load_marks()
    shown = {}
    out = []
    out.append("# Component catalog")
    out.append("")
    out.append("Every component of Mallet UI: what it is for, how it behaves, how it is declared in XML and")
    out.append("in code, and which of its parts can be set. The design rules behind the components are in")
    out.append("[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md), their movement in [INTERACTION_GUIDE.md](INTERACTION_GUIDE.md).")
    out.append("")
    out.append("> This file is generated from the sources by `tools/catalog.py`. Texts are the KDoc of the")
    out.append("> classes, attribute tables are read from the resource files, and the pictures are drawn by")
    out.append("> the unit tests. Do not edit it by hand.")
    out.append("")
    out.append("Each component carries a mark that says where it comes from; the evidence is in")
    out.append("[DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).")
    out.append("")
    out.append("| Mark | Meaning |")
    out.append("|---|---|")
    out.append("| **Extracted** | Taken from the original application: its geometry, colours and behaviour, rewritten as a reusable component |")
    out.append("| **Extended** | An original idea generalised or completed |")
    out.append("| **New** | Designed for Mallet in the same language |")
    out.append("")
    out.append("## Contents")
    out.append("")
    missing = []
    for title, _, components in GROUPS:
        names = ", ".join("[%s](#%s)" % (name.replace("Mallet", ""), anchor(name)) for name, _, _, _ in components)
        out.append("- **%s**: %s" % (title, names))
    out.append("")
    count = 0
    for title, intro, components in GROUPS:
        out.append("---")
        out.append("")
        out.append("## %s" % title)
        out.append("")
        out.append(intro)
        out.append("")
        for name, directory, picture, page in components:
            found = find_class(name, directory)
            if found is None:
                missing.append(name)
                continue
            count += 1
            lines, index, qualified = found
            prose, blocks = split_kdoc(kdoc_before(lines, index))
            mark, origin = marks.get(name, ("", ""))
            out.append("### %s" % name)
            out.append("")
            facts = []
            if mark:
                facts.append("**%s**" % mark)
            facts.append("`%s`" % qualified)
            facts.append("Showcase: %s" % page)
            out.append(" · ".join(facts))
            out.append("")
            out.extend(prose)
            out.append("")
            if picture:
                light = "%s/%s_light.png" % (PICTURES, picture)
                dark = "%s/%s_dark.png" % (PICTURES, picture)
                if picture in shown:
                    out.append("Pictures: see [%s](#%s)." % (shown[picture], anchor(shown[picture])))
                    out.append("")
                elif os.path.exists(light):
                    shown[picture] = name
                    relative = "images/components/%s" % picture
                    tags = '<img src="%s_light.png" width="320" alt="%s, light theme">' % (relative, name)
                    if os.path.exists(dark):
                        tags += ' <img src="%s_dark.png" width="320" alt="%s, dark theme">' % (relative, name)
                    out.append(tags)
                    out.append("")
            for language, code in blocks:
                out.append("```%s" % language)
                out.extend(code)
                out.append("```")
                out.append("")
            attributes = styleables.get(name, [])
            if attributes:
                out.append("| XML attribute | Value | Meaning |")
                out.append("|---|---|---|")
                for attr in attributes:
                    label, kind, comment = describe(attr, shared)
                    out.append("| %s | %s | %s |" % (label, cell(kind), cell(comment)))
                out.append("")
            listed = members(lines, index)
            if listed:
                out.append("| Member | What it does |")
                out.append("|---|---|")
                base = min(depth for depth, _, _ in listed)
                for depth, signature, summary in listed:
                    mark_nested = "↳ " if depth > base else ""
                    out.append("| %s`%s` | %s |" % (mark_nested, cell(signature[:150]), cell(link_names(summary))))
                out.append("")
    icon_values = shared.get("malletIcon", {}).get("values", [])
    if icon_values:
        out.append("---")
        out.append("")
        out.append("## Icon names")
        out.append("")
        out.append("Values of `app:malletIcon`, which are the constants of `MalletIcon` in lower case:")
        out.append("")
        out.append(", ".join("`%s`" % value for value in icon_values) + ".")
        out.append("")
    return "\n".join(out), count, missing


def main():
    text, count, missing = render()
    if missing:
        print("not found in the sources:", ", ".join(missing))
    if "--check" in sys.argv:
        current = read(TARGET) if os.path.exists(TARGET) else ""
        if current != text:
            print(TARGET, "is out of date: run python tools/catalog.py")
            sys.exit(1)
        print(TARGET, "is up to date")
        return
    with io.open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("wrote %s: %d components, %d lines" % (TARGET, count, text.count("\n") + 1))


if __name__ == "__main__":
    main()
