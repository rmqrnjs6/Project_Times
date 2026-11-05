const byId = id => document.getElementById(id);
const axes = Object.fromEntries(['F', 'S', 'I', 'T', 'C', 'R', 'E'].map(a => [a, a === 'E' ? null : {positive: 6, negative: 2, unknown: 2}]));
byId('axes').value = JSON.stringify(axes, null, 2);
async function request(path, data) {
  const csrf = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
  const response = await fetch(path, data === undefined ? {} : {method: 'POST', headers: {'Content-Type': 'application/json', [csrfHeader]: csrf}, body: JSON.stringify(data)});
  if (response.status === 401) { window.location.assign('/login'); throw new Error('로그인이 필요합니다.'); }
  const result = await response.json();
  if (!response.ok) throw new Error(`${response.status}: ${result.error || '요청 실패'}`);
  return result;
}
async function checks() {
  const rows = await request('/api/checks');
  byId('checks').replaceChildren(...rows.map(row => {const li = document.createElement('li'); li.textContent = `${row.question} · ${row.created_at} · 연결 점검`; return li;}));
}
byId('health').onclick = async () => {
  try {byId('health-result').textContent = JSON.stringify(await request('/ready'));}
  catch (e) {byId('health-result').textContent = e.message;}
};
byId('question-form').onsubmit = async e => {
  e.preventDefault();
  try {const result = await request('/api/checks', {question: byId('question').value}); byId('question-result').textContent = `저장 완료: ${result.id} (사실 검증 아님)`; await checks();}
  catch (error) {byId('question-result').textContent = error.message;}
};
byId('evaluation-form').onsubmit = async e => {
  e.preventDefault(); byId('evaluation-error').textContent = ''; byId('evaluation-result').textContent = '계산 중…';
  try {const result = await request('/api/evaluate', {axes: JSON.parse(byId('axes').value), stage: byId('stage').value}); byId('evaluation-result').textContent = JSON.stringify(result, null, 2);}
  catch (error) {byId('evaluation-result').textContent = '계산하지 못했습니다.'; byId('evaluation-error').textContent = error.message;}
};
checks().catch(error => {byId('question-result').textContent = error.message;});
