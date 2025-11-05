import time, uuid
from datetime import datetime, timedelta
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from passlib.context import CryptContext
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.config import settings
from app.database import get_session
from app.models import User

pwd = CryptContext(schemes=["pbkdf2_sha256"], deprecated="auto")
auth_scheme = HTTPBearer(auto_error=False)

def hash_password(p: str) -> str:
    return pwd.hash(p)

def verify_password(p: str, h: str) -> bool:
    return pwd.verify(p, h)

def create_access_token(user: User) -> str:
    payload = {
        "sub": str(user.id),
        "username": user.username,
        "iat": int(time.time()),
        "exp": int(time.time()) + settings.JWT_EXPIRE_MIN * 60,
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALG)

async def get_current_user(
    creds: Annotated[HTTPAuthorizationCredentials | None, Depends(auth_scheme)],
    session: Annotated[AsyncSession, Depends(get_session)]
) -> User:
    if not creds:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)
    token = creds.credentials
    try:
        payload = jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALG])
        uid = payload.get("sub")
    except Exception:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)
    res = await session.execute(select(User).where(User.id == uuid.UUID(uid)))
    user = res.unique().scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)
    return user

async def require_verified(user: Annotated[User, Depends(get_current_user)]) -> User:
    if not user.email_verified:
        raise HTTPException(403, "почта не подтверждена")
    return user