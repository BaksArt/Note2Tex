from pydantic import BaseModel, HttpUrl, Field
from typing import List, Optional, Tuple, Literal

class InferRequest(BaseModel):
    image_url: HttpUrl
    detector_weights: Optional[str] = None
    trocr_dir: Optional[str] = None
    det_conf: Optional[float] = None
    det_iou: Optional[float] = None
    det_imgsz: Optional[int] = None
    det_pad: Optional[float] = None
    beams: Optional[int] = None
    max_new_tokens: Optional[int] = None
    length_penalty: Optional[float] = None
    bin_strength: Optional[float] = None
    want_tex: bool = True
    want_csv: bool = True
    want_pdf: bool = True

class BlockOut(BaseModel):
    idx: int
    bbox: Tuple[int, int, int, int]
    latex: str
    crop_path: str
    bin_path: str

class InferResponse(BaseModel):
    model_config = {'protected_namespaces': ()}
    latex: str
    blocks: List[BlockOut]
    tex_path: Optional[str] = None
    csv_path: Optional[str] = None
    pdf_path: Optional[str] = None
    time_ms: int
    model_version: str = "trocr-custom"
    detector_weights: str


class EnqueueRequest(InferRequest):
    job_id: Optional[str] = None
    callback_url: Optional[str] = None

class JobStatus(BaseModel):
    job_id: str
    status: Literal["QUEUED", "RUNNING", "DONE", "FAILED"]
    result: Optional[InferResponse] = None
    error: Optional[str] = None

class HealthResponse(BaseModel):
    ok: bool
    device: str
    trocr_dir: str
    detector_weights: str
