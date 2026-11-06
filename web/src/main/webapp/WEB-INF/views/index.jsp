<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="_csrf" content="${_csrf.token}"><meta name="_csrf_header" content="${_csrf.headerName}"><title>Project Times · 검증 작업대</title><link rel="stylesheet" href="/style.css"><script src="/workbench.js" defer></script></head>
<body><main><header><p>PROJECT TIMES · 개발 기반 v0.1</p><h1>근거를 남기는 검증 작업대</h1><p>다중 AI · 원본 근거 · 독립 전문가 검토를 연결하기 위한 개발 기반입니다.</p></header>
<aside>로그인한 계정의 연결 점검과 산술 계산을 제공합니다. 실제 사실 판별·AI 호출·전문가 검토는 아직 구현하지 않았습니다.</aside>
<form method="post" action="/logout"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><button>로그아웃</button></form>
<section><h2>검증 작업</h2><p>질문과 근거 확인 조건을 등록하고 진행 상태와 이력을 확인합니다. AI 실행기는 다음 단계에서 연결하므로 새 작업은 대기 상태로 유지됩니다.</p>
<form id="task-form"><label for="task-question">검증할 질문</label><textarea id="task-question" required maxlength="2000"></textarea>
<label for="task-requirement">근거 확인 조건</label><select id="task-requirement"><option value="PRIMARY_REQUIRED">1차 출처 확인 필요</option><option value="CROSS_CHECK_REQUIRED">독립 출처 교차 확인 필요</option></select><button id="task-submit">작업 등록</button></form>
<output id="task-message" aria-live="polite"></output><button id="task-refresh">목록 새로고침</button><p>최근 작업 최대 50개를 표시합니다.</p><ul id="task-list"></ul>
<h3>작업 상세 · 변경 이력</h3><pre id="task-detail" aria-live="polite">목록에서 작업을 선택하세요.</pre></section>
<section><h2>서비스 연결 확인</h2><button id="health">Java · Python · DB 확인</button><output id="health-result" aria-live="polite"></output></section>
<section><h2>질문 저장 · 연결 점검</h2><p>질문은 PostgreSQL에 저장됩니다. 검증 실행이나 AI 응답 생성은 하지 않습니다.</p><form id="question-form"><label for="question">확인할 질문</label><textarea id="question" required maxlength="2000"></textarea><button>저장하고 연결 확인</button></form><output id="question-result" aria-live="polite"></output><ul id="checks"></ul></section>
<section><h2>7축 참조 계산기</h2><p>F 정확성 · S 공신력 · I 독립성 · T 시간 적합성 · C 일치성 · R 모순 위험 · E 전문가 검증</p><p>각 항목의 지지·반박·미확인 수량을 입력하세요. R은 모순 위험의 긍정·부정 판정 수량이며, 종합 계산에서 한 번 역변환합니다. 미검토 E는 null로 남깁니다.</p>
<form id="evaluation-form"><label for="stage">검토 단계</label><select id="stage"><option value="provisional">전문가 검토 전 · E 제외</option><option value="reviewed">전문가 검토 후 · E 필요</option></select><label for="axes">항목별 판정 수량 (JSON)</label><textarea id="axes" rows="12" spellcheck="false"></textarea><button>계산 결과와 원본 입력 저장</button></form><output id="evaluation-error" aria-live="polite"></output><h3>계산 결과</h3><pre id="evaluation-result">아직 계산하지 않았습니다.</pre><p>표시 범위는 미확인 항목에 따른 산술 범위이며, 정답 확률이나 통계적 신뢰구간이 아닙니다.</p></section>
<footer>원본 입력 · 계산 결과 · 정책 버전은 함께 보존됩니다. 외부 공개용 서비스가 아닙니다.</footer></main></body></html>
