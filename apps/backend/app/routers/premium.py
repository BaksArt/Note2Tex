from fastapi import APIRouter, Depends, HTTPException, Header, Query, Response
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime, timedelta, timezone

from typing import Annotated
from app.database import get_session
from app.config import settings
from app.models import User, Plan
from app.security import require_verified

router = APIRouter(prefix="/premium", tags=["premium"])

class GrantIn(BaseModel):
    period: str

def _delta(period: str) -> timedelta:
    p = period.lower()
    if p in ("month", "1m"): return timedelta(days=30)
    if p in ("3months", "3m"): return timedelta(days=90)
    if p in ("year", "12m", "1y"): return timedelta(days=365)
    raise HTTPException(400, "Unknown period")

def _check_secret(secret_hdr: str | None, secret_q: str | None):
    if not settings.DEBUG_PREMIUM_SECRET:
        raise HTTPException(403, "Premium debug secret not configured")
    provided = (secret_hdr or secret_q or "").strip()
    if provided != settings.DEBUG_PREMIUM_SECRET:
        raise HTTPException(403, "Invalid secret")

UserDep = Annotated[User, Depends(require_verified)]
SessDep = Annotated[AsyncSession, Depends(get_session)]

@router.post("/grant", status_code=204, response_model=None)
async def grant_premium(
    data: GrantIn,
    user: UserDep,
    session: SessDep,
    x_secret: str | None = Header(None, alias="X-Debug-Secret"),
    q_secret: str | None = Query(None, alias="secret"),
) -> Response:
    _check_secret(x_secret, q_secret)
    now = datetime.now(timezone.utc)
    base = user.plan_expires_at if (user.plan == Plan.premium and user.plan_expires_at and user.plan_expires_at > now) else now
    user.plan = Plan.premium
    user.plan_expires_at = base + _delta(data.period)
    await session.commit()
    return Response(status_code=204)

@router.post("/revoke", status_code=204, response_model=None)
async def revoke_premium(
    user: UserDep,
    session: SessDep,
    x_secret: str | None = Header(None, alias="X-Debug-Secret"),
    q_secret: str | None = Query(None, alias="secret"),
) -> Response:
    _check_secret(x_secret, q_secret)
    user.plan = Plan.free
    user.plan_expires_at = None
    await session.commit()
    return Response(status_code=204)
