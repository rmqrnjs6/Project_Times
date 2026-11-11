# 평가 작업대 화면 코드

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일별 책임과 로딩 순서

| 파일 | 책임 |
|---|---|
| [index.html](index.html) | 질문·비교·전문가·자동 계산·질문 기록·계산 기준 섹션과 상대평가 dialog |
| [scoring.js](scoring.js) | 브라우저의 scoreQ6, 여섯 원점수 검증과 BigInt 가중합 |
| [app.js](app.js) | 라우팅, 합성 후보/기여점수, 비교 기준, 브라우저 질문 기록 |
| [design.js](design.js) | 막대·레이더·분포·가중치 SVG, 가상 전문가·자료 탭, 설명표 |
| [automation.js](automation.js) | 3종 모델 설정 조회, A~E 미리보기, 요청/실패/결과 상태 |
| [style.css](style.css) | 기본 화면·표·레이아웃 |
| [design.css](design.css) | 차트·전문가·팝업·입력 흐름·반응형 보완 |
| [scoring.cjs](scoring.cjs) | Node CommonJS 테스트용 동일 Q6 구현; 브라우저 로드 대상 아님 |

HTML은 scoring → app → design → automation 순서로 로드합니다. 뒤 스크립트가 앞 파일의 전역 상태와 $ 함수를 사용하므로 순서를 바꾸면 초기화가 깨질 수 있습니다. scoring.js/cjs는 정책 수정 시 함께 확인해야 합니다.

## 질문에서 결과까지

루트는 #home입니다. 입력 시 A~C의 모델 ID·동일 prompt 미리보기가 바뀝니다. 160자 미리보기 제한은 실제 문제 2,000자 제한과 다릅니다. D·E는 모델 미지정입니다.

설정 조회 GET /api/integrations의 owner/ready에 따라 체크박스를 활성화합니다. ‘평가’ 시 실제 요청 조건이 없으면 전송 없이 #comparison에 미실행 사유를 표시합니다. 조건이 충족되면 POST /api/solve로 prompt/providers/confirm_cost를 보내고 응답을 개별 카드로 출력합니다. 모델 출력은 textContent로 넣습니다.

#comparison의 기존 Q6 그래프는 별도 합성 예시입니다. A~E 합성 후보를 실제 공급자 슬롯의 측정 점수로 바꾸지 않습니다. 전문가 프로필도 가상 예시입니다.

## 저장·계산·수정

질문 기록 메뉴는 localStorage의 project-times-notes를 사용합니다. 실제 평가 세션·응답은 현재 페이지 메모리이며 새로고침 후 복원하지 않습니다. API 키를 브라우저에 보관하지 않습니다.

Q6는 F/S/I/T/C/R 원점수 0~100·소수 3자리까지 받고 R을 한 번 역산한 뒤 가중치 합 0.85로 나눕니다. C/A만 원점수가 제공되며 다른 합성 후보는 기록된 총점을 표시합니다.

미리보기는 저장소 루트의 `node scripts/workbench.mjs start`입니다. DOM ID·이벤트·CSS를 함께 확인하고 [서버 계약](../worker/README.md)과 [테스트](../tests/README.md)를 참고하세요. 코드의 설정됨 표시는 실제 호출 성공을 검증한 상태가 아닙니다.

[전체 코드 읽기 지도](../../../docs/CODE_GUIDE.md)
