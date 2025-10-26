import asyncio, uuid, traceback
from typing import Dict
from app.schemas import JobStatus, InferResponse
from app.pipeline import run_full_pipeline
from app.config import settings

queue: "asyncio.Queue[dict]" = asyncio.Queue()
jobs: Dict[str, JobStatus] = {}

async def submit_job(payload: dict) -> str:
    job_id = payload.get("job_id") or uuid.uuid4().hex
    jobs[job_id] = JobStatus(job_id=job_id, status="QUEUED")
    await queue.put({"job_id": job_id, **payload})
    return job_id

async def worker():
    while True:
        task = await queue.get()
        job_id = task["job_id"]
        try:
            jobs[job_id] = JobStatus(job_id=job_id, status="RUNNING")
            result = run_full_pipeline(**task["params"])
            jobs[job_id] = JobStatus(job_id=job_id, status="DONE", result=InferResponse(**result))
        except Exception as e:
            tb = traceback.format_exc()
            jobs[job_id] = JobStatus(job_id=job_id, status="FAILED", error=str(e) + "\n" + tb)
        finally:
            queue.task_done()
