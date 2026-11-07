# 코드 리뷰 기준선 — 2026-11-08 KST

| 항목 | 확인한 사실 |
|---|---|
| 기준 커밋 | `488ecff1412c25743b852376525fcdbce8a3e25b` |
| 기본 브랜치 / 파일 | main / 추적 파일 80개. 커밋 지정 원본 확보, Git blob 해시 확인 |
| 기존 지침 | AGENTS.md, docs/REVIEW_POLICY.md, README, docs/security/DESIGN.md·VERIFICATION.md, docs/verification/RESULTS.md 읽음 |
| 열린 PR | #2: feat/verification-task-lifecycle. 이 PR의 기능을 main에 구현됐다고 간주하지 않음 |
| 다른 브랜치 | chore/import-project-template, feat/verification-task-lifecycle 존재 |
| 이번 변경 | 감사 문서와 실행 로그만 추가. 보안·점수·애플리케이션 코드는 변경하지 않음 |
| 실행 환경 | Linux, Python 3.12.14, pytest 8.4.1; [패키지](logs/python-packages.txt) |

## 이번 실행

저장소 루트에서 준비된 가상환경으로 `python -m pytest tests -q`: 20 PASS, AnyIO BlockingPortal 별칭 사용 중단 예고 경고 1개. [실행 로그](logs/times-python.log).

실제 UI JS의 `node --check web/src/main/resources/static/workbench.js`는 종료 코드 0. Python 7개 파일의 AST 구문 검사도 오류 없음. 구문 검사는 동작/보안 입증이 아니다.

`docker --version`, `mvn -v`는 command not found였다. Java 보안 테스트·WAR·PostgreSQL·실제 로그인·백업 복구는 이번 세션에서 BLOCKED다. 기존 검증 문서의 결과를 이번 성과로 합산하지 않는다. Python 의존성 범위는 requirements/requirements-dev와 호환되는 Jiwoo 검증 가상환경을 사용했으며 새 컨테이너 환경의 재현은 아니다.

조회한 최근 Actions 3개는 성공이지만 모두 chore/import-project-template 브랜치 커밋이었다. 그것만으로 기준 main의 CI 통과를 주장하지 않는다. 기존 기록은 그대로 보존한다.

## 검토 범위

Compose/Nginx 경계, SecurityConfig·RequestSizeFilter·LoginAttemptStore, ExternalMaterialPolicy, Controller→Service→Database/AnalysisService, Python 7축 계산/API, 테스트·CI 문서를 집중 검토했다. 전체 독립 보안 감사나 모든 분기의 실행 완료를 뜻하지 않는다.

실제 비밀번호·외부 AI·사용자 DB는 사용하지 않았다. main 병합, 배포, 볼륨 삭제, 설정 완화도 하지 않았다.
