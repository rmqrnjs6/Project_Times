# Project_Times 실행·검증 흐름

기준일: 2026-11-11. 기존 앱 실행과 새 평가 작업대의 책임을 구분합니다.

| 목적 | 실행 경로 | 결과 |
|---|---|---|
| 기존 앱 | 기존 README의 환경설정 → `docker compose up -d --build` | Spring 로그인·질문 저장 → Python 계산·PostgreSQL |
| 새 작업대 검증 | `node scripts/workbench.mjs check` | 화면 JS 구문 검사 → Worker 빌드 → 모의 연동·계산 테스트 |
| 새 작업대 미리보기 | `node scripts/workbench.mjs start` | 루프백 http://127.0.0.1:5173/ 의 질문 입력·평가 화면 |
| 변경 이력 | `CHANGELOG.md`, `docs/PROGRESS.md` | 구현·미구현·검증 범위 추적 |

## 질문에서 결과까지

1. 첫 화면에서 검색어 또는 질문을 입력합니다.
2. 동일한 prompt가 A~C 요청 미리보기에 반영됩니다. D·E는 모델 미지정으로 남습니다.
3. ‘평가’ 버튼으로 분석 화면에 이동합니다.
4. 실제 요청 조건이 갖춰진 Sites 환경에서는 선택한 모델의 응답·실패·사용량을 구분합니다.
5. 키가 없거나 로컬 미리보기이면 요청을 시작하지 않고 미실행 사유를 표시합니다.
6. 기존 점수·레이더·분포도는 합성 예시로 명시합니다. 실제 응답의 자동 신뢰점수가 아닙니다.

## 파일의 역할

- `modules/evaluation-workbench/public/`: 첫 화면, 비교 분석, 전문가 가상 예시, 상대평가 설명 팝업.
- `modules/evaluation-workbench/worker/index.mjs`: 3종 서버 어댑터, 소유자·Origin 확인, 입력/출력 요청 제한, 오류 격리.
- `modules/evaluation-workbench/tests/`: 모의 공급자 응답과 기존 Q6 계산 검증.
- `scripts/workbench.mjs`: 루트에서 실행하는 빌드·검증·미리보기 진입점.
- `.github/workflows/ci.yml`: 기존 세 작업을 유지하고 독립 `evaluation-workbench` 작업 추가.
- `docs/research/Project_Times_AI_5_Model_Cost_Summary_2026-11-11.md`: 5종 구성안·단가·사용량 가정·미완료 범위.

## 연결 경계

이 모듈은 Sites 공개 버전 5의 소스를 재현하기 위한 독립 기능입니다. 기존 Spring 인증·DB를 공유하도록 포팅한 것이 아닙니다. 루트 Docker 네트워크나 보안 필터는 변경하지 않습니다. Node.js 22 이상이 필요하며 외부 npm 패키지는 필요하지 않습니다.

로컬 개발 서버는 127.0.0.1에만 바인딩하고 Worker에 비밀 환경변수나 방문자의 인증 헤더를 전달하지 않습니다. 실제 호출은 비활성화됩니다. Sites용 인증 헤더는 플랫폼이 검증해 넣는 경우에만 신뢰해야 합니다. 일반 Node 서버에서 임의 헤더로 이를 대체하면 안 됩니다.

Sites용 원본 프로젝트 식별자는 모듈 내부 `.openai/hosting.json`에 보존했습니다. API 키·소유자 이메일 값·로그인 비밀번호는 포함하지 않습니다. GitHub 커밋 자체로 기존 공개 사이트가 재배포되지는 않습니다.

## 현재 미완료

실제 API 키 등록·응답 검증, D/E 어댑터, SymPy·Lean 실행, 실제 응답 자동 채점, Spring 인증/DB 통합. 유료 가입·충전·실호출은 이번 작업 범위에 포함하지 않습니다.
