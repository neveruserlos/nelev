# NELEV Release Script v1.9.37
# Automated GitHub Release Upload

$apkPath = "app\build\outputs\apk\release\app-release.apk"
$versionName = "1.9.37"
$versionCode = "72"
$notesFile = "release_notes_1.9.37.txt"

Write-Host "=== NELEV Release v$versionName ===" -ForegroundColor Cyan

# Check APK exists
if (-not (Test-Path $apkPath)) {
    Write-Host "ERROR: APK not found at $apkPath" -ForegroundColor Red
    exit 1
}

# Rename APK
$newApkName = "nelev-app-release-$versionName.apk"
$newApkPath = "app\build\outputs\apk\release\$newApkName"
Copy-Item $apkPath $newApkPath -Force
Write-Host "APK copied to $newApkName" -ForegroundColor Green

# Calculate SHA-256
Write-Host "Computing SHA-256..." -ForegroundColor Yellow
$hash = (Get-FileHash $newApkPath -Algorithm SHA256).Hash.ToLower()
Write-Host "SHA-256: $hash" -ForegroundColor Green

# Get file size
$sizeBytes = (Get-Item $newApkPath).Length
Write-Host "Size: $sizeBytes bytes" -ForegroundColor Green

# Create version.json
# NOTE: must be UTF-8 WITHOUT BOM and ASCII-safe bullets, otherwise
# org.json.JSONObject() in Updater.kt fails and the update is never found.
$changelog = "- Service notification truly cannot be hidden now - increased channel and notification priority"
# Localized changelogs for all languages, so the update window does not depend
# on raw.githubusercontent.com (which can be blocked). Old clients ignore this
# field and keep using the plain "changelog" above.
$historyPath = "app\src\main\assets\version_history.json"
$history = Get-Content $historyPath -Raw -Encoding UTF8 | ConvertFrom-Json
$historyEntry = $history.versions | Where-Object { $_.versionCode -eq [int]$versionCode } | Select-Object -First 1
$changelogs = @{}
if ($null -ne $historyEntry) {
    foreach ($p in $historyEntry.changelog.PSObject.Properties) {
        $changelogs[$p.Name] = $p.Value
    }
} else {
    Write-Host "WARNING: no version_history entry for $versionCode, changelogs will be empty" -ForegroundColor Yellow
}
$versionJson = @{
    versionCode = [int]$versionCode
    versionName = $versionName
    apkUrl = "https://github.com/neveruserlos/nelev/releases/download/v$versionName/$newApkName"
    sha256 = $hash
    sizeBytes = $sizeBytes
    changelog = $changelog
    changelogs = $changelogs
    minSdk = 26
} | ConvertTo-Json -Depth 6

$versionJsonPath = "app\build\outputs\apk\release\version.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $versionJsonPath), $versionJson, $utf8NoBom)
$firstBytes = [System.IO.File]::ReadAllBytes((Join-Path (Get-Location).Path $versionJsonPath))[0..2]
if ($firstBytes[0] -eq 0xEF -and $firstBytes[1] -eq 0xBB -and $firstBytes[2] -eq 0xBF) {
    Write-Host "ERROR: version.json has BOM, update check will fail" -ForegroundColor Red
    exit 1
}
Write-Host "version.json created (UTF-8, no BOM)" -ForegroundColor Green

# Keep an up-to-date copy of the changelog at the repo root. Older app
# versions fetch this file from raw.githubusercontent.com; the current
# versions prefer the assets path. Syncing avoids drift between the two.
Write-Host "Syncing version_history.json to repo root..." -ForegroundColor Yellow
Copy-Item "app\src\main\assets\version_history.json" "version_history.json" -Force
git add version_history.json
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    git commit -m "chore: sync root version_history.json for v$versionName" | Out-Null
    git push origin main
}

# Create GitHub Release
Write-Host "Creating GitHub Release..." -ForegroundColor Yellow
$ghPath = "C:\Program Files\GitHub CLI\gh.exe"

& $ghPath release create "v$versionName" $newApkPath $versionJsonPath --title "NELEV $versionName" --notes-file $notesFile

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Release v$versionName published successfully!" -ForegroundColor Green
    Write-Host "APK: https://github.com/neveruserlos/nelev/releases/download/v$versionName/$newApkName" -ForegroundColor Cyan
    Write-Host "SHA-256: $hash" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "ERROR: Failed to create release" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Release Complete ===" -ForegroundColor Cyan
