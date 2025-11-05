from fastapi import APIRouter
from .auth import router as auth
from .projects import router as projects

api = APIRouter()
api.include_router(auth)
api.include_router(projects)