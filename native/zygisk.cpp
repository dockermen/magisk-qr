#include <sys/types.h>
#include <cstdint>
#include "zygisk.hpp"
#include <android/log.h>
#include <cstring>
#include <string>

namespace {
zygisk::Api* g_api = nullptr;
class Module final : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api* api, JNIEnv* env) override { g_api = api; g_env = env; }
    void preAppSpecialize(zygisk::AppSpecializeArgs* args) override {
        if (!isTarget(args)) return;
            __android_log_print(ANDROID_LOG_INFO, "ZygiskJYQr", "injecting com.lemon.lv");
        }
    void postAppSpecialize(const zygisk::AppSpecializeArgs* args) override {
        if (!isTarget(args)) return;
        const bool xposedBridge = hasClass("de/robv/android/xposed/XposedBridge");
        __android_log_print(ANDROID_LOG_INFO, "ZygiskJYQr",
                "attached com.lemon.lv java_backend=%s capture=%s",
                xposedBridge ? "xposed-bridge" : "unavailable",
                xposedBridge ? "HookEntry" : "disabled");
    }
private:
    bool isTarget(const zygisk::AppSpecializeArgs* args) const {
        if (!g_env || !args || !args->nice_name) return false;
        const char* name = g_env->GetStringUTFChars(args->nice_name, nullptr);
        bool target = name && std::strcmp(name, "com.lemon.lv") == 0;
        if (name) g_env->ReleaseStringUTFChars(args->nice_name, name);
        return target;
    }

    bool hasClass(const char* name) const {
        if (!g_env || !name) return false;
        jclass type = g_env->FindClass(name);
        if (g_env->ExceptionCheck()) g_env->ExceptionClear();
        if (type == nullptr) return false;
        g_env->DeleteLocalRef(type);
        return true;
    }
    JNIEnv* g_env = nullptr;
};
}
REGISTER_ZYGISK_MODULE(Module)
