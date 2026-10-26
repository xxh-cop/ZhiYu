"""Outline LXGW WenKai glyphs into the Zhiyu logo SVG assets."""
import glob
import os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

SUBSET_DIR = ".ai-tmp/fonts/subsets"
OUT_DIR = "design/logo"

STUDENT_PRIMARY = "#2f7566"
STUDENT_ACCENT = "#bb914d"
ADMIN_PRIMARY = "#4f46e5"
ADMIN_ACCENT = "#f59e0b"
WHITE = "#ffffff"

NAME = "织愈"
SLOGAN = "用一针一线，缝补青春的焦虑"

GLYPHS = {}
UPEM = 1000


def load_glyphs():
    global UPEM
    for path in sorted(glob.glob(os.path.join(SUBSET_DIR, "*.woff2"))):
        font = TTFont(path)
        UPEM = font["head"].unitsPerEm
        cmap = font.getBestCmap()
        glyph_set = font.getGlyphSet()
        hmtx = font["hmtx"]
        for cp, gname in cmap.items():
            ch = chr(cp)
            if ch in (NAME + SLOGAN) and ch not in GLYPHS:
                pen = SVGPathPen(glyph_set)
                glyph_set[gname].draw(pen)
                bounds = BoundsPen(glyph_set)
                glyph_set[gname].draw(bounds)
                if not pen.getCommands() or not bounds.bounds:
                    continue
                GLYPHS[ch] = {
                    "d": pen.getCommands(),
                    "bbox": bounds.bounds,
                    "adv": hmtx[gname][0],
                }
    missing = [c for c in NAME + SLOGAN if c not in GLYPHS]
    if missing:
        raise SystemExit("missing glyphs: " + "".join(missing))


def glyph_on_baseline(ch, x, baseline, s, fill):
    return (
        f'<path transform="translate({round(x, 2)} {round(baseline, 2)}) '
        f'scale({round(s, 5)} {round(-s, 5)})" d="{GLYPHS[ch]["d"]}" fill="{fill}"/>'
    )


def glyph_centered(ch, cx, cy, height, fill):
    """Size a single glyph by its own bounding box (used for the seal)."""
    x0, y0, x1, y1 = GLYPHS[ch]["bbox"]
    s = height / (y1 - y0)
    return (
        f'<path transform="translate({round(cx, 2)} {round(cy, 2)}) '
        f'scale({round(s, 5)} {round(-s, 5)}) '
        f'translate({round(-(x0 + x1) / 2, 1)} {round(-(y0 + y1) / 2, 1)})" '
        f'd="{GLYPHS[ch]["d"]}" fill="{fill}"/>'
    )


def run(text, font_size, start_x, cy, fill, tracking=0.14):
    """Layout by em size so flat glyphs (一 ,) do not blow up.

    Returns (elements, width, top, bottom).
    """
    s = font_size / UPEM
    extra = font_size * tracking
    x = start_x
    placed = []
    tops, bots = [], []
    for ch in text:
        g = GLYPHS[ch]
        x0, y0, x1, y1 = g["bbox"]
        placed.append((ch, x))
        tops.append(-y1 * s)
        bots.append(-y0 * s)
        x += g["adv"] * s + extra
    width = x - extra - start_x
    baseline = cy - (min(tops) + max(bots)) / 2
    elements = [glyph_on_baseline(ch, px, baseline, s, fill) for ch, px in placed]
    return elements, width, baseline + min(tops), baseline + max(bots)


def thread(x0, x1, y, color, stroke=2.8, dot=3.5):
    end = x1 - dot * 2 - 3
    mid = (x0 + end) / 2
    return (
        f'<path d="M{x0:.1f} {y:.1f} C{mid:.1f} {y - 7:.1f}, {mid:.1f} {y - 7:.1f}, '
        f'{end:.1f} {y:.1f}" fill="none" stroke="{color}" stroke-width="{stroke}" stroke-linecap="round"/>\n'
        f'  <circle cx="{x1 - dot:.1f}" cy="{y - 1:.1f}" r="{dot}" fill="{color}"/>'
    )


def seal_svg(px, primary, glyph_fill, ratio=0.69):
    box = 128
    inset = 6
    side = box - 2 * inset
    rx = round(side * 30 / 116, 2)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box} {box}" '
        f'width="{px}" height="{px}" role="img" aria-label="织愈">\n'
        f'  <rect x="{inset}" y="{inset}" width="{side}" height="{side}" rx="{rx}" fill="{primary}"/>\n'
        f'  {glyph_centered("织", 64, 64, round(side * ratio, 2), glyph_fill)}\n'
        f"</svg>\n"
    )


def horizontal_svg(primary, accent, text_fill):
    seal = 84
    seal_y = 14
    rx = round(seal * 30 / 116, 2)
    name_size = 68
    start_x = seal + 24
    _, width, _, _ = run(NAME, name_size, 0, 0, text_fill)
    cy = seal_y + seal / 2 - 6
    parts, width, top, bottom = run(NAME, name_size, start_x, cy, text_fill)
    box_w = round(start_x + width + 8, 2)
    box_h = round(max(seal_y + seal + 8, bottom + 22), 2)
    thread_y = bottom + 13
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box_w} {box_h}" '
        f'width="{box_w}" height="{box_h}" role="img" aria-label="织愈 Zhiyu">\n'
        f'  <rect x="0" y="{seal_y}" width="{seal}" height="{seal}" rx="{rx}" fill="{primary}"/>\n'
        f'  {glyph_centered("织", seal / 2, seal_y + seal / 2, round((seal - 12) * 0.69, 2), WHITE)}\n'
        + "".join("  " + p + "\n" for p in parts)
        + f'  {thread(start_x, start_x + width, thread_y, accent)}\n'
        f"</svg>\n"
    )


def splash_svg(primary, accent, text_fill, slogan_fill, seal_fill, seal_glyph_fill):
    seal = 96
    seal_y = 24
    rx = round(seal * 30 / 116, 2)
    name_size = 74
    _, name_w, _, _ = run(NAME, name_size, 0, 0, text_fill)
    name_x = (340 - name_w) / 2
    name_cy = seal_y + seal + 52
    parts, name_w, top, bottom = run(NAME, name_size, name_x, name_cy, text_fill)
    thread_y = bottom + 15
    slogan_size = 20
    _, slogan_w, _, slogan_bot = run(SLOGAN, slogan_size, 0, 0, slogan_fill)
    slogan_x = (340 - slogan_w) / 2
    slogan_parts, slogan_w, _, slogan_bot = run(
        SLOGAN, slogan_size, slogan_x, thread_y + 44, slogan_fill, tracking=0.10
    )
    box_h = round(slogan_bot + 26, 2)
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 340 {box_h}" '
        f'width="340" height="{box_h}" role="img" aria-label="织愈 {SLOGAN}">\n'
        f'  <rect x="{round((340 - seal) / 2, 2)}" y="{seal_y}" width="{seal}" height="{seal}" '
        f'rx="{rx}" fill="{seal_fill}"/>\n'
        f'  {glyph_centered("织", 170, seal_y + seal / 2, round((seal - 14) * 0.69, 2), seal_glyph_fill)}\n'
        + "".join("  " + p + "\n" for p in parts)
        + f'  {thread(name_x, name_x + name_w, thread_y, accent, 3, 3.8)}\n'
        + "".join("  " + p + "\n" for p in slogan_parts)
        + f"</svg>\n"
    )


def write(name, content):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(content)
    print("wrote", path, len(content))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    load_glyphs()
    print("glyphs:", len(GLYPHS), "upem:", UPEM)
    write("zhiyu-seal-student.svg", seal_svg(512, STUDENT_PRIMARY, WHITE))
    write("zhiyu-seal-admin.svg", seal_svg(512, ADMIN_PRIMARY, WHITE))
    write("zhiyu-seal-inverse-student.svg", seal_svg(512, WHITE, STUDENT_PRIMARY))
    write("zhiyu-seal-inverse-admin.svg", seal_svg(512, WHITE, ADMIN_PRIMARY))
    write("zhiyu-logo-horizontal-student.svg", horizontal_svg(STUDENT_PRIMARY, STUDENT_ACCENT, STUDENT_PRIMARY))
    write("zhiyu-logo-horizontal-admin.svg", horizontal_svg(ADMIN_PRIMARY, ADMIN_ACCENT, ADMIN_PRIMARY))
    write(
        "zhiyu-logo-splash-student.svg",
        splash_svg(STUDENT_PRIMARY, STUDENT_ACCENT, STUDENT_PRIMARY, "#586e62", STUDENT_PRIMARY, WHITE),
    )
    write(
        "zhiyu-logo-splash-inverse-student.svg",
        splash_svg(WHITE, "#f0c98a", WHITE, "#e8efe4", WHITE, STUDENT_PRIMARY),
    )
    write(
        "zhiyu-logo-splash-admin.svg",
        splash_svg(ADMIN_PRIMARY, ADMIN_ACCENT, ADMIN_PRIMARY, "#6b7280", ADMIN_PRIMARY, WHITE),
    )


if __name__ == "__main__":
    main()
