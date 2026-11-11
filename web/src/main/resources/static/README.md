# 기존 JSP 화면의 JavaScript와 CSS

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일과 기능

| 파일 | 역할 |
|---|---|
| [workbench.js](workbench.js) | 연결 점검·질문 저장/목록·판정 수량 전송, CSRF fetch 래퍼 |
| [style.css](style.css) | 기존 JSP 작업대·로그인 화면 스타일 |

스크립트는 [index.jsp](../../webapp/WEB-INF/views/index.jsp)에 정의된 DOM ID와 meta CSRF 정보를 사용합니다. DOM이 준비된 뒤 defer로 실행됩니다. 이 화면은 새 Sites 작업대의 검색어·모델 슬롯 화면과 별개입니다.

## 동작 순서

1. F/S/I/T/C/R의 수량 6/2/2, E=null인 예시를 JSON 입력란에 넣습니다.
2. 초기 질문 목록을 GET /api/checks로 읽습니다.
3. request()는 쓰기 요청에 application/json과 JSP에서 받은 CSRF 헤더를 붙입니다.
4. 401이면 /login으로 이동합니다. 나머지 실패는 상태·공개 오류 코드를 표시합니다.
5. 질문 저장 후 목록을 다시 읽고, 산술 계산 결과는 JSON으로 표시합니다.

질문·오류·응답은 textContent와 DOM 노드로 출력합니다. 표시 문자열을 사용자 HTML로 실행하지 않습니다. JSON 파싱 실패도 화면 오류로 처리합니다.

## 수정과 확인

DOM ID를 바꾸면 JSP와 이 스크립트를 함께 확인하세요. Spring의 CSP는 외부 스크립트와 인라인 스크립트를 허용하도록 구성하지 않았습니다. 실제 권한은 서버가 판단하며 JS 검사만으로 대체하지 않습니다.

기존 Docker 앱에 로그인해 질문 저장, 산술 결과, 세션 만료·오류 표시를 확인합니다. [Java 테스트](../../../test/java/dev/projecttimes/README.md)와 [인증 통합 스크립트](../../../../../scripts/README.md)는 브라우저 시각 검증과 다른 범위를 검사합니다.

[전체 코드 읽기 지도](../../../../../docs/CODE_GUIDE.md)
