import uuid, os
from typing import Optional
from fastapi import APIRouter, Depends, UploadFile, File, HTTPException
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_session
from app.security import require_verified
from app.models import Project, ProjectStatus
from app.config import settings
from app import crud
from app.quotas import can_consume, consume, under_project_cap
from app.storage import upload_file, make_download_url, delete_objects
from app.worker import submit_infer_job, submit_texjob
from app.schemas import RatingIn, RatingOut
from uuid import UUID

router = APIRouter(prefix="/projects", tags=["projects"])

class ProjectOut(BaseModel):
    id: uuid.UUID
    title: str
    description: str
    status: ProjectStatus
    imageUrl: Optional[str] = None
    texUrl: Optional[str] = None
    pdfUrl: Optional[str] = None
    docxUrl: Optional[str] = None

    class Config:
        json_encoders = {uuid.UUID: str}

@router.post("", response_model=ProjectOut)
async def create_project(
    image: UploadFile = File(...),
    user = Depends(require_verified),
    session: AsyncSession = Depends(get_session)
):
    if not await under_project_cap(session, user):
        raise HTTPException(403, "количество проектов превышено")
    if not await can_consume(session, user, pages=1):
        raise HTTPException(403, "количество обработок в месяц превышено")
    next_title = await crud.next_untitled_title(session, user.id)
    p = Project(user_id=user.id, title=next_title, description="", status=ProjectStatus.processing)
    session.add(p)
    await session.flush()

    tmp_path = f"{settings.TEMP_DIR}/inbox/{uuid.uuid4().hex}.png"
    os.makedirs(os.path.dirname(tmp_path), exist_ok=True)
    with open(tmp_path, "wb") as f:
        f.write(await image.read())
    img_key = f"users/{user.id}/projects/{p.id}/image.png"
    upload_file(tmp_path, img_key)
    p.image_key = img_key
    await session.commit()

    await submit_infer_job(project_id=p.id)
    await consume(session, user, pages=1)
    await session.commit()

    return await _out_for_project(p)

async def _out_for_project(p: Project) -> ProjectOut:
    def url(k: str | None):
        return make_download_url(k) if k else None
    return ProjectOut(
        id=p.id, title=p.title, description=p.description, status=p.status,
        imageUrl=url(p.image_key), texUrl=url(p.tex_key), pdfUrl=url(p.pdf_key), docxUrl=url(p.docx_key)
    )

@router.get("", response_model=list[ProjectOut])
async def list_projects(user = Depends(require_verified), session: AsyncSession = Depends(get_session)):
    res = await session.execute(select(Project).where(Project.user_id==user.id).order_by(Project.created_at.desc()))
    return [await _out_for_project(p) for p in res.unique().scalars().all()]

@router.get("/{pid}", response_model=ProjectOut)
async def get_project(pid: uuid.UUID, user = Depends(require_verified), session: AsyncSession = Depends(get_session)):
    p = await crud.get_project(session, pid, user.id)
    if not p:
        raise HTTPException(404)
    return await _out_for_project(p)

class PatchIn(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    tex: Optional[str] = None

@router.patch("/{pid}", response_model=ProjectOut)
async def patch_project(pid: uuid.UUID, data: PatchIn, user=Depends(require_verified), session: AsyncSession = Depends(get_session)):
    p = await crud.get_project(session, pid, user.id)
    if not p:
        raise HTTPException(404)
    if data.title is not None:
        p.title = data.title
    if data.description is not None:
        p.description = data.description
    await session.commit()

    if data.tex is not None:
        p.status = ProjectStatus.processing
        await session.commit()
        await submit_texjob(project_id=p.id, tex_content=data.tex)
    return await _out_for_project(p)

@router.post("/{pid}/reprocess", status_code=202)
async def reprocess(pid: uuid.UUID, user=Depends(require_verified), session: AsyncSession = Depends(get_session)):
    p = await crud.get_project(session, pid, user.id)
    if not p:
        raise HTTPException(404)
    if not await can_consume(session, user, pages=1):
        raise HTTPException(403, "количество обработок в месяц превышено")
    p.status = ProjectStatus.processing
    await session.commit()
    await submit_infer_job(project_id=p.id)
    await consume(session, user, pages=1)
    await session.commit()
    return

@router.delete("/{pid}", status_code=204)
async def delete_project(pid: uuid.UUID, user=Depends(require_verified), session: AsyncSession = Depends(get_session)):
    p = await crud.get_project(session, pid, user.id)
    if not p:
        return
    keys = [k for k in [p.image_key, p.tex_key, p.pdf_key, p.docx_key] if k]
    delete_objects(keys)
    await session.delete(p)
    await session.commit()
    return

@router.post("/{pid}/rating", response_model=RatingOut, status_code=201)
async def rate_project(
    pid: UUID,
    data: RatingIn,
    user = Depends(require_verified),
    session: AsyncSession = Depends(get_session),
):
    p = await crud.get_project_owned(session, pid, user.id)
    if not p:
        raise HTTPException(status_code=404, detail="project not found")
    r = await crud.upsert_rating(session, user.id, p.id, data.value, data.comment)
    await session.commit()
    return RatingOut(
        id=r.id,
        projectId=p.id,
        value=r.value,
        comment=r.comment,
        createdAt=r.created_at,
        updatedAt=r.updated_at,
    )

