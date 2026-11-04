param(
    [string]$Notes = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.properties"
$apkOut = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$distDir = Split-Path -Parent $root   # X:\nelev

$props = @{}
foreach ($line in [System.IO.File]::ReadAllLines($versionFile)) {
    if ($line -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2].Trim() }
}
$versionCode = [int]$props["versionCode"]
$versionName = $props["versionName"]
if (-not $versionName) { throw "versionName missing in $versionFile" }

$slug = (($versionName -replace '\s+', '-') -replace '[^A-Za-z0-9._-]', '')
$tag = "v$slug"
$apkName = "nelev-app-release-$slug.apk"
$apkCopy = Join-Path $distDir $apkName

if (-not $SkipBuild) {
    Push-Location $root
    try {
        & .\gradlew.bat assembleRelease -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    } finally { Pop-Location }
}

if (-not (Test-Path $apkOut)) { throw "APK not found: $apkOut" }
Copy-Item $apkOut $apkCopy -Force

$hash = (Get-FileHash $apkCopy -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item $apkCopy).Length

# Remote -> apk URL
$apkUrl = ""
try {
    $remote = (git -C $root remote get-url origin 2>$null)
    if ($remote -match 'github\.com[:/](?<owner>[^/]+)/(?<repo>[^/.]+)(\.git)?$') {
        $apkUrl = "https://github.com/$($Matches['owner'])/$($Matches['repo'])/releases/download/$tag/$apkName"
    }
} catch { }

if (-not $Notes) { $Notes = "NELEV $versionName" }

$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    minSdk      = 26
    apkUrl      = $apkUrl
    sha256      = $hash
    sizeBytes   = $size
    publishedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    changelog   = $Notes
}
$manifestPath = Join-Path $root "version.json"
$jsonText = $manifest | ConvertTo-Json -Depth 4
[System.IO.File]::WriteAllText($manifestPath, $jsonText, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "APK:      $apkCopy"
Write-Host "SHA256:   $hash"
Write-Host "Tag:      $tag"
Write-Host "Manifest: $manifestPath"

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    Write-Host "gh CLI not found. Install it and run: gh auth login" -ForegroundColor Yellow
    exit 0
}

& $gh.Source auth status *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "GitHub CLI is not authenticated. Run: gh auth login" -ForegroundColor Yellow
    exit 0
}

$tags = (& $gh.Source release list --limit 100 2>$null)
if ($tags -match [regex]::Escape($tag)) {
    Write-Host "Release $tag exists, uploading assets with --clobber..."
    & $gh.Source release upload $tag $apkCopy $manifestPath --clobber
} else {
    & $gh.Source release create $tag $apkCopy $manifestPath --title "NELEV $versionName" --notes $Notes
}
if ($LASTEXITCODE -ne 0) { throw "gh release failed" }

Write-Host "Released $tag" -ForegroundColor Green
