$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Push-Location (Split-Path $PSScriptRoot -Parent)
$probeId = $null
try {
    $probeId = docker run -d --network project-times_default project-times-analysis sleep 600
    if ($LASTEXITCODE -or $probeId -notmatch '^[0-9a-f]{64}$') { throw 'Could not create isolated test client' }
    # Clear only this held disposable client's address, in case Docker reused an old test IP.
    $probeAddress=(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $probeId).Trim()
    $addressBytes=[System.Text.Encoding]::UTF8.GetBytes($probeAddress)
    $addressHasher=[System.Security.Cryptography.SHA256]::Create()
    try { $probeKey=([BitConverter]::ToString($addressHasher.ComputeHash($addressBytes))).Replace('-','').ToLowerInvariant() } finally { $addressHasher.Dispose() }
    if ($probeKey -notmatch '^[0-9a-f]{64}$') { throw 'Invalid isolated client key' }
    docker compose exec -T db psql -U project_times -d project_times -q -c "DELETE FROM login_attempts WHERE remote_key='$probeKey'"
    if ($LASTEXITCODE) { throw 'Test fixture preparation failed' }
    Get-Content scripts/login_budget_check.py -Raw -Encoding UTF8 | docker exec -i -e BUDGET_PHASE=exhaust $probeId python -
    if ($LASTEXITCODE) { throw 'Initial budget test failed' }
    $ErrorActionPreference = 'Continue'
    docker compose restart web 2>$null
    $restartExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($restartExit) { throw 'Web restart failed' }
    $portLine = Get-Content .env | Where-Object { $_ -match '^WEB_PORT=' } | Select-Object -First 1
    $port = if ($portLine) { [int]($portLine -split '=',2)[1] } else { 18080 }
    $available = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        try { if ((Invoke-RestMethod "http://127.0.0.1:$port/ready" -TimeoutSec 5).status -eq 'ok') { $available=$true; break } } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $available) { throw 'Web readiness did not recover' }
    Get-Content scripts/login_budget_check.py -Raw -Encoding UTF8 | docker exec -i -e BUDGET_PHASE=verify $probeId python -
    if ($LASTEXITCODE) { throw 'Persistent budget test failed' }
    Write-Output 'PASS login budget survives web restart from the same isolated client address'
} finally {
    if ($probeId -match '^[0-9a-f]{64}$' -and $probeKey -match '^[0-9a-f]{64}$') {
        $ErrorActionPreference='Continue'
        docker compose exec -T db psql -U project_times -d project_times -q -c "DELETE FROM login_attempts WHERE remote_key='$probeKey'" | Out-Null
    }
    if ($probeId -match '^[0-9a-f]{64}$') { $ErrorActionPreference='Continue'; docker rm -f $probeId | Out-Null }
    Pop-Location
}
