<#
.SYNOPSIS
    Capture the screenshots in docs/screenshots from a running emulator or a connected phone.

.DESCRIPTION
    Installs the debug APK, resets its data so the run starts from the default state, makes
    Shobdo Keyboard the active input method, opens the debug-only demo chat screen and
    photographs it with `adb exec-out screencap`.

    Keyboard keys are drawn on a Canvas, so they have no accessibility nodes: the taps on the
    keyboard itself are positions in its own layout on a 1080x2400 Pixel-class screen, listed
    in one place below. On a different device, take one screenshot first and adjust them.
    Everything in the settings app is found by its visible text instead, so the theme sequence
    keeps working when the gallery grows.

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
$ime = "$package/app.shobdo.keyboard.ime.ShobdoIME"
$settings = "$package/app.shobdo.keyboard.settings.SettingsActivity"
$demo = "$package/.DemoActivity"

# Keyboard geometry on 1080x2400, measured from a capture of this build.
$SHIFT = @(56, 2107)
$KEY_A = @(56, 1920)
$KEY_I = @(812, 1754)
$KEY_M = @(831, 2107)
$BACKSPACE = @(995, 2107)
$GLOBE = @(837, 2242)

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') failed" }
}

function Tap-At {
    param([int[]]$Point, [int]$WaitMs = 400)
    Invoke-Adb shell input tap $Point[0] $Point[1]
    Start-Sleep -Milliseconds $WaitMs
}

function Save-Shot {
    param([string]$Name)
    Start-Sleep -Milliseconds 900
    $path = Join-Path $shots "$Name.png"
    # cmd's redirection writes the raw bytes. Piping through PowerShell's own
    # redirection (or capturing exec-out into a variable) would decode the PNG as
    # text and corrupt it.
    & cmd /c "adb -s $Serial exec-out screencap -p > `"$path`""
    if ($LASTEXITCODE -ne 0) { throw "screencap failed for $Name" }
    Write-Host "  captured $Name.png"
}

# --- reading the UI ---------------------------------------------------------
# The settings app is Compose, so every label is a real accessible node with bounds.

function Get-UiNodes {
    Invoke-Adb shell 'uiautomator dump /sdcard/ui.xml' | Out-Null
    $text = (& adb -s $Serial shell 'cat /sdcard/ui.xml') -join ''
    if (-not $text) { return @() }
    try { $xml = [xml]$text } catch { return @() }
    $nodes = @()
    foreach ($node in $xml.SelectNodes('//node')) {
        $bounds = $node.GetAttribute('bounds')
        if (-not $bounds) { continue }
        $match = [regex]::Match($bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $match.Success) { continue }
        $left = [int]$match.Groups[1].Value
        $top = [int]$match.Groups[2].Value
        $right = [int]$match.Groups[3].Value
        $bottom = [int]$match.Groups[4].Value
        $nodes += [pscustomobject]@{
            Class = $node.GetAttribute('class')
            Text  = $node.GetAttribute('text')
            X     = [int](($left + $right) / 2)
            Y     = [int](($top + $bottom) / 2)
        }
    }
    return $nodes
}

function Find-Text {
    param([string]$Text)
    return Get-UiNodes | Where-Object { $_.Text -eq $Text } | Select-Object -First 1
}

function Find-Contains {
    param([string]$Text)
    return Get-UiNodes | Where-Object { $_.Text -and $_.Text.Contains($Text) } |
        Select-Object -First 1
}

function Tap-Text {
    param([string]$Text, [int]$Attempts = 6)
    for ($i = 0; $i -lt $Attempts; $i++) {
        $node = Find-Text -Text $Text
        if ($node) {
            Invoke-Adb shell input tap $node.X $node.Y
            Start-Sleep -Milliseconds 800
            return
        }
        Invoke-Adb shell input swipe 540 1800 540 900 300
        Start-Sleep -Milliseconds 700
    }
    throw "could not find '$Text' on screen"
}

function Tap-Below {
    param([string]$Anchor, [int]$Offset)
    $node = Find-Text -Text $Anchor
    if (-not $node) { throw "could not find the '$Anchor' heading" }
    Invoke-Adb shell input tap 540 ($node.Y + $Offset)
    Start-Sleep -Milliseconds 900
}

function Tap-Field {
    # Tap the message field where it actually is. With the keyboard hidden the field sits
    # at the bottom of the screen, so a fixed coordinate would miss it.
    $node = Get-UiNodes | Where-Object { $_.Class -eq 'android.widget.EditText' } |
        Select-Object -First 1
    if (-not $node) { throw 'the demo message field is not on screen' }
    Invoke-Adb shell input tap $node.X $node.Y
    Start-Sleep -Milliseconds 700
}

function Wait-Keyboard {
    # Ask the system whether the input method window is shown instead of guessing with a
    # sleep: the keyboard attaches a moment after the field takes focus.
    for ($i = 0; $i -lt 16; $i++) {
        $dump = (& adb -s $Serial shell dumpsys input_method) -join "`n"
        if ($dump -match 'mInputShown=true') { return }
        if ($i -eq 4 -or $i -eq 9) { Tap-Field }
        Start-Sleep -Milliseconds 700
    }
    throw 'the keyboard did not appear on the demo screen'
}

function Show-Home {
    # A resumed settings instance may still be on the Themes screen; walk back to Home.
    for ($i = 0; $i -lt 3; $i++) {
        if (Find-Text -Text 'THEME') { return }
        Invoke-Adb shell input keyevent 4
        Start-Sleep -Milliseconds 900
    }
}

function Show-Demo {
    # Walk back to the demo instead of restarting it: force-stopping the app detaches the
    # input method and the next shot can catch the screen before the keyboard returns.
    for ($i = 0; $i -lt 6; $i++) {
        if (Find-Contains -Text 'Nusrat') { return }
        Invoke-Adb shell input keyevent 4
        Start-Sleep -Milliseconds 900
    }
    throw 'could not get back to the demo screen'
}

function Set-Switch {
    # The switch sits at the right edge of the language row, level with its label.
    param([string]$Label)
    $node = Find-Text -Text $Label
    if (-not $node) { throw "could not find the '$Label' row" }
    Invoke-Adb shell input tap 937 $node.Y
    Start-Sleep -Milliseconds 700
}

function Set-Theme {
    param([string]$Name)
    Write-Host "  applying $Name"
    # Start from the demo so the settings app never stacks a second copy, which would need
    # more BACK presses than Show-Demo allows.
    Show-Demo
    Invoke-Adb shell am start -n $settings | Out-Null
    Start-Sleep -Seconds 2
    Show-Home
    Tap-Below -Anchor 'THEME' -Offset 110
    Invoke-Adb shell input swipe 540 700 540 2100 300
    Tap-Text -Text $Name
    Tap-Text -Text 'Apply'
    Start-Sleep -Milliseconds 800
    Show-Demo
    Tap-Field
    Wait-Keyboard
    # Focusing an empty text field arms shift once; tap it back off for a lower-case shot.
    Tap-At -Point $SHIFT
    Start-Sleep -Milliseconds 800
}

# --- steps ------------------------------------------------------------------

Write-Host 'installing the debug build'
Invoke-Adb install -r $Apk

Write-Host 'resetting app data so the run starts from the default state'
# Default theme, English, all three languages on: without this the run would inherit
# whatever language and shift state the last session left behind.
Invoke-Adb shell pm clear $package | Out-Null

Write-Host 'selecting Shobdo Keyboard as the input method'
# Reinstalling and clearing data both reset the default IME to the system one.
Invoke-Adb shell ime enable $ime | Out-Null
Invoke-Adb shell ime set $ime | Out-Null

Write-Host 'opening the demo chat screen'
Invoke-Adb shell am start -n $demo | Out-Null
Start-Sleep -Seconds 4
Tap-Field
Wait-Keyboard

Write-Host 'capturing the three languages'
Tap-At -Point $SHIFT   # auto-capitalisation arms shift on first focus; clear it
Save-Shot '01-english'
Tap-At -Point $GLOBE -WaitMs 700   # -> Bangla
Save-Shot '02-bangla'
Tap-At -Point $GLOBE -WaitMs 700   # -> Banglish
Tap-At -Point $KEY_A -WaitMs 300
Tap-At -Point $KEY_M -WaitMs 300
Tap-At -Point $KEY_I -WaitMs 500
Save-Shot '03-banglish'

# Clear the Banglish composing text so the theme shots start from an empty field.
for ($i = 0; $i -lt 4; $i++) { Tap-At -Point $BACKSPACE -WaitMs 150 }

Write-Host 'capturing the settings screens'
Invoke-Adb shell am start -n $settings | Out-Null
Start-Sleep -Seconds 3
# A cleared app starts on onboarding; step past it so the shots show the real screens.
if (Find-Text -Text 'Skip') {
    Tap-Text -Text 'Skip'
    Start-Sleep -Milliseconds 900
}
Save-Shot '04-settings'
Tap-Below -Anchor 'THEME' -Offset 110
Save-Shot '05-themes'
Invoke-Adb shell input swipe 540 1800 540 500 400
Start-Sleep -Milliseconds 800
Save-Shot '06-more-themes'
Invoke-Adb shell input swipe 540 700 540 2100 300
Start-Sleep -Milliseconds 800

Write-Host 'capturing the theme dialog'
Tap-Text -Text 'Golden Hour'
Start-Sleep -Milliseconds 600
Save-Shot '06-theme-dialog'
Tap-Text -Text 'Cancel'

Write-Host 'limiting the keyboard to English for the theme shots'
Invoke-Adb shell am start -n $settings | Out-Null
Start-Sleep -Seconds 2
Show-Home
Set-Switch -Label 'Bangla'
Set-Switch -Label 'Banglish'

Write-Host 'capturing three themes on the keyboard'
Set-Theme -Name 'Pure Light'
Save-Shot '07-pure-light'
Set-Theme -Name 'Lava'
Save-Shot '08-lava'
Set-Theme -Name 'Aurora Borealis'
Save-Shot '09-aurora'

Write-Host 'restoring the other two languages'
Invoke-Adb shell am start -n $settings | Out-Null
Start-Sleep -Seconds 2
Show-Home
Set-Switch -Label 'Bangla'
Set-Switch -Label 'Banglish'

Write-Host 'composing the presentation images'
& python (Join-Path $PSScriptRoot 'compose_screenshots.py')
Write-Host 'done'
