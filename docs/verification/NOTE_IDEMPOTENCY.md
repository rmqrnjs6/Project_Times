# 수동 근거 기록의 중복 저장 방지

기준일: 2026-11-09 KST. 모드: combined. 기준 SHA dc908f8, 브랜치 feat/manual-note-idempotency. refactor/config-and-input-limits 위의 후속 변경이며 PR #4의 병합 여부와 별개다. 기존 실행 서비스는 반영하지 않았고 격리 서버에만 적용했다.

## 문제와 재현

TaskStore.addNote는 요청마다 새 UUID를 만들고 INSERT했다. 화면의 버튼 비활성화는 빠른 연속 클릭만 줄일 뿐, 서버가 저장한 뒤 응답이 유실되어 재전송하면 새 기록이 추가되는 것을 막지 못한다. 같은 요청 번호를 헤더에 보내도 기존 Controller는 이를 읽지 않았다.

수정 전 격리된 기존 서버에 같은 Idempotency-Key와 같은 합성 본문을 두 번 보냈다. 기대: 같은 기록 ID. 실제: 서로 다른 ID, same-key retry returns original note ID 단언 FAIL. 원본 재현 로그는 artifacts/review/note-idempotency-red.log다. 내용이 우연히 같은 기록과 요청 재전송을 구분해야 하므로 본문 해시만으로 중복을 삭제하는 방법은 선택하지 않았다.

## 입력 → 처리 → 출력

1. workbench.js의 noteSubmission이 원문·출처·해석·판단의 SHA-256 지문과 무작위 UUID 요청 번호를 sessionStorage에 보관한다. 본문 자체는 보관하지 않는다. 지문은 보안 인증/익명화/진실 증명이 아니라 같은 입력인지 구분하는 용도다.
2. 같은 작업의 미확정 입력이 같으면 기존 번호를 쓴다. 정상 저장 응답을 받으면 대기 번호를 지운다. 내용이 달라지거나 성공 후 새 저장이면 새 번호다.
3. request 함수가 기존 CSRF 토큰과 Idempotency-Key 헤더를 전송한다. TaskController는 헤더를 TaskService에 전달한다.
4. TaskService는 기존 입력 검사에 더해 요청 번호의 정규 UUID 표기를 검사한다. 잘못된 번호는 400 invalid_idempotency_key다. 요청 본문의 소유자 값은 신뢰하지 않는다.
5. TaskStore는 소유자 조건으로 작업을 SELECT FOR UPDATE한 뒤 같은 작업·요청 번호의 기록을 찾는다. 기존 출처·원문·해석·판단이 모두 같으면 기존 ID를 반환한다. 다르면 409 idempotency_key_conflict이며 덮어쓰지 않는다.
6. 최초 요청만 INSERT/commit한다. 같은 작업의 동시 요청은 기존 작업 행 잠금으로 직렬화한다. V5의 부분 고유 인덱스가 DB에서도 중복 번호를 방어한다. 번호는 작업별 범위이며 타인 작업은 번호를 알아도 404다.

HTTP 재전송 성공 응답은 최초와 같은 201 및 ID다. 기존 기록의 본문·작성 시각은 바꾸지 않는다. API 호환을 위해 헤더가 없으면 종전의 새 기록 저장을 유지한다. 요청 번호를 보내는 클라이언트와 새 화면만 보호 대상이다.

## 실제 수정 파일

- TaskController.java: 선택적 Idempotency-Key 헤더 수신.
- TaskService.java: UUID 검증 및 기존 입력·소유자 흐름 유지.
- TaskStore.java: addNote의 키 조회·내용 충돌 검사·재사용·트랜잭션. 기존 키 없는 호출은 null 요청 번호로 연결한다.
- V5__note_idempotency.sql: request_id nullable 열과 (task_id,request_id) 부분 고유 인덱스를 추가한다. V1~V4와 기존 기록을 수정하지 않는다.
- workbench.js: 한 탭의 미확정 요청 번호 보관, 재시도 시 재사용, 동시 이벤트 차단, 저장 성공과 이후 목록 조회 실패 구분. 저장소 접근이 차단되면 추적 불가능한 요청을 보내지 않고 오류를 표시한다.
- TaskMvcTest.java, TaskStoreTest.java: 형식 검사·내용 충돌·소유자·동시성·의도적 반복 검증.
- tests/ui_note_idempotency.test.cjs: 실제 화면 JS를 Node VM과 가짜 DOM/fetch/sessionStorage로 실행한다. 브라우저 클릭 검증이나 실제 네트워크 응답 유실 시험은 아니다.
- scripts/integration_check.py: 같은 번호 재전송·다른 내용 충돌·새 번호의 새 기록·기록 수·잘못된 번호를 실제 HTTP에서 검사한다.
- .github/workflows/ci.yml: Node 22 화면 로직 테스트 추가. 로컬 Node는 v24.14.1이므로 환경 차이를 구분한다.

## 이번 검증

- PASS: Java 웹 빌드 65건, 실패/오류/생략 0. DB가 필요한 TaskStoreTest 본문은 이 빌드에서 실행하지 않았다.
- PASS: 별도 PostgreSQL DB의 TaskStoreTest 15건, 실패/오류/생략 0. 기존 12건과 새 요청 재사용·충돌/격리·의도적 반복/작업 범위·동시 저장 3건이다. 같은 키 동시 저장은 ID 하나와 기록 한 건을 확인했다.
- PASS: Node VM 화면 로직 5건. 응답 유실 후 재시도, 같은 탭 재시작을 모의한 재로딩, 내용 변경, 동시 제출, 저장소 접근 차단을 검사했다. 성공 후 같은 내용을 의도적으로 다시 저장하면 다른 번호가 생성되는 것도 확인했다.
- PASS: 격리된 실제 HTTP 검사 41개. 원문/해석 보존, 기존 로그인·CSRF·권한·계산 기능까지 회귀 확인했다.
- PASS: 격리 웹 컨테이너를 실제 restart한 뒤 재로그인했다. 같은 번호/내용은 기존 ID를 반환하고 작업 기록은 한 건이었다. 재시작 검증 2개 핵심 단언은 HTTP 41개에 합산하지 않는다.
- PASS: node --check 및 git diff --check.
- NOT RUN: 실제 Edge 클릭·접근성·모바일, 실제 패킷 유실, 운영 부하/보안 감사, 원격 CI 결과 확인. Python 로직은 수정하지 않았다.

Docker Engine 29.5.3, Java 21, PostgreSQL 17 이미지. times-note-baseline 프로젝트의 별도 DB/합성 계정/포트 18082에서 수정 전후를 실행했다. 실제 서버 재시작도 이 테스트 프로젝트만 대상으로 했다. Docker가 꺼져 있어 docker desktop start로 엔진을 시작했으며 기존 서비스 컨테이너를 재빌드/재생성하지 않았다. 기존 컨테이너의 restart 정책에 따른 시작 여부와 새 코드 반영은 구분한다.

원본 로그/JUnit은 artifacts/review/note-idempotency-*에 보관한다. 비밀 환경 파일은 Git 제외 대상이다. 공개 결과·원본 해시는 note-idempotency-results.txt에 기록한다. 테스트 종료 시 격리 컨테이너와 네트워크만 종료하고 볼륨은 보존한다.

## 제한과 복구

- 보호 범위는 수동 근거 기록 POST다. 작업 등록·진단 질문·계산 저장을 중복 방지하지 않는다.
- sessionStorage는 같은 탭의 새로고침에 유지되지만 탭 종료·저장소 삭제·다른 기기는 보장하지 않는다. 본문은 자동 복구하지 않아 새로고침 후 다시 입력해야 한다. 한 작업에는 한 개의 미확정 입력 지문을 보관한다.
- 다른 요청 번호로 들어온 같은 내용은 새 기록이다. 저장이 불확실한 상태에서 내용을 바꾸면 새 번호가 되어 기존 기록과 별개로 저장될 수 있으므로 상세를 먼저 확인한다.
- 요청 번호는 인증 수단이 아니며 DB 고유 인덱스만으로 소유자 검사를 대체하지 않는다. CSRF·출처·역할·소유자·원본 보존 정책은 유지했다.
- 기존 실행 서비스는 미배포라 즉시 복구할 변경이 없다. 향후 코드 롤백 시 이미 적용한 V5 파일과 Flyway 이력/추가 열·인덱스를 유지해야 한다. 마이그레이션을 삭제하거나 DB 이력을 지우지 않는다. 키 없는 기존 호출은 유지되므로 데이터 삭제 없이 코드만 복구하는 방안을 검토할 수 있다.

학습 포인트: 멱등성은 같은 요청을 반복해도 부작용이 한 번만 발생하도록 하는 성질이다. 버튼 잠금, 내용 중복 제거, 요청 재전송 방지는 서로 다르다. 이번 기능은 요청 번호와 DB 트랜잭션을 결합해 네트워크 재시도로 원본 기록이 불필요하게 늘어나는 것을 막는다.
