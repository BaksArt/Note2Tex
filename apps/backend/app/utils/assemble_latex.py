from pathlib import Path
from typing import List, Tuple, Dict, Any
import statistics

HEADER = r"""\documentclass[12pt]{article}
\usepackage[T1]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage{lmodern}
\usepackage{amsmath, amssymb}
\usepackage[margin=1in]{geometry}
\usepackage[russian]{babel}
\title{%TITLE%}
\date{}
\begin{document}
\setlength{\abovedisplayskip}{8pt}
\setlength{\belowdisplayskip}{8pt}
"""
FOOTER = r"""
\end{document}
"""

def _wrap_display(formula: str) -> str:
    f = (formula or "").strip()
    if not f: return ""
    return f"\\[\n{f}\n\\]\n"

def _wrap_inline(formula: str) -> str:
    f = (formula or "").strip()
    if not f: return ""
    return f"\\({f}\\)"

def latex_escape_text(s: str) -> str:
    repl = {
        '\\':'\\textbackslash{}', '&':'\\&', '%':'\\%', '$':'\\$',
        '#':'\\#', '_':'\\_', '{':'\\{', '}':'\\}', '~':'\\textasciitilde{}',
        '^':'\\textasciicircum{}',
    }
    return "".join(repl.get(ch, ch) for ch in s)


def _height(b: Dict[str, Any]) -> float:
    x1, y1, x2, y2 = b["bbox"]
    return max(1.0, float(y2 - y1))

def _y_center(b: Dict[str, Any]) -> float:
    x1, y1, x2, y2 = b["bbox"]
    return (float(y1) + float(y2)) / 2.0

def _x_left(b: Dict[str, Any]) -> float:
    x1, _, _, _ = b["bbox"]
    return float(x1)

def _x_right(b: Dict[str, Any]) -> float:
    _, _, x2, _ = b["bbox"]
    return float(x2)

def _vertical_overlap(a: Dict[str, Any], b: Dict[str, Any]) -> float:
    ax1, ay1, ax2, ay2 = a["bbox"]
    bx1, by1, bx2, by2 = b["bbox"]
    top = max(ay1, by1)
    bottom = min(ay2, by2)
    inter = max(0.0, float(bottom - top))
    ha = max(1.0, float(ay2 - ay1))
    hb = max(1.0, float(by2 - by1))
    return inter / min(ha, hb)

def _cluster_into_lines(blocks: List[Dict[str, Any]]) -> List[List[Dict[str, Any]]]:
    bs = [b for b in blocks if isinstance(b.get("content", ""), str) and b["content"].strip() != ""]
    if not bs:
        return []

    bs_sorted = sorted(bs, key=lambda b: (b["bbox"][1], b["bbox"][0]))

    heights = [ _height(b) for b in bs_sorted ]
    med_h = statistics.median(heights) if heights else 20.0
    y_tol = max(10.0, 0.6 * med_h)
    if statistics.pstdev(heights) > 0.8 * (med_h + 1e-6):
        y_tol *= 1.25

    lines: List[List[Dict[str, Any]]] = []

    for b in bs_sorted:
        cy = _y_center(b)
        placed = False
        best_line_idx = -1
        best_score = -1.0

        for i, line in enumerate(lines):
            line_cy = statistics.median([_y_center(x) for x in line])
            dy = abs(cy - line_cy)
            ovl = max(_vertical_overlap(b, x) for x in line)
            score = (max(0.0, y_tol - dy) / y_tol) + 0.5 * ovl
            if dy <= y_tol or ovl >= 0.5:
                if score > best_score:
                    best_score = score
                    best_line_idx = i

        if best_line_idx >= 0:
            lines[best_line_idx].append(b)
            placed = True

        if not placed:
            lines.append([b])

    for line in lines:
        line.sort(key=_x_left)

    lines.sort(key=lambda ln: min(b["bbox"][1] for b in ln))
    return lines

def _render_line_to_latex(line: List[Dict[str, Any]]) -> str:
    if not line:
        return ""

    only_one = (len(line) == 1)
    if only_one and line[0]["kind"] == "formula":
        return _wrap_display(line[0]["content"])

    parts = []
    for i, b in enumerate(line):
        kind = b["kind"]
        if kind == "formula":
            parts.append(_wrap_inline(b["content"]))
        else:
            parts.append(latex_escape_text(b["content"]))

    out = []
    for i, part in enumerate(parts):
        out.append(part)
        if i + 1 < len(parts):
            left_kind = line[i]["kind"]
            right_kind = line[i+1]["kind"]
            if left_kind == "formula" and right_kind == "formula":
                out.append(r"\, ")
            else:
                out.append(" ")

    return "".join(out) + "\n"

def build_mixed_document(
    blocks: List[Tuple[int, str, str, float, float, float, float]],
    title: str
) -> str:
    norm_blocks: List[Dict[str, Any]] = []
    for tup in blocks:
        if len(tup) != 7:
            continue
        idx, kind, content, x1, y1, x2, y2 = tup
        norm_blocks.append({
            "idx": idx,
            "kind": kind,
            "content": content if isinstance(content, str) else "",
            "bbox": (float(x1), float(y1), float(x2), float(y2)),
        })

    lines = _cluster_into_lines(norm_blocks)

    body_lines: List[str] = []
    if not lines:
        body_lines.append("\\textit{Нет блоков.}\n")
    else:
        for i, line in enumerate(lines):
            cmts = " ".join([
                f"(#{b['idx']} {b['kind']} x={int(b['bbox'][0])}..{int(b['bbox'][2])} y={int(b['bbox'][1])}..{int(b['bbox'][3])})"
                for b in line
            ])
            body_lines.append(f"% {cmts}\n")
            body_lines.append(_render_line_to_latex(line))
            body_lines.append("\n")

    return HEADER.replace("%TITLE%", title) + "".join(body_lines) + FOOTER

def write_mixed_latex_file(
    items: List[Tuple[int, str, str, float, float, float, float]],
    out_path: str,
    title: str = ""
) -> str:
    out = Path(out_path); out.parent.mkdir(parents=True, exist_ok=True)
    tex = build_mixed_document(items, title=title)
    out.write_text(tex, encoding="utf-8")
    return str(out.resolve())
