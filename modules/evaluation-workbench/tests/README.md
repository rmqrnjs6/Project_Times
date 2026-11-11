# 평가 작업대 모의 연동·Q6 테스트

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일과 검증 조건

| 파일 | 사례 |
|---|---|
| [integration.test.mjs](integration.test.mjs) | 공개 설정의 비밀 미노출·소유자 차단, Origin·과금 확인·모델/문제 검사, 키 미등록 시 호출 0회 |
| integration.test.mjs | 세 고정 모델·출력 제한·각 1회 요청, Qwen SSE 사용량·본문 추출, 오류 격리·비밀 제거, 주소 허용 목록, 정적 자산 범위 |
| [scoring.test.cjs](scoring.test.cjs) | 합성 C=80/A=75, BigInt 기여 차이, R 한 번 역산, 0/100 경계, 미제공·잘못된 원점수 거부 |

createWorker에 upstreamFetch를 주입해 실제 공급자 대신 합성 JSON/SSE를 반환합니다. 테스트의 test-secret-* 값과 owner@example.com은 합성 fixture입니다. 실제 키나 개인 계정 값이 아닙니다.

## 실행과 결과 해석

루트에서 `node scripts/workbench.mjs check`를 실행합니다. 모듈 폴더에서 npm test만 실행해도 되지만, 이는 전체 빌드·구문 검사를 포함하지 않습니다. [CI 작업](../../../.github/workflows/README.md)도 루트 check를 사용합니다.

이전 검증 기록의 11건은 위 파일의 모의 연동·산술 사례입니다. [실행 로그와 한계](../../../docs/verification/WORKBENCH_2026-11-11.md)를 확인하세요. 이번 README 작성에서는 해당 테스트를 재실행하지 않았습니다.

이 테스트는 실제 공급자 연결·수학 정답률·브라우저 배치·플랫폼 로그인·비용 청구를 검증하지 않습니다. 새 모델 어댑터 추가 시 허용 모델, 요청 필드, 최종 본문 형식, 실패 분리, 키 미등록 호출 0회 조건을 함께 검토해야 합니다. 공급자 API를 직접 부르는 테스트를 기본 CI에 섞지 않습니다.

[전체 코드 읽기 지도](../../../docs/CODE_GUIDE.md)
