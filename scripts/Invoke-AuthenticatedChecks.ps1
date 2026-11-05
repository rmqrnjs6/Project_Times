$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
$savedPassword=$env:TEST_LOGIN_PASSWORD
$savedUsername=$env:TEST_LOGIN_USERNAME
$savedEncoding=$OutputEncoding
try {
    $passwordLine=Get-Content -LiteralPath .env | Where-Object { $_ -match '^BOOTSTRAP_PASSWORD=' } | Select-Object -First 1
    $usernameLine=Get-Content -LiteralPath .env | Where-Object { $_ -match '^BOOTSTRAP_USERNAME=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'Local bootstrap password setting is required' }
    $env:TEST_LOGIN_PASSWORD=($passwordLine -split '=',2)[1]
    $env:TEST_LOGIN_USERNAME=if ($usernameLine) { ($usernameLine -split '=',2)[1] } else { 'owner' }
    $OutputEncoding=[System.Text.UTF8Encoding]::new($false)
    $ErrorActionPreference='Continue'
    Get-Content -LiteralPath scripts/integration_check.py -Raw -Encoding UTF8 | docker compose exec -T -e TEST_LOGIN_PASSWORD -e TEST_LOGIN_USERNAME analysis python -
    $nativeExit=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if ($nativeExit) { throw 'Authenticated integration checks failed; secrets were not logged' }
} finally {
    $env:TEST_LOGIN_PASSWORD=$savedPassword
    $env:TEST_LOGIN_USERNAME=$savedUsername
    $OutputEncoding=$savedEncoding
    Pop-Location
}
