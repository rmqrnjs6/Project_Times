# Python 내부 산술 API

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 역할과 파일

Spring 서비스가 전달한 판정 수량을 산술 범위로 계산합니다. LLM 호출, 사실 판별, DB 저장은 담당하지 않습니다.

| 파일 | 책임 | 주요 구성 |
|---|---|---|
| [main.py](main.py) | HTTP 입력 검사·응답 조립 | Counts, Evaluation, axis_result, evaluate |
| [scoring.py](scoring.py) | Decimal 계산 정책 | Bounds, fraction_bounds, score_bounds, paired_change, display |
| [__init__.py](__init__.py) | Python 패키지 식별 | 패키지 초기화 |

## 입력에서 출력까지

1. `POST /evaluate`가 F/S/I/T/C/R/E 일곱 축을 정확히 받습니다. 각 축은 수량 객체 또는 null입니다.
2. Counts의 positive/negative/unknown은 0~1,000,000의 엄격한 정수입니다. bool·추가 필드·누락 축은 거부됩니다.
3. 총수 N에 대해 하한=positive/N, 상한=(positive+unknown)/N입니다. N=0이면 미제공(null)입니다.
4. R은 종합 합산에서 [하한, 상한]을 [1−상한, 1−하한]으로 한 번 역변환합니다.
5. provisional은 E를 제외하고 활성 가중치 합 0.85로 나눕니다. reviewed는 E를 포함합니다. 필요한 축이 null이면 종합값도 null입니다.
6. `policy_version=reference-v1`, 단계·원본 수량·축별 범위·판정 비율·종합 범위를 반환합니다. Java가 이를 저장합니다.

F/S/I/T/C/R/E 기본 가중치는 0.25/0.20/0.10/0.10/0.10/0.10/0.15입니다. Decimal 연산은 독립적인 precision 28 문맥을 사용하고 표시에는 HALF_UP을 적용합니다. F의 수량이 6/2/2이면 60~80%, 판정 완료 비율은 80%입니다. 이는 신뢰구간이나 정답 확률이 아닙니다.

## 경로와 실행

| 경로 | 응답 |
|---|---|
| GET / | 서비스 설명, fact_verification=false |
| GET /ready | status=ok; Python 응답 확인 |
| POST /evaluate | 산술 결과; 스키마 위반은 422 |

저장소 루트에서 `docker compose up -d --build`로 기존 앱과 함께 실행합니다. 이 API는 Compose 내부용이며 외부 공개 인증을 제공하지 않습니다. Python의 /ready만으로 Java·DB까지 준비되었다고 해석하지 않습니다.

검증은 루트에서 `python -m pytest -q` 또는 Docker test target으로 수행합니다. [Python 테스트 설명](../tests/README.md)과 [Java 호출부](../web/src/main/java/dev/projecttimes/README.md)를 함께 읽으세요. 새 평가 모듈의 Q6 원점수 계산과 입력 형식이 다릅니다.

[전체 코드 읽기 지도](../docs/CODE_GUIDE.md)
