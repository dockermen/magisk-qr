param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [Parameter(Mandatory = $true)][string]$XposedApiJar
)

$ErrorActionPreference = 'Stop'
if (-not $SdkRoot) { throw 'Specify -SdkRoot or set ANDROID_HOME.' }
$jyRoot = $PSScriptRoot
$jySdk = (Resolve-Path -LiteralPath $SdkRoot).Path
$jyApi = (Resolve-Path -LiteralPath $XposedApiJar).Path
$jyTools = Join-Path $jySdk 'build-tools\35.0.0'
$jyAndroid = Join-Path $jySdk 'platforms\android-35\android.jar'
$jyOut = Join-Path $jyRoot 'app\build\manual'
# A new work directory avoids stale classes in repeated builds.
$jyWork = Join-Path $jyOut ([Guid]::NewGuid().ToString('N'))
$jyClasses = Join-Path $jyWork 'classes'
$jyDex = Join-Path $jyWork 'dex'
$jyRes = Join-Path $jyWork 'res.zip'
$jyUnsigned = Join-Path $jyWork 'unsigned.apk'
$jyManifest = Join-Path $jyWork 'AndroidManifest.xml'
$jyAligned = Join-Path $jyWork 'aligned.apk'
$jyApk = Join-Path $jyOut 'jianying-qr-hook-debug.apk'
$jyKey = Join-Path $jyOut 'debug.keystore'
New-Item -ItemType Directory -Force $jyClasses,$jyDex | Out-Null
[xml]$jyManifestXml = Get-Content -LiteralPath (Join-Path $jyRoot 'app\src\main\AndroidManifest.xml') -Encoding UTF8 -Raw
$jyManifestXml.DocumentElement.SetAttribute('package', 'dev.yg.jianyingqrhook')
$jyManifestXml.Save($jyManifest)

function Invoke-JyTool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool exited with code $LASTEXITCODE" }
}

$jySources = @(Get-ChildItem -LiteralPath (Join-Path $jyRoot 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-JyTool 'javac' (@('--release','8','-encoding','UTF-8','-cp',"$jyAndroid;$jyApi",'-d',$jyClasses) + $jySources)
$jyJar = Join-Path $jyWork 'module.jar'
Invoke-JyTool 'jar' @('cf',$jyJar,'-C',$jyClasses,'.')
Invoke-JyTool (Join-Path $jyTools 'd8.bat') @('--min-api','26','--lib',$jyAndroid,'--classpath',$jyApi,'--output',$jyDex,$jyJar)
Invoke-JyTool (Join-Path $jyTools 'aapt2.exe') @('compile','--dir',(Join-Path $jyRoot 'app\src\main\res'),'-o',$jyRes)
Invoke-JyTool (Join-Path $jyTools 'aapt2.exe') @('link','-o',$jyUnsigned,'-I',$jyAndroid,'--manifest',$jyManifest,'--min-sdk-version','26','--target-sdk-version','28','--version-code','1','--version-name','1.0.0','-A',(Join-Path $jyRoot 'app\src\main\assets'),$jyRes)
Invoke-JyTool 'jar' @('uf',$jyUnsigned,'-C',$jyDex,'classes.dex')
Invoke-JyTool (Join-Path $jyTools 'zipalign.exe') @('-f','4',$jyUnsigned,$jyAligned)
if (-not (Test-Path -LiteralPath $jyKey)) {
    Invoke-JyTool 'keytool' @('-genkeypair','-keystore',$jyKey,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Invoke-JyTool (Join-Path $jyTools 'apksigner.bat') @('sign','--ks',$jyKey,'--ks-pass','pass:android','--key-pass','pass:android','--out',$jyApk,$jyAligned)
Invoke-JyTool (Join-Path $jyTools 'apksigner.bat') @('verify','--verbose',$jyApk)
Write-Output "APK: $jyApk"
