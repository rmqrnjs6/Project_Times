# 실행·인증·복구 스크립트

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 목적별 진입점

아래 명령은 저장소 루트에서 실행하는 기준입니다. 실제 DB·컨테이너를 바꾸는 검사는 개발 환경에서 실행하고, 작업 결과와 로그를 구분해 기록합니다.

| 파일 | 실행·요건 | 동작과 주의 |
|---|---|---|
| [workbench.mjs](workbench.mjs) | node scripts/workbench.mjs check 또는 start; Node 22+ | 별도 작업대 빌드/테스트 또는 루프백 미리보기; 실제 API 비활성 |
| [check.sh](check.sh) | sh scripts/check.sh | 개발 의존성 설치 후 pytest; Docker·Java 검사 아님 |
| [Invoke-AuthenticatedChecks.ps1](Invoke-AuthenticatedChecks.ps1) | .\\scripts\\Invoke-AuthenticatedChecks.ps1; 실행 중 Docker 앱·.env | 초기 계정 설정을 환경으로 전달해 HTTP 통합 검사; 값을 로그에 출력하지 않음 |
| [integration_check.py](integration_check.py) | 위 PS1 또는 컨테이너 stdin으로 실행 | 실제 로그인·CSRF·질문/계산 저장·로그아웃; 검사 행이 DB에 남음 |
| [Test-LoginBudget.ps1](Test-LoginBudget.ps1) | .\\scripts\\Test-LoginBudget.ps1 | 임시 네트워크 클라이언트로 실패 한도 소진→web 재시작→차단 유지; 테스트 전용 예산 행 정리 |
| [login_budget_check.py](login_budget_check.py) | PS1이 BUDGET_PHASE=exhaust/verify로 호출 | 가짜 잘못된 비밀번호만 사용; 10회 302, 11회 429 확인 |
| [Recovery-Check.ps1](Recovery-Check.ps1) | .\\scripts\\Recovery-Check.ps1 | analysis 중지→ready 503→복구 200, web 재시작 후 질문 ID/내용 집계 해시 비교 |
| [Backup-Restore-Check.ps1](Backup-Restore-Check.ps1) | .\\scripts\\Backup-Restore-Check.ps1 | pg_dump→별도 DB 복원→행/마이그레이션 수 비교; 복원 DB·백업 파일 남음 |
| [New-Project.ps1](New-Project.ps1) | .\\scripts\\New-Project.ps1 -Name Project_Alpha; gh 인증 | 템플릿 기반 새 GitHub 저장소 생성·clone; 기본 private, -Public은 공개 생성 |

루트 START_DOCKER.bat는 Compose 시작·빌드, STOP_DOCKER.bat는 down을 실행합니다. [기존 앱 시작 안내](../README.md)를 먼저 읽으세요. Node 스크립트는 기존 Docker 앱의 시작 명령을 바꾸지 않습니다.

## 인증 검사 데이터와 환경

integration_check.py의 기본 TEST_BASE_URL은 내부 ingress:8080, 계정은 TEST_LOGIN_USERNAME(기본 owner), 비밀번호는 TEST_LOGIN_PASSWORD입니다. 직접 명령 인수로 비밀번호를 넣지 않습니다. PS1은 .env에서 초기 설정을 읽고 종료 시 이전 환경값을 복원합니다. 기존 DB 계정 비밀번호가 달라졌다면 초기 설정과 실제 계정의 일치를 확인해야 합니다.

복구 검사는 서비스 중지·재시작을 수행합니다. 백업 복원 검사는 운영 DB를 덮어쓰지 않고 새 이름의 DB를 만들지만, 복사된 데이터와 로컬 dump도 민감정보로 관리해야 합니다. 템플릿 스크립트 실행은 외부 저장소를 실제 생성하는 작업입니다.

## 기록과 한계

실행 여부·종료 코드·사용 환경·남은 파일/DB·실패 조건을 기록합니다. [기존 검증 기록](../docs/security/VERIFICATION.md)과 [작업대 검증 기록](../docs/verification/WORKBENCH_2026-11-11.md)은 서로 다른 범위입니다. 코드 존재나 설명 추가만으로 각 검사를 이번에 실행했다고 주장하지 않습니다.

[전체 코드 읽기 지도](../docs/CODE_GUIDE.md)
