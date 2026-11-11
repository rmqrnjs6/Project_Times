# Changelog

## 2026-11-06 - MVC authentication and CIA boundaries

- Migrate direct Servlet handling to Spring Boot MVC and Spring Security.
- Add real JDBC/BCrypt authentication, CSRF/session/role controls, owner-scoped storage and persistent login budgets.
- Add MockMvc adversarial tests, default HTTPS tests, actual authenticated integration and restart-budget checks.
- Quarantine manual external text with provenance/hash; deny intake by default and isolate application egress behind a local ingress.
- Leave MFA, identity lifecycle, production TLS/least-privilege database setup and live external fetching for further discussion.

## Unreleased
- Imported reusable Docker/FastAPI starter structure into Project_Times as a proposed change.
- At import time, Docker runtime and GitHub Actions execution had not been verified.

## 2026-11-06 - Project Times development foundation

- Reimplemented the shared-conversation baseline as JSP/Java Servlet, Python arithmetic API, PostgreSQL/Flyway and Docker services.
- Added unknown-aware seven-axis reference arithmetic and persisted calculation inputs/results/policy versions.
- Added integration, recovery and backup/restore checks plus execution and architecture documentation.
- Actual AI verification, authentication, expert workflow, retry budgets and checkpoint resumption remain unimplemented.

## 2026-11-11 · 평가 작업대 소스 통합

- Sites 공개 버전 5의 입력·A~E 파라미터 미리보기·분석 화면, 3종 서버 어댑터와 테스트를 `modules/evaluation-workbench`에 추가.
- 루트 실행 스크립트와 기존 CI의 독립 Node 검증 작업을 연결. 기존 Docker·Java·Python 작업은 유지.
- 비용 보고서·실행 흐름·검증 범위를 문서에 연결. 실키·실호출·D/E 어댑터·Spring 인증 통합은 미완료.
