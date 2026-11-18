# 剪映扫码记录 Magisk/Zygisk 项目

这是独立 Git 仓库，与 `../xposed-qr` 为兄弟目录，包含桌面日志查看器、Zygisk 原生入口、模块打包脚本和 GitHub Actions。

## 当前状态

**当前为注入诊断版本，请求采集链路尚未完成。** 原生入口只尝试为 `com.lemon.lv` 主进程输出状态日志，尚未接入 Java 方法 Hook 后端。旧 `HookEntry` 依赖传统 Xposed API，作为待迁移代码保留。CI 成功表示构建和静态检查通过，仍需真机验证。

## GitHub Actions

将本目录作为 GitHub 仓库根目录上传。工作流位于 `.github/workflows/build-magisk.yml`，支持手动运行和推送触发。

CI 安装 JDK 17、Android SDK 35、NDK 27.2.12479018，执行 Java 单元测试，构建 APK 和 ARM64 原生库，随后检查 ZIP CRC、ELF 文件头和 APK 存在性。缺少原生库时打包直接失败。

完成后下载 `jianying-qr-magisk` artifact 并解压外层归档，其中包含模块 ZIP 和桌面 APK。

## 本地构建

```powershell
$env:ANDROID_NDK_ROOT = 'C:\Android\Sdk\ndk\27.2.12479018'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\build-zygisk.ps1
.\build-magisk.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk
```

模块输出：`app/build/outputs/magisk/jianying-qr-magisk.zip`。Zygisk 库位于 ZIP 根目录下的 `zygisk/arm64-v8a.so`。

## 源码

- `app/`：桌面查看器与待迁移的 Java Hook。
- `native/`：Zygisk 原生入口、官方 API 头文件及 CMake 配置。
- `magisk/`：模块元数据和安装脚本。
- `tools/`：日志解码器及测试。
