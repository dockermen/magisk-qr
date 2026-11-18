param(
    [string]$ApkPath = (Join-Path $PSScriptRoot 'app\build\manual\jianying-qr-hook-debug.apk'),
    [string]$OutputPath = (Join-Path $PSScriptRoot 'app\build\outputs\magisk\jianying-qr-magisk.zip')
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$native = Join-Path $root 'native/build-arm64-v8a/libjianyingqr.so'
if (-not (Test-Path -LiteralPath $native)) {
    throw 'Missing Zygisk library. Build with build-zygisk.ps1 or GitHub Actions first. No module was produced.'
}
if (-not (Test-Path -LiteralPath $ApkPath)) {
    & (Join-Path $root 'gradlew.bat') :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }
    $ApkPath = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
}
if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }

$outDir = Split-Path -Parent $OutputPath
$stage = Join-Path $outDir 'stage'
Remove-Item -LiteralPath $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $stage 'system\app\JianyingQrViewer') | Out-Null
Copy-Item (Join-Path $root 'magisk\module.prop') $stage
Copy-Item (Join-Path $root 'magisk\post-fs-data.sh') $stage
Copy-Item (Join-Path $root 'magisk\service.sh') $stage
Copy-Item (Join-Path $root 'magisk\uninstall.sh') $stage
Copy-Item (Join-Path $root 'magisk\META-INF') (Join-Path $stage 'META-INF') -Recurse
Copy-Item $ApkPath (Join-Path $stage 'system\app\JianyingQrViewer\JianyingQrViewer.apk')
New-Item -ItemType Directory -Force (Join-Path $stage 'zygisk') | Out-Null
Copy-Item $native (Join-Path $stage 'zygisk/arm64-v8a.so')
New-Item -ItemType Directory -Force $outDir | Out-Null
Remove-Item -LiteralPath $OutputPath -Force -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression
$stream = [System.IO.File]::Create($OutputPath)
$archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create, $false)
try {
    $stagePath = (Resolve-Path -LiteralPath $stage).Path
    Get-ChildItem -LiteralPath $stagePath -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($stagePath.Length + 1).Replace('\', '/')
        $entry = $archive.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
        $input = [System.IO.File]::OpenRead($_.FullName)
        $output = $entry.Open()
        try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
    }
} finally {
    $archive.Dispose()
    $stream.Dispose()
}
Write-Output "Magisk module: $OutputPath"
