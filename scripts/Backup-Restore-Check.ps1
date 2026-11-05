$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $restoreName = 'times_restore_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
    docker compose exec -T db pg_dump -U project_times -d project_times -Fc -f /tmp/project-times-check.dump
    if ($LASTEXITCODE) { throw 'pg_dump failed' }
    New-Item -ItemType Directory -Path artifacts -Force | Out-Null
    # Windows PowerShell 5 treats Docker's progress on stderr as ErrorRecord.
    $ErrorActionPreference = 'Continue'
    docker compose cp db:/tmp/project-times-check.dump "artifacts/$restoreName.dump" 2>$null
    $copyExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($copyExit) { throw 'backup copy failed' }
    docker compose exec -T db createdb -U project_times $restoreName
    if ($LASTEXITCODE) { throw 'temporary restore database creation failed' }
    docker compose exec -T db pg_restore -U project_times -d $restoreName --exit-on-error /tmp/project-times-check.dump
    if ($LASTEXITCODE) { throw 'restore failed' }
    $query = 'SELECT (SELECT count(*) FROM diagnostic_checks), (SELECT count(*) FROM calculation_runs), (SELECT count(*) FROM flyway_schema_history WHERE success);'
    $original = docker compose exec -T db psql -U project_times -d project_times -At -c $query
    if ($LASTEXITCODE) { throw 'source query failed' }
    $restored = docker compose exec -T db psql -U project_times -d $restoreName -At -c $query
    if ($LASTEXITCODE) { throw 'restore query failed' }
    if (($original -join '') -ne ($restored -join '')) { throw 'restored row counts differ' }
    Write-Output "PASS backup and restore row counts: $original"
    Write-Output "Restore database retained for inspection: $restoreName"
    Write-Output "Local backup: artifacts/$restoreName.dump (not committed)"
} finally { Pop-Location }
