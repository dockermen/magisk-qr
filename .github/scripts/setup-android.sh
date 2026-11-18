#!/usr/bin/env bash
set -euo pipefail

sdk_root="$RUNNER_TEMP/android-sdk"
tools_zip="$RUNNER_TEMP/android-commandlinetools.zip"
tools_unpack="$RUNNER_TEMP/android-commandlinetools"
mkdir -p "$sdk_root/cmdline-tools" "$tools_unpack"

# Published checksum from Google's repository2-1.xml, cmdline-tools;19.0.
curl --fail --location --retry 3 \
  https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
  --output "$tools_zip"
printf '5fdcc763663eefb86a5b8879697aa6088b041e70  %s\n' "$tools_zip" | sha1sum --check
unzip -q "$tools_zip" -d "$tools_unpack"
mv "$tools_unpack/cmdline-tools" "$sdk_root/cmdline-tools/latest"

export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
"$sdkmanager" --sdk_root="$sdk_root" --licenses <<< "$(printf 'y\n%.0s' {1..100})"
"$sdkmanager" --sdk_root="$sdk_root" --install \
  'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018'

printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$sdk_root" "$sdk_root" >> "$GITHUB_ENV"
printf '%s/cmdline-tools/latest/bin\n%s/build-tools/35.0.0\n' "$sdk_root" "$sdk_root" >> "$GITHUB_PATH"

if ! command -v cmake >/dev/null || ! command -v ninja >/dev/null; then
  sudo apt-get update
  sudo apt-get install --yes cmake ninja-build
fi
