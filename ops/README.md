# Docker 진입점과 Nginx

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일의 역할

[nginx.conf](nginx.conf)는 브라우저 요청을 고정된 Java web:8080으로 전달하는 진입점입니다. [compose.yaml](../compose.yaml)이 이를 읽기 전용으로 마운트합니다. 루트 [Dockerfile](../Dockerfile)은 Python, [web/Dockerfile](../web/Dockerfile)은 Java 빌드·실행을 담당합니다.

## 통신 흐름

브라우저 → 127.0.0.1:WEB_PORT(기본 18080) → ingress:8080 → web:8080 → analysis:8000 / db:5432 순서입니다. 기존 앱 세 서비스는 internal 네트워크에 있고 ingress만 두 네트워크에 연결됩니다. 새 Node 작업대는 이 Compose에 포함되지 않습니다.

| 설정 | 값·의미 |
|---|---|
| resolver | Docker DNS 127.0.0.11, 5초 갱신 |
| 프록시 대상 | 고정 web:8080; 사용자 URL을 대신 다운로드하지 않음 |
| 본문·시간 | 32k, 본문 5초, 연결 3초, 읽기 10초, 전송 5초 |
| 헤더 | Host 전달, X-Forwarded-For/Proto 제거 |
| 실행 | UID 1000, 읽기 전용, /tmp 임시 저장, capability 제거 |
| 로그 | access_log off, 오류는 stderr warn |

## 실행과 확인

루트 .env를 준비해 `docker compose up -d --build`로 실행합니다. 외부 루프백 /ready가 200인지, 의존 서비스 중지 시 503인지 확인합니다. 실제 절차는 [스크립트 안내](../scripts/README.md)에 있습니다.

현재 구성은 로컬 개발용 HTTP이며 운영 TLS 구성이 아닙니다. Nginx의 본문 제한이 애플리케이션 입력 검사를 대체하지 않습니다. 전달 주소를 신뢰하지 않아 로그인 예산이 진입점 주소로 공유될 수 있습니다. 네트워크·프록시 정책을 변경할 때 [보안 설계](../docs/security/DESIGN.md)를 함께 검토하세요.

[전체 코드 읽기 지도](../docs/CODE_GUIDE.md)
