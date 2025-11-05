from __future__ import annotations
import os
import shutil
from pathlib import Path
from typing import Optional
from app.config import settings

def _local_root() -> Path:
    root = Path(settings.FILES_DIR)
    root.mkdir(parents=True, exist_ok=True)
    return root

def local_put_file(src_path: str | Path, key: str, content_type: Optional[str] = None) -> str:
    dst = _local_root() / key
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(str(src_path), str(dst))
    return f"{settings.FILES_BASE_URL}/{key.replace(os.sep, '/')}"  # ссылка для скачивания

def local_put_bytes(data: bytes, key: str, content_type: Optional[str] = None) -> str:
    dst = _local_root() / key
    dst.parent.mkdir(parents=True, exist_ok=True)
    with open(dst, "wb") as f:
        f.write(data)
    return f"{settings.FILES_BASE_URL}/{key.replace(os.sep, '/')}"

def local_delete(keys: list[str]) -> None:
    for k in keys:
        p = _local_root() / k
        try:
            if p.exists():
                p.unlink()
        except Exception:
            pass

def upload_file(src_path: str | Path, key: str, content_type: Optional[str] = None) -> str:
    if settings.STORAGE_BACKEND.lower() == "local":
        return local_put_file(src_path, key, content_type)
    else:
        from app.s3_storage import upload_file as s3_upload
        return s3_upload(src_path, key, content_type)

def upload_bytes(data: bytes, key: str, content_type: Optional[str] = None) -> str:
    if settings.STORAGE_BACKEND.lower() == "local":
        return local_put_bytes(data, key, content_type)
    else:
        from app.s3_storage import upload_bytes as s3_upload_bytes  # type: ignore
        return s3_upload_bytes(data, key, content_type)

def make_download_url(key: str, expires_sec: int = 3600) -> str:
    if settings.STORAGE_BACKEND.lower() == "local":
        return f"{settings.FILES_BASE_URL}/{key.replace(os.sep, '/')}"
    else:
        from app.s3_storage import generate_presigned_url  # type: ignore
        return generate_presigned_url(key, expires=expires_sec)

def delete_objects(keys: list[str]) -> None:
    if settings.STORAGE_BACKEND.lower() == "local":
        return local_delete(keys)
    else:
        from app.s3_storage import delete_objects as s3_del  # type: ignore
        return s3_del(keys)

def fetch_to_path(key: str, dest_path: str | Path) -> str:
    dest = Path(dest_path)
    dest.parent.mkdir(parents=True, exist_ok=True)

    if settings.STORAGE_BACKEND.lower() == "local":
        src = Path(settings.FILES_DIR) / key
        if not src.exists():
            raise FileNotFoundError(f"not found in local storage: {src}")
        shutil.copy2(src, dest)
        return str(dest.resolve())
    else:
        from app.s3_storage import _s3  # type: ignore
        _s3.download_file(settings.S3_BUCKET, key, str(dest))
        return str(dest.resolve())
