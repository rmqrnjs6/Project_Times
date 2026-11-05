# 로그인 및 외부 자료 보호 설계

기준일: 2026-11-06 (Asia/Seoul). 이번 변경의 범위는 Spring Boot MVC 전환, 실제 로그인 보호, Mock 기반 부정 조건 검증, 외부 텍스트의 격리 접수다. 여기서 CIA는 **기밀성(Confidentiality), 무결성(Integrity), 가용성(Availability)**을 뜻한다. 보안을 완전히 보장하거나 인증을 취득했다는 뜻이 아니다.

## 1. 읽는 순서와 계층

1. `web/pom.xml`: Java 21, Spring Boot 3.5.16의 MVC·Security·JDBC·테스트 의존성. 프레임워크 관리 버전을 사용한다.
2. `Application`: Spring Boot 시작 지점. WAR를 실행 가능한 형태로 빌드한다. 이전 직접 Servlet 등록은 제거했다.
3. `SecurityConfig`: 요청이 Controller에 도달하기 전의 인증·역할·CSRF·세션·HTTPS·헤더 정책.
4. `LoginAttemptFilter` → `LoginAttemptStore`: 로그인 예산을 PostgreSQL에서 원자적으로 확인한다.
5. `PageController`: JSP 화면 선택. JSP는 `WEB-INF/views` 안에 있어 직접 URL로 실행할 수 없다.
6. `WorkbenchController`: HTTP 입력과 로그인 주체를 받고 상태 코드로 응답한다.
7. `WorkbenchService`: 질문·계산·자료 접수의 업무 흐름. `Database`에 현재 인증 주체를 넘긴다.
8. `Database`: 파라미터 바인딩으로 저장·소유자 조건 조회. `AnalysisService`는 기존 내부 Python 계산기와 통신한다.
9. `V2__authentication_and_materials.sql`: 사용자·역할·로그인 예산·보안 이벤트·자료 격리와 소유자 필드의 변경 이력.

브라우저 요청 → 보안 필터 → MVC Controller → Service → Repository/Python → 응답 순서다. JSP는 표시를 담당하며 권한을 결정하지 않는다.

## 2. Mock의 역할

Mock은 테스트에서만 사용한다. `src/test/java`의 `SecurityMvcTest`는 DB·분석 서비스·사용자 저장소·예산·감사 저장소를 Mockito로 대체한다. 실제 Spring Security 필터·CSRF 처리·BCrypt 검증·세션 로그인은 그대로 실행한다. `with(user(...))` 테스트는 역할·접근 경계를 검사하고, `formLogin` 테스트는 별도로 비밀번호 검증 경로를 실행한다.

따라서 Mock 테스트 통과가 실제 DB·브라우저·네트워크의 안전을 의미하지 않는다. 실제 DB 로그인과 컨테이너 HTTP 검증, 분리된 클라이언트의 로그인 예산 검증을 따로 실행한다. 실행 앱에는 Mock 사용자·인증 우회 URL·기본 공개 비밀번호가 없다. 테스트용 합성 비밀번호는 테스트 소스에만 있다.

## 3. 로그인 흐름과 기밀성

1. `/login` 페이지가 세션에 연결된 CSRF 토큰을 제공한다.
2. 로그인 POST는 CSRF 확인을 먼저 통과해야 한다. Origin이 있는 변경 요청은 현재 출처와 일치해야 한다.
3. 로그인 필터가 서버가 관찰한 원격 주소의 예산을 확인한다. `X-Forwarded-For`는 신뢰하지 않는다.
4. JDBC 사용자 조회와 BCrypt strength 12 해시 대조를 수행한다. 비밀번호 원문은 DB에 저장하지 않는다.
5. 성공 시 세션 ID를 변경하고 성공 이벤트를 남긴다. 감사 저장 실패 시 세션을 무효화해 접근을 허용하지 않는다.
6. 기존 CSRF 토큰은 인증 후 폐기된다. 이후 변경 요청은 새 토큰을 사용한다.

계정 존재 여부와 비밀번호 오류는 같은 공개 실패 경로를 사용한다. 세부 DB 예외나 비밀번호는 응답·진단 로그에 기록하지 않는다. 구현은 비밀번호 인증이며 MFA·패스키는 아직 없다. 동일한 실패 화면이 완전한 시간차 공격 방어를 입증하지는 않는다.

세션은 HttpOnly·SameSite=Strict, 15분 비활성 만료, 쿠키 기반 추적을 사용한다. URL 세션 ID를 사용하지 않는다. 계정당 동시 세션은 1개로 제한해 새 로그인 시 이전 세션이 만료된다. 로그아웃은 CSRF가 필요한 POST이며 서버 세션을 무효화한다. 웹 재시작 후 로그인 세션은 복구하지 않으므로 다시 로그인해야 한다.

기본 프로파일은 HTTPS를 요구하고 Secure 쿠키를 사용한다. 현재 Compose는 **127.0.0.1에만 노출되는 local 프로파일**을 명시해 개발용 HTTP와 Secure=false를 사용한다. 이는 운영 설정이 아니다. 실제 TLS 인증서·프록시 구성은 이번 작업에서 검증하지 않았다.

최초 계정은 `.env`의 `BOOTSTRAP_USERNAME`과 `BOOTSTRAP_PASSWORD`로 한 번 생성한다. 비밀번호가 16~64 UTF-8 바이트가 아니거나 누락되면 기본 계정을 만들지 않는다. 이미 존재하는 계정의 비밀번호를 환경변수 변경만으로 덮어쓰지 않는다. 계정 생성 후 비밀번호 재설정·회수·복구 절차는 후속 논의 대상이다. `.env`에는 초기 비밀번호 원문이 있으므로 로컬 비밀 파일로 취급하며 저장소에 넣지 않는다.

## 4. 권한과 데이터 경계

미인증 API는 401, 페이지는 로그인 화면으로 이동한다. USER/ADMIN만 업무 API에 접근하며 관리자 진단 경로는 ADMIN으로 제한한다. 다른 경로는 기본 거부한다. JSP로 향하는 내부 forward만 허용하고 API forward에 인증 예외를 주지 않는다.

질문 조회 조건은 `WHERE owner_key = 현재 인증 이름`이다. 등록·계산·자료의 소유자도 세션에서 가져오며 요청 본문의 owner 값을 사용하지 않는다. ADMIN도 기존 질문 조회에서 다른 사용자 데이터를 자동으로 열람하지 않는다.

이전 행은 `legacy-unassigned`로 보존했다. 어느 사용자 소유인지 확인하지 않고 현재 계정에 일괄 귀속하지 않았다. 화면에서 이전 행이 보이지 않는 것은 삭제가 아니다. 소유권 이관 정책은 추후 확인해야 한다.

## 5. 외부 자료와 CIA

**기밀성:** 인증·CSRF·소유자 조건을 적용한다. URL의 사용자 정보·쿼리·fragment를 거부해 자료 URL에 토큰·비밀번호를 붙여 저장하는 경로를 제한한다. 자료를 Python·AI·외부 서비스로 자동 전송하지 않는다. 바이너리 업로드는 비활성화했다.

**무결성:** 원본 텍스트·출처 URL·수집 시각·소유자·UTF-8 바이트의 SHA-256을 함께 INSERT한다. 접수 상태는 `quarantined`, `trusted=false`, `fetched=false`다. 허용 출처나 해시 일치가 내용의 진실을 증명하지 않는다. 출처 URL을 실제로 내려받거나 원문과 대조한 것도 아니다. 현재 애플리케이션은 이 자료를 수정·실행·신뢰 승격하는 API를 제공하지 않는다.

**가용성:** HTTP JSON 본문 32 KiB, 텍스트 20,000바이트, 출처 URL 2,048문자 제한을 적용한다. 자료 형식은 text/plain만 허용하고 NUL을 거부한다. HTML처럼 보이는 문자열도 실행하지 않고 격리 데이터로 저장한다. PDF·ZIP·XML·이미지 파서나 자동 미리보기는 연결하지 않았다. 따라서 바이러스 검사나 CDR을 완료했다고 주장하지 않는다.

허용 호스트는 정확히 일치하는 HTTPS 출처만 받고, 기본 허용 목록은 비어 있어 접수를 거부한다. HTTP·file 스킴, 로컬/리터럴 IP, 다른 포트, 사용자 정보, suffix 위장 도메인을 차단한다. 이 기능은 **수동 텍스트 접수**이며 네트워크 수집기가 아니다. DNS 해석·리다이렉트 추적·외부 다운로드를 수행하지 않는다.

Java·Python·DB의 Compose 네트워크는 internal로 설정해 직접 외부로 나가는 경로를 기본 차단한다. 로컬 127.0.0.1 포트는 별도 Nginx 진입점만 노출한다. 진입점은 일반 네트워크와 내부 네트워크에 연결되지만, 자격증명을 갖지 않고 고정된 Java 서비스로만 전달한다. 읽기 전용·비root·capability 제거와 본문/시간 제한을 적용했다. 재생성된 Java 컨테이너의 IP는 Docker DNS로 다시 확인한다. 빌드 시 패키지 다운로드는 별개다. 향후 수집기를 추가할 때는 DNS 재검증·연결 IP 고정·리다이렉트별 재검사·송신 방화벽·압축 해제/응답 크기·시간 예산을 설계해야 한다. 현재 URL 문자열 검사만으로 향후 SSRF까지 해결됐다고 간주하면 안 된다.

외부 자료 안의 ‘지시’는 실행 요청이 아니라 비신뢰 데이터다. 현재 LLM 호출이 없으므로 프롬프트 인젝션을 모델 수준에서 해결한 것은 아니다. 추후에는 원문/명령 분리, 도구 권한 제한과 사용자 검토가 필요하다.

## 6. 가용성·감사와 실패

로그인 예산은 원격 주소별 15분에 최대 10회이며 11번째부터 429/Retry-After를 반환한다. 성공 시 해당 주소 예산을 초기화한다. 카운터는 DB의 원자적 UPSERT이므로 웹 재시작만으로 없어지지 않는다. 예산 저장소 실패는 503으로 닫는다. IP 해시는 익명화가 아니라 주소의 가명 표현이다.

이 기준은 초기 구현값이다. 현재 Nginx를 경유한 사용자는 Java가 관찰하는 진입점 주소를 공유하므로 예산도 공유한다. NAT 뒤 여러 사용자를 같은 주소로 제한하거나, 공격자가 여러 주소·유효 계정을 사용하는 경우에는 한계가 있다. 분산 방어·계정별 예산·신뢰 프록시 정책·MFA·봇 제어는 후속 검토가 필요하다. 현재 값을 운영 적정값으로 확정하지 않는다.

로그인 성공·실패 이벤트는 별도 DB에 INSERT한다. 보안 이벤트가 서명된 불변 감사 저장소인 것은 아니다. 역할 변경·로그아웃·관리자 조작 전체를 포괄하는 감사는 아직 없다. DB 연결 풀은 최대 5개, 연결 대기는 3초이며 컨테이너 자원 제한과 기존 Python 호출 제한도 유지한다. `/ready`는 최초 계정 생성 등 Boot 시작 처리가 끝나고 Python·DB 준비 확인도 통과한 뒤에만 200을 반환한다.

현재 개발 DB 접속 계정은 기존 PostgreSQL 초기 관리자 계정이다. **운영 전에는 마이그레이션 계정과 최소 권한 실행 계정을 반드시 분리해야 한다.** 이 한계 때문에 현재 상태를 운영 수준 보안 완료로 표현하지 않는다. DB·백업 암호화, 비밀 저장소, 보안 이벤트 보존 기간·용량 한도·서명도 별도 검토 대상이다.

## 7. 검토할 파일과 기준

- `SecurityMvcTest`: 미인증·CSRF 누락/위조·교차 출처·소유자 주입·역할 부족·실제 비밀번호 대조·동일 실패 응답·로그인 제한·예산/감사 저장 실패·세션 ID 변경·로그아웃·크기 제한·예외 정보 노출을 검사한다.
- `ProductionTransportTest`: local이 아닌 설정에서 HTTP→HTTPS 이동과 HTTPS HSTS 헤더를 검사한다. 실제 TLS 핸드셰이크 검증은 아니다.
- `ExternalMaterialPolicyTest`: 기본 거부, 위장 출처·스킴·IP·자격증명·큰 자료 거부, 알려진 SHA-256 값을 확인한다.
- `scripts/integration_check.py`: 실제 DB 사용자 로그인, 쿠키 속성·세션 회전·이전 CSRF 폐기·업무 저장·로그아웃을 HTTP로 확인한다.
- `scripts/Test-LoginBudget.ps1`: 별도 임시 클라이언트에서 실패 한도 소진→웹 재시작→계속 429를 확인한다. 실제 비밀번호는 사용하지 않는다.

실행 결과는 `docs/security/VERIFICATION.md`에서 Mock/실제 환경과 실패·수정·재실행을 구분한다. 기존 `docs/verification`은 이전 기준의 이력이며 이번 로그인 보안 검증을 대신하지 않는다.

## 8. 참고와 다음 논의

[Spring Boot 지원 버전·실행 요건](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Security CSRF와 MockMvc](https://docs.spring.io/spring-security/reference/servlet/test/mockmvc/csrf.html), [OWASP SSRF 예방](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html), [OWASP 파일 업로드 예방](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)을 참고했다. 문서의 권고 전부를 구현했다고 주장하지 않는다.

후속 논의는 MFA/패스키·사용자 발급/회수, 역할과 전문가 권한, 과거 데이터 소유권, 외부 자료 허용 출처·실수집 방식, 비밀·DB 최소 권한·TLS 배포, 감사 보존 정책의 순서로 검토할 수 있다. 이번 작업에서 해당 정책이나 유료 API 도입을 확정하지 않는다.
