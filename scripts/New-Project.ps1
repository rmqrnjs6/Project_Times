param(
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[A-Za-z0-9_.-]+$')]
  [string]$Name,
  [string]$Template = 'rmqrnjs6/Project_Times',
  [switch]$Public
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) is required.' }
gh auth status | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Run gh auth login first.' }
$visibility = if ($Public) { '--public' } else { '--private' }
gh repo create $Name --template $Template $visibility --clone
if ($LASTEXITCODE -ne 0) { throw 'Repository creation failed. Ensure Project_Times is marked as a Template repository.' }
