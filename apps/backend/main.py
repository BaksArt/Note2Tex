import asyncio, os
from fastapi import HTTPException
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.responses import FileResponse

from app.config import settings
from app.routers import api
from app.routers import premium as premium_router
from app.routers import account as account_router
from app.worker import worker

app = FastAPI(title=settings.APP_NAME, version=settings.APP_VERSION)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def _startup():
    app.state.worker_tasks = [asyncio.create_task(worker()) for _ in range(2)]

@app.on_event("shutdown")
async def _shutdown():
    tasks = getattr(app.state, "worker_tasks", [])
    for t in tasks:
        t.cancel()
    for t in tasks:
        try:
            await t
        except Exception:
            pass

@app.get("/v1/health")
async def health():
    import torch
    return {
        "ok": True,
        "device": "cuda" if torch.cuda.is_available() else "cpu",
        "trocr_dir": settings.TROCR_DIR,
        "detector_weights": settings.DETECTOR_WEIGHTS,
    }

FILES_ROOT = Path(settings.FILES_DIR)
FILES_ROOT.mkdir(parents=True, exist_ok=True)

@app.get("/files/{path:path}")
async def get_file_any(path: str):
    full = (FILES_ROOT / path).resolve()
    if not str(full).startswith(str(FILES_ROOT.resolve())):
        raise HTTPException(403, "forbidden")
    if not full.exists() or not full.is_file():
        raise HTTPException(404, "file not found")
    return FileResponse(full, filename=full.name)

app.include_router(api)
app.include_router(premium_router.router)
app.include_router(account_router.router)