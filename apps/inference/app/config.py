from pydantic import BaseModel
import os

class Settings(BaseModel):
    DETECTOR_WEIGHTS: str = os.getenv("DETECTOR_WEIGHTS", "models/detector/best.pt")
    TROCR_DIR: str        = os.getenv("TROCR_DIR", "models/trocr_latex_fast")
    TEMP_DIR: str         = os.getenv("TEMP_DIR", "temp")

    DET_CONF: float = float(os.getenv("DET_CONF", "0.25"))
    DET_IOU: float  = float(os.getenv("DET_IOU", "0.5"))
    DET_IMGSZ: int  = int(os.getenv("DET_IMGSZ", "1280"))
    DET_PAD: float  = float(os.getenv("DET_PAD", "0.04"))

    BEAMS: int           = int(os.getenv("BEAMS", "4"))
    MAX_NEW_TOKENS: int  = int(os.getenv("MAX_NEW_TOKENS", "224"))
    LENGTH_PENALTY: float= float(os.getenv("LENGTH_PENALTY", "1.1"))

    BIN_STRENGTH: float  = float(os.getenv("BIN_STRENGTH", "0.9"))

settings = Settings()
