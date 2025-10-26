import os
os.environ["TOKENIZERS_PARALLELISM"] = "false"

import asyncio
import uuid
import anyio
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse, FileResponse
from pathlib import Path

from app.pipeline import run_full_pipeline
from app.worker import submit_job, jobs, worker
from app.config import settings
from app.schemas import JobStatus, HealthResponse

# -----------------------------------------------------------
# Конфигурация
# -----------------------------------------------------------
app = FastAPI(title="Note2Tex Inference API", version="1.1.0")

BASE_DIR = Path(__file__).resolve().parent.parent
TEMP_DIR = BASE_DIR / "temp"
FILES_DIR = TEMP_DIR / "files"
FILES_DIR.mkdir(parents=True, exist_ok=True)
RESULTS_URL_BASE = "http://192.168.50.68:8003/files"


# -----------------------------------------------------------
# Фоновые задачи
# -----------------------------------------------------------
@app.on_event("startup")
async def _startup():
    app.state.worker_task = asyncio.create_task(worker())

@app.on_event("shutdown")
async def _shutdown():
    task = getattr(app.state, "worker_task", None)
    if task:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass


# -----------------------------------------------------------
# Новый эндпоинт: принимает файл напрямую
# -----------------------------------------------------------
@app.post("/v1/infer")
async def infer(
    image: UploadFile = File(...),
    det_conf: float = Form(0.25),
    det_iou: float = Form(0.5),
    det_imgsz: int = Form(1280),
    det_pad: float = Form(0.04),
    beams: int = Form(4),
    max_new_tokens: int = Form(224),
    length_penalty: float = Form(1.1),
    bin_strength: float = Form(0.85),
):
    try:
        # === 1. сохраняем входной файл ===
        inbox = TEMP_DIR / "inbox"
        inbox.mkdir(parents=True, exist_ok=True)
        fn = f"upload_{uuid.uuid4().hex}.png"
        path_img = inbox / fn
        path_img.write_bytes(await image.read())

        # === 2. параметры пайплайна ===
        params = dict(
            image_path=str(path_img),
            detector_weights=str(BASE_DIR / "models/detector/best.pt"),
            trocr_dir=str(BASE_DIR / "models/trocr_latex_fast"),
            det_conf=det_conf,
            det_iou=det_iou,
            det_imgsz=det_imgsz,
            det_pad=det_pad,
            beams=beams,
            max_new_tokens=max_new_tokens,
            length_penalty=length_penalty,
            bin_strength=bin_strength,
            temp_dir=str(TEMP_DIR),
            make_tex=True,
            make_csv=True,
            make_pdf=True,
        )

        # === 3. запускаем инференс ===
        result = await anyio.to_thread.run_sync(lambda: run_full_pipeline(**params))

        # === 4. переносим файлы в temp/files и формируем ссылки ===
        download_links = {}
        for key in ("tex_path", "pdf_path", "csv_path"):
            p = result.get(key)
            if p and os.path.exists(p):
                dst = FILES_DIR / f"{uuid.uuid4().hex}_{Path(p).name}"
                os.replace(p, dst)
                download_links[key.replace("_path", "_url")] = f"{RESULTS_URL_BASE}/{dst.name}"
            else:
                download_links[key.replace("_path", "_url")] = None

        # === 5. финальный ответ ===
        return JSONResponse({
            "latex": result.get("latex", ""),
            "time_ms": result.get("time_ms", 0),
            **download_links
        })

    except Exception as e:
        return JSONResponse({"error": str(e)}, status_code=500)


# -----------------------------------------------------------
# Очередь (enqueue) — оставить совместимым
# -----------------------------------------------------------
@app.post("/v1/enqueue", response_model=JobStatus, status_code=202)
async def enqueue(image: UploadFile = File(...)):
    inbox = TEMP_DIR / "inbox"
    inbox.mkdir(parents=True, exist_ok=True)
    fn = f"queued_{uuid.uuid4().hex}.png"
    path_img = inbox / fn
    path_img.write_bytes(await image.read())

    params = dict(
        image_path=str(path_img),
        detector_weights=str(BASE_DIR / "models/detector/best.pt"),
        trocr_dir=str(BASE_DIR / "models/trocr_latex_fast"),
        det_conf=settings.DET_CONF,
        det_iou=settings.DET_IOU,
        det_imgsz=settings.DET_IMGSZ,
        det_pad=settings.DET_PAD,
        beams=settings.BEAMS,
        max_new_tokens=settings.MAX_NEW_TOKENS,
        length_penalty=settings.LENGTH_PENALTY,
        bin_strength=settings.BIN_STRENGTH,
        temp_dir=settings.TEMP_DIR,
        make_tex=True,
        make_csv=True,
        make_pdf=True,
    )

    job_id = await submit_job({"job_id": str(uuid.uuid4()), "params": params})
    return jobs[job_id]


@app.get("/v1/jobs/{job_id}", response_model=JobStatus)
async def job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(404, "job not found")
    return jobs[job_id]


# -----------------------------------------------------------
# Отдача файлов
# -----------------------------------------------------------
@app.get("/files/{filename}")
async def get_file(filename: str):
    path = FILES_DIR / filename
    if not path.exists():
        return JSONResponse({"error": "file not found"}, status_code=404)
    return FileResponse(path, filename=path.name)


# -----------------------------------------------------------
# Healthcheck
# -----------------------------------------------------------
@app.post("/v1/health", response_model=HealthResponse)
async def health():
    import torch
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    return HealthResponse(
        ok=True,
        device=dev,
        trocr_dir=settings.TROCR_DIR,
        detector_weights=settings.DETECTOR_WEIGHTS,
    )
