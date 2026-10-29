<#
.SYNOPSIS
    Cuts a Shobdo Keyboard release: bumps the version, moves the changelog, builds and signs
    the APK, tags it, pushes and creates the GitHub release.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\release.ps1 0.1.0

Everything that can go wrong is checked before the first file is written, so a failed run
leaves the working copy untouched.
#>
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version
)

$ErrorActionPreference = 'Stop'
$scriptRoot = $PSScriptRoot
$root = Split-Path -Parent $scriptRoot
Set-Location $root

function Stop-Release {
    param([string]$Message)
    Write-Host ''
    Write-Host "release: $Message" -ForegroundColor Red
    exit 1
}

function Write-TextFile {
    # UTF-8 without a byte-order mark: a BOM at the start of version.properties would make
    # the first property name unreadable to Java's Properties loader.
    param([string]$Path, [AllowEmptyString()][string]$Text)
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $full = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path (Get-Location) $Path }
    [System.IO.File]::WriteAllText($full, $Text, $utf8)
}

function Write-Step {
    param([string]$Message)
    Write-Host "release: $Message" -ForegroundColor Cyan
}

# --- 1. preflight -----------------------------------------------------------------------

Write-Step 'checking guard.ps1'
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptRoot 'guard.ps1')
if ($LASTEXITCODE -ne 0) { Stop-Release 'guard.ps1 refused the repository contents' }

Write-Step 'checking for a clean git tree'
$dirty = @(& git status --porcelain)
if ($dirty.Count -gt 0) {
    Write-Host ($dirty -join [Environment]::NewLine)
    Stop-Release 'the working tree has uncommitted changes; commit or discard them first'
}

if (-not (Test-Path (Join-Path $root 'gradlew.bat'))) { Stop-Release 'gradlew.bat is missing' }
if (-not (Test-Path (Join-Path $root 'app\build.gradle.kts'))) { Stop-Release 'app/build.gradle.kts is missing' }

$versionFile = Join-Path $root 'version.properties'
$changelogFile = Join-Path $root 'CHANGELOG.md'
if (-not (Test-Path $versionFile)) { Stop-Release 'version.properties is missing' }
if (-not (Test-Path $changelogFile)) { Stop-Release 'CHANGELOG.md is missing' }

$props = Get-Content $versionFile -Raw
$nameMatch = [regex]::Match($props, 'VERSION_NAME\s*=\s*(\S+)')
$codeMatch = [regex]::Match($props, 'VERSION_CODE\s*=\s*(\d+)')
if (-not $nameMatch.Success -or -not $codeMatch.Success) {
    Stop-Release 'version.properties must contain VERSION_NAME and VERSION_CODE'
}
$currentName = $nameMatch.Groups[1].Value
$currentCode = [int]$codeMatch.Groups[1].Value

if ($Version -eq $currentName) {
    $newCode = $currentCode
    Write-Step "version $Version is already set; keeping VERSION_CODE $newCode"
} else {
    $newCode = $currentCode + 1
    Write-Step "bumping $currentName ($currentCode) to $Version ($newCode)"
}

# --- 2. changelog -----------------------------------------------------------------------

$today = (Get-Date).ToString('yyyy-MM-dd')

function Update-Changelog {
    param([string]$Version, [string]$Date, [string]$Path)

    $lines = @(Get-Content $Path -Encoding UTF8)
    $existing = $lines | Where-Object { $_ -match ('^## \[' + [regex]::Escape($Version) + '\]') }

    if ($existing) {
        $updated = $lines | ForEach-Object {
            if ($_ -match ('^## \[' + [regex]::Escape($Version) + '\]')) { "## [$Version] - $Date" } else { $_ }
        }
        Write-TextFile -Path $Path -Text ($updated -join [Environment]::NewLine)
        Write-Step "CHANGELOG already had [$Version]; date set to $Date"
        return
    }

    $start = -1
    $end = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^## \[Unreleased\]') { $start = $i; continue }
        if ($start -ge 0 -and $lines[$i] -match '^## ') { $end = $i; break }
    }
    if ($start -lt 0) { throw 'CHANGELOG.md has no [Unreleased] section' }
    if ($end -lt 0) { $end = $lines.Count }

    $body = @()
    for ($i = $start + 1; $i -lt $end; $i++) {
        $line = $lines[$i]
        if ($line -match 'No unreleased changes yet\.') { continue }
        $body += $line
    }

    # Drop section headings that ended up with no bullets under them.
    $cleaned = @()
    for ($i = 0; $i -lt $body.Count; $i++) {
        if ($body[$i] -match '^### ') {
            $hasBullet = $false
            for ($j = $i + 1; $j -lt $body.Count; $j++) {
                if ($body[$j] -match '^### ') { break }
                if ($body[$j] -match '^\s*[-*]\s+\S') { $hasBullet = $true; break }
            }
            if (-not $hasBullet) { continue }
        }
        $cleaned += $body[$i]
    }
    while ($cleaned.Count -gt 0 -and [string]::IsNullOrWhiteSpace($cleaned[0])) { $cleaned = $cleaned[1..($cleaned.Count - 1)] }
    while ($cleaned.Count -gt 0 -and [string]::IsNullOrWhiteSpace($cleaned[-1])) { $cleaned = $cleaned[0..($cleaned.Count - 2)] }
    if ($cleaned.Count -eq 0) {
        $cleaned = @('### Changed', '', '- Maintenance release.')
    }

    $head = @()
    for ($i = 0; $i -lt $start; $i++) { $head += $lines[$i] }
    $tail = @()
    for ($i = $end; $i -lt $lines.Count; $i++) { $tail += $lines[$i] }

    $new = @()
    $new += $head
    $new += '## [Unreleased]'
    $new += ''
    $new += '### Added'
    $new += ''
    $new += '- No unreleased changes yet.'
    $new += ''
    $new += "## [$Version] - $Date"
    $new += ''
    $new += $cleaned
    $new += ''
    $new += $tail

    Write-TextFile -Path $Path -Text ($new -join [Environment]::NewLine)
    Write-Step "moved the Unreleased notes under [$Version]"
}

Update-Changelog -Version $Version -Date $today -Path $changelogFile

# --- 3. version.properties -------------------------------------------------------------

Write-TextFile -Path $versionFile -Text ("VERSION_NAME=$Version`nVERSION_CODE=$newCode`n")
Write-Step "version.properties now $Version / $newCode"

# --- 4. build ---------------------------------------------------------------------------

Write-Step 'running unit tests'
& (Join-Path $root 'gradlew.bat') testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { Stop-Release 'unit tests failed' }

Write-Step 'building the signed release APK'
& (Join-Path $root 'gradlew.bat') assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { Stop-Release 'the release build failed' }

$builtApk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $builtApk)) {
    Stop-Release 'app-release.apk was not produced (is the signing keystore configured?)'
}

$distDir = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$apkName = "Shobdo-Keyboard-v$Version.apk"
$apkPath = Join-Path $distDir $apkName
Copy-Item $builtApk $apkPath -Force
Write-Step "APK: dist\$apkName ($([math]::Round((Get-Item $apkPath).Length / 1MB, 2)) MB)"

$hash = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash.ToLower()
$checksumPath = "$apkPath.sha256"
Write-TextFile -Path $checksumPath -Text "$hash  $apkName"
Write-Step "SHA-256: $hash"

# --- 5. commit, tag, push ---------------------------------------------------------------

Write-Step 'committing the version bump'
& git add version.properties CHANGELOG.md
& git commit -m "chore(release): v$Version"
if ($LASTEXITCODE -ne 0) { Stop-Release 'the release commit failed' }

& git tag -a "v$Version" -m "Shobdo Keyboard v$Version"
if ($LASTEXITCODE -ne 0) { Stop-Release "could not create tag v$Version" }

$remotes = @(& git remote)
if ($remotes -contains 'origin') {
    Write-Step 'pushing the commit and tag'
    & git push origin HEAD
    if ($LASTEXITCODE -ne 0) { Stop-Release 'git push failed' }
    & git push origin "v$Version"
    if ($LASTEXITCODE -ne 0) { Stop-Release 'pushing the tag failed' }
} else {
    Write-Host 'release: no origin remote; skipping the push. Add one with gh repo create.'
}

# --- 6. GitHub release ------------------------------------------------------------------

$notesPath = Join-Path $env:TEMP "shobdo-notes-$Version.md"
$lines = @(Get-Content $changelogFile -Encoding UTF8)
$start = -1
$end = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match ('^## \[' + [regex]::Escape($Version) + '\]')) { $start = $i; continue }
    if ($start -ge 0 -and $lines[$i] -match '^## \[') { $end = $i; break }
}
if ($start -ge 0) {
    if ($end -lt 0) { $end = $lines.Count }
    $notes = @()
    for ($i = $start + 1; $i -lt $end; $i++) { $notes += $lines[$i] }
    Write-TextFile -Path $notesPath -Text ($notes -join [Environment]::NewLine)
} else {
    Write-TextFile -Path $notesPath -Text "Shobdo Keyboard v$Version"
}

$hasOrigin = @(& git remote) -contains 'origin'
if (-not $hasOrigin) {
    Write-Host 'release: no origin remote, so no GitHub release was created.'
    Write-Host "release: APK and checksum are in $distDir"
    exit 0
}

Write-Step 'creating the GitHub release'
& gh release create "v$Version" $apkPath $checksumPath --title "Shobdo Keyboard v$Version" --notes-file $notesPath
if ($LASTEXITCODE -ne 0) { Stop-Release 'gh release create failed' }

$repo = (& gh repo view --json nameWithOwner --jq .nameWithOwner)
Write-Host ''
Write-Host "release: v$Version published" -ForegroundColor Green
Write-Host "  repository: https://github.com/$repo"
Write-Host "  release:    https://github.com/$repo/releases/tag/v$Version"
