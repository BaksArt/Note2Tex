from pydantic_settings import BaseSettings
from pydantic import AnyUrl

class Settings(BaseSettings):
    # Core
    APP_NAME: str = "Note2Tex Inference API"
    APP_VERSION: str = "1.1.0"
    BASE_URL: str = "https://note2tex.baksart.ru"
    DEEPLINK_SCHEME: str = "note2tex://"

    # Database
    DATABASE_URL: str = "postgresql+asyncpg://user:pass@localhost:5432/note2tex"

    # JWT
    JWT_SECRET: str
    JWT_EXPIRE_MIN: int = 7*24*60
    JWT_ALG: str = "HS256"

    # SMTP (Gmail)
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 465
    SMTP_USERNAME: str
    SMTP_PASSWORD: str
    SMTP_FROM: str = "Note2Tex"

    STORAGE_BACKEND: str = "local"

    # локальное хранилище
    FILES_DIR: str = "storage"  # корневая папка, куда будем класть файлы
    FILES_BASE_URL: str = "https://note2tex.baksart.ru/files"  # базовый URL для скачивания

    # Plans
    FREE_MAX_PROJECTS: int = 10
    FREE_PAGES_PER_MONTH: int = 10

    # Inference defaults
    DETECTOR_WEIGHTS: str = "models/detector/best.pt"
    TROCR_DIR: str = "models/trocr_latex_fast"
    WORDS_OCR_WEIGHTS: str = "models/words_recognizer/ocr_transformer_multi.pt"
    TEMP_DIR: str = "temp"
    DET_CONF: float = 0.25
    DET_IOU: float = 0.5
    DET_IMGSZ: int = 1280
    DET_PAD: float = 0.001
    BEAMS: int = 4
    MAX_NEW_TOKENS: int = 224
    LENGTH_PENALTY: float = 1.1
    BIN_STRENGTH: float = 0.75
    ERODE_KERNEL: int = 3

    DEBUG_PREMIUM_SECRET: str

    class Config:
        env_file = ".env"

settings = Settings()