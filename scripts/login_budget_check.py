"""Use a disposable test container/network address. Does not use real passwords."""
import json
import os
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPRedirectHandler
from urllib.parse import urlencode
from urllib.error import HTTPError
from http.cookiejar import CookieJar

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

opener=build_opener(HTTPCookieProcessor(CookieJar()),NoRedirect())
with opener.open('http://web:8080/api/csrf',timeout=10) as response:
    token=json.load(response)['token']
phase=os.environ.get('BUDGET_PHASE','all')
attempts=range(1,11) if phase=='exhaust' else ([11] if phase=='verify' else range(1,12))
for attempt in attempts:
    body=urlencode({'username':'synthetic_missing_user','password':'synthetic-invalid-password','_csrf':token}).encode()
    request=Request('http://web:8080/login',data=body,headers={'Content-Type':'application/x-www-form-urlencoded'})
    try:
        with opener.open(request,timeout=10) as response: status=response.status
    except HTTPError as error: status=error.code
    expected=302 if attempt<=10 else 429
    assert status==expected, f'Attempt {attempt}: expected {expected}, observed {status}'
print('PASS actual login budget phase: '+phase+' (10 failures allowed; request 11 denied)')
print('No bootstrap credentials used. Budget persistence checked before the runner cleans its disposable fixture.')
