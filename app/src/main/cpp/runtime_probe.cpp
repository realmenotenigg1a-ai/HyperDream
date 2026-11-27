#include "zn_abi.h"
#include "probe_policy.h"
#include <android/log.h>
#include <android/dlext.h>
#include <dlfcn.h>
#include <link.h>
#include <unistd.h>
#include <cstdio>
#include <cerrno>
#include <atomic>
#ifndef HD_BUILD
#define HD_BUILD "local"
#endif
namespace {
const Api* api = nullptr;
using Loader = void* (*)(const char*, int, const android_dlextinfo*);
Loader original_loader = nullptr;
std::atomic<unsigned> seen{0};
std::atomic<bool> entered{false};
// Every line identifies the payload revision and PID. No addresses or full filesystem paths.
#define REPORT(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "HyperDreamNative", \
    "build=" HD_BUILD " pid=%d " fmt, getpid(), ##__VA_ARGS__)
int image(dl_phdr_info* info, size_t, void*) {
    const char* name = hyperdream::target_image(info->dlpi_name);
    if (!name) return 0;
    const unsigned bit = !std::strcmp(name, "libapp.so") ? 1u : 2u;
    if (seen.fetch_or(bit) & bit) return 0;
    char id[129] = "unavailable";
    // Only read a PT_NOTE wholly inside a readable PT_LOAD. No raw address scanning.
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i) {
        const auto& note = info->dlpi_phdr[i];
        if (note.p_type != PT_NOTE || note.p_memsz > 65536) continue;
        for (ElfW(Half) j = 0; j < info->dlpi_phnum; ++j) {
            const auto& load = info->dlpi_phdr[j];
            if (load.p_type != PT_LOAD || !(load.p_flags & PF_R) || note.p_vaddr < load.p_vaddr) continue;
            const auto delta = note.p_vaddr - load.p_vaddr;
            if (delta <= load.p_memsz && note.p_memsz <= load.p_memsz - delta) {
                if (hyperdream::build_id(reinterpret_cast<const unsigned char*>(info->dlpi_addr + note.p_vaddr),
                        note.p_memsz, id, sizeof(id))) break;
            }
        }
    }
    REPORT("stage=IMAGE_SEEN image=%s elf_build_id=%s animation_applied=0", name, id);
    return 0;
}
void* observe_load(const char* name, int flags, const android_dlextinfo* ext) {
    // Original is always called exactly once; flags, extension and return value are unchanged.
    void* handle = original_loader(name, flags, ext);
    const int saved_errno = errno;
    if (handle && hyperdream::target_image(name)) dl_iterate_phdr(image, nullptr);
    errno = saved_errno;
    return handle;
}
void specialized(const AppArgs* args) {
    if (!args || !hyperdream::launcher(args->package_name, args->process_name)) return;
    if (entered.exchange(true)) return;
    REPORT("stage=LAUNCHER_SPECIALIZED uid=%d animation_applied=0", getuid());
    dl_iterate_phdr(image, nullptr);
    // Hook only this child, never the shared spawner. This observes library loads, not animation calls.
    void* target = dlsym(RTLD_DEFAULT, "android_dlopen_ext");
    if (!target || !api || !api->inlineHook) {
        REPORT("stage=OBSERVER_UNAVAILABLE animation_applied=0"); return;
    }
    const int result = api->inlineHook(target, reinterpret_cast<void*>(observe_load),
                                      reinterpret_cast<void**>(&original_loader));
    REPORT("stage=%s result=%d animation_applied=0 reason=NO_VALIDATED_DURATION_PROFILE",
           result == 0 && original_loader ? "OBSERVER_INSTALLED" : "OBSERVER_FAILED", result);
}
const HyosModule module{1, specialized};
void loaded(void*, const Api* supplied) {
    api = supplied;
    if (!api || !api->getRuntime) {
        REPORT("stage=RUNTIME_UNAVAILABLE animation_applied=0"); return;
    }
    const Runtime* runtime = api->getRuntime();
    if (!runtime || runtime->type != 1 || runtime->api_version < 1 || !runtime->registerModule) {
        REPORT("stage=RUNTIME_UNAVAILABLE animation_applied=0"); return;
    }
    const int result = runtime->registerModule(&module);
    REPORT("stage=RUNTIME_REGISTRATION result=%d hyos_api=%d zn_api=4 animation_applied=0",
           result, runtime->api_version);
}
}
extern "C" {
__attribute__((visibility("default"))) Module zn_module{4, loaded};
}
