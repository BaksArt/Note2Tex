from pathlib import Path
from typing import List, Tuple

HEADER = r"""\documentclass[12pt]{article}
\usepackage[T1]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage{lmodern}
\usepackage{amsmath, amssymb}
\usepackage[margin=1in]{geometry}
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
    formula = formula.strip()
    if not formula:
        return ""
    return f"\\[\n{formula}\n\\]\n"

def build_latex_document(formulas: List[Tuple[int, str]], title: str) -> str:
    body_parts = []
    for idx, fx in sorted(formulas, key=lambda x: x[0]):
        if not isinstance(fx, str) or not fx.strip():
            continue
        body_parts.append(f"% ({idx})")
        body_parts.append(_wrap_display(fx))
    body = "\n".join(body_parts) if body_parts else "\\textit{Нет формул.}\n"
    return HEADER.replace("%TITLE%", title) + body + FOOTER

def write_latex_file(
    formulas: List[Tuple[int, str]],
    out_path: str,
    title: str = "Recognized Formulas"
) -> str:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    tex = build_latex_document(formulas, title=title)
    out.write_text(tex, encoding="utf-8")
    return str(out.resolve())

if __name__ == "__main__":
    demo = [(1, r"\frac{a+b}{c}"), (2, r"\int_0^1 x^2\,dx")]
    p = write_latex_file(demo, "temp/formulas.tex", title="Demo Formulas")
    print("[assemble] written:", p)
