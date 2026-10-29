# Shobdo Keyboard - repository guard (PowerShell).
#
# Refuses to let the memory folder, assistant instruction files, signing material or local
# machine configuration be tracked or staged. Run by the pre-commit and pre-push hooks, and
# by release.ps1 before anything is published.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/guard.ps1
#
# Exit code 0 means the repository is clean.

$ErrorActionPreference = 'Stop'

$forbiddenNames = @(
    'AGENTS.md', 'CLAUDE.md', 'GEMINI.md', '.cursorrules',
    'local.properties', '.env'
)
$forbiddenPrefixes = @('.ai/')
$forbiddenSuffixes = @('.jks', '.keystore')

function Test-ForbiddenPath {
    param([string]$Path)

    $normalized = ($Path -replace '\\', '/').Trim()
    if ([string]::IsNullOrEmpty($normalized)) { return $false }

    foreach ($prefix in $forbiddenPrefixes) {
        if ($normalized.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    $leaf = ($normalized -split '/')[-1]
    foreach ($name in $forbiddenNames) {
        if ($leaf -ieq $name) { return $true }
    }
    foreach ($suffix in $forbiddenSuffixes) {
        if ($normalized.EndsWith($suffix, [System.StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

function Get-RepositoryRoot {
    $root = & git rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($root)) {
        Write-Host 'guard: not inside a git repository'
        exit 1
    }
    return $root.Trim()
}

$root = Get-RepositoryRoot
Push-Location $root
try {
    $candidates = @()
    $candidates += @(& git ls-files)
    $candidates += @(& git diff --cached --name-only --diff-filter=ACMRT)
    $candidates += @(& git diff --cached --name-only --diff-filter=D)

    $offenders = @()
    foreach ($path in $candidates) {
        if ([string]::IsNullOrWhiteSpace($path)) { continue }
        if (Test-ForbiddenPath $path) { $offenders += $path }
    }

    $offenders = $offenders | Sort-Object -Unique
    if ($offenders.Count -gt 0) {
        Write-Host ''
        Write-Host 'guard: these paths must never be committed, pushed or uploaded:' -ForegroundColor Red
        foreach ($path in $offenders) { Write-Host "  - $path" -ForegroundColor Red }
        Write-Host ''
        Write-Host 'Remove them from the index with:' -ForegroundColor Yellow
        Write-Host '  git rm --cached <path>' -ForegroundColor Yellow
        Write-Host 'and keep them listed in .gitignore and .git/info/exclude.'
        exit 1
    }

    $tracked = (& git ls-files | Measure-Object).Count
    Write-Host "guard: OK - $tracked tracked file(s), none private"
    exit 0
}
finally {
    Pop-Location
}
