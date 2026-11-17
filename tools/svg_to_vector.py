"""Convert the line-icon SVGs in design/icons/ into Android VectorDrawables.

SVGs are the source of truth for every graphic in the app; the XML under
app/src/main/res/drawable/ is generated. After editing an SVG, run:

    python tools/svg_to_vector.py

Supported SVG subset (all our icons use it): a root <svg> with a viewBox and
stroke attributes, containing only <path d="..."> elements. Icons are line-only
(fill="none"), so strokes are emitted with no fill.

Outputs:
  drawable/ic_nav_<name>.xml   24dp, black stroke (Compose's Icon tints it)
  drawable/ic_launcher_foreground.xml   from songs.svg, centred in the
                                        108dp adaptive-icon canvas
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "design" / "icons"
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"
SVG_NS = "{http://www.w3.org/2000/svg}"

LAUNCHER_SOURCE = "songs"
LAUNCHER_SCALE = 2.0  # 24 -> 48 units, well inside the 66dp safe zone


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


def launcher_foreground() -> str:
    w, _, stroke, paths = read_svg(SRC / f"{LAUNCHER_SOURCE}.svg")
    offset = (108 - w * LAUNCHER_SCALE) / 2
    body = "".join(path_xml(d, stroke, "@color/ic_launcher_foreground", "        ") for d in paths)
    return (
        header(LAUNCHER_SOURCE)
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    android:width="108dp"\n    android:height="108dp"\n'
        + '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + f'    <group\n        android:scaleX="{LAUNCHER_SCALE:g}"\n        android:scaleY="{LAUNCHER_SCALE:g}"\n'
        + f'        android:translateX="{offset:g}"\n        android:translateY="{offset:g}">\n'
        + body
        + "    </group>\n</vector>\n"
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for svg in sorted(SRC.glob("*.svg")):
        target = OUT / f"ic_nav_{svg.stem}.xml"
        target.write_text(nav_icon(svg.stem), encoding="utf-8", newline="\n")
        print(f"wrote {target.relative_to(ROOT)}")
    target = OUT / "ic_launcher_foreground.xml"
    target.write_text(launcher_foreground(), encoding="utf-8", newline="\n")
    print(f"wrote {target.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
