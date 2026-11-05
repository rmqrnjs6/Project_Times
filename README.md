# Project_Times

공유 대화의 설계를 바탕으로 다시 구현한 **로컬 개발용 검증 작업대**입니다. JSP·Java Servlet·Python FastAPI·PostgreSQL·Docker로 구성합니다. 실제 AI 판별 서비스의 완성본은 아닙니다.

## 시작

Docker Desktop을 실행하고 `.env.example`을 `.env`로 복사한 뒤 `DB_PASSWORD`를 설정합니다. `.env`는 커밋하지 않습니다.

```powershell
Copy-Item .env.example .env
# .env의 DB_PASSWORD를 변경한 뒤:
docker compose up -d --build
```

브라우저에서 http://localhost:18080 을 엽니다. `START_DOCKER.bat`도 같은 명령을 실행합니다. 최초 설치에는 이미지·의존성 다운로드가 필요합니다. 중지는 `docker compose down`이며, DB 볼륨은 남습니다. `down -v`는 데이터를 지우므로 사용하지 마세요.

## 현재 기능

- JSP 화면과 Java → Python·DB 준비 상태 확인.
- 연결 확인용 질문 저장·조회. 질문 저장은 AI 검증 실행이 아닙니다.
- 7축 판정 수량의 Decimal 산술 계산, 미확인 범위, 전문가 미검토, 모순 위험 역변환.
- PostgreSQL에 원본 계산 입력·결과·정책 버전 저장. Flyway 최초 마이그레이션.
- HTTP 입력 검사·보안 헤더·로컬호스트 바인딩·컨테이너 자원 제한.

로그인·권한·실제 AI 호출·근거 판별·전문가 검토·레이더 차트·10회 재시도·체크포인트 작업 재개는 아직 구현하지 않았습니다. 외부 공개 배포에 사용하지 마세요.

## 검증

```powershell
docker build --target test -t project-times-test .
docker run --rm project-times-test
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content scripts/integration_check.py -Raw -Encoding UTF8 | docker compose exec -T analysis python -
.\scripts\Backup-Restore-Check.ps1
```

백업 복원 검사는 별도의 DB를 만들고 남겨둡니다. 실제 데이터가 있다면 로컬 백업 파일도 민감정보로 관리하세요. 실행 로그·백업은 `artifacts/`에 보관하며 Git에는 포함하지 않습니다.

설계와 한계는 [구조 문서](docs/ARCHITECTURE.md), 작업 기록은 [진행 기록](docs/PROGRESS.md)을 확인하세요. GitHub Actions는 Python 테스트·웹 빌드·통합 연결을 검사하도록 구성했습니다. 로컬 성공과 원격 CI 성공은 별개입니다.
