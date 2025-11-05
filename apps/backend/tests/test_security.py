import uuid
import jwt
import types
import pytest

from app.security import hash_password, verify_password, create_access_token, require_verified
from app.config import settings

def test_hash_and_verify_password():
    raw = "S3cureP@ss"
    h = hash_password(raw)
    assert h != raw
    assert verify_password(raw, h) is True
    assert verify_password("wrong", h) is False

def test_create_access_token_payload(monkeypatch):
    monkeypatch.setattr(settings, "JWT_SECRET", "testsecret", raising=False)
    monkeypatch.setattr(settings, "JWT_ALG", "HS256", raising=False)
    monkeypatch.setattr(settings, "JWT_EXPIRE_MIN", 5, raising=False)

    user = types.SimpleNamespace(id=uuid.uuid4(), username="alice")
    token = create_access_token(user)
    payload = jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALG])

    assert payload["sub"] == str(user.id)
    assert payload["username"] == "alice"
    assert isinstance(payload["iat"], int)
    assert isinstance(payload["exp"], int)
    assert payload["exp"] > payload["iat"]

@pytest.mark.asyncio
async def test_require_verified_raises_for_unverified():
    from fastapi import HTTPException
    user = types.SimpleNamespace(email_verified=False)
    with pytest.raises(HTTPException) as e:
        await require_verified(user)
    assert e.value.status_code == 403

@pytest.mark.asyncio
async def test_require_verified_ok():
    user = types.SimpleNamespace(email_verified=True)
    got = await require_verified(user)
    assert got is user
