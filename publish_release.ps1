# NELEV Release Script v1.9.21
# Automated GitHub Release Upload

$apkPath = "app\build\outputs\apk\release\app-release.apk"
$versionName = "1.9.21"
$versionCode = "56"
$notesFile = "release_notes_1.9.21.txt"

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
$changelog = "- Changelog now always in the app language (update window and What's New)`n- What's New dialog now matches the update window width`n- Smooth, jank-free changelog expansion in the update window"
$versionJson = @{
    versionCode = [int]$versionCode
    versionName = $versionName
    apkUrl = "https://github.com/neveruserlos/nelev/releases/download/v$versionName/$newApkName"
    sha256 = $hash
    sizeBytes = $sizeBytes
    changelog = $changelog
    minSdk = 26
} | ConvertTo-Json

$versionJsonPath = "app\build\outputs\apk\release\version.json"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path (Get-Location).Path $versionJsonPath), $versionJson, $utf8NoBom)
$firstBytes = [System.IO.File]::ReadAllBytes((Join-Path (Get-Location).Path $versionJsonPath))[0..2]
if ($firstBytes[0] -eq 0xEF -and $firstBytes[1] -eq 0xBB -and $firstBytes[2] -eq 0xBF) {
    Write-Host "ERROR: version.json has BOM, update check will fail" -ForegroundColor Red
    exit 1
}
Write-Host "version.json created (UTF-8, no BOM)" -ForegroundColor Green

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
