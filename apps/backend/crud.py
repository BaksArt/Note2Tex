import uuid
import re

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_
from sqlalchemy import select, delete
from uuid import UUID
from datetime import datetime, timezone

from app.models import User, EmailVerificationToken, PasswordResetToken, Project, ProjectStatus, MonthlyUsage
from app.models import Project, ProjectRating
from app.quotas import month_key_now

UNTITLED_RX = re.compile(r"^untitled(\d+)$", re.IGNORECASE)


async def get_user_by_email(session: AsyncSession, email: str) -> User | None:
    res = await session.execute(select(User).where(User.email==email))
    return res.unique().scalar_one_or_none()

async def get_user_by_login(session: AsyncSession, login: str) -> User | None:
    q = select(User).where((User.username==login) | (User.email==login))
    res = await session.execute(q)
    return res.unique().scalar_one_or_none()

async def create_user(session: AsyncSession, email: str, username: str, password_hash: str) -> User:
    u = User(email=email, username=username, password_hash=password_hash)
    session.add(u)
    await session.flush()
    return u

async def create_email_token(session: AsyncSession, user_id, token: str, expires_at):
    t = EmailVerificationToken(token=token, user_id=user_id, expires_at=expires_at, used=False)
    session.add(t)

async def use_email_token(session: AsyncSession, token: str) -> User | None:
    res = await session.execute(select(EmailVerificationToken).where(EmailVerificationToken.token==token))
    t = res.unique().scalar_one_or_none()
    if not t or t.used:
        return None
    from datetime import datetime
    if t.expires_at < datetime.now(timezone.utc):
        return None
    res2 = await session.execute(select(User).where(User.id==t.user_id))
    u = res2.unique().scalar_one_or_none()
    if not u:
        return None
    t.used = True
    u.email_verified = True
    await session.flush()
    return u

async def create_reset_token(session: AsyncSession, user_id, token: str, expires_at):
    t = PasswordResetToken(token=token, user_id=user_id, expires_at=expires_at, used=False)
    session.add(t)

async def get_reset_token(session: AsyncSession, token: str) -> PasswordResetToken | None:
    res = await session.execute(select(PasswordResetToken).where(PasswordResetToken.token==token))
    return res.unique().scalar_one_or_none()

async def mark_reset_used(session: AsyncSession, t: PasswordResetToken):
    t.used = True
    await session.flush()

async def get_project(session: AsyncSession, pid: uuid.UUID, user_id: uuid.UUID) -> Project | None:
    res = await session.execute(select(Project).where(Project.id==pid, Project.user_id==user_id))
    return res.unique().scalar_one_or_none()


async def get_project_owned(session: AsyncSession, project_id: UUID, user_id: UUID) -> Project | None:
    res = await session.execute(select(Project).where(and_(Project.id == project_id, Project.user_id == user_id)))
    return res.scalar_one_or_none()

async def upsert_rating(session: AsyncSession, user_id: UUID, project_id: UUID, value: int, comment: str | None) -> ProjectRating:
    res = await session.execute(
        select(ProjectRating).where(
            and_(ProjectRating.user_id == user_id, ProjectRating.project_id == project_id)
        )
    )
    r = res.scalar_one_or_none()
    now = datetime.utcnow()
    if r:
        r.value = value
        r.comment = comment
        r.updated_at = now
    else:
        r = ProjectRating(user_id=user_id, project_id=project_id, value=value, comment=comment, created_at=now, updated_at=now)
        session.add(r)
    await session.flush()
    return r

def month_bounds_utc(dt: datetime | None = None) -> tuple[datetime, datetime]:
    now = dt or datetime.now(timezone.utc)
    start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    if start.month == 12:
        end = start.replace(year=start.year+1, month=1)
    else:
        end = start.replace(month=start.month+1)
    return start, end

async def count_projects_for_user(session: AsyncSession, user_id: UUID) -> tuple[int, int]:
    mk = month_key_now()
    res1 = await session.execute(
        select(MonthlyUsage.pages_used)
        .where(MonthlyUsage.user_id == user_id, MonthlyUsage.month_key == mk)
    )
    month_count = res1.scalar_one_or_none() or 0
    res2 = await session.execute(select(func.count()).select_from(Project).where(Project.user_id == user_id))
    total_count = res2.scalar_one()
    return int(month_count), int(total_count)



async def next_untitled_title(session: AsyncSession, user_id) -> str:

    res = await session.execute(
        select(Project.title).where(Project.user_id == user_id)
    )
    titles = [row[0] for row in res.all()]

    max_n = 0
    for t in titles:
        m = UNTITLED_RX.fullmatch(t.strip())
        if m:
            try:
                n = int(m.group(1))
                if n > max_n:
                    max_n = n
            except Exception:
                pass

    return f"untitled{max_n + 1}"