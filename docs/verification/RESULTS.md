# 2026-11-06 최종 검증 결과

Asia/Seoul 기준. 이 기록은 이번 재구현을 실제로 실행한 관찰 결과다. 과거 공유 대화의 테스트 결과를 재사용하지 않았다. 컨테이너·백업 파일명에 포함된 시각은 UTC이다.

## 실행 환경과 명령

Windows PowerShell + Docker Desktop 4.78.0, Engine 29.5.3의 Linux 컨테이너. Python 테스트 런타임은 3.12.16, pytest 9.2.0이다. 설치된 전체 Python 패키지는 [버전 기록](python-packages.txt)에 보존했다. Java는 Maven/Temurin 21로 WAR를 빌드하고 Tomcat 10.1에서 실행했으며 DB는 PostgreSQL 17 이미지다. 이미지 태그는 향후 패치로 바뀔 수 있으므로 동일 바이너리의 완전 고정 재현을 보장하지 않는다.

```powershell
docker compose up -d --build
docker build --target test -t project-times-test .
docker run --rm project-times-test python -m pytest -v -p no:cacheprovider
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content scripts/integration_check.py -Raw -Encoding UTF8 | docker compose exec -T analysis python -
.\scripts\Recovery-Check.ps1
.\scripts\Backup-Restore-Check.ps1
```

## 관찰 결과

- Python: 20개 케이스 통과, Starlette TestClient의 httpx 사용 중단 예고 경고 1건. 개별 이름·결과는 [실행 로그](python-tests.log), 입력·기대값은 `tests/test_api.py`와 `tests/test_scoring.py`에 있다. 경고는 숨기지 않았다.
- Java: Maven 컴파일·WAR 빌드 성공. 전용 Java 단위 테스트는 없다. HTTP 통합으로 Servlet의 실제 요청 경로를 검사했다.
- 통합: JSP 한글 출력, 보안 헤더, Java→Python·DB, 질문 저장·조회, 정책 버전·계산 실행 ID, null 보존, 잘못된 입력·교차 출처·본문 크기 제한의 14개 검사가 통과했다. [실행 로그](integration-tests.log)
- 장애·재시작: Python 중단 시 Java `/ready` 503, 재기동 후 200을 확인했다. 웹 서버를 재시작한 뒤 기존 질문 3건의 ID·내용이 유지됐다. [실행 로그](recovery-tests.log)
- 백업·복원: 운영 중인 개발 DB를 덤프하고 별도 임시 DB에 복원했다. 원본과 복원본의 질문 3건, 계산 5건, 성공한 마이그레이션 1건의 행 수가 일치했다. 임시 복원 DB와 덤프는 로컬에서 확인할 수 있도록 남겼다. [실행 로그](backup-restore.log)

합성 질문과 합성 판정 수량만 사용했다. 실제 사용자 질문이나 개인 메모리를 테스트 입력으로 사용하지 않았다. 백업·비밀번호 파일은 커밋하지 않았다.

## 수정과 재실행

초기 실행에서 Windows의 8080 포트 바인딩이 거부되어 기본 접속 포트를 18080으로 바꿨다. Python null이 Java에서 누락된 통합 실패는 Gson `serializeNulls()`로 수정했고 같은 케이스를 다시 실행해 통과했다.

읽기 전용 Python 컨테이너의 `docker compose cp` 실패는 표준입력 실행으로 해결했다. 백업 스크립트에서 Docker 진행 출력이 PowerShell 오류로 해석된 문제는 해당 구간의 출력·종료 코드 처리를 분리했다.

장애 복구 점검은 시작 직후 준비 미완료가 남을 수 있어 제한된 준비 상태 조회로 기다리도록 변경했다. 이것은 유료 API 재시도가 아니다. 재시작 데이터 비교에서는 PowerShell `@(Invoke-RestMethod ...)`가 JSON 배열을 중첩해 보관하는 문제를 확인해 직접 배열 할당으로 수정했다. 수정 후 중단→복구→웹 재시작→백업 복원을 순서대로 재실행한 최종 결과가 위 로그다. 이전 실패 로그와 빌드 원문은 로컬 `artifacts/`에 보존했다.

## 검증하지 않은 범위

이 결과는 실제 사실 판별 정확도, AI·검색 API 통합, 전문가 독립 검토, 로그인·권한, 실행 중 작업 재개, 운영 보안·부하·브라우저 상호작용을 증명하지 않는다. 백업은 행 수를 비교했으며 모든 필드와 재해 복구 절차를 전수 검증한 것은 아니다. GitHub Actions는 이번 변경에 구성했지만 이 문서를 작성한 시점의 원격 실행 결과는 아직 확인하지 않았다.
