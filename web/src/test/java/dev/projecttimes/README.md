# Java 테스트의 대상과 한계

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 테스트 파일

| 파일 | 주요 조건 | 대체되는 의존성 |
|---|---|---|
| [SecurityMvcTest.java](SecurityMvcTest.java) | 인증 전 접근·CSRF·Origin·역할·세션·로그아웃·크기·오류·자료 격리 | DB·분석·사용자/예산/감사 저장소 Mock |
| [ProductionTransportTest.java](ProductionTransportTest.java) | 기본 프로파일 HTTP→HTTPS와 HTTPS 응답 HSTS | MockMvc 요청 환경 |
| [ExternalMaterialPolicyTest.java](ExternalMaterialPolicyTest.java) | 빈 허용 목록·위장 출처·큰/바이너리 자료 거부·알려진 SHA-256 | 정책 객체 중심 |
| [AuthBootstrapTest.java](AuthBootstrapTest.java) | 약한/예제 비밀번호 거부·기존 계정 비밀번호 비변경 | JDBC·암호화 의존성 Mock |

SecurityMvcTest의 user() 테스트는 역할 경계를, formLogin 테스트는 실제 필터·비밀번호 해시 대조 경로를 검사합니다. 모든 테스트를 실제 DB 인증으로 해석하면 안 됩니다.

## 실행

web 폴더에서 JDK 21·Maven으로 `mvn -B verify`를 실행합니다. 루트의 `docker build -t project-times-web ./web`도 빌드 단계에서 Maven verify를 실행합니다. 이미지·의존성 다운로드가 필요할 수 있습니다.

테스트 이름·기대 HTTP 상태·실패한 조건과 Surefire 결과를 확인하세요. 테스트 수만으로 보안 수준을 확정하지 않습니다. 이번 README 작성에서는 애플리케이션 테스트를 재실행하지 않았습니다.

## 별도 확인이 필요한 범위

실제 PostgreSQL 계정 로그인·세션·질문 보존은 [인증 통합 검사](../../../../../../scripts/README.md)가 담당합니다. TLS 테스트는 실제 인증서 핸드셰이크가 아니며, 운영 공개·MFA·보안 감사의 완료를 보장하지 않습니다. [기존 보안 검증 기록](../../../../../../docs/security/VERIFICATION.md)의 환경·이력을 함께 보세요.

[전체 코드 읽기 지도](../../../../../../docs/CODE_GUIDE.md)
