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

安装模块后，在 LSPosed 中启用“剪映扫码请求 Hook”并将 `com.lemon.lv` 加入作用域；再打开桌面上的“剪映扫码请求 Hook”即可读取 `logcat` 中的 `JYQrHook` 和 `ZygiskJYQr` 数据。界面中的 `hook-ready` 表示 Java Hook 已加载，`java_backend=unavailable` 表示当前只有 Zygisk 注入而没有 Java Hook 后端。

Zygisk 原生入口会为目标主进程检查 Java Hook 后端并输出状态；请求字段采集由现有 `HookEntry` 完成。CI 构建成功不表示已经完成扫码采集，仍需在目标设备验证 LSPosed 作用域、Root logcat 读取和扫码请求。
