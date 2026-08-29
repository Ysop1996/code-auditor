from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Literal
import json
import os
import uuid

from fastapi import APIRouter, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


DATA_FILE = Path(__file__).with_name("reviews.json")
DATA_LOCK = Lock()


class HealthResponse(BaseModel):
    status: str


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


def read_reviews() -> list[dict]:
    try:
        return json.loads(DATA_FILE.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return []


api_router = APIRouter(prefix="/api")


@api_router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok")


@api_router.get("/reviews", response_model=list[ReviewResponse])
async def list_reviews() -> list[ReviewResponse]:
    return [ReviewResponse(**review) for review in read_reviews()]


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
    with DATA_LOCK:
        reviews = read_reviews()
        DATA_FILE.write_text(
            json.dumps([review.model_dump(), *reviews][:100], ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    return review


app = FastAPI(title="AuditIQ Reviews API")
cors_origins = [origin.strip() for origin in os.getenv("CORS_ORIGINS", "*").split(",") if origin.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type"],
)
app.include_router(api_router)