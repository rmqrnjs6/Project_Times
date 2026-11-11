# GitHub Actions 작업별 설명

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

[ci.yml](ci.yml)은 push와 pull_request에서 실행하며 기본 권한을 contents: read로 제한합니다.

| 작업 | 준비·명령 | 확인 범위 |
|---|---|---|
| test | Python 3.12, requirements-dev 설치, pytest | Python HTTP 스키마·산술 정책 |
| docker-build | 루트 Python 이미지, web 이미지 빌드 | 이미지 빌드; web 빌드의 Maven verify 포함 |
| integration | 임시 DB/초기 비밀번호 생성, Compose 실행·ready 대기, integration_check.py | 실제 DB 인증·질문/계산 저장·세션·CSRF |
| evaluation-workbench | Node.js 22, node scripts/workbench.mjs check | 별도 Worker 빌드·모의 연동·Q6 테스트 |

integration은 임시 자격증명을 로그에 노출하지 않고 생성하며 종료 시 always 조건으로 Compose를 내립니다. 다른 작업과 별개 VM에서 실행되므로 앞 작업의 이미지가 자동 공유되는 구조는 아닙니다.

## 결과 확인

커밋의 Actions 실행에서 작업별 상태·실패 단계·로그를 확인합니다. 로컬 테스트 통과와 원격 CI 통과는 다릅니다. 빌드 실패는 패키지 다운로드·환경 문제인지, 코드/테스트 실패인지 구분합니다.

CI는 유료 AI 키를 설정하거나 실제 공급자를 호출하지 않습니다. 새 모듈의 테스트는 주입한 모의 응답입니다. 이 워크플로우에는 Sites 자동 배포 단계가 없습니다. 커밋 성공만으로 공개 사이트 갱신이나 실제 AI 연결을 의미하지 않습니다.

명령의 세부 동작은 [루트 스크립트](../../scripts/README.md), [Python 테스트](../../tests/README.md), [Java 테스트](../../web/src/test/java/dev/projecttimes/README.md), [Node 테스트](../../modules/evaluation-workbench/tests/README.md)를 참고하세요.

[전체 코드 읽기 지도](../../docs/CODE_GUIDE.md)
