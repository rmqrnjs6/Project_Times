from fastapi.testclient import TestClient
from app.main import app

def test_ready():
    response = TestClient(app).get("/ready")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
