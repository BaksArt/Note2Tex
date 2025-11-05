import uuid
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, EmailStr
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from starlette import status
from datetime import datetime, timezone

from app.database import get_session
from app.security import hash_password, verify_password, create_access_token
from app.config import settings
from app.models import User
from app import crud
from app.emailer import send_verification_email, send_reset_email

router = APIRouter(prefix="/auth", tags=["auth"])

class RegisterIn(BaseModel):
    email: EmailStr
    username: str
    password: str

@router.post("/register", status_code=204)
async def register(data: RegisterIn, session: AsyncSession = Depends(get_session)):
    if await crud.get_user_by_email(session, data.email):
        raise HTTPException(409, "почта уже занята")
    if await crud.get_user_by_login(session, data.username):
        raise HTTPException(409, "имя пользователя уже занято")
    u = await crud.create_user(session, data.email, data.username, hash_password(data.password))
    token = uuid.uuid4().hex
    await crud.create_email_token(session, u.id, token, datetime.utcnow()+timedelta(days=2))
    await session.commit()
    send_verification_email(u.email, token)
    return

class ResendIn(BaseModel):
    email: EmailStr

@router.post("/resend-verification", status_code=204)
async def resend(data: ResendIn, session: AsyncSession = Depends(get_session)):
    u = await crud.get_user_by_email(session, data.email)
    if not u:
        return
    token = uuid.uuid4().hex
    await crud.create_email_token(session, u.id, token, datetime.utcnow()+timedelta(days=2))
    await session.commit()
    send_verification_email(u.email, token)
    return

@router.get("/verify")
async def verify(token: str, session: AsyncSession = Depends(get_session)):
    u = await crud.use_email_token(session, token)
    if not u:
        raise HTTPException(400, "срок действия токена истек")
    await session.commit()
    access = create_access_token(u)
    from fastapi.responses import RedirectResponse
    return RedirectResponse(url=f"{settings.DEEPLINK_SCHEME}auth/verify?accessToken={access}")

class LoginIn(BaseModel):
    login: str
    password: str

class LoginOut(BaseModel):
    accessToken: str

@router.post("/login", response_model=LoginOut)
async def login(data: LoginIn, session: AsyncSession = Depends(get_session)):
    u = await crud.get_user_by_login(session, data.login)
    if not u or not verify_password(data.password, u.password_hash):
        raise HTTPException(401, "неверные данные для входа")

    if not u.email_verified:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="почта не подтверждена"
        )
    return LoginOut(accessToken=create_access_token(u))

class ForgotIn(BaseModel):
    email: EmailStr

@router.post("/forgot", status_code=204)
async def forgot(data: ForgotIn, session: AsyncSession = Depends(get_session)):
    u = await crud.get_user_by_email(session, data.email)
    if not u:
        return
    token = uuid.uuid4().hex
    from datetime import timedelta
    await crud.create_reset_token(session, u.id, token, datetime.utcnow()+timedelta(hours=2))
    await session.commit()
    send_reset_email(u.email, token)
    return

@router.get("/reset/confirm")
async def reset_confirm(token: str):
    from fastapi.responses import RedirectResponse
    return RedirectResponse(url=f"{settings.DEEPLINK_SCHEME}auth/reset-ok?token={token}")

class ResetIn(BaseModel):
    token: str
    newPassword: str

@router.post("/reset", status_code=204)
async def reset(data: ResetIn, session: AsyncSession = Depends(get_session)):
    t = await crud.get_reset_token(session, data.token)
    if not t or t.used:
        raise HTTPException(400, "invalid or expired token")
    res = await session.execute(select(User).where(User.id==t.user_id))
    u = res.unique().scalar_one_or_none()
    if not u:
        raise HTTPException(400, "invalid token")
    u.password_hash = hash_password(data.newPassword)
    await crud.mark_reset_used(session, t)
    await session.commit()
    return
