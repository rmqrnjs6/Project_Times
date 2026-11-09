"""Run through stdin in analysis container: python - < scripts/integration_check.py.
Synthetic inputs only. No paid API calls. Persists diagnostic/calculation rows.
"""
import json
import re
import os
from http.cookiejar import CookieJar
from urllib.parse import urlencode
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPRedirectHandler
from urllib.error import HTTPError

BASE = os.environ.get('TEST_BASE_URL', 'http://ingress:8080')
class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None
cookies = CookieJar()
opener = build_opener(HTTPCookieProcessor(cookies), NoRedirect())
csrf_token = None

def call(path, data=None, headers=None, raw=None):
    body = json.dumps(data).encode() if data is not None else raw
    request_headers = dict(headers or ({'Content-Type': 'application/json'} if body else {}))
    if body is not None and csrf_token:
        request_headers['X-CSRF-TOKEN'] = csrf_token
    request = Request(BASE + path, data=body, headers=request_headers)
    try:
        with opener.open(request, timeout=12) as response:
            return response.status, response.read().decode(), response.headers
    except HTTPError as error:
        return error.code, error.read().decode(), error.headers

def check(name, condition):
    assert condition, name
    print('PASS ' + name, flush=True)

status, _, policy_headers = call('/login')
check('browser form origin preserved by referrer policy', status == 200 and policy_headers.get('Referrer-Policy') == 'same-origin')
status, _, _ = call('/api/checks')
check('anonymous API access denied', status == 401)
status, body, headers = call('/api/csrf')
csrf_token = json.loads(body)['token']
old_token = csrf_token
old_session = next(cookie.value for cookie in cookies if cookie.name == 'JSESSIONID')
cookie_headers = ';'.join(policy_headers.get_all('Set-Cookie', []) + headers.get_all('Set-Cookie', []))
check('session cookie is HttpOnly and SameSite Strict', 'HttpOnly' in cookie_headers and 'SameSite=Strict' in cookie_headers)
password = os.environ.get('TEST_LOGIN_PASSWORD')
if not password:
    raise RuntimeError('TEST_LOGIN_PASSWORD must be supplied without logging it')
status, _, login_headers = call('/login', raw=urlencode({'username': os.environ.get('TEST_LOGIN_USERNAME', 'owner'), 'password': password}).encode(), headers={'Content-Type': 'application/x-www-form-urlencoded', 'Origin': BASE})
if status != 302:
    print('Observed login HTTP status: ' + str(status), flush=True)
check('real database-backed login succeeds', status == 302 and login_headers.get('Location', '').rstrip('/') in ('', BASE))
new_session = next(cookie.value for cookie in cookies if cookie.name == 'JSESSIONID')
check('session identifier rotated at login', old_session != new_session)
status, body, _ = call('/api/csrf')
csrf_token = json.loads(body)['token']
new_token = csrf_token
csrf_token = old_token
status, _, _ = call('/api/checks', {'question': 'old csrf rejected'})
check('pre-login CSRF token rejected after authentication', status == 403)
csrf_token = new_token
status, body, headers = call('/')
check('JSP renders Korean workbench', status == 200 and '검증 작업대' in body and '<%@' not in body)
check('JSP input limits render from shared server policy', all(re.search(r'id="'+field+r'"[^>]*maxlength="'+limit+r'"',body) for field,limit in [('task-question','2000'),('question','2000'),('note-original','4000'),('note-interpretation','4000'),('note-source','2048')]))
check('security headers', headers['X-Content-Type-Options'] == 'nosniff' and "frame-ancestors 'none'" in headers['Content-Security-Policy'])
status, body, _ = call('/ready')
check('Java -> Python and PostgreSQL readiness', status == 200 and json.loads(body)['status'] == 'ok')
question = 'QA synthetic question: <script>alert(1)</script> 한글 보존'
status, body, _ = call('/api/checks', {'question': question})
check('question persistence returns ID', status == 201 and json.loads(body)['kind'] == 'connectivity_only')
row_id = json.loads(body)['id']
status, body, _ = call('/api/checks')
check('stored UTF-8 question can be read', status == 200 and any(row['id'] == row_id and row['question'] == question for row in json.loads(body)))
status, _, _ = call('/api/checks', {'question': ' '})
check('empty question rejected', status == 400)
status, _, _ = call('/api/checks', raw=b'question=bad', headers={'Content-Type': 'application/x-www-form-urlencoded'})
check('form submissions rejected', status == 415)
status, _, _ = call('/api/checks', {'question': 'cross-origin'}, headers={'Content-Type': 'application/json', 'Origin': 'https://untrusted.invalid'})
check('cross-origin writes rejected', status == 403)
axes = {a: {'positive': 6, 'negative': 2, 'unknown': 2} if a != 'E' else None for a in 'FSITCRE'}
status, body, _ = call('/api/evaluate', {'axes': axes})
result = json.loads(body)
check('evaluation is persisted with run ID', status == 201 and bool(result['run_id']))
check('unknown retained and expert unreviewed', result['axes']['F']['raw']['lower_percent'] == '60.0' and result['axes']['F']['raw']['upper_percent'] == '80.0' and result['axes']['F']['assessed_percent'] == '80.0' and result['axes']['E']['raw'] is None)
check('reference policy recorded', result['policy_version'] == 'reference-v1' and result['kind'] == 'arithmetic_reference_not_fact_verification')
status, body, _ = call('/api/evaluate', {'axes': axes, 'stage': 'reviewed'})
check('reviewed composite missing without expert', status == 201 and json.loads(body)['composite'] is None)
axes['F']['positive'] = -1
status, _, _ = call('/api/evaluate', {'axes': axes})
check('invalid counts rejected through Java', status == 422)
status, _, _ = call('/api/checks', raw=b'x' * 32769, headers={'Content-Type': 'application/json'})
check('request size bounded', status == 413)
status, body, _ = call('/api/materials', {'sourceUrl': 'https://example.org/source', 'content': 'synthetic external text', 'mediaType': 'text/plain'})
check('external intake denied by default empty allowlist', status == 400)
status, body, task_headers = call('/api/tasks', {'question': question, 'evidenceRequirement': 'PRIMARY_REQUIRED', 'owner': 'forged-owner'})
check('task created queued with location', status == 201 and json.loads(body)['status'] == 'QUEUED' and bool(task_headers.get('Location')))
task_id = json.loads(body)['id']
status, body, _ = call('/api/tasks/' + task_id)
task = json.loads(body)
check('task input and creation event persisted', status == 200 and task['question'] == question and task['version'] == 0 and len(task['events']) == 1)
status, body, _ = call('/api/tasks')
check('created task appears in owner list', status == 200 and any(row['id'] == task_id for row in json.loads(body)))
status, _, _ = call('/api/tasks/' + task_id + '/cancel', {'version': 7})
check('stale task version rejected', status == 409)
status, _, _ = call('/api/tasks/' + task_id + '/cancel', {'version': 0})
check('queued task cancellation succeeds', status == 204)
status, body, _ = call('/api/tasks/' + task_id)
task = json.loads(body)
check('cancellation and event persisted', status == 200 and task['status'] == 'CANCELLED' and task['version'] == 1 and len(task['events']) == 2)
status, _, _ = call('/api/tasks/' + task_id + '/cancel', {'version': 1})
check('terminal task cannot be changed', status == 409)
status, _, _ = call('/api/tasks', {'question': 'invalid condition', 'evidenceRequirement': 'UNKNOWN'})
check('invalid evidence condition rejected', status == 400)
note_input = {'sourceUrl': 'https://example.org/reference', 'originalText': ' synthetic <script>alert(1)</script> original ', 'interpretation': 'Manual interpretation only', 'stance': 'UNVERIFIED', 'owner': 'forged-owner'}
status, body, _ = call('/api/tasks/' + task_id + '/notes', note_input)
check('manual note stored as untrusted reference without fetch', status == 201 and json.loads(body)['trusted'] is False and json.loads(body)['fetched'] is False)
status, body, _ = call('/api/tasks/' + task_id)
note = json.loads(body)['notes'][0]
check('original and interpretation preserved separately', status == 200 and note['original_text'] == note_input['originalText'] and note['interpretation'] == note_input['interpretation'])
status, body, headers = call('/api/tasks/' + task_id + '/export')
check('private export includes note snapshot', status == 200 and json.loads(body)['task']['notes'][0]['id'] == note['id'] and headers.get('Cache-Control') == 'no-store' and 'attachment' in headers.get('Content-Disposition', ''))
note_input['sourceUrl'] = 'https://example.org/?token=secret'
status, _, _ = call('/api/tasks/' + task_id + '/notes', note_input)
check('sensitive query source rejected', status == 400)
status, _, _ = call('/logout', raw=b'', headers={'Content-Type': 'application/x-www-form-urlencoded'})
check('logout succeeds with CSRF', status == 302)
status, _, _ = call('/api/checks')
check('logged-out session cannot read data', status == 401)
print('Integration complete. Authentication tested with actual DB; factual judgments and production deployment remain outside scope.')
