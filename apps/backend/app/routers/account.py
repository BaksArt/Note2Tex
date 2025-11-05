from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.schemas import AccountOut
from app.security import require_verified
from app import crud

router = APIRouter(prefix="/account", tags=["account"])

@router.get("/me", response_model=AccountOut)
async def get_account_me(user = Depends(require_verified), session: AsyncSession = Depends(get_session)):
    month_count, total_count = await crud.count_projects_for_user(session, user.id)
    return AccountOut(
        id=user.id,
        email=user.email,
        username=user.username,
        plan=user.plan.value,
        planExpiresAt=user.plan_expires_at,
        monthProjects=month_count,
        totalProjects=total_count,
    )
