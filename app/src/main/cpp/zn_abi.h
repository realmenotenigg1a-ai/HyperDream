#pragma once
#include <stddef.h>
// Minimal ABI declarations, independently transcribed from the documented API v4.
// Reference: https://github.com/Dr-TSNG/ZygiskNext/blob/main/zygisk_next_api.h
// Keep every slot in order, including unused APIs. No upstream implementation included.
struct ZnSymbolResolver;
struct Runtime;
struct Api {
    int (*pltHook)(void*, const char*, void*, void**);
    int (*inlineHook)(void*, void*, void**);
    int (*inlineUnhook)(void*);
    ZnSymbolResolver* (*newSymbolResolver)(const char*, void*);
    void (*freeSymbolResolver)(ZnSymbolResolver*);
    void* (*getBaseAddress)(ZnSymbolResolver*);
    void* (*symbolLookup)(ZnSymbolResolver*, const char*, bool, size_t*);
    void (*forEachSymbols)(ZnSymbolResolver*, bool (*)(const char*, void*, size_t, void*), void*);
    int (*connectCompanion)(void*);
    const Runtime* (*getRuntime)();
};
struct AppArgs { const char* process_name; const char* package_name; const char* se_info; };
struct HyosModule { int target_api_version; void (*onAppSpecialized)(const AppArgs*); };
struct Runtime { int type; int api_version; int (*registerModule)(const void*); };
struct Module { int target_api_version; void (*onModuleLoaded)(void*, const Api*); };
