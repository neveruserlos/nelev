# NELEV Release Script v1.9.17
# Automated GitHub Release Upload

$apkPath = "app\build\outputs\apk\release\app-release.apk"
$versionName = "1.9.17"
$versionCode = "52"
$notesFile = "release_notes_1.9.17.txt"

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
$changelog = "• Improved text centering in update pause list items`n• Added selected period description in muted text`n• You won't see update notifications text shown for each option"
$versionJson = @{
    versionCode = [int]$versionCode
    versionName = $versionName
    apkUrl = "https://github.com/neveruserlos/nelev/releases/download/v$versionName/$newApkName"
    sha256 = $hash
    sizeBytes = $sizeBytes
    changelog = $changelog
} | ConvertTo-Json

$versionJsonPath = "app\build\outputs\apk\release\version.json"
$versionJson | Out-File -FilePath $versionJsonPath -Encoding UTF8
Write-Host "version.json created" -ForegroundColor Green

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
