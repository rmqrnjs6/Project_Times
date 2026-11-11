# Spring 설정과 환경변수

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일

| 파일 | 적용 범위 |
|---|---|
| [application.properties](application.properties) | DB 연결·뷰 위치·세션·본문 파서·초기 계정의 기본 정책 |
| [application-local.properties](application-local.properties) | 명시적 local 프로파일에서 Secure 쿠키만 false로 설정 |
| [db/migration](db/migration/README.md) | Flyway 스키마 |
| [static](static/README.md) | JS·CSS |

기본 프로파일은 SecurityConfig에서 HTTPS를 요구합니다. 현재 Compose는 루프백 개발 환경에 local을 명시합니다. local은 운영 TLS 구성의 대체물이 아닙니다.

## 환경변수 대응

| 변수 | 사용 | 기본·주의 |
|---|---|---|
| DB_URL | JDBC URL | 내부 db:5432/project_times; 연결·소켓 제한 포함 |
| DB_USER | DB 사용자 | project_times |
| DB_PASSWORD | DB 비밀번호 | 빈 기본값; Compose 시작에는 설정 필수 |
| ANALYSIS_URL | AnalysisService의 Python 주소 | http://analysis:8000; Java 클래스에서 직접 읽음 |
| BOOTSTRAP_USERNAME | 최초 계정 이름 | owner; 3~64자의 영숫자·밑줄·하이픈 |
| BOOTSTRAP_PASSWORD | 최초 계정 생성용 비밀번호 | 새 계정에는 UTF-8 16~64바이트 필요 |
| EXTERNAL_ALLOWED_HOSTS | 수동 자료 출처 허용 목록 | 빈 값은 전부 거부, 쉼표로 구분 |
| SPRING_PROFILES_ACTIVE | Spring 프로파일 | Compose에서 local |

비밀번호·실제 키는 문서나 소스에 적지 않습니다. 환경변수 이름만 정리한 표입니다.

## 설정에 따른 동작

Hikari 풀 최대 5개·연결 대기 3초, 세션 비활성 만료 15분, HttpOnly·SameSite Strict·쿠키 추적을 사용합니다. forward 헤더 자동 신뢰는 꺼져 있고 multipart도 비활성화됩니다. 폼은 4KB·파라미터 5개 제한, JSON 중복 키 검출이 설정되어 있습니다. API 본문 32KiB 제한은 Java 필터에서 별도로 적용합니다.

설정만으로 초기 계정 비밀번호를 재설정하지 않습니다. 운영 이전에는 계정·DB 권한·TLS 정책을 별도로 검토해야 합니다. [웹 실행 설명](../../../README.md)을 기준으로 시작하세요.

[전체 코드 읽기 지도](../../../../docs/CODE_GUIDE.md)
