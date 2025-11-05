import asyncio, os, uuid
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import AsyncSessionLocal
from app.models import Project, ProjectStatus
from app.config import settings
from app.storage import upload_file, make_download_url, delete_objects, fetch_to_path
from app.pipeline import run_full_pipeline
from app.utils.latex_to_pdf import compile_tex_file_to_pdf
import re
from app.utils.assemble_latex import HEADER, FOOTER

queue: "asyncio.Queue[dict]" = asyncio.Queue()

async def submit_infer_job(project_id):
    await queue.put({"kind": "infer", "project_id": project_id})

async def submit_texjob(project_id, tex_content: str):
    await queue.put({"kind": "tex", "project_id": project_id, "tex": tex_content})

async def worker():
    while True:
        job = await queue.get()
        try:
            if job["kind"] == "infer":
                await _do_infer(job["project_id"])
            elif job["kind"] == "tex":
                await _do_build_tex(job["project_id"], job["tex"])
        except Exception as e:
            try:
                async with AsyncSessionLocal() as session:
                    p = await _load_proj(session, job.get("project_id"))
                    if p:
                        p.status = ProjectStatus.failed
                        await session.commit()
            except Exception:
                pass
            print(f"[worker] job failed: {e}")
        finally:
            queue.task_done()

async def _do_infer(project_id):
    async with AsyncSessionLocal() as session:
        p = await _load_proj(session, project_id)
        if not p or not p.image_key:
            return
        workdir = os.path.join(settings.TEMP_DIR, "work", uuid.uuid4().hex)
        os.makedirs(workdir, exist_ok=True)
        local_image = os.path.join(workdir, "input.png")
        try:
            await asyncio.to_thread(fetch_to_path, p.image_key, local_image)
        except Exception as e:
            print(f"[worker] failed to fetch image '{p.image_key}': {e}")
            p.status = ProjectStatus.failed
            await session.commit()
            return

        params = dict(
            image_path=local_image,
            detector_weights=settings.DETECTOR_WEIGHTS,
            words_ocr_weights=settings.WORDS_OCR_WEIGHTS,
            trocr_dir=settings.TROCR_DIR,
            det_conf=settings.DET_CONF,
            det_iou=settings.DET_IOU,
            det_imgsz=settings.DET_IMGSZ,
            det_pad=settings.DET_PAD,
            beams=settings.BEAMS,
            max_new_tokens=settings.MAX_NEW_TOKENS,
            length_penalty=settings.LENGTH_PENALTY,
            bin_strength=settings.BIN_STRENGTH,
            erode_kernel=settings.ERODE_KERNEL,
            temp_dir=workdir,
            make_tex=True,
            make_csv=False,
            make_pdf=True,
        )

        try:
            result = await asyncio.to_thread(run_full_pipeline, **params)
            tex_path = result.get("tex_path"); pdf_path = result.get("pdf_path")
            docx_path = await asyncio.to_thread(_maybe_make_docx, tex_path)
            if tex_path: upload_file(tex_path, f"users/{p.user_id}/projects/{p.id}/formulas.tex"); p.tex_key = f"users/{p.user_id}/projects/{p.id}/formulas.tex"
            if pdf_path: upload_file(pdf_path, f"users/{p.user_id}/projects/{p.id}/formulas.pdf"); p.pdf_key = f"users/{p.user_id}/projects/{p.id}/formulas.pdf"
            if docx_path: upload_file(docx_path, f"users/{p.user_id}/projects/{p.id}/formulas.docx"); p.docx_key = f"users/{p.user_id}/projects/{p.id}/formulas.docx"
            p.status = ProjectStatus.ready
        except Exception as e:
            print(e)
            p.status = ProjectStatus.failed
        await session.commit()

async def _do_build_tex(project_id, tex_content: str):
    async with AsyncSessionLocal() as session:
        p = await _load_proj(session, project_id)
        if not p:
            return
        tex_content = _wrap_tex_if_needed(tex_content)

        workdir = os.path.join(settings.TEMP_DIR, "work", uuid.uuid4().hex)
        os.makedirs(workdir, exist_ok=True)
        tex_path = os.path.join(workdir, "patched.tex")

        with open(tex_path, "w", encoding="utf-8") as f:
            f.write(tex_content)

        pdf_path = None
        docx_path = None
        try:
            pdf_path = compile_tex_file_to_pdf(tex_path, engine="pdflatex", timeout=240)
        except Exception as e:
            print(f"[worker] pdf compile failed (patch): {e}")
            pdf_path = None

        try:
            docx_path = _maybe_make_docx(tex_path)
        except Exception as e:
            print(f"[worker] docx make failed (patch): {e}")
            docx_path = None

        try:
            upload_file(tex_path, f"users/{p.user_id}/projects/{p.id}/formulas.tex")
            p.tex_key = f"users/{p.user_id}/projects/{p.id}/formulas.tex"

            if pdf_path:
                upload_file(pdf_path, f"users/{p.user_id}/projects/{p.id}/formulas.pdf")
                p.pdf_key = f"users/{p.user_id}/projects/{p.id}/formulas.pdf"
            else:
                p.pdf_key = None

            if docx_path:
                upload_file(docx_path, f"users/{p.user_id}/projects/{p.id}/formulas.docx")
                p.docx_key = f"users/{p.user_id}/projects/{p.id}/formulas.docx"
            else:
                p.docx_key = None

            p.status = ProjectStatus.ready if p.tex_key else ProjectStatus.failed
        except Exception as e:
            print(f"[worker] upload or status update failed (patch): {e}")
            p.status = ProjectStatus.failed

        await session.commit()


async def _load_proj(session: AsyncSession, pid):
    res = await session.execute(select(Project).where(Project.id==pid))
    return res.unique().scalar_one_or_none()

def _maybe_make_docx(tex_path: str) -> str | None:
    tex_path = str(Path(tex_path).resolve())
    out = tex_path.replace(".tex", ".docx")

    pandoc_exe = shutil.which("pandoc")
    if not pandoc_exe and sys.platform.startswith("win"):
        candidates = [
            r"C:\Program Files\Pandoc\pandoc.exe",
            r"C:\Program Files (x86)\Pandoc\pandoc.exe",
        ]
        for c in candidates:
            if os.path.exists(c):
                pandoc_exe = c
                break

    if pandoc_exe:
        try:
            print(f"[worker] using pandoc: {pandoc_exe}")
            subprocess.run(
                [pandoc_exe, tex_path, "-o", out],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            if os.path.exists(out):
                return out
        except subprocess.CalledProcessError as e:
            print(f"[worker] pandoc failed:\n{e.stderr}")
        except Exception as e:
            print(f"[worker] pandoc error: {e}")

    try:
        from docx import Document
        txt = Path(tex_path).read_text(encoding="utf-8", errors="ignore")
        d = Document()
        d.add_paragraph(txt)
        d.save(out)
        return out
    except Exception as e:
        print(f"[worker] fallback docx failed: {e}")
        return None


def _wrap_tex_if_needed(tex_content: str) -> str:
    body = (tex_content or "").strip()
    if not body:
        return HEADER.replace("%TITLE%", "Patched") + "\\textit{Empty}\\n" + FOOTER

    def remove_spaces_in_formulas(match):
        formula_content = match.group(1)
        cleaned_formula = re.sub(r'\s+', ' ', formula_content).strip()
        return f"\\[{cleaned_formula}\\]"

    body = re.sub(r'\\\[\s*(.*?)\s*\\\]', remove_spaces_in_formulas, body, flags=re.DOTALL)

    if re.search(r"\\documentclass\b", body):
        return body

    header = HEADER.replace("%TITLE%", "Patched")
    return header + body + FOOTER