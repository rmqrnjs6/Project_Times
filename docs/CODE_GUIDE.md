# 코드 읽기 지도

기준일: 2026-11-11. 확인한 소스 커밋: `442f869`. 기능별 README에서 실제 파일·입출력·실행·검증 범위를 설명합니다.

## 코드 영역별 문서

| 코드 영역 | 세부 README | 읽을 내용 |
|---|---|---|
| Python 산술 API | [설명 보기](../app/README.md) | 판정 수량·Decimal 범위·HTTP 입력 |
| Spring 앱 | [설명 보기](../web/README.md) | 로그인·질문 저장·빌드 |
| Java 클래스 | [설명 보기](../web/src/main/java/dev/projecttimes/README.md) | 필터·Controller·Service·DB |
| Spring 설정 | [설명 보기](../web/src/main/resources/README.md) | 프로파일·환경변수·세션 |
| DB 스키마 | [설명 보기](../web/src/main/resources/db/migration/README.md) | 마이그레이션·소유자·보존 |
| 기존 브라우저 JS | [설명 보기](../web/src/main/resources/static/README.md) | CSRF fetch·표시·CSS |
| JSP 화면 | [설명 보기](../web/src/main/webapp/WEB-INF/views/README.md) | 로그인·작업대 DOM 계약 |
| Java 테스트 | [설명 보기](../web/src/test/java/dev/projecttimes/README.md) | Mock·인증·자료 정책·HTTPS |
| Python 테스트 | [설명 보기](../tests/README.md) | 수량·HTTP·결측·정밀도 |
| 루트 스크립트 | [설명 보기](../scripts/README.md) | 실행·인증·백업·복구·템플릿 |
| Docker 진입점 | [설명 보기](../ops/README.md) | 프록시·네트워크·제한 |
| GitHub Actions | [설명 보기](../.github/workflows/README.md) | 4개 작업·로그·실행 범위 |
| 평가 작업대 전체 | [설명 보기](../modules/evaluation-workbench/README.md) | 독립 모듈 시작·기존 앱과 관계 |
| 평가 화면 코드 | [설명 보기](../modules/evaluation-workbench/public/README.md) | 검색어→파라미터→결과·차트 |
| Worker 서버 | [설명 보기](../modules/evaluation-workbench/worker/README.md) | 모델 어댑터·API·비밀·실패 |
| Worker 빌드/서버 | [설명 보기](../modules/evaluation-workbench/scripts/README.md) | 자산 삽입·루프백 미리보기 |
| Node 테스트 | [설명 보기](../modules/evaluation-workbench/tests/README.md) | 모의 연동·Q6·한계 |
| 공통 코드 영역 | [설명 보기](../common/README.md) | 현재 빈 확장 영역 |

## 처음 읽는 순서

1. [루트 README](../README.md)에서 기존 Docker 앱과 새 독립 작업대를 구분합니다.
2. 기존 앱은 Spring 웹 → Java 요청 흐름 → Python 산술 → DB 스키마 순서로 읽습니다.
3. 평가 작업대는 public/index.html → scoring/app/design/automation → Worker → 빌드 → 테스트 순서로 읽습니다.
4. 검증은 단위·Mock·실제 DB/HTTP·브라우저·외부 API를 분리해 확인합니다.

## 서로 다른 두 계산 경로

| 구분 | 기존 Python | 새 Node/Worker 작업대 |
|---|---|---|
| 입력 | 축별 positive/negative/unknown 수량 | 합성 예시의 6개 원점수; 모델 호출은 질문 문자열 |
| 숫자 처리 | Decimal 범위, 전문가 검토 전/후 | BigInt Q6 가중합; 실제 모델 응답 점수화 없음 |
| 저장 | Java가 소유자·입력·정책·결과를 PostgreSQL에 저장 | 질문 기록 localStorage; 평가 응답은 페이지 메모리 |
| 인증 | Spring DB 사용자·세션 | Sites 플랫폼 소유자 확인; 로컬 실제 호출 비활성 |

이 두 경로는 현재 공유 인증·DB로 합쳐진 서비스가 아닙니다. 모델 응답 일치는 사실 확정이 아니며 예시 그래프를 실제 공급자 성능으로 읽지 않습니다. [실행 워크플로우](WORKFLOW.md)와 [구현·미구현 상태](PROGRESS.md)를 함께 확인하세요.

## 문서의 검증 범위

이번 변경은 설명 문서만 수정합니다. 현재 소스의 파일 목록·호출부·환경설정·테스트 조건을 읽고 상대 링크·파일 경로·문서 외 변경 여부를 확인합니다. 이 문서 작성 중 애플리케이션 실행·성능 시험·유료 API 호출을 수행했다는 뜻은 아닙니다.
