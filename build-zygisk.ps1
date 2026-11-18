param(
    [string]$NdkRoot = $env:ANDROID_NDK_ROOT,
    [string]$Abi = 'arm64-v8a'
)

$ErrorActionPreference = 'Stop'
if (-not $NdkRoot) { throw 'Set ANDROID_NDK_ROOT to an installed Android NDK.' }
$cmake = Join-Path $NdkRoot 'build\cmake\android.toolchain.cmake'
if (-not (Test-Path $cmake)) { throw "NDK not found: $NdkRoot" }
$root = $PSScriptRoot
$build = Join-Path $root "native\build-$Abi"
cmake -S (Join-Path $root 'native') -B $build -G Ninja `
    -DCMAKE_TOOLCHAIN_FILE=$cmake -DANDROID_ABI=$Abi -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
if ($LASTEXITCODE -ne 0) { throw 'CMake configure failed.' }
cmake --build $build --config Release
if ($LASTEXITCODE -ne 0) { throw 'Zygisk build failed.' }
Write-Output (Join-Path $build 'libjianyingqr.so')
