<#
.SYNOPSIS
    Capture the screenshots in docs/screenshots from a running emulator or a connected phone.

.DESCRIPTION
    Installs the debug APK, makes Shobdo Keyboard the active input method, opens the debug-only
    demo chat screen (a list of ordinary chat bubbles plus a message field) and photographs it
    with `adb exec-out screencap`.

    The taps below are positions in the keyboard's own layout on a 1080x2400 Pixel-class
    screen. On a different device, take one screenshot first and adjust the coordinates.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\screenshots.ps1
#>
param(
    [string]$Serial = "emulator-5554",
    [string]$Apk = "app\build\outputs\apk\debug\app-debug.apk"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$shots = Join-Path $root 'docs\screenshots'
New-Item -ItemType Directory -Force -Path $shots | Out-Null

$package = 'app.shobdo.keyboard.debug'
$component = "$package/app.shobdo.keyboard.ime.ShobdoIME"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') failed" }
}

function Save-Shot {
    param([string]$Name)
    Start-Sleep -Milliseconds 900
    $path = Join-Path $shots "$Name.png"
    $bytes = & adb -s $Serial exec-out screencap -p
    [System.IO.File]::WriteAllBytes($path, $bytes)
    Write-Host "  captured $Name.png"
}

Write-Host 'installing the debug build'
Invoke-Adb install -r $Apk

Write-Host 'selecting Shobdo Keyboard as the input method'
Invoke-Adb shell ime enable $component
Invoke-Adb shell ime set $component

Write-Host 'opening the demo chat screen'
Invoke-Adb shell am force-stop $package
Invoke-Adb shell am start -n "$package/.DemoActivity"
Start-Sleep -Seconds 4

# Keyboard geometry on 1080x2400: number row y=1622, q-row 1782, a-row 1942,
# z-row 2100, bottom row 2250. Globe x=823, space x=456.
Write-Host 'capturing the three languages'
Invoke-Adb shell input tap 56 2100   # shift off for a lower-case shot
Save-Shot '01-english'
Invoke-Adb shell input tap 823 2250  # globe -> Bangla
Save-Shot '02-bangla'
Invoke-Adb shell input tap 823 2250  # globe -> Banglish
Invoke-Adb shell input tap 58 1968   # a
Invoke-Adb shell input tap 859 2106  # m
Invoke-Adb shell input tap 796 1824  # i
Save-Shot '03-banglish'

Write-Host 'capturing the settings screens'
Invoke-Adb shell am start -n "$package/.settings.SettingsActivity"
Start-Sleep -Seconds 2
Save-Shot '04-settings'
Invoke-Adb shell input tap 400 1202  # the theme row
Save-Shot '05-themes'
Invoke-Adb shell input tap 240 1500   # Pure Light
Save-Shot '06-theme-dialog'
Invoke-Adb shell input tap 732 1488   # Apply
Invoke-Adb shell am start -n "$package/.DemoActivity"
Start-Sleep -Seconds 2
Save-Shot '07-pure-light'

Write-Host 'composing the presentation images'
& python (Join-Path $PSScriptRoot 'compose_screenshots.py')
Write-Host 'done'
