import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Optional, Tuple

MIKTEX_CANDIDATES = [
    r"C:\Program Files\MiKTeX\miktex\bin\x64",
    r"C:\Program Files\MiKTeX 2.9\miktex\bin\x64",
    r"C:\Users\%USERNAME%\AppData\Local\Programs\MiKTeX\miktex\bin\x64",
]

TEXLIVE_CANDIDATES = [
    r"C:\texlive\2025\bin\win32",
    r"C:\texlive\2024\bin\win32",
    r"C:\texlive\2023\bin\win32",
]

def _extend_path(env: dict) -> dict:
    """Добавляем типичные папки MiKTeX/TeXLive во временный PATH."""
    extra = []
    for p in MIKTEX_CANDIDATES + TEXLIVE_CANDIDATES:
        p = os.path.expandvars(p)
        if os.path.isdir(p):
            extra.append(p)
    if extra:
        env = env.copy()
        env["PATH"] = os.pathsep.join(extra + [env.get("PATH", "")])
    return env

def _which(cmd: str, env: Optional[dict] = None) -> Optional[str]:
    return shutil.which(cmd, path=env.get("PATH") if env else None)

def _run(cmd: list, cwd: Path, env: dict, timeout: int) -> Tuple[int, str, str]:
    p = subprocess.Popen(
        cmd,
        cwd=str(cwd),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=False,
        creationflags=subprocess.CREATE_NO_WINDOW if hasattr(subprocess, "CREATE_NO_WINDOW") else 0,
    )
    out, err = p.communicate(timeout=timeout)
    return p.returncode, out.decode(errors="replace"), err.decode(errors="replace")

def compile_latex_to_pdf(
    latex_source: str,
    workdir: Optional[Path] = None,
    engine: str = "pdflatex",
    jobname: str = "document",
    timeout: int = 180,
    keep_temp: bool = False,
) -> Path:

    base_env = os.environ.copy()
    env = _extend_path(base_env)
    engine = engine.lower().strip()

    tmpdir_cm = None
    if workdir is None:
        tmpdir_cm = tempfile.TemporaryDirectory(prefix="latexbuild_")
        work = Path(tmpdir_cm.name)
    else:
        work = Path(workdir)
        work.mkdir(parents=True, exist_ok=True)

    tex_path = work / f"{jobname}.tex"
    pdf_path = work / f"{jobname}.pdf"
    log_path = work / f"{jobname}.log"

    tex_path.write_text(latex_source, encoding="utf-8", errors="strict")

    latexmk = _which("latexmk", env)
    eng_path = _which(engine, env)
    ok = False
    last_out, last_err = "", ""

    try:
        if latexmk:
            cmd = [
                latexmk,
                "-interaction=nonstopmode",
                f"-pdflatex={engine} -interaction=nonstopmode -halt-on-error -file-line-error",
                "-pdf",
                f"-jobname={jobname}",
                tex_path.name,
            ]
            code, out, err = _run(cmd, cwd=work, env=env, timeout=timeout)
            last_out, last_err = out, err
            ok = (code == 0) and pdf_path.exists()
        else:
            if not eng_path:
                raise RuntimeError(
                    "Не найден latexmk и не найден движок "
                    f"'{engine}' в PATH. Установите MiKTeX/TeX Live и добавьте в PATH."
                )
            cmd = [
                eng_path,
                "-interaction=nonstopmode",
                "-halt-on-error",
                "-file-line-error",
                tex_path.name,
            ]
            for i in range(3):
                code, out, err = _run(cmd, cwd=work, env=env, timeout=timeout)
                last_out, last_err = out, err
                if pdf_path.exists():
                    ok = True
                    break

        if not ok:
            tail = ""
            if log_path.exists():
                try:
                    lines = log_path.read_text(encoding="utf-8", errors="replace").splitlines()
                    tail = "\n".join(lines[-80:])
                except Exception:
                    pass
            raise RuntimeError(
                "Компиляция LaTeX → PDF не удалась.\n\n"
                f"STDOUT:\n{last_out[-2000:]}\n\nSTDERR:\n{last_err[-2000:]}\n\n"
                f"Хвост {log_path.name}:\n{tail}"
            )
        return pdf_path
    finally:

        if tmpdir_cm is not None and not keep_temp:
            tmpdir_cm.cleanup()


if __name__ == "__main__":
    minimal_tex = r"""
\documentclass[12pt]{article}
\usepackage[T2A]{fontenc}
\usepackage[utf8]{inputenc}
\usepackage[russian,english]{babel}
\usepackage{lmodern}
\usepackage{amsmath, amssymb}
\usepackage{geometry}
\geometry{margin=1in}

\begin{document}
\section*{Проверка}
Привет, мир! Формула: \( E = mc^2 \).

\end{document}
""".lstrip()

    try:
        pdf = compile_latex_to_pdf(
            latex_source=minimal_tex,
            engine="pdflatex",
            timeout=240,
            keep_temp=False,
        )
        print(f"✅ PDF готов: {pdf}")
    except Exception as e:
        print(str(e))
