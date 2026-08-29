from __future__ import annotations

import asyncio
import json
import logging
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import FastAPI
from pymongo import AsyncMongoClient, DESCENDING
from pymongo.server_api import ServerApi


log = logging.getLogger(__name__)
BASE_DIR = Path(__file__).parent
ENV_FILE = BASE_DIR / ".env"
REVIEWS_FILE = BASE_DIR / "reviews.json"
FILE_LOCK = asyncio.Lock()


def load_local_env() -> None:
    if not ENV_FILE.exists():
        return
    for raw_line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


load_local_env()


def env_value(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


def safe_config_status() -> dict[str, bool]:
    return {
        "mongo_url_configured": bool(env_value("MONGO_URL")),
        "db_name_configured": bool(env_value("DB_NAME")),
        "cors_origins_configured": bool(env_value("CORS_ORIGINS")),
        "production_mode": env_value("APP_ENV", "development").lower() == "production",
    }


async def read_file_reviews() -> list[dict[str, Any]]:
    async with FILE_LOCK:
        try:
            raw = await asyncio.to_thread(REVIEWS_FILE.read_text, encoding="utf-8")
            value = json.loads(raw or "[]")
            return value if isinstance(value, list) else []
        except (FileNotFoundError, json.JSONDecodeError):
            return []


async def write_file_reviews(reviews: list[dict[str, Any]]) -> None:
    async with FILE_LOCK:
        temp_file = REVIEWS_FILE.with_suffix(".json.tmp")
        payload = json.dumps(reviews, ensure_ascii=False, indent=2)
        await asyncio.to_thread(temp_file.write_text, payload, encoding="utf-8")
        await asyncio.to_thread(temp_file.replace, REVIEWS_FILE)


async def list_reviews_from_storage(app: FastAPI) -> list[dict[str, Any]]:
    if app.state.mongo_connected:
        cursor = app.state.db.reviews.find({}, {"_id": 0}).sort("createdAt", DESCENDING).limit(100)
        return [document async for document in cursor]
    return await read_file_reviews()


async def insert_review_into_storage(app: FastAPI, review: dict[str, Any]) -> None:
    if app.state.mongo_connected:
        await app.state.db.reviews.insert_one(review.copy())
        return
    reviews = await read_file_reviews()
    await write_file_reviews([review, *reviews][:100])


def initialize_storage_state(app: FastAPI) -> None:
    app.state.mongo_client = None
    app.state.db = None
    app.state.mongo_connected = False
    app.state.storage_mode = "file"


def storage_settings() -> tuple[str, str, bool]:
    mongo_url = env_value("MONGO_URL")
    db_name = env_value("DB_NAME")
    app_env = env_value("APP_ENV", "development").lower()
    production_mode = app_env == "production" or mongo_url.startswith("mongodb+srv://")
    return mongo_url, db_name, production_mode


async def connect_mongo_storage(
    app: FastAPI,
    mongo_url: str,
    db_name: str,
    production_mode: bool,
) -> None:
    client = AsyncMongoClient(
        mongo_url,
        server_api=ServerApi("1"),
        connectTimeoutMS=1500,
        serverSelectionTimeoutMS=1500,
    )
    try:
        await client.admin.command("ping")
        app.state.mongo_client = client
        app.state.db = client[db_name]
        app.state.mongo_connected = True
        app.state.storage_mode = "mongodb"
        await app.state.db.reviews.create_index("id", unique=True)
        if await app.state.db.reviews.count_documents({}) == 0:
            seed_reviews = await read_file_reviews()
            if seed_reviews:
                await app.state.db.reviews.insert_many([review.copy() for review in seed_reviews])
        log.info("MongoDB review storage enabled")
    except Exception as error:
        await client.close()
        if production_mode:
            raise RuntimeError("Production review storage is unavailable") from error
        log.warning("MongoDB unavailable; using local JSON review fallback")


async def configure_review_storage(app: FastAPI) -> None:
    mongo_url, db_name, production_mode = storage_settings()
    if production_mode and (not mongo_url or not db_name):
        raise RuntimeError("Production review storage configuration is incomplete")
    if mongo_url and db_name:
        await connect_mongo_storage(app, mongo_url, db_name, production_mode)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    initialize_storage_state(app)
    await configure_review_storage(app)
    try:
        yield
    finally:
        # Identity comparison is intentional here: None is a singleton sentinel.
        if app.state.mongo_client is not None:
            await app.state.mongo_client.close()