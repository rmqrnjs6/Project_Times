# JSP 화면과 폼 연결

기준일: 2026-11-11. 코드 확인 기준: `442f869`. 이 문서는 구현 설명이며 실행 성공이나 운영 준비 완료를 뜻하지 않습니다.

## 파일

| 화면 | 연결 경로 | 책임 |
|---|---|---|
| [login.jsp](login.jsp) | GET /login | 계정·비밀번호·CSRF를 POST /login에 전달, 공통 실패 안내 |
| [index.jsp](index.jsp) | 인증 후 GET / | 연결 점검, 질문 저장, 7축 수량 계산, POST 로그아웃 |

PageController는 뷰 이름만 반환합니다. Spring 설정의 /WEB-INF/views/ 접두사와 .jsp 접미사로 렌더링됩니다. WEB-INF의 JSP는 정적 파일처럼 직접 여는 대상이 아닙니다.

## 입력·출력 계약

login.jsp는 _csrf.parameterName/token을 숨김 입력으로 넣습니다. index.jsp는 브라우저 fetch용 CSRF 토큰·헤더 이름을 meta로 전달하고 workbench.js를 defer로 불러옵니다. 로그아웃 폼에도 CSRF를 포함합니다.

질문은 최대 2,000자, 산술 입력은 axes JSON과 stage입니다. provisional은 E 제외, reviewed는 E 필요라는 의미를 화면에서 안내합니다. 계산 응답은 JS가 pre에 문자열로 표시합니다. 질문 저장 성공은 사실 검증이나 AI 생성 성공을 의미하지 않습니다.

## 수정 시 확인

JSP의 ID, [workbench.js](../../../resources/static/workbench.js)의 이벤트 대상, Controller 입력 필드를 함께 확인하세요. 외부 비신뢰 자료를 JSP 표현식이나 innerHTML로 직접 끼워 넣지 않습니다. 역할·소유권 검사는 JSP가 아니라 서버 보안 계층에서 수행합니다.

실행은 [웹 모듈 안내](../../../../../README.md)를 따릅니다. JSP 소스를 파일로 여는 대신 Docker 앱 로그인 후 / 경로를 확인합니다. 디자인 변경·실제 브라우저 검증과 API/Mock 테스트는 구분해서 기록합니다.

[전체 코드 읽기 지도](../../../../../../docs/CODE_GUIDE.md)
