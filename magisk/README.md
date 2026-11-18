# Magisk 版本

构建 Zygisk 原生库（需要 Android NDK）：

```powershell
$env:ANDROID_NDK_ROOT = 'C:\Android\Sdk\ndk\27.2.12479018'
.\build-zygisk.ps1
```

也可以使用 GitHub Actions：打开仓库 Actions，运行 `Build Magisk Zygisk module`，完成后下载 `jianying-qr-magisk` artifact。

再构建 Magisk ZIP：

```powershell
cd D:\project\jianyinqQR\magisk-qr
.\build-magisk.ps1
```

输出 `app\build\outputs\magisk\jianying-qr-magisk.zip`。模块按 Magisk 规范包含 `zygisk/arm64-v8a.so`，并将 APK 安装为带桌面图标的应用。缺少原生库时打包直接失败。

打开桌面上的“剪映扫码请求 Hook”即可读取 `logcat` 中的 `JYQrHook` 和 `ZygiskJYQr` 数据。

当前产物属于注入诊断版本：原生入口仅尝试为目标主进程输出状态日志，尚未真机验证。请求字段采集仍由原有 `HookEntry` 提供，纯 Zygisk 环境下还需要接入 Java 方法 Hook 后端；CI 构建成功不表示已经完成扫码采集。
