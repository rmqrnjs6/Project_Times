const byId = id => document.getElementById(id);
const axes = Object.fromEntries(['F', 'S', 'I', 'T', 'C', 'R', 'E'].map(a => [a, a === 'E' ? null : {positive: 6, negative: 2, unknown: 2}]));
byId('axes').value = JSON.stringify(axes, null, 2);
async function request(path, data) {
  const csrf = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
  const response = await fetch(path, data === undefined ? {} : {method: 'POST', headers: {'Content-Type': 'application/json', [csrfHeader]: csrf}, body: JSON.stringify(data)});
  if (response.status === 401) { window.location.assign('/login'); throw new Error('로그인이 필요합니다.'); }
  if (response.status === 204) return null;
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

const taskStatus = {QUEUED:'대기', RUNNING:'실행 중', SUCCEEDED:'완료', FAILED:'실패', CANCELLED:'취소'};
let selectedTask = null;
async function showTask(id) {
  const row = await request('/api/tasks/' + encodeURIComponent(id));
  selectedTask = id;
  byId('task-detail').textContent = JSON.stringify(row, null, 2);
}
async function tasks() {
  const rows = await request('/api/tasks');
  byId('task-list').replaceChildren(...rows.map(row => {
    const li = document.createElement('li');
    const view = document.createElement('button');
    view.type = 'button';
    view.textContent = row.question + ' · ' + (taskStatus[row.status] || row.status);
    view.onclick = () => showTask(row.id).catch(error => {byId('task-message').textContent = error.message;});
    li.append(view);
    if (['QUEUED', 'RUNNING'].includes(row.status)) {
      const cancel = document.createElement('button');
      cancel.type = 'button'; cancel.textContent = '취소';
      cancel.onclick = async () => {
        cancel.disabled = true;
        try {
          await request('/api/tasks/' + encodeURIComponent(row.id) + '/cancel', {version: row.version});
          byId('task-message').textContent = '작업을 취소했습니다.';
          await tasks();
          await showTask(row.id);
        } catch (error) {
          byId('task-message').textContent = error.message + ' · 새로고침 후 현재 상태를 확인하세요.';
          cancel.disabled = false;
        }
      };
      li.append(cancel);
    }
    return li;
  }));
  if (rows.length === 0) {
    const li = document.createElement('li'); li.textContent = '등록된 작업이 없습니다.'; byId('task-list').append(li);
  }
}
byId('task-form').onsubmit = async event => {
  event.preventDefault(); byId('task-submit').disabled = true;
  try {
    const row = await request('/api/tasks', {question: byId('task-question').value, evidenceRequirement: byId('task-requirement').value});
    byId('task-message').textContent = '등록 완료 · AI 실행기 연결 전까지 대기합니다.';
    await tasks(); await showTask(row.id);
  } catch (error) {byId('task-message').textContent = error.message;}
  finally {byId('task-submit').disabled = false;}
};
byId('task-refresh').onclick = async () => {
  try {await tasks(); if (selectedTask) await showTask(selectedTask);}
  catch (error) {byId('task-message').textContent = error.message;}
};
tasks().catch(error => {byId('task-message').textContent = error.message;});
