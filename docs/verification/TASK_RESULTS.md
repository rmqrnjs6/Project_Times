# 검증 작업 관리 실행 결과

기준일: 2026-11-06 UTC. 대상 PR: https://github.com/rmqrnjs6/Project_Times/pull/2

## 검증 대상과 환경
- 구현 커밋: 7e98c257f0db29e3ec6e86155e209397937948e5.
- GitHub Actions: https://github.com/rmqrnjs6/Project_Times/actions/runs/42166780001
- GitHub-hosted Ubuntu 26.04.1; DB 작업 Java Temurin 21.0.13, PostgreSQL 17.11.
- Docker 웹 빌드는 web/Dockerfile의 Java 21 Maven 빌드, 실제 로그인 통합은 Compose 서비스 사용.
- 이 대화의 executor는 시작 실패로 사용 불가. 로컬 실행 결과를 주장하지 않는다.

## 발견한 문제 → 수정 → 재검증
최초 커밋 6720d8ba439dc5de6f177af7bea4d1f2404fd1ba의 이벤트 INSERT는 열/바인딩이 6개인데 자리표시자가 7개였다.
기대: 작업과 CREATED 이벤트 함께 저장. 실제: PostgreSQL의 "No value specified for parameter 7" 오류.
웹 빌드/MockMvc는 통과했으나 실제 DB 테스트 10개는 준비 단계에서 오류, 인증 통합도 작업 생성 단계에서 실패했다.
최초 실행: https://github.com/rmqrnjs6/Project_Times/actions/runs/42166627549
INSERT 자리표시자를 6개로 수정한 후 동일 테스트들을 재실행했다. 테스트의 기대값을 낮추지 않았다.

## 관찰한 최종 결과
| 작업 | 실제 관찰 | 범위 |
|---|---|---|
| test | 성공 | 기존 Python 테스트 |
| docker-build | 성공, Java 테스트 52개 실패/오류 0 | 기존 보안·입력 검사와 신규 MockMvc 7개 |
| task-database | TaskStoreTest 10개 실행, 실패/오류/생략 0 | 실제 PostgreSQL 소유자 격리·버전·임대·경쟁·롤백 |
| integration | 성공, HTTP 검사 30개 PASS | 실제 로그인·DB, 기존 기능과 신규 작업 생성/조회/취소 |

Docker 빌드에서는 DB 환경변수가 없어 TaskStoreTest 테스트 본문이 실행되지 않는다.
52개와 별도 DB 10개의 의미를 구분해야 한다.
DB 로그의 duplicate key 오류 한 건은 이벤트 INSERT 실패를 주입해 롤백을 확인한 의도된 입력이다.
실제 DB 테스트의 JUnit 원본은 CI의 task-database-test-results 아티팩트로 확인할 수 있다.

## 대표 입력과 기대/관찰
- synthetic 질문 + PRIMARY_REQUIRED: 201, QUEUED, 버전 0, 생성 이벤트 1개. 실제 HTTP에서 일치.
- 취소 버전 7(현재 0): 409. 올바른 버전 0: 204, CANCELLED, 버전 1, 이벤트 2개. 실제 HTTP에서 일치.
- 타인 계정으로 상세/취소: 404, 자기 목록에 타인 작업 없음. 실제 SQL 테스트에서 일치.
- 두 스레드의 claim: 토큰 발급 한 번. 취소/완료 경쟁: 종료 상태와 종료 이벤트 하나. DB 테스트에서 일치.
- 이벤트 INSERT 키 충돌: SQLException 후 작업은 QUEUED/버전 0 유지. DB 테스트에서 일치.
- 만료 임대의 늦은 결과: false. 복구 두 번 호출: FAILED/EXECUTION_INTERRUPTED 이벤트는 한 번. DB 테스트에서 일치.

## 한계
브라우저 버튼 조작·시각/접근성 검증, 실제 프로세스 강제 종료·재시작, 운영 부하·보안 감사는 수행하지 않았다.
임대 만료는 DB 시간을 과거로 변경해 결정적으로 재현했다. 실제 재시작 실험과 동일하다고 주장하지 않는다.
AI 실행기는 미연결이다. 일반 사용자가 등록한 작업은 대기하며, 성공 결과 테스트는 합성 JSON이다.
이번 결과는 명시된 사례에 대한 증거이며 전체 서비스 품질이나 사실 판별 정확도의 보장이 아니다.
