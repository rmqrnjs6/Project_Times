# 수동 근거 검토 기능과 실사용 범위

기준일: 2026-11-06 KST. 프로젝트: Project_Times. 대상: 로컬 개인 검토 작업대.

## 문제와 변경 이유

기존 WorkbenchService.saveCheck는 연결 확인용 질문만 저장했다. 질문별 원문, 출처, 해석, 판단의 기록이나 내보내기가 없어 검토 업무에 바로 쓰기 어려웠다. 기존 계산기는 JSON 수량을 산술 처리할 뿐 자료를 분석하지 않는다. 별도 feat/verification-task-lifecycle 브랜치의 작업 관리 기능을 작업 브랜치에 통합하고 수동 기록 기능을 연결했다. main은 변경하지 않는다.

## 코드 읽는 순서

1. `web/src/main/webapp/WEB-INF/views/index.jsp`: 작업 선택, 출처·원문·해석 입력 폼, 수동 판단 선택, JSON 내보내기. 작업 선택 전 입력을 비활성화한다.
2. `web/src/main/resources/static/workbench.js`: showTask로 상세·기록을 읽고 note-form 제출에서 POST /api/tasks/{id}/notes를 호출한다. 화면에는 textContent로 원문을 표시한다. HTML 태그를 입력해도 실행 가능한 HTML로 삽입하지 않는다. CSRF 토큰은 기존 공통 request 함수가 보낸다.
3. `TaskController.java`: 세션 Principal의 이름을 소유자로 사용한다. 요청 본문의 owner는 권한 기준이 아니다. notes는 수동 기록·비신뢰·다운로드 미수행으로 응답한다. export는 no-store와 attachment를 지정한다.
4. `TaskService.java`의 note: 원문과 해석은 각각 1~4,000자, 출처 주소는 2,048자 이내다. HTTPS와 호스트가 필요하며 사용자정보·쿼리·fragment·비표준 포트를 거부한다. 판단은 SUPPORTS, CONTRADICTS, UNVERIFIED만 받는다. 원문을 strip하지 않아 앞뒤 공백도 보존한다.
5. `TaskStore.java`의 addNote: 소유자 조건으로 작업 행을 잠근 후 기록을 INSERT한다. 실패 시 rollback한다. detail은 작업 행의 공유 잠금 안에서 이력과 기록을 읽는다. 기록 추가는 상태 전환과 구분하며 대기 작업을 검증 성공으로 바꾸지 않는다.
6. `V4__manual_review_notes.sql`: 기존 테이블을 삭제하지 않고 기록 전용 테이블과 인덱스를 추가한다. source_url, original_text, interpretation, stance, author, created_at을 분리 보관한다. 기존 마이그레이션은 수정하지 않는다.

## 원본 보존과 CIA 적용

- 기밀성: 로그인·소유자별 조회/추가/내보내기, CSRF 유지, 비밀이 섞이기 쉬운 URL 쿼리·인증정보 거부, 내보내기 캐시 방지.
- 무결성: 원문과 해석을 별도 저장하고 정정은 새 기록으로 남긴다. 기존 기록 수정·삭제 HTTP 경로는 제공하지 않는다. DB 관리자의 직접 변경까지 방지하는 불변 저장소는 아니다.
- 가용성: 기존 본문 32KiB 제한과 DB 연결·컨테이너 자원 제한을 유지한다. 외부 사이트 요청이나 AI 호출을 추가하지 않는다. 대규모 목록·페이지네이션·보관 정책은 후속 과제다.
- 출처 주소는 참고 문자열이다. 기존 /api/materials의 격리 수집 허용 목록과 별개이며 자동 수집 권한을 부여하지 않는다. 서버는 이 주소에 DNS 조회나 다운로드를 하지 않고 화면에서도 링크를 자동 실행하지 않는다. 해시나 사람이 적은 판단만으로 사실·저작권·전문가 인증을 보장하지 않는다.

## 실행 검증

- 웹 이미지 빌드: 58건 실행, 실패·오류·생략 0. MVC 보안·입력 검사이며 이 빌드에서 PostgreSQL TaskStoreTest 본문은 DB 환경변수가 없어 실행되지 않았다.
- 별도 PostgreSQL 테스트 DB: TaskStoreTest 12건 실행, 실패·오류·생략 0. 기존 상태 경쟁·취소·임대·트랜잭션 10건과 수동 기록의 원문 보존/정정, 소유자 차단/저장 실패 rollback 2건이다.
- 실제 서비스 HTTP: 35개 PASS. 실제 DB 인증·CSRF 회전·기존 기능·작업 생성/취소·수동 기록·원문과 해석의 별도 조회·private JSON export·민감 쿼리 거부·로그아웃을 확인했다. 입력은 합성 자료다. 브라우저를 직접 클릭한 결과는 아니다.
- JS: node --check 통과. 화면 시각·접근성·실제 다운로드 UI는 사용자 확인이 남아 있다.
- DB 변경 전 pg_dump를 로컬 artifacts/review/pre_manual_review.sql에 보관했다. 비밀번호 해시 등 민감 자료가 포함될 수 있어 공개하지 않는다. 별도 테스트 DB는 review_notes_20261106215825로 남겼다.
- 원본 로그: artifacts/review/manual-review-build-final.log, manual-review-database.log, manual-review-http.log. JUnit XML: artifacts/review/manual-review-junit/. Git에는 비밀을 제외한 요약만 올린다.

발견·수정: 최초 빌드에서 새 테스트의 JSON 문자열 따옴표 오류를 발견해 ObjectMapper 직렬화로 바꾸고 재빌드했다. 초기 한글 입력은 Windows 파이프 인코딩 때문에 손실되어 UTF-8로 수정하고 파일·응답을 확인했다. HTTP 검증에 /login 선행 요청을 추가하면서 Cookie가 먼저 발급되어 기존 쿠키 단언이 실패했다. 선행 응답도 검사하도록 테스트를 수정했고 보안 기준은 낮추지 않았다.

## 지금 사용할 수 있는 일과 남은 일

질문별로 수동 자료를 모으고 지지·반박·미확인을 구분하며 원문과 판단을 보존하는 개인 기록 용도로 사용할 수 있다. 자동 판별·실제 AI 응답 비교·독립 전문가 승인·출처 다운로드·서비스 운영 준비 완료를 뜻하지 않는다.

작업 목록은 최근 50개다. 기록 수 제한·중복 제출 방지·검색/페이지네이션·기록 간 명시적인 정정 연결·수동 검토 완료 상태는 아직 없다. 네트워크 오류 직후 재전송하기 전 상세를 확인해야 중복 기록을 피할 수 있다. AI 실행 상태는 대기이며 수동 기록과 별개다. 별도 TLS·MFA·계정 복구·DB 최소 권한·운영 백업 복구는 후속 범위다.

다음 논의는 이 화면의 수동 사용성 확인 후, 실제 AI 연동 또는 기록 검색·수동 검토 상태 중 우선순위를 정한다. 외부 AI 연결에는 API 계정·비용·데이터 전송 범위 결정이 필요하다.
