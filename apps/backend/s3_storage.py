import mimetypes
from pathlib import Path
import boto3
from botocore.client import Config
from app.config import settings

_s3 = boto3.client(
    "s3",
    region_name=settings.AWS_REGION,
    aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
    aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
    config=Config(signature_version="s3v4"),
)

BUCKET = settings.S3_BUCKET

def upload_file(local_path: str, key: str):
    ctype, _ = mimetypes.guess_type(key)
    _s3.upload_file(str(local_path), BUCKET, key, ExtraArgs={"ContentType": ctype or "application/octet-stream"})

def presign_url(key: str, ttl: int | None = None) -> str:
    return _s3.generate_presigned_url(
        "get_object",
        Params={"Bucket": BUCKET, "Key": key},
        ExpiresIn=ttl or settings.S3_PRESIGN_TTL,
    )

def delete_keys(keys: list[str]):
    if not keys:
        return
    _s3.delete_objects(Bucket=BUCKET, Delete={"Objects": [{"Key": k} for k in keys]})