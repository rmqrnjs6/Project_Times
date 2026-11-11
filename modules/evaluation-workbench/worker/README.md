# Worker API와 모델 호출 코드

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일과 주요 함수

[index.mjs](index.mjs)는 default.fetch를 내보내는 Worker ES 모듈입니다.

| 구성 | 책임 |
|---|---|
| PROVIDERS | DeepSeek Flash, Qwen3.5-Plus, Gemini 3.8 Flash의 고정 ID·모델·비밀 변수 |
| qwenEndpoint | 허용된 HTTPS 호스트·경로로 Qwen 주소 제한 |
| isOwner | 플랫폼의 검증된 사용자 ID·이메일과 서버 소유자 설정 비교 |
| configuration | 키·주소 준비 상태 판정; 실제 호출 검증과 별개 |
| runProvider | 고정 메시지·한도·타임아웃, HTTP 오류 격리 |
| parseCompletion / parseStream | 최종 응답 본문·사용량 추출; reasoning_content는 표시하지 않음 |
| createWorker | 정적 자산·API 라우팅, 테스트용 upstreamFetch 주입 |

## API 계약

| 요청 | 입력·조건 | 응답 |
|---|---|---|
| GET /api/integrations | 공개 조회 | owner·설정 여부·모델 목록·한도; 키·소유자 이메일·주소 값 제외 |
| POST /api/solve | 소유자, 동일 Origin, JSON, prompt 1~2,000자, 중복 없는 1~3 모델, confirm_cost=true | 모델별 본문·상태·제공된 토큰 사용량, 검증 미수행 안내 |

본문은 12,000바이트 제한입니다. 키가 등록된 선택 모델이 하나도 없으면 503으로 끝내며 공급자를 부르지 않습니다. 공급자 하나의 오류는 다른 결과와 분리합니다. 서버는 입력된 임의 모델명이나 임의 호출 URL을 사용하지 않습니다.

## 설정과 호출 한도

Sites 비밀 환경변수는 MODEL_OWNER_EMAIL, DEEPSEEK_API_KEY, QWEN_API_KEY, QWEN_BASE_URL, GEMINI_API_KEY입니다. Qwen 주소는 계정의 지역·워크스페이스와 맞아야 합니다. 실제 값은 저장소나 브라우저에 넣지 않습니다.

모델당 max_tokens=1024, Qwen thinking_budget=256을 요청하고 응답 대기는 45초입니다. 자동 재시도는 없으며 공급자 응답은 262,144바이트 읽기 제한을 적용합니다. 이 값은 금액 상한이 아니며 대기 중단이 공급자 과금 취소를 보장하지 않습니다.

isOwner의 oai-authenticated-user-* 헤더는 Sites 플랫폼이 검증해 주입하는 신뢰 경계에서만 사용합니다. 일반 서버에서 클라이언트 임의 헤더를 인증으로 받아들이면 안 됩니다. [로컬 서버](../scripts/README.md)는 환경·신원 정보를 Worker에 전달하지 않아 실제 호출을 차단합니다.

## 검증·미구현

[테스트](../tests/README.md)는 모의 fetch로 요청 형식·오류·보안 조건을 확인합니다. 실제 키·모델 접근·공급자 응답은 미검증입니다. D/E 어댑터, 영구 결과 저장, SymPy·Lean 실행, 실제 응답 점수화는 구현되지 않았습니다. 출력 일치나 다수결을 정답으로 판정하지 않습니다.

[전체 코드 읽기 지도](../../../docs/CODE_GUIDE.md)
