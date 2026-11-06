# 검증 작업 관리: 구현·QA·코드 리뷰 안내

## 이번 범위와 실행 여부
질문과 근거 조건을 작업으로 등록하고, 소유자별 목록·상세·취소·상태 이력을 제공한다.
AI 호출이나 자동 실행기는 연결하지 않았다. 실제 사용자 작업은 QUEUED에서 대기하며 취소할 수 있다.
RUNNING/성공/실패는 다음 실행기가 사용할 내부 저장소 경계이며 합성 결과로 테스트한다.
근거 조건은 요청 의도를 보존하는 값이다. 현재 출처 검증을 수행하거나 충족을 보장하지 않는다.
로그인과 기존 질문 저장·계산 API는 계속 별도로 사용한다.

작업 환경의 executor 시작 실패로 로컬 Java/Docker/브라우저 검증을 수행하지 못했다.
원격 실행 결과와 최초 실패·수정·재검증은 [작업 관리 검증 기록](verification/TASK_RESULTS.md)에 남겼다.
자동 브라우저 조작, 실제 프로세스 강제 종료·재시작, 실제 AI 결과는 이번 테스트 대상이 아니다.

## 요청이 코드를 통과하는 순서
1. SecurityConfig / RequestSizeFilter: 인증·역할·CSRF·Origin·본문 크기 검사.
2. TaskController: 요청 JSON을 받고 소유자는 Principal에서 가져온다. 입력 owner/status는 신뢰하지 않는다.
3. TaskService: 질문과 근거 조건·UUID·취소 버전을 검증한다.
4. TaskStore: 소유자 조건을 포함한 SQL, 상태 변경, 이력 저장을 수행한다.
5. V3 마이그레이션: 상태·결과·실행 임대의 제약을 DB에도 적용한다.
6. workbench.js: textContent로 입력을 표시하고 서버 버전과 함께 취소한다.

Controller를 얇게 두는 이유는 HTTP 처리와 업무 규칙을 구분하기 위해서다.
Service의 검사는 사용자가 이해할 오류로 바꾸기 위한 것이고, DB 제약은 잘못된 저장 자체를 막는 마지막 경계다.
PreparedStatement는 입력을 SQL 문법과 분리한다. 소유자 검사는 SQL의 WHERE 조건으로 시행한다.

## API 계약
| 요청 | 성공 | 주요 오류 |
|---|---|---|
| POST /api/tasks {question,evidenceRequirement} | 201 + Location + id/status/version | 400 입력, 401 인증, 403 CSRF/Origin |
| GET /api/tasks | 최근 50개, 최신순 | 401 인증 |
| GET /api/tasks/{UUID} | 원본 조건·상태·결과·실패 코드·전체 이벤트 | 400 UUID, 404 없음/타인 소유 |
| POST /api/tasks/{UUID}/cancel {version} | 204 | 400 버전, 404 없음/타인, 409 오래된 버전/종료 상태 |

question: 공백만 제외, Java 문자열 길이 최대 2000, 앞뒤 공백 제거 후 저장.
evidenceRequirement: PRIMARY_REQUIRED 또는 CROSS_CHECK_REQUIRED.
목록 최대 50개는 명시적 초기 범위다. 페이지 이동·검색·중복 요청 키는 후속 범위다.
버튼 중복 클릭은 비활성화하지만 네트워크 재전송의 생성 중복까지 보장하지 않는다.

## 상태 규칙
| 현재 | 다음 | 주체 | 조건 |
|---|---|---|---|
| 없음 | QUEUED | 소유자 | 유효한 질문·조건 |
| QUEUED | RUNNING | 내부 실행기 | claim 1개만 성공 |
| QUEUED/RUNNING | CANCELLED | 소유자 | 예상 버전 일치 |
| RUNNING | SUCCEEDED | 내부 실행기 | 유효한 임대 토큰·기간, JSON 객체 결과 |
| RUNNING | FAILED | 내부 실행기 | 유효한 임대, 허용된 실패 코드 |
| RUNNING | FAILED | 복구 처리 | 임대 만료, EXECUTION_INTERRUPTED |

종료 상태는 수정하지 않는다. 재시도 기능은 이번에 만들지 않는다.
완료나 실행 시작을 일반 사용자가 요청할 HTTP 경로는 없다.
취소는 저장된 상태 변경이다. 다음 단계의 외부 호출을 물리적으로 중단하는 기능은 아니다.
원본 질문·조건은 상태 변경 시 UPDATE하지 않는다.

## 코드 리뷰에서 따져볼 네 가지
### 상태와 이력이 함께 저장되는가?
TaskStore.transaction은 autoCommit을 끄고 상태 UPDATE와 event INSERT 후 commit한다.
INSERT가 실패하면 rollback한다. 단순히 두 SQL을 순서대로 실행하는 것만으로는 원자성이 없다.
TaskStoreTest.failedEventInsertRollsBackStatusUpdate는 의도적으로 이벤트 키 충돌을 만들어 실제 롤백을 확인한다.

### 동시 요청 중 누가 이기는가?
claim은 WHERE status='QUEUED'인 UPDATE 하나로 경쟁한다. 승자만 임대 토큰을 받는다.
cancel은 FOR UPDATE로 행을 잠근 다음 버전과 상태를 검사한다.
finish는 RUNNING·토큰·임대 기간을 SQL 조건으로 함께 검사한다.
취소와 완료 중 먼저 성공한 상태가 유지되고 나중 요청은 충돌 또는 false를 받는다.
외부 네트워크 작업 중 DB 잠금을 유지하지 않도록 실행기 경계를 분리했다.

### 타인의 ID를 알면 접근할 수 있는가?
목록·상세·취소는 항상 인증 소유자 조건을 사용한다.
UUID는 접근 권한이 아니다. 타인 작업도 없는 작업과 같은 404를 반환한다.
MockMvc는 Principal 전달을, PostgreSQL 테스트는 실제 소유자 WHERE 조건을 각각 확인한다.
둘 중 하나만으로 전체 인증·인가를 검증했다고 주장하지 않는다.

### 재시작과 중단은 어떻게 구분하는가?
실행기는 claim 시 DB에 120초 임대를 저장한다. 현재 임대 연장·자동 재개는 없다.
소유자가 목록·상세·취소 API를 호출할 때 만료된 RUNNING을 FAILED로 정리한다.
즉시 프로세스 사망 감지가 아니라 임대 만료 후 조회 시 감지하는 방식이다. QUEUED는 그대로 보존한다.
이전 실행기의 늦은 응답은 토큰과 만료 검사로 거부한다.
향후 120초 이상 실행에는 heartbeat/lease 연장과 실행 시간 예산을 함께 설계해야 한다.
단일/다중 인스턴스 모두 다른 프로세스의 정상 RUNNING을 시작 시 일괄 실패 처리하지 않는다.
DB가 끊기면 접근은 기존 공통 처리에 따라 503으로 실패하며, 복구 감지는 DB 복구 후 가능하다.

## 테스트를 읽는 법
입력 → 기대값 → 실제 관찰 → 남는 한계 순서로 읽는다.

| 위험 | 테스트 입력/행동 | 기대값 | 검증 계층 |
|---|---|---|---|
| 위조 소유자·완료 상태 | alice 세션, owner=bob/status=SUCCEEDED | alice의 QUEUED로만 생성 | TaskMvcTest |
| 인증·CSRF 누락 | 비로그인 GET, CSRF 없는 POST | 401/403, 저장소 미호출 | TaskMvcTest |
| 입력 오류 | 공백·잘못된 조건·UUID·버전 | 400, 저장소 미호출 | TaskMvcTest |
| 타인 데이터 노출 | bob이 alice UUID 조회·취소 | 404, alice 데이터 유지 | TaskStoreTest |
| 오래된 화면 | 현재 버전과 다른 취소 버전 | 409, 이벤트 추가 없음 | TaskStoreTest |
| 이력 기록 실패 | 다음 이벤트 버전 충돌 주입 | 상태 UPDATE 롤백 | TaskStoreTest |
| 중복 실행 | 두 스레드 동시 claim | 한 토큰만 발급 | TaskStoreTest |
| 취소/완료 경쟁 | 동시 cancel/finish | 종료 상태·종료 이벤트 하나 | TaskStoreTest |
| 중단 후 늦은 응답 | 임대를 과거로 변경 후 finish | false, 복구 시 FAILED 1회 | TaskStoreTest |
| 실제 연결 | 로그인 후 생성·조회·취소 | DB 입력/이력 보존, HTTP 계약 일치 | integration_check.py |

JUnit DB 테스트는 TASK_TEST_DB_URL이 없으면 건너뛴다.
따라서 Docker 웹 빌드 성공만으로 PostgreSQL 테스트 통과를 주장하면 안 된다.
CI의 task-database 작업이 PostgreSQL 17에서 실제 마이그레이션·잠금·롤백을 검증한다.
상태 결과 테스트의 synthetic JSON은 AI 판별 결과가 아니다.
기존 통합 스크립트는 실제 로그인·DB와 새 작업 API를 함께 확인한다.

## 직접 확인할 순서
1. 로그인 → 질문/근거 조건으로 작업 등록 → 대기와 생성 이력 확인.
2. 목록을 두 창에서 열고 한 창에서 취소 → 다른 창의 오래된 취소가 409인지 확인.
3. 상세에서 원본 질문·근거 조건이 유지되고 이벤트가 생성/취소 순인지 확인.
4. 질문에 script 태그를 입력 → 목록에서 문자로 표시되는지 확인.
5. 로그아웃 → API 접근 차단 확인.
6. PR의 task-database / docker-build / integration 결과와 테스트 로그를 읽는다.

현재 상세 화면은 QA 점검용 JSON 표시다. 사용자 친화적 단계 설명·자동 갱신·페이지 이동은 후속 개선이다.
완전한 품질 보장은 하지 않는다. 이 테스트는 명시된 입력·경쟁·실패 조건에 대한 증거다.
