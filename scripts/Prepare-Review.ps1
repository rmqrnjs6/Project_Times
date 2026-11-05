param([switch]$NoOpen)
$ErrorActionPreference='Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not (Test-Path -LiteralPath .env)) { throw 'Existing local .env is required; no passwords will be generated or reset.' }
    $settings=@{}
    foreach ($line in Get-Content -LiteralPath .env) {
        if ($line -match '^(BOOTSTRAP_USERNAME|BOOTSTRAP_PASSWORD|WEB_PORT)=(.*)$') { $settings[$Matches[1]]=$Matches[2] }
    }
    if (-not $settings['BOOTSTRAP_PASSWORD']) { throw 'Local login setting is missing.' }
    $port=if ($settings['WEB_PORT']) { [int]$settings['WEB_PORT'] } else { 18080 }
    $baseUrl="http://127.0.0.1:$port"
    $ErrorActionPreference='Continue'
    docker compose up -d 2>$null
    $startExit=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if ($startExit) { throw 'Docker services could not be started. Start Docker Desktop and retry.' }
    $available=$false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        try {
            if ((Invoke-RestMethod "$baseUrl/ready" -TimeoutSec 5).status -eq 'ok') {
                $page=Invoke-WebRequest "$baseUrl/login" -UseBasicParsing -TimeoutSec 5
                if ($page.StatusCode -eq 200) { $available=$true; break }
            }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $available) { throw 'The login page or its dependencies are not ready.' }
    git check-ignore -q artifacts/review/LOGIN.txt
    if ($LASTEXITCODE -ne 0) { throw 'Private review notes must be excluded from Git before proceeding.' }
    New-Item -ItemType Directory -Path artifacts/review -Force | Out-Null
    $username=if ($settings['BOOTSTRAP_USERNAME']) { $settings['BOOTSTRAP_USERNAME'] } else { 'owner' }
    $note=@(
        'PROJECT TIMES - LOCAL REVIEW ACCESS',
        'Private local file. Do not upload or share this file.',
        '',
        "Login page: $baseUrl/login",
        "Username: $username",
        "Password: $($settings['BOOTSTRAP_PASSWORD'])",
        '',
        'These are the original bootstrap settings. No password was changed.',
        'If the account password has since changed, use the current password.',
        '',
        'Review guide: docs/REVIEW_GUIDE.md',
        'Current scope: login, owned question storage, reference calculations.',
        'No external AI calls or live factual verification are performed.',
        'The address works on this computer only. Docker must remain running.'
    )
    $accessPath=Join-Path (Get-Location) 'artifacts/review/LOGIN.txt'
    [System.IO.File]::WriteAllLines($accessPath,[string[]]$note,[System.Text.UTF8Encoding]::new($false))
    Write-Output 'READY: login page and Java/Python/database readiness verified.'
    Write-Output "Review URL: $baseUrl/login"
    Write-Output 'Private login instructions: artifacts/review/LOGIN.txt (ignored by Git).'
    if (-not $NoOpen) {
        Start-Process -FilePath notepad.exe -ArgumentList ('"'+$accessPath+'"')
        Start-Process "$baseUrl/login"
    }
} finally { Pop-Location }
