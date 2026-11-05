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
