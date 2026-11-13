$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$testRoots = @('domain/build/test-results/test', 'app/build/test-results/testDebugUnitTest')
$suites = @()
foreach ($testRoot in $testRoots) {
    $files = @(Get-ChildItem -LiteralPath (Join-Path $projectDirectory $testRoot) -Filter 'TEST-*.xml')
    if ($files.Count -eq 0) { throw "尚未找到測試結果：$testRoot" }
    foreach ($file in $files) {
        [xml]$suite = Get-Content -LiteralPath $file.FullName
        if ([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0) { throw "測試尚未通過：$($suite.testsuite.name)" }
        $suites += [ordered]@{ name = $suite.testsuite.name; tests = [int]$suite.testsuite.tests; skipped = [int]$suite.testsuite.skipped; failures = [int]$suite.testsuite.failures; timestamp = $suite.testsuite.timestamp }
    }
}
[xml]$lint = Get-Content -LiteralPath (Join-Path $projectDirectory 'app/build/reports/lint-results-debug.xml')
$lintErrors = @($lint.issues.issue | Where-Object { $_.severity -eq 'Error' -or $_.severity -eq 'Fatal' }).Count
if ($lintErrors -ne 0) { throw 'Android 靜態檢查仍有錯誤。' }
[xml]$manifest = Get-Content -LiteralPath (Join-Path $projectDirectory 'app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml')
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$permissions = @($manifest.manifest.'uses-permission' | ForEach-Object { $_.GetAttribute('name', $androidNamespace) })
if ($permissions -contains 'android.permission.INTERNET') { throw '本機版本不得包含網路權限。' }
$releaseDirectory = Join-Path $projectDirectory 'releases'
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$apkSource = Join-Path $projectDirectory 'app/build/outputs/apk/debug/app-debug.apk'
$apkTarget = Join-Path $releaseDirectory 'shilu-0.1.0-local-debug.apk'
Copy-Item -LiteralPath $apkSource -Destination $apkTarget -Force
$apk = Get-Item -LiteralPath $apkTarget
$sha256 = (Get-FileHash -LiteralPath $apkTarget -Algorithm SHA256).Hash.ToLowerInvariant()
$testCount = 0
foreach ($suiteRow in $suites) { $testCount += [int]$suiteRow['tests'] }
$sdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $sdkDirectory) { throw '請設定 ANDROID_HOME 或 ANDROID_SDK_ROOT。' }
$signatureVerifier = Join-Path $sdkDirectory 'build-tools/34.0.0/apksigner.bat'
& $signatureVerifier verify --verbose $apkTarget | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'APK 簽章驗證失敗。' }
$taipeiTime = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Taipei Standard Time').ToString('yyyy-MM-dd HH:mm:ss')
$report = [ordered]@{
    version = '0.1.0-local'; generatedAtTaipei = $taipeiTime
    apk = [ordered]@{ file = $apk.Name; bytes = $apk.Length; sha256 = $sha256; signing = 'Android debug signing; not a production release'; signatureVerified = $true }
    automated = [ordered]@{ environment = 'Windows JVM + Robolectric Android API 28; no connected phone'; totalTests = $testCount; suites = $suites; lintErrors = $lintErrors; lintWarnings = @($lint.issues.issue | Where-Object { $_.severity -eq 'Warning' }).Count; internetPermission = $false; declaredPermissions = $permissions }
    deviceVerification = [ordered]@{ model = 'vivo V40'; android = '16'; status = 'not_run'; reason = 'No Android device connected to ADB at verification time' }
    manualChecklist = 'quality/manual-acceptance.json'
    futureFeatures = @('backup_restore_export', 'authentication_collaboration_sync', 'nearby_search_offline_maps')
}
$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $projectDirectory 'quality/automated-results.json') -Encoding utf8
Write-Output ($report | ConvertTo-Json -Depth 12)
