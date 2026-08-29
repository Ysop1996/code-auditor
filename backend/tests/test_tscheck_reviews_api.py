"""Backend tests for the reviews API (public reviews + validation).

Covers acceptance criteria:
- "Öffentliche Rezensionen kommen aus dem Backend" (GET /api/reviews includes seeded 'Lea' review)
- "Ungültige Rezension wird abgelehnt" (POST /api/reviews with invalid payload -> 4xx)
- "Öffentliche Health-API funktioniert" (GET /api/health -> 200 {status: 'ok'})
"""
import uuid

import httpx

BASE_URL = "http://localhost:8001"


def test_health_endpoint_ok() -> None:
    resp = httpx.get(f"{BASE_URL}/api/health", timeout=10)
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_reviews_list_contains_seeded_lea_review() -> None:
    resp = httpx.get(f"{BASE_URL}/api/reviews", timeout=10)
    assert resp.status_code == 200
    reviews: list[dict[str, object]] = resp.json()
    assert isinstance(reviews, list)
    lea_reviews = [r for r in reviews if r.get("name") == "Lea" and r.get("rating") == 5]
    assert len(lea_reviews) >= 1, f"Expected seeded 'Lea' review, got: {reviews}"


def test_create_valid_review_is_accepted_and_listed() -> None:
    suffix = uuid.uuid4().hex[:8]
    name = f"tscheck-{suffix}"
    payload: dict[str, object] = {
        "name": name,
        "rating": 5,
        "text": f"tscheck-{suffix} Dies ist eine gueltige Testrezension mit genug Text.",
    }
    resp = httpx.post(f"{BASE_URL}/api/reviews", json=payload, timeout=10)
    assert resp.status_code == 201, resp.text
    created: dict[str, object] = resp.json()
    assert created["name"] == name
    assert created["rating"] == 5

    listing = httpx.get(f"{BASE_URL}/api/reviews", timeout=10)
    assert listing.status_code == 200
    ids = [r["id"] for r in listing.json()]
    assert created["id"] in ids


def test_create_review_with_short_name_and_text_and_bad_rating_rejected() -> None:
    payload: dict[str, object] = {
        "name": "a",
        "rating": 1,
        "text": "short",
    }
    resp = httpx.post(f"{BASE_URL}/api/reviews", json=payload, timeout=10)
    assert resp.status_code in (400, 422), resp.text
