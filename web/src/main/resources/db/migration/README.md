# DB 스키마와 마이그레이션

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 역할

Spring Boot의 Flyway가 순서대로 적용하는 PostgreSQL 스키마입니다. 임의 SQL 실행 기록이 아니라 버전이 있는 변경 파일입니다.

| 파일 | 주요 변경 |
|---|---|
| [V1__baseline.sql](V1__baseline.sql) | diagnostic_checks, calculation_runs 생성 |
| [V2__authentication_and_materials.sql](V2__authentication_and_materials.sql) | 로그인·역할·예산·감사·격리 자료, 기존 데이터의 소유자 필드 |

## 테이블과 코드 연결

| 테이블 | 보존 데이터 | 쓰는 코드 |
|---|---|---|
| diagnostic_checks | UUID·질문·시각·connectivity_only·owner_key | Database.saveCheck |
| calculation_runs | UUID·정책·입력/결과 JSONB·시각·소유자 | Database.saveCalculation |
| security_users | 사용자명·비밀번호 해시·활성 여부 | AuthBootstrap, JDBC 인증 |
| security_authorities | 사용자별 역할 | AuthBootstrap, JDBC 인증 |
| login_attempts | 원격 주소 해시·시작 시각·횟수 | LoginAttemptStore |
| security_events | 이벤트 유형·행위자·시각 | SecurityAudit |
| external_materials | 원문·출처·해시·소유자·quarantined | Database.saveMaterial |

V2 이전 질문·계산은 owner_key=legacy-unassigned로 남습니다. 현재 로그인 계정에 임의 귀속하거나 삭제하지 않습니다. 질문 목록은 현재 소유자만 조회하므로 이전 행이 보이지 않을 수 있습니다.

## 변경·검증 절차

이미 적용된 V1/V2를 다시 편집하기보다 후속 V3 등 새 파일로 변경을 추적하세요. 변경 전 백업과 별도 DB 복원 검사를 수행하고, 기존 행·소유권·제약조건·Flyway 이력을 함께 확인합니다. [백업·복구 스크립트](../../../../../../scripts/README.md)는 복원 DB를 별도로 남깁니다.

정상 시작은 저장소 루트의 Docker 실행 흐름을 따릅니다. 이 폴더에 자동 되돌리기 SQL은 없습니다. DB 볼륨 삭제를 마이그레이션 문제 해결책으로 사용하면 기존 데이터가 사라집니다. 현재 DB 실행 계정의 운영 최소 권한 분리는 완료되지 않았습니다.

[전체 코드 읽기 지도](../../../../../../docs/CODE_GUIDE.md)
