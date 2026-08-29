"""Backend test for the public health endpoint.

Covers acceptance criterion:
- "Öffentliche Health-API funktioniert": GET /api/health over the public
  ingress URL returns HTTP 200 and {"status": "ok"}.
"""
import httpx

PUBLIC_URL = "https://deploy-hub-247.preview.emergentagent.com"


def test_public_health_endpoint_ok():
    resp = httpx.get(f"{PUBLIC_URL}/api/health", timeout=15)
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}
