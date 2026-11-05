$ErrorActionPreference = 'Stop'
function Invoke-TimesDocker {
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { & docker @args 2>$null; $nativeExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $savedPreference }
    if ($nativeExit) { throw "Docker command failed with exit $nativeExit" }
}
function Wait-TimesReady($baseUrl) {
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $ready = Invoke-RestMethod "$baseUrl/ready" -TimeoutSec 8
            if ($ready.status -eq 'ok') { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw 'Readiness did not recover within the polling window'
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $port = 18080
    $portLine = Get-Content .env | Where-Object { $_ -match '^WEB_PORT=' } | Select-Object -First 1
    if ($portLine) { $port = [int]($portLine -split '=', 2)[1] }
    $baseUrl = "http://127.0.0.1:$port"
    $query = "SELECT count(*)::text || ':' || md5(COALESCE(string_agg(id::text || question,'' ORDER BY id),'')) FROM diagnostic_checks"
    $before = docker compose exec -T db psql -U project_times -d project_times -At -c $query
    if ($LASTEXITCODE) { throw 'source persistence query failed' }
    Invoke-TimesDocker compose stop analysis
    $failureStatus = 0
    try { Invoke-WebRequest "$baseUrl/ready" -UseBasicParsing | Out-Null }
    catch { if ($_.Exception.Response) { $failureStatus = [int]$_.Exception.Response.StatusCode } else { throw } }
    if ($failureStatus -ne 503) { throw "Expected 503, got $failureStatus" }
    Write-Output 'PASS analysis stopped -> Java readiness HTTP 503'
} finally {
    $ErrorActionPreference = 'Continue'
    docker compose up -d --wait 2>$null
    $startExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($startExit) { Pop-Location; throw 'service recovery failed' }
}
try {
    Wait-TimesReady $baseUrl
    Write-Output 'PASS analysis restarted -> Java readiness HTTP 200'
    Invoke-TimesDocker compose restart web
    Wait-TimesReady $baseUrl
    $after = docker compose exec -T db psql -U project_times -d project_times -At -c $query
    if ($LASTEXITCODE) { throw 'restored persistence query failed' }
    if (($before -join '') -ne ($after -join '')) { throw 'saved question digest changed after restart' }
    Write-Output 'PASS web restart retained all saved question IDs and content (aggregate digest compared)'
} finally { Pop-Location }
