# Worker 빌드와 로컬 개발 서버

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일

| 파일 | 입력 → 처리 → 출력 |
|---|---|
| [build.mjs](build.mjs) | public 자산·Worker·hosting manifest → 허용 자산 삽입 → dist/server/index.js와 dist/.openai/hosting.json |
| [dev.mjs](dev.mjs) | 빌드된 Worker → Node HTTP 어댑터 → 127.0.0.1 포트의 화면/API 미리보기 |

build는 .html/.css/.js/.svg만 자산에 넣습니다. README.md와 scoring.cjs는 공개 자산으로 포함하지 않습니다. index.html 존재, Worker형 manifest(static 없음), default.fetch 유무를 확인합니다. 기존 dist는 삭제 후 재생성하므로 원본을 dist 안에서 수정하지 않습니다.

## 실행

저장소 루트의 `node scripts/workbench.mjs check`는 구문 검사 → 빌드 → 테스트를 순서대로 실행하며 앞 단계 실패 시 중단합니다. `node scripts/workbench.mjs start`는 빌드 후 개발 서버를 시작합니다. 외부 npm 패키지 설치는 필요하지 않습니다.

모듈 폴더에서는 npm run build, npm test, npm run dev도 사용할 수 있습니다. Node.js 22 이상을 기준으로 하며 기본 포트는 5173입니다. dev의 --port 인수가 PORT 환경변수보다 우선합니다. 새 빌드 결과는 개발 서버를 재시작해야 다시 로드됩니다.

## 로컬과 배포의 차이

개발 서버는 worker.fetch(request,{})로 호출해 비밀 환경설정·방문자 신원 헤더를 인증에 쓰지 않습니다. API 키가 셸 환경에 있어도 실제 모델 요청을 활성화하지 않습니다. 이 서버를 운영 인증 서버로 간주하지 않습니다.

원본 프로젝트 ID는 ../.openai/hosting.json에 보존합니다. build는 로컬 산출물 생성이며 GitHub push나 Sites 배포를 수행하지 않습니다. dist·node_modules·실제 .env는 커밋하지 않습니다. [모듈 시작 안내](../README.md)와 [Worker 계약](../worker/README.md)을 함께 확인하세요.

[전체 코드 읽기 지도](../../../docs/CODE_GUIDE.md)
