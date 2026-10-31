$ErrorActionPreference = 'Continue'
$repo = (Get-Location).Path
$log  = Join-Path $repo ('stage67-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
try { Start-Transcript -Path $log -Force | Out-Null } catch {}
function Say($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function N($v) { if ($null -eq $v -or "$v" -eq '') { 0 } else { [int]$v } }
function Must($label) { if ($LASTEXITCODE -ne 0) { throw "STEP FAILED: $label (exit $LASTEXITCODE)" } }

Say 'sanity'
foreach ($p in 'gradlew.bat','.ai\PROGRESS.md','.ai\HANDOFF.md') {
  if (-not (Test-Path $p)) { throw "missing $p - wrong directory, aborting" }
}
git rev-parse --show-toplevel

Say 'cleanup scratch pngs'
$tracked = git ls-files -- 'crop-*.png' 'shot-tmp.png'
if ($tracked) { Write-Host "WARN tracked, NOT deleted: $tracked" -ForegroundColor Yellow }
else {
  Remove-Item -Path '.\crop-*.png','.\shot-tmp.png' -Force -ErrorAction SilentlyContinue
  Write-Host ("root pngs left: " + (Get-ChildItem . -Filter '*.png' -File -ErrorAction SilentlyContinue | Measure-Object).Count)
}

Say 'testDebugUnitTest on uncommitted tree'
$global:LASTEXITCODE = 0
.\gradlew.bat testDebugUnitTest --console=plain 2>&1 | Tee-Object -Variable testOut
$testExit = $LASTEXITCODE
Write-Host "TEST_EXIT=$testExit"
Must 'testDebugUnitTest'

Say 'test counts'
$tc=0; $tfail=0; $tskip=0; $tsuite=0
$xmls = Get-ChildItem -Recurse -Path . -Filter *.xml -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like '*test-results*testDebugUnitTest*' }
foreach ($x in $xmls) {
  try { [xml]$d = Get-Content -LiteralPath $x.FullName -Raw } catch { continue }
  if ($d.testsuite) {
    $tc += N $d.testsuite.tests
    $tfail += (N $d.testsuite.failures) + (N $d.testsuite.errors)
    $tskip += N $d.testsuite.skipped
    $tsuite++
  }
}
Write-Host "testcases=$tc failures=$tfail skipped=$tskip suites=$tsuite"

Say 'commit Stage 6 polish'
git add -A
git status --short
$pending = git status --porcelain
if ($pending) {
  git commit -m "Premium UI polish for Banglish v2 (Stage 6)" -m "KeyPainter, KeyboardView, ShobdoIME, StripPainter, ThemesScreen; ReviewSheetTest; DATA_SOURCES and banglish_review docs; regenerated screenshots including 13 per-theme captures."
  Must 'commit polish'
} else { Write-Host 'nothing to commit' }
git log --oneline -1

Say 'verify local image references'
$missing = @(); $imgChecked = 0
Get-ChildItem -Path . -Filter *.md -File -Recurse -ErrorAction SilentlyContinue |
  Where-Object { $_.FullName -notlike '*\build\*' -and $_.FullName -notlike '*\node_modules\*' -and $_.FullName -notlike '*\.git\*' } |
  ForEach-Object {
    $f = $_
    $ms = (Select-String -LiteralPath $f.FullName -Pattern '!\[[^\]]*\]\(([^)\s]+)' -AllMatches).Matches
    foreach ($m in $ms) {
      $rel = $m.Groups[1].Value
      if ($rel -match '^(https?:)?//' -or $rel -match '^data:' -or $rel -match '^mailto:') { continue }
      $imgChecked++
      $p = Join-Path $f.DirectoryName ($rel -replace '[#?].*$','')
      if (-not (Test-Path -LiteralPath $p)) { $missing += ($f.FullName + ' -> ' + $rel) }
    }
  }
Write-Host "imgrefs_checked=$imgChecked missing=$($missing.Count)"
if ($missing.Count -gt 0) { Write-Host 'MISSING IMAGE REFS:' -ForegroundColor Yellow; $missing | ForEach-Object { Write-Host "  $_" } }

Say 'Stage 7 gate'
$global:LASTEXITCODE = 0
.\gradlew.bat lintDebug --console=plain 2>&1 | Tee-Object -Variable lintOut
$lintExit = $LASTEXITCODE; Write-Host "LINT_EXIT=$lintExit"; Must 'lintDebug'
$global:LASTEXITCODE = 0
.\gradlew.bat testDebugUnitTest --console=plain 2>&1 | Tee-Object -Variable test2Out
$test2Exit = $LASTEXITCODE; Write-Host "TEST2_EXIT=$test2Exit"; Must 'testDebugUnitTest (gate)'
$global:LASTEXITCODE = 0
.\gradlew.bat assembleRelease --console=plain 2>&1 | Tee-Object -Variable asmOut
$asmExit = $LASTEXITCODE; Write-Host "ASSEMBLE_EXIT=$asmExit"; Must 'assembleRelease'

Say 'aapt permission check'
$permText = 'aapt unavailable - NOT CHECKED'
$apkName = 'not found'; $apkMB = 0
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$aapt = Get-ChildItem -Path "$sdk\build-tools" -Recurse -Filter aapt.exe -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
$apk = Get-ChildItem -Recurse -Path . -Filter *.apk -ErrorAction SilentlyContinue |
       Where-Object { $_.FullName -like '*\build\outputs\apk\release\*' } | Select-Object -First 1
if ($apk) { $apkName = $apk.Name; $apkMB = [math]::Round($apk.Length/1MB, 2); Write-Host "apk=$($apk.FullName)" }
if ($aapt -and $apk) {
  $permText = (& $aapt.FullName dump permissions $apk.FullName 2>&1 | Out-String).Trim()
  Write-Host $permText
} else { Write-Host 'WARN: aapt or apk missing' -ForegroundColor Yellow }

Say 'tick U1-U7 and record results'
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$progress = Get-Content '.ai\PROGRESS.md' -Raw
$progress = [regex]::Replace($progress, '(?m)^(\s*[-*]\s*)\[ \](\s*U[1-7]\b)', '$1[x]$2')
$progress = [regex]::Replace($progress, '(?m)^(\s*[-*]\s*)\[ \](\s*\*\*U[1-7])', '$1[x]$2')
Set-Content -Path '.ai\PROGRESS.md' -Value $progress -Encoding utf8 -NoNewline
Select-String -Path '.ai\PROGRESS.md' -Pattern 'U[1-7]\b' | ForEach-Object { Write-Host ('  ' + $_.Line) }

$app = @()
$app += ''
$app += '## Stage 6/7 verification — ' + $stamp
$app += ''
$app += '- testDebugUnitTest: exit ' + $testExit + '; testcases=' + $tc + ' failures=' + $tfail + ' skipped=' + $tskip + ' suites=' + $tsuite
$app += '- lintDebug: exit ' + $lintExit
$app += '- assembleRelease: exit ' + $asmExit
$app += '- release APK: ' + $apkName + ' (' + $apkMB + ' MB)'
$app += '- declared permissions:'
$app += '```'
$app += $permText
$app += '```'
$app += '- local image refs checked: ' + $imgChecked + '; missing: ' + $missing.Count
if ($missing.Count -gt 0) { foreach ($m in $missing) { $app += '  - MISSING: ' + $m } }
Add-Content -Path '.ai\PROGRESS.md' -Value ($app -join "`r`n") -Encoding utf8

$hand = @()
$hand += ''
$hand += '## Session log — ' + $stamp
$hand += ''
$hand += '- Stage 6 polish committed; scratch crop-*.png and shot-tmp.png removed from repo root.'
$hand += '- Gate: lintDebug ' + $lintExit + ', testDebugUnitTest ' + $test2Exit + ' (' + $tc + ' testcases, ' + $tfail + ' failures), assembleRelease ' + $asmExit + '.'
$hand += '- Release APK ' + $apkName + ' (' + $apkMB + ' MB); permissions recorded in PROGRESS.md.'
$hand += '- Image refs: ' + $imgChecked + ' checked, ' + $missing.Count + ' missing.'
$hand += '- Next: confirm v0.3.0 tag/assets on GitHub; fix any missing image refs listed above; keep U1–U7 evidence in PROGRESS.md current.'
Add-Content -Path '.ai\HANDOFF.md' -Value ($hand -join "`r`n") -Encoding utf8

Say 'commit docs'
git add -A
git commit -m "Record Stage 6/7 verification in PROGRESS and HANDOFF" 
Must 'commit docs'

Say 'guard'
$global:LASTEXITCODE = 0
powershell -ExecutionPolicy Bypass -File scripts\guard.ps1
$guardExit = $LASTEXITCODE; Write-Host "GUARD_EXIT=$guardExit"; Must 'guard.ps1'

Say 'release 0.3.0'
$global:LASTEXITCODE = 0
powershell -ExecutionPolicy Bypass -File scripts\release.ps1 0.3.0
$relExit = $LASTEXITCODE; Write-Host "RELEASE_EXIT=$relExit"; Must 'release.ps1 0.3.0'

Say 'verify release'
$global:LASTEXITCODE = 0
$ghOut = (& gh release view v0.3.0 2>&1 | Out-String)
Write-Host $ghOut
$ghExit = $LASTEXITCODE; Write-Host "GH_EXIT=$ghExit"
git tag --list 'v0.3.0'

$app2 = @()
$app2 += ''
$app2 += '### Release v0.3.0 verification — ' + (Get-Date -Format 'yyyy-MM-dd HH:mm')
$app2 += ''
$app2 += '- scripts\guard.ps1: exit ' + $guardExit
$app2 += '- scripts\release.ps1 0.3.0: exit ' + $relExit
$app2 += '- gh release view v0.3.0: exit ' + $ghExit
$app2 += '```'
$app2 += $ghOut.Trim()
$app2 += '```'
Add-Content -Path '.ai\PROGRESS.md' -Value ($app2 -join "`r`n") -Encoding utf8
git add -A
git commit -m "Record v0.3.0 release verification"
Must 'commit release verification'

Say 'push'
git push --follow-tags
Write-Host "PUSH_EXIT=$LASTEXITCODE"

Say 'DONE'
Write-Host ('log: ' + $log)
git log --oneline -4
try { Stop-Transcript | Out-Null } catch {}
