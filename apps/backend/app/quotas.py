from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, update, insert
from app.config import settings
from app.models import MonthlyUsage, Project, User, Plan

def month_key_now() -> str:
    dt = datetime.utcnow()
    return f"{dt.year:04d}-{dt.month:02d}"

async def get_or_create_usage(session: AsyncSession, user_id) -> MonthlyUsage:
    mk = month_key_now()
    res = await session.execute(select(MonthlyUsage).where(MonthlyUsage.user_id==user_id, MonthlyUsage.month_key==mk))
    mu = res.scalar_one_or_none()
    if mu:
        return mu
    mu = MonthlyUsage(user_id=user_id, month_key=mk, pages_used=0)
    session.add(mu)
    await session.flush()
    return mu

async def can_consume(session: AsyncSession, user: User, pages: int = 1) -> bool:
    if is_premium(user):
        return True
    mu = await get_or_create_usage(session, user.id)
    return (mu.pages_used + pages) <= settings.FREE_PAGES_PER_MONTH

async def consume(session: AsyncSession, user: User, pages: int = 1):
    mu = await get_or_create_usage(session, user.id)
    mu.pages_used += pages
    await session.flush()

async def under_project_cap(session: AsyncSession, user: User) -> bool:
    if is_premium(user):
        return True
    res = await session.execute(select(Project).where(Project.user_id==user.id))
    count = len(res.unique().scalars().all())
    return count < settings.FREE_MAX_PROJECTS

def is_premium(user: User) -> bool:
    if user.plan != Plan.premium:
        return False
    if not user.plan_expires_at:
        return False
    return user.plan_expires_at > datetime.now(timezone.utc)
