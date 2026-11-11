# Java 클래스별 구조와 요청 흐름

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 읽는 순서와 파일별 책임

| 파일 | 책임·핵심 동작 | 연결 대상 |
|---|---|---|
| [Application.java](Application.java) | Spring Boot 시작, 실행 WAR 진입 | Spring 설정 |
| [SecurityConfig.java](SecurityConfig.java) | DB 사용자 조회, BCrypt 12, 역할·CSRF·세션·HTTPS·헤더 | 두 필터, 로그인 저장소, 감사 |
| [RequestSizeFilter.java](RequestSizeFilter.java) | 변경 요청의 Origin 검사, 32 KiB JSON 본문 제한 | CSRF 필터 앞 |
| [LoginAttemptFilter.java](LoginAttemptFilter.java) | POST /login 예산 확인, 초과 429·저장소 실패 503 | LoginAttemptStore |
| [LoginAttemptStore.java](LoginAttemptStore.java) | 원격 주소 해시별 DB 원자적 로그인 카운터·성공 시 초기화 | login_attempts |
| [AuthBootstrap.java](AuthBootstrap.java) | 최초 ADMIN 계정 생성; 기존 계정은 변경하지 않음 | 사용자·역할 테이블 |
| [SecurityAudit.java](SecurityAudit.java) | 로그인 성공·실패 이벤트 INSERT | security_events |
| [PageController.java](PageController.java) | / → index, /login → login 뷰 이름 반환 | JSP |
| [WorkbenchController.java](WorkbenchController.java) | HTTP 경로·JSON 입력·Principal 연결 | WorkbenchService |
| [WorkbenchService.java](WorkbenchService.java) | 질문 검사·산술 요청·자료 검사·저장 순서 | Database, AnalysisService, ExternalMaterialPolicy |
| [AnalysisService.java](AnalysisService.java) | 내부 Python HTTP 호출; 연결 3초·요청 5초 | ANALYSIS_URL |
| [Database.java](Database.java) | JDBC 바인딩, 현재 소유자의 최근 질문 50개 조회, UUID 저장 | PostgreSQL |
| [ExternalMaterialPolicy.java](ExternalMaterialPolicy.java) | 허용 HTTPS 출처·text/plain 검사, UTF-8 SHA-256 | 격리 접수 |
| [RequestFailure.java](RequestFailure.java) | 예상 HTTP 실패의 상태와 공개 오류 코드 | ApiErrors |
| [ApiErrors.java](ApiErrors.java) | JSON·미디어·메서드·의존성 오류를 안전한 응답으로 변환 | Controller 예외 |

## 요청 경로와 결과

| 요청 | 입력·전제 | 결과 |
|---|---|---|
| GET /ready | Boot 준비 후 DB·Python 준비 확인 | 200 또는 503 |
| GET /api/csrf | 로그인 전에도 허용 | 세션의 CSRF 토큰·헤더/필드 이름 |
| GET /api/checks | USER/ADMIN | 현재 계정의 질문 목록 |
| POST /api/checks | question 1~2,000자, 인증·CSRF | 201, UUID, connectivity_only |
| POST /api/evaluate | Python 판정 수량 JSON | 201, 산술 결과·run_id; 스키마 오류 422 |
| POST /api/materials | sourceUrl/content/mediaType | 201, quarantined·trusted=false·fetched=false |
| GET /api/admin/security | ADMIN | 인증·자료 격리 방식 설명 |
| POST /logout | 세션·CSRF | 세션 폐기 후 로그인 화면 이동 |

질문 저장은 Python 준비를 확인한 뒤 DB에 INSERT합니다. 평가 요청은 Python 결과에 policy_version이 있는지 확인하고 원본 입력·결과·정책·소유자를 저장한 다음 run_id를 추가합니다. 소유자는 요청 본문이 아니라 인증 Principal에서 가져옵니다.

## 인증·실패의 의미

로그인 예산은 원격 주소 기준 15분에 10회이며, 11번째부터 차단합니다. 현재 Nginx 경유 요청은 Java에서 같은 진입점 주소로 보일 수 있어 예산을 공유합니다. X-Forwarded-For를 신뢰하지 않습니다. 성공 시 세션 ID를 바꾸며 계정당 세션을 하나로 제한합니다.

로그인 성공 후 예산 초기화·감사 저장이 실패하면 세션을 무효화합니다. 일반 예외는 세부 정보 대신 dependency_unavailable로 응답합니다. 감사 저장소는 서명된 불변 로그가 아니며 모든 관리자 행위를 포괄하지 않습니다.

자료 출처는 정확한 허용 호스트만 받고 기본값은 거부입니다. 본문은 최대 UTF-8 20,000바이트입니다. DNS·URL 다운로드·리다이렉트·바이너리 파서는 실행하지 않습니다. 코드 변경 시 [보안 설계](../../../../../../docs/security/DESIGN.md)와 [테스트 설명](../../../../test/java/dev/projecttimes/README.md)을 확인하세요. 검증 실행은 web 폴더에서 mvn verify입니다.

[전체 코드 읽기 지도](../../../../../../docs/CODE_GUIDE.md)
