param([switch]$Check)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    # Windows certificate validation stays enabled; use the OS trust anchors on this workstation.
    $taskNames = @(':app:assembleDebug')
    if ($Check) { $taskNames += @(':domain:test', ':app:testDebugUnitTest', ':app:lintDebug') }
    & (Join-Path $projectDirectory 'gradlew.bat') @taskNames --console=plain --no-daemon '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE'
    if ($LASTEXITCODE -ne 0) { throw "建置或檢查失敗（$LASTEXITCODE），請查看上方輸出。" }
    Write-Output '安裝檔：app/build/outputs/apk/debug/app-debug.apk'
} finally { Pop-Location }
