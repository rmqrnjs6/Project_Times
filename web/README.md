# Spring MVC 웹 애플리케이션

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 역할

기존 Docker 실행 흐름의 로그인·JSP·질문 저장·산술 요청·DB 보존을 담당합니다. 별도 [평가 작업대](../modules/evaluation-workbench/README.md)의 Worker와 인증·DB를 공유하지 않습니다.

| 영역 | 읽을 문서 | 주요 내용 |
|---|---|---|
| Java 업무·보안 | [클래스별 설명](src/main/java/dev/projecttimes/README.md) | 필터 → Controller → Service → DB/Python |
| 환경설정 | [설정 설명](src/main/resources/README.md) | 프로파일·세션·DB·환경변수 |
| DB | [마이그레이션 설명](src/main/resources/db/migration/README.md) | V1/V2 테이블·소유권 |
| 브라우저 JS | [정적 자산 설명](src/main/resources/static/README.md) | CSRF 포함 fetch·안전한 출력 |
| JSP | [화면 설명](src/main/webapp/WEB-INF/views/README.md) | 로그인·연결 점검·산술 UI |
| 테스트 | [Java 테스트 설명](src/test/java/dev/projecttimes/README.md) | Mock 경계·HTTPS 정책 |

## 시작과 빌드

루트 .env.example을 .env로 복사해 DB_PASSWORD·BOOTSTRAP_PASSWORD를 설정한 뒤 저장소 루트에서 `docker compose up -d --build`를 실행합니다. 기본 접속은 http://localhost:18080/login 입니다. 이미 생성된 계정의 비밀번호는 환경변수 수정만으로 초기화되지 않습니다.

[pom.xml](pom.xml)은 Java 21, Spring Boot 3.5.16, MVC/Security/JDBC, JSP, PostgreSQL, Flyway와 실행 가능한 WAR 구성을 정의합니다. 버전은 이 코드 스냅샷의 값입니다.

[Dockerfile](Dockerfile)은 Maven/Temurin 21로 `mvn -B verify`를 실행한 뒤 ROOT.war를 JRE 이미지에 복사합니다. Java UID 1000으로 실행하며 메모리 비율 옵션을 적용합니다. 독립 빌드는 루트에서 `docker build -t project-times-web ./web`입니다. 로컬 JDK 21·Maven이 있다면 web 폴더의 `mvn -B verify`로 Java 테스트를 실행할 수 있습니다.

## 현재 범위

질문 저장은 연결 점검입니다. 판정 수량의 산술 계산은 실제 AI 검증이 아닙니다. 외부 자료는 수동 text/plain 격리 접수만 제공하며 URL 다운로드·자료 실행은 하지 않습니다. 운영 TLS·MFA·계정 복구 등은 완료되지 않았습니다. 자세한 기준은 [보안 설계](../docs/security/DESIGN.md), 전체 실행 순서는 [워크플로우](../docs/WORKFLOW.md)를 참고하세요.

[전체 코드 읽기 지도](../docs/CODE_GUIDE.md)
