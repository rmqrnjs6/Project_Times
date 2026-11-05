<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>로그인 · Project Times</title><link rel="stylesheet" href="/style.css"></head><body><main>
<header><p>PROJECT TIMES</p><h1>검증 작업대 로그인</h1><p>허가된 계정으로 작업 기록에 접근하세요.</p></header><section>
<% if (request.getParameter("error") != null) { %><p role="alert">로그인할 수 없습니다. 입력한 정보를 확인하거나 잠시 후 다시 시도하세요.</p><% } %>
<form action="/login" method="post"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<label for="username">계정</label><input id="username" name="username" autocomplete="username" required maxlength="64">
<label for="password">비밀번호</label><input id="password" name="password" type="password" autocomplete="current-password" required maxlength="64">
<button>로그인</button></form></section><footer>공용 기기에서는 사용 후 로그아웃하세요.</footer></main></body></html>
