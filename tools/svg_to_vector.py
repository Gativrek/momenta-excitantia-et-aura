"""Convert the line-icon SVGs in design/icons/ into Android VectorDrawables.

SVGs are the source of truth for every graphic in the app; the XML under
app/src/main/res/drawable/ is generated. After editing an SVG, run:

    python tools/svg_to_vector.py

Supported SVG subset (all our icons use it): a root <svg> with a viewBox and
stroke attributes, containing only <path d="..."> elements. Icons are line-only
(fill="none"), so strokes are emitted with no fill.

Outputs:
  drawable/ic_<name>.xml   24dp, black stroke (Compose's Icon tints it)
                           e.g. nav_songs.svg -> ic_nav_songs.xml
  drawable/ic_launcher_foreground.xml   from design/logo/logo.svg, in its colors
  drawable/ic_launcher_monochrome.xml   the same shapes in one color (themed icons)
  ui/theme/LogoPaths.kt                 the logo's path data, so the app can draw
                                        the logo in the current theme's colors

The logo SVG is 108x108 (the adaptive-icon canvas, content inside the 66dp safe
zone) and each <path> carries its own stroke or fill color.
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "design" / "icons"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"
SVG_NS = "{http://www.w3.org/2000/svg}"

LOGO = ROOT / "design" / "logo" / "logo.svg"
LOGO_KOTLIN = ROOT / "app" / "src" / "main" / "java" / "io" / "github" / "gativrek" / "momenta" / "ui" / "theme" / "LogoPaths.kt"


def read_svg(path: Path):
    root = ET.parse(path).getroot()
    _, _, w, h = (float(v) for v in root.attrib["viewBox"].split())
    stroke = {
        "width": root.attrib.get("stroke-width", "2"),
        "cap": root.attrib.get("stroke-linecap", "round"),
        "join": root.attrib.get("stroke-linejoin", "round"),
    }
    paths = [el.attrib["d"] for el in root.iter(f"{SVG_NS}path")]
    other = [el.tag for el in root if el.tag != f"{SVG_NS}path"]
    if other:
        sys.exit(f"{path.name}: only <path> elements are supported, found {other}")
    return w, h, stroke, paths


def path_xml(d: str, stroke: dict, color: str, indent: str) -> str:
    return (
        f"{indent}<path\n"
        f'{indent}    android:pathData="{d}"\n'
        f'{indent}    android:strokeColor="{color}"\n'
        f'{indent}    android:strokeWidth="{stroke["width"]}"\n'
        f'{indent}    android:strokeLineCap="{stroke["cap"]}"\n'
        f'{indent}    android:strokeLineJoin="{stroke["join"]}" />\n'
    )


def header(name: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generated from design/icons/{name}.svg by tools/svg_to_vector.py. Edit the SVG, not this file. -->\n"
    )


def nav_icon(name: str) -> str:
    w, h, stroke, paths = read_svg(SRC / f"{name}.svg")
    body = "".join(path_xml(d, stroke, "#FF000000", "    ") for d in paths)
    return (
        header(name)
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + f'    android:width="{w:g}dp"\n    android:height="{h:g}dp"\n'
        + f'    android:viewportWidth="{w:g}"\n    android:viewportHeight="{h:g}">\n'
        + body
        + "</vector>\n"
    )


def read_logo():
    """(cap, join, [(d, stroke, fill, stroke_width)]) with paths in document order."""
    root = ET.parse(LOGO).getroot()
    cap = root.attrib.get("stroke-linecap", "round")
    join = root.attrib.get("stroke-linejoin", "miter")
    parts = []
    for el in root.iter(f"{SVG_NS}path"):
        stroke = el.attrib.get("stroke")
        fill = el.attrib.get("fill")
        parts.append((el.attrib["d"], stroke, fill, el.attrib.get("stroke-width", "2")))
    return cap, join, parts


def android_color(svg_color: str) -> str:
    return "#FF" + svg_color.lstrip("#").upper()


def logo_vector(monochrome: bool) -> str:
    cap, join, parts = read_logo()
    body = ""
    for d, stroke, fill, width in parts:
        body += f'    <path\n        android:pathData="{d}"\n'
        if fill:
            body += f'        android:fillColor="{"#FF000000" if monochrome else android_color(fill)}"'
        else:
            body += (
                f'        android:strokeColor="{"#FF000000" if monochrome else android_color(stroke)}"\n'
                f'        android:strokeWidth="{width}"\n'
                f'        android:strokeLineCap="{cap}"\n'
                f'        android:strokeLineJoin="{join}"'
            )
        body += " />\n"
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated from design/logo/logo.svg by tools/svg_to_vector.py. Edit the SVG, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + body
        + "</vector>\n"
    )


def logo_kotlin() -> str:
    _, _, parts = read_logo()
    halo, mark, spark = parts
    return (
        "// Generated from design/logo/logo.svg by tools/svg_to_vector.py. Edit the SVG, not this file.\n"
        "package io.github.gativrek.momenta.ui.theme\n\n"
        "/** The logo's shapes on its 108x108 canvas: the halo (accent), the M (ink) and the spark (accent). */\n"
        "internal object LogoPaths {\n"
        "    const val VIEWPORT = 108f\n"
        f'    const val HALO = "{halo[0]}"\n'
        f"    const val HALO_WIDTH = {float(halo[3])}f\n"
        f'    const val MARK = "{mark[0]}"\n'
        f"    const val MARK_WIDTH = {float(mark[3])}f\n"
        f'    const val SPARK = "{spark[0]}"\n'
        "}\n"
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for svg in sorted(SRC.glob("*.svg")):
        target = OUT / f"ic_{svg.stem}.xml"
        target.write_text(nav_icon(svg.stem), encoding="utf-8", newline="\n")
        print(f"wrote {target.relative_to(ROOT)}")
    outputs = {
        OUT / "ic_launcher_foreground.xml": logo_vector(monochrome=False),
        OUT / "ic_launcher_monochrome.xml": logo_vector(monochrome=True),
        LOGO_KOTLIN: logo_kotlin(),
    }
    for target, text in outputs.items():
        target.write_text(text, encoding="utf-8", newline="\n")
        print(f"wrote {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
