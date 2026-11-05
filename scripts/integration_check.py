"""Run through stdin in analysis container: python - < scripts/integration_check.py.
Synthetic inputs only. No paid API calls. Persists diagnostic/calculation rows.
"""
import json
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = 'http://web:8080'

def call(path, data=None, headers=None, raw=None):
    body = json.dumps(data).encode() if data is not None else raw
    request = Request(BASE + path, data=body,
                      headers=headers or ({'Content-Type': 'application/json'} if body else {}))
    try:
        with urlopen(request, timeout=12) as response:
            return response.status, response.read().decode(), response.headers
    except HTTPError as error:
        return error.code, error.read().decode(), error.headers

def check(name, condition):
    assert condition, name
    print('PASS ' + name, flush=True)

status, body, headers = call('/')
check('JSP renders Korean workbench', status == 200 and '검증 작업대' in body and '<%@' not in body)
check('security headers', headers['X-Content-Type-Options'] == 'nosniff' and "frame-ancestors 'none'" in headers['Content-Security-Policy'])
status, body, _ = call('/ready')
check('Java -> Python and PostgreSQL readiness', status == 200 and json.loads(body)['database'] == 'ok')
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
print('Integration checks complete; these checks do not verify factual judgments or authentication.')
