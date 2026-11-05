from fastapi.testclient import TestClient
from app.main import app

def test_ready():
    response = TestClient(app).get("/ready")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

def payload():
    return {'axes': {axis: {'positive': 6, 'negative': 2, 'unknown': 2} if axis != 'E' else None for axis in 'FSITCRE'}}

def test_evaluation_is_explicitly_arithmetic():
    response = TestClient(app).post('/evaluate', json=payload())
    assert response.status_code == 200
    result = response.json()
    assert result['axes']['F']['raw']['lower_percent'] == '60.0'
    assert result['axes']['F']['raw']['upper_percent'] == '80.0'
    assert result['axes']['F']['assessed_percent'] == '80.0'
    assert result['axes']['E']['raw'] is None
    assert result['kind'] == 'arithmetic_reference_not_fact_verification'

def test_incomplete_axes_rejected():
    data = payload(); del data['axes']['F']
    assert TestClient(app).post('/evaluate', json=data).status_code == 422

def test_boolean_counts_rejected():
    data = payload(); data['axes']['F']['positive'] = True
    assert TestClient(app).post('/evaluate', json=data).status_code == 422

def test_expert_missing_after_review_is_not_zero():
    data = payload(); data['stage'] = 'reviewed'
    assert TestClient(app).post('/evaluate', json=data).json()['composite'] is None
