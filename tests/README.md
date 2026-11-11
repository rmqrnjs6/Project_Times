# Python 산술·HTTP 테스트

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일

| 파일 | 입력과 기대 결과 |
|---|---|
| [test_api.py](test_api.py) | /ready=200, F 6/2/2→60~80%, 축 누락·bool 수량→422, E 미제공 reviewed→종합 null |
| [test_scoring.py](test_scoring.py) | 미확인 분모 유지, 수량·가중치·범위 검사, R 구간 역변환, Decimal 문맥 격리, 변화량·반올림 |

계산 코드는 [app/scoring.py](../app/scoring.py), HTTP 스키마는 [app/main.py](../app/main.py)에 있습니다. Python TestClient는 앱을 프로세스 내부에서 호출하며 외부 공급자·DB·Java 인증을 실행하지 않습니다.

## 실행

저장소 루트에서 환경에 맞는 Python 가상환경을 준비한 뒤 실행합니다.

```sh
python -m pip install -r requirements-dev.txt
python -m pytest -q
```

Docker 방식은 `docker build --target test -t project-times-test .` 다음 `docker run --rm project-times-test`입니다. CI의 test 작업도 Python 검사를 수행합니다.

## 결과 해석

테스트는 입력된 판정의 산술 처리를 확인합니다. 원문 근거의 진실성·LLM 정확도·전문가 판단을 검사하는 것이 아닙니다. 실패 시 해당 입력·기대값·실제값·정책 버전을 확인하세요. 실제 DB/인증은 [별도 스크립트](../scripts/README.md), 새 Node 작업대는 [모듈 테스트](../modules/evaluation-workbench/tests/README.md)를 사용합니다. 이 문서 작성 시 테스트를 다시 실행한 것은 아닙니다.

[전체 코드 읽기 지도](../docs/CODE_GUIDE.md)
