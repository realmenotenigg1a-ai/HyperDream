#pragma once
#include <cstring>
#include <cstdint>
#include <cstddef>
namespace hyperdream {
inline bool launcher(const char* package, const char* process) {
    return package && process && !std::strcmp(package, "com.miui.home")
        && !std::strcmp(process, "com.miui.home");
}
inline const char* target_image(const char* path) {
    if (!path) return nullptr;
    const char* slash = std::strrchr(path, '/');
    const char* base = slash ? slash + 1 : path;
    return (!std::strcmp(base, "libapp.so") || !std::strcmp(base, "libhyper_os_flutter.so"))
        ? base : nullptr;
}
// Bounded ELF note parser: malformed notes are rejected, never followed out of range.
inline bool build_id(const unsigned char* notes, size_t length, char* out, size_t capacity) {
    if (!notes || !out || capacity < 3 || length > 65536) return false;
    size_t offset = 0;
    while (offset <= length && length - offset >= 12) {
        uint32_t header[3]; std::memcpy(header, notes + offset, 12); offset += 12;
        const size_t name = header[0], desc = header[1];
        if (name > length - offset || name > 65532 || desc > 65532) return false;
        const size_t aligned_name = (name + 3) & ~size_t(3);
        const size_t aligned_desc = (desc + 3) & ~size_t(3);
        if (aligned_name > length - offset) return false;
        const auto* value = notes + offset + aligned_name;
        if (aligned_desc > length - offset - aligned_name) return false;
        if (header[2] == 3 && name == 4 && !std::memcmp(notes + offset, "GNU\0", 4)
                && desc > 0 && desc <= 64 && desc * 2 + 1 <= capacity) {
            constexpr char hex[] = "0123456789abcdef";
            for (size_t i = 0; i < desc; ++i) {
                out[i*2] = hex[value[i] >> 4]; out[i*2+1] = hex[value[i] & 15];
            }
            out[desc*2] = 0; return true;
        }
        offset += aligned_name + aligned_desc;
    }
    return false;
}
}
