# Runs the desktop memory matrix before a release: six 45 minute soaks of
# the packaged app (scripts/soak-desktop.ps1), two at a time, each judged by
# its own -Gate against 1.10.0's runs. Prints one table at the end and exits 3
# when any leg failed.
#
#   powershell -File scripts/soak-matrix.ps1 -App C:\path\to\Octo
#   powershell -File scripts/soak-matrix.ps1 -WhatIf
#
# Legs:
#   full       the defaults (full player, art, wash, lyrics)
#   still      full player off, wash and its motion off, art off, no advance
#   tray       still, window closed to the tray after 2 minutes
#   minimized  still, window minimized after 2 minutes
#   radio      still, playing radio
#   mini       full player off, no advance, handed over to the mini player
#
# Options:
#   -App <folder>   the app image to soak (default: the one
#                   :desktop:createDistributable made)
#   -Minutes <n>    how long each leg plays (default 45, at least 40)
#   -Label <name>   prefix of each leg's run folder (default matrix)
#   -WhatIf         prints the legs and their commands without running them
#
# Each leg's results go to desktop\build\soak\<label>-<leg>. Never more than
# two soaks run at once; a pair has ended before the next starts.
param(
    [string]$App = "",
    [int]$Minutes = 45,
    [string]$Label = "matrix",
    [switch]$WhatIf
)
$ErrorActionPreference = "Stop"
if ($Minutes -lt 40) { throw "-Minutes must be at least 40" }
$root = Split-Path -Parent $PSScriptRoot
$soakScript = Join-Path $PSScriptRoot "soak-desktop.ps1"

$still = @("-Full", "off", "-Wash", "off", "-WashMotion", "off", "-Art", "off", "-Advance", "0")
$legs = [ordered]@{
    full = @()
    still = $still
    tray = $still + @("-TrayAfter", "2")
    minimized = $still + @("-MinimizeAfter", "2")
    radio = $still + @("-Source", "radio")
    mini = @("-Full", "off", "-Advance", "0", "-MiniOnly")
}

function Get-LegArguments([string]$Leg) {
    $arguments = @("-NoProfile", "-File", $soakScript, "-Label", "$Label-$Leg", "-Minutes", "$Minutes") + $legs[$Leg] + @("-Gate", $Leg)
    if ($App) { $arguments += @("-App", $App) }
    $arguments
}

if ($WhatIf) {
    foreach ($leg in $legs.Keys) { Write-Host "$leg`: pwsh $((Get-LegArguments $leg) -join ' ')" }
    return
}

$names = @($legs.Keys)
$results = @()
for ($i = 0; $i -lt $names.Count; $i += 2) {
    $pair = @($names[$i..([math]::Min($i + 1, $names.Count - 1))])
    $running = foreach ($leg in $pair) {
        Write-Host "Starting $leg"
        $log = Join-Path ([IO.Path]::GetTempPath()) "$Label-$leg.out"
        [pscustomobject]@{ Leg = $leg; Log = $log; Process = Start-Process -FilePath "pwsh" -ArgumentList (Get-LegArguments $leg) -PassThru -NoNewWindow -RedirectStandardOutput $log }
    }
    foreach ($job in $running) {
        $job.Process.WaitForExit()
        $summary = Join-Path $root "desktop\build\soak\$Label-$($job.Leg)\summary.txt"
        $gate = if (Test-Path $summary) { Get-Content $summary | Where-Object { $_ -like "GATE *" } | Select-Object -First 1 } else { $null }
        $results += [pscustomobject]@{
            Leg = $job.Leg
            Result = if ($gate -like "GATE OK *") { "OK" } else { "FAILED" }
            Numbers = if ($gate) { ($gate -replace '^GATE (OK|FAILED) \S+ ', '') } else { "no summary (exit $($job.Process.ExitCode))" }
        }
        Remove-Item $job.Log -ErrorAction SilentlyContinue
    }
}

$results | Format-Table -AutoSize -Wrap | Out-String -Width 200 | Write-Host
if (@($results | Where-Object { $_.Result -ne "OK" }).Count -gt 0) { exit 3 }
