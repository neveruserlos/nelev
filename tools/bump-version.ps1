param(
    [Parameter(Mandatory = $true)][string]$VersionName,
    [int]$VersionCode = 0
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root "version.properties"

if (-not (Test-Path $file)) { throw "version.properties not found: $file" }

$lines = [System.IO.File]::ReadAllLines($file)
$currentCode = 0
foreach ($line in $lines) {
    if ($line -match '^\s*versionCode\s*=\s*(\d+)\s*$') { $currentCode = [int]$Matches[1] }
}

if ($VersionCode -le 0) { $VersionCode = $currentCode + 1 }
if ($VersionCode -le $currentCode) {
    throw "versionCode must grow: current=$currentCode, requested=$VersionCode"
}
if ([string]::IsNullOrWhiteSpace($VersionName)) { throw "VersionName is empty" }

$out = foreach ($line in $lines) {
    if ($line -match '^\s*versionCode\s*=') { "versionCode=$VersionCode" }
    elseif ($line -match '^\s*versionName\s*=') { "versionName=$VersionName" }
    else { $line }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($file, (($out -join "`n") + "`n"), $utf8NoBom)

Write-Host "version.properties -> versionCode=$VersionCode versionName=$VersionName"
