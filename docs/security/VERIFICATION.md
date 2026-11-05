# 로그인·CIA 변경 검증 기록

2026-11-06, Asia/Seoul. 이번 코드는 초기 개발 기반 이후의 변경이다. 과거 테스트 수를 합쳐 현재 성능으로 표현하지 않는다. 테스트 수는 실행 범위의 크기이며 안전성 점수가 아니다.

## 실행 환경

Windows PowerShell, Docker Desktop의 Linux 컨테이너. Java 21.0.13, Spring Boot 3.5.16, Spring MVC 6.2.19, Spring Security 6.5.11, Tomcat 10.1.55, PostgreSQL 17.11을 실제 빌드·런타임 자료에서 확인했다. 프레임워크 의존성은 Boot BOM을 따른다. Python 산술 서비스 코드는 이번 변경에서 수정하지 않았다.

실제 배포 경로는 로컬 브라우저 → 127.0.0.1:18080의 Nginx → 내부 Java → 내부 Python/DB이다. local HTTP 예외를 사용하는 개발 환경이며 운영 TLS 연결 검증은 아니다. 이미지 태그는 향후 패치로 변할 수 있다.

## Mock 및 Java 테스트

`docker compose build web`의 Maven verify 단계에서 45개 케이스가 실행됐고 실패·오류·보류는 없었다. 분류는 SecurityMvcTest 25개, ProductionTransportTest 2개, AuthBootstrapTest 4개, ExternalMaterialPolicyTest 14개다.

- [빌드 원문 추출 요약](results/build-summary.txt)
- [JUnit XML에서 추출한 개별 케이스·실제 결과](results/mock-cases.txt)
- 원본 XML: 로컬 `artifacts/security-junit-final/TEST-*.xml`. 원본 SHA-256은 [해시 기록](results/junit-source-hashes.txt)에 보존했다.

핵심 케이스의 입력·기대·실제 결과는 다음과 같다.

1. 미인증 `/api/checks`: 401을 기대했고 관찰했다. 페이지 `/`는 `/login`으로 이동한다. API forward도 인증을 우회하지 못했다.
2. 사용자 인증 상태에서 CSRF 누락·위조, 또는 다른 Origin의 POST: 403을 기대했고 관찰했다. 질문 저장소 호출은 수행되지 않았다.
3. alice가 요청에 bob 소유자 값을 넣어도 조회·저장은 alice로 실행됐다. 역할 USER는 관리자 경로에서 403, ADMIN은 200이었다.
4. 실제 Spring 인증 필터와 BCrypt는 합성 올바른 비밀번호를 인증했다. 틀린 비밀번호·존재하지 않는 사용자·비활성 사용자 모두 공개 실패 경로를 사용했다. Mockito로 인증 성공을 강제로 주입한 결과가 아니다.
5. 예산 거부는 429, 예산 저장소 장애는 503이었고 비밀번호 조회에 도달하지 않았다. 로그인 성공 후 감사 저장 실패는 503과 세션 무효화로 처리됐다.
6. 로그인 시 세션 ID가 바뀌었다. 새 로그인 뒤 이전 세션의 API 접근은 401/session_expired였다. 로그아웃 후 세션은 무효화됐다.
7. 빈 값·짧은 값·공개 예제 비밀번호로 최초 계정을 만들지 않았다. 기존 계정은 새 환경 비밀번호로 조용히 초기화하지 않았다.
8. default 프로파일의 HTTP 요청은 HTTPS로 이동하고 HTTPS Mock 요청에는 HSTS가 있었다. 이는 실제 TLS 인증서·Secure 쿠키의 브라우저 교환을 검증한 것이 아니다.
9. HTTP/file/로컬 IP/IPv6 루프백/자격증명/토큰 쿼리/위장 suffix/다른 포트 등 출처를 거부했다. 빈 허용 목록은 기본 거부했다. `abc`의 SHA-256은 알려진 기대값과 일치했다.
10. HTML처럼 보이는 텍스트는 실행·AI 전송 없이 quarantined/trusted=false/fetched=false로 저장 경계에 도달했다. 바이너리 형식·큰 텍스트·NUL은 거부했다.
11. 최초 계정 생성 등 시작 처리가 끝나기 전에는 `/ready`가 503/initializing을 반환하고, Boot 준비 상태 및 내부 의존성 확인 후에만 200이었다.

입력과 개별 assertion은 `web/src/test/java/dev/projecttimes`에 있다. DB·분석·사용자 조회·예산·감사 저장소를 Mock으로 교체했으므로 이 결과만으로 실제 DB나 외부 네트워크의 안전성을 주장하지 않는다.

빌드에는 Spring Security의 사용 중단 예정 API 안내, Mockito/Byte Buddy 동적 Java agent 및 클래스 공유 경고가 있었다. 현재 Java 21 테스트에서는 실패하지 않았지만 향후 JDK·Security 버전 전환 시 테스트 실행 방식과 API를 갱신해야 한다. 경고를 숨기거나 운영 취약점 검사를 통과한 것으로 취급하지 않았다.

## 실제 DB·HTTP 검증

`.\scripts\Invoke-AuthenticatedChecks.ps1`은 로컬 `.env`의 최초 계정 설정을 읽어 Docker 환경으로만 전달하고 출력하지 않는다. `scripts/integration_check.py`를 읽기 전용 Python 컨테이너의 표준입력으로 실행한다. 런타임 로그인은 Mock이 아닌 PostgreSQL 사용자·BCrypt를 사용한다.

[최종 HTTP 로그](results/authenticated-http.txt)에서 미인증 401, HttpOnly/SameSite=Strict 쿠키, 실제 로그인, 세션 ID 변경, 인증 전 CSRF 거부, Nginx 경유 JSP, 소유자 질문 저장·조회, 계산 null 보존, 400/403/413/415/422 응답, 기본 출처 거부, 로그아웃 후 401을 확인했다. 유효한 출처의 격리 저장은 MockMvc에서 확인했으며, 실제 운영 출처를 허용하거나 자료를 인터넷에서 수집하지 않았다.

실제 HTTP 검증은 합성 질문·합성 판정 수량을 사용했다. 로컬 개발 Secure=false는 의도된 예외이며 브라우저의 실제 상호작용·시각 배치 검증은 수행하지 않았다.

## 재시작·격리·백업

- `.\scripts\Test-LoginBudget.ps1`: 별도 임시 클라이언트에서 존재하지 않는 합성 계정으로 10회 실패를 만들었다. 같은 컨테이너 주소를 유지하면서 웹 서버를 재시작한 뒤 11번째 요청이 429였다. 실제 비밀번호는 사용하지 않았다. 종료 시 자기 합성 카운터와 임시 컨테이너만 정리한다. [실행 로그](results/persistent-budget.txt)
- `.\scripts\Recovery-Check.ps1`: Python 중단 시 Java 준비 응답 503, 재시작 후 200, 웹 재시작 전후 질문 ID·내용의 집계 해시 일치를 확인했다. 메모리 로그인 세션은 재시작 후 재로그인이 필요하며 실행 중 AI 작업 재개와는 다르다. [실행 로그](results/recovery.txt)
- `.\scripts\Backup-Restore-Check.ps1`: 별도 DB에 복원 후 질문 7건·계산 13건·마이그레이션 2건의 행 수가 원본과 같았다. 덤프에는 로그인 해시 등도 포함되므로 로컬 민감 파일로 보관하며 Git에 넣지 않았다. 행 수 비교는 전체 필드 무결성·복구 능력을 증명하지 않는다. [실행 로그](results/backup-restore.txt)
- Docker 설정 관찰에서 backend network Internal=true, Java/Python/Nginx의 read-only 및 비root 사용자, Python의 IPv4 기본 경로 부재를 확인했다. 설정과 경로 관찰이며 우회 기법을 포함한 네트워크 침투 테스트는 아니다. [관찰 로그](results/runtime-isolation.txt)

## 발견·수정·재실행

1. Mock 요청에서 servletPath만 검사해 로그인 예산 필터가 건너뛰어졌다. contextPath와 requestURI 기준으로 바꾸고 429·503/미조회 케이스를 재실행했다.
2. 초기 인증 entry point가 페이지에도 401을 줬다. 페이지 이동과 API JSON 401을 구분했다. 이어진 테스트에서는 올바른 `/login` 응답을 잘못된 URL 패턴으로 비교한 assertion도 정확한 경로 비교로 수정했다.
3. 내부 전용 네트워크는 Java의 호스트 직접 노출을 대신할 접속 경계가 필요했다. 비밀을 갖지 않는 제한된 Nginx 진입점을 분리했다. 읽기 전용 Nginx의 기본 임시 경로 오류는 모든 임시 경로를 tmpfs `/tmp`로 옮겨 수정했다.
4. 삭제된 합성 클라이언트의 IP를 진입점이 재사용해 이전 실패 예산이 적용됐다. 확인된 합성 기록만 정리하고, 테스트 도구가 자신이 보유한 주소의 fixture만 준비·정리하도록 변경했다. 로그인 제한을 비활성화하거나 실제 계정 예산을 일괄 초기화하지 않았다.
5. 예제 비밀번호가 복사만으로 실제 최초 계정에 쓰이지 않도록 예제 비밀을 비우고 알려진 placeholder를 거부했다. CI에서는 출력하지 않는 임시 난수를 생성한다.
6. 첫 계정 생성 전에 의존성만으로 준비 완료를 보고할 수 있는 시작 순서 문제를 예방하기 위해 Boot의 ACCEPTING_TRAFFIC 상태까지 확인하도록 보완하고 회귀 검증했다.

수정 뒤 최종 Maven verify와 실제 인증 → 로그인 예산/재시작 → 장애·데이터 보존 → 별도 DB 복원을 재실행한 결과가 이 문서의 로그다. 이전 실패·빌드 원문은 로컬 `artifacts/`에 남아 있다.

## 검증 한계 및 검토 지점

운영 TLS·MFA/패스키·계정 발급/회수/복구, 최소 권한 DB 계정 분리, 암호화된 백업과 비밀 저장소, 분산 로그인 제한·프록시 신뢰, 보안 이벤트 보존·서명·용량 정책, 실제 외부 수집기·악성 파일 검사·LLM 프롬프트 인젝션 대응은 미완료다. 독립 보안 감사·부하·침투 테스트도 하지 않았다.

특히 현재 개발 DB 계정의 관리자 권한과 local HTTP 예외 때문에 외부 공개 운영 수준의 완료 상태로 표현하지 않는다. 로그인 10회/15분·세션 15분/1개·자료 크기와 출처 정책은 검토 가능한 초기 구현값이며 사용자 최종 운영 정책으로 확정하지 않는다. 다음 진행 범위는 별도로 논의한다.

GitHub CI 구성도 갱신했지만 이 기록을 작성할 때 원격 실행 결과는 아직 확인하지 않았다. 로컬 결과와 원격 상태를 구분한다.
