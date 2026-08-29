from datetime import datetime, timezone
from collections.abc import Awaitable, Callable
from typing import Literal
import os
import uuid

from fastapi import APIRouter, FastAPI, HTTPException, Request, Response, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from db import insert_review_into_storage, lifespan, list_reviews_from_storage, safe_config_status


class HealthResponse(BaseModel):
    status: str


class ConfigStatus(BaseModel):
    mongo_url_configured: bool
    db_name_configured: bool
    cors_origins_configured: bool
    production_mode: bool


class DiagnosticsResponse(BaseModel):
    backend_healthy: bool
    mongo_connected: bool
    storage_mode: str
    fallback_available: bool
    config: ConfigStatus


class ReviewCreate(BaseModel):
    name: str = Field(min_length=2, max_length=50)
    rating: Literal[3, 4, 5]
    text: str = Field(min_length=10, max_length=500)


class ReviewResponse(BaseModel):
    id: str
    name: str
    rating: int
    text: str
    createdAt: str


api_router = APIRouter(prefix="/api")


@api_router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok")


@api_router.get("/health/config", response_model=DiagnosticsResponse)
async def health_config() -> DiagnosticsResponse:
    return DiagnosticsResponse(
        backend_healthy=True,
        mongo_connected=bool(app.state.mongo_connected),
        storage_mode=app.state.storage_mode,
        fallback_available=True,
        config=ConfigStatus(**safe_config_status()),
    )


@api_router.get("/reviews", response_model=list[ReviewResponse])
async def list_reviews() -> list[ReviewResponse]:
    reviews = await list_reviews_from_storage(app)
    return [ReviewResponse(**review) for review in reviews]


@api_router.post("/reviews", response_model=ReviewResponse, status_code=status.HTTP_201_CREATED)
async def create_review(payload: ReviewCreate) -> ReviewResponse:
    review = ReviewResponse(
        id=uuid.uuid4().hex,
        name=payload.name.strip(),
        rating=payload.rating,
        text=payload.text.strip(),
        createdAt=datetime.now(timezone.utc).isoformat(),
    )
    if len(review.name) < 2 or len(review.text) < 10:
        raise HTTPException(status_code=400, detail="Ungültige Rezension")
    await insert_review_into_storage(app, review.model_dump())
    return review


app = FastAPI(title="AuditIQ Reviews API", lifespan=lifespan)
cors_origins = [origin.strip() for origin in os.getenv("CORS_ORIGINS", "*").split(",") if origin.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)


@app.middleware("http")
async def prevent_api_edge_caching(
    request: Request,
    call_next: Callable[[Request], Awaitable[Response]],
) -> Response:
    response = await call_next(request)
    if request.url.path.startswith("/api/"):
        response.headers["Cache-Control"] = "no-store"
    return response


app.include_router(api_router)