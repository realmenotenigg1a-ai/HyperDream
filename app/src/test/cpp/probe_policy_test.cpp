#include "probe_policy.h"
#include <cassert>
#include <limits>
int main() {
    using namespace hyperdream;
    assert(launcher("com.miui.home", "com.miui.home"));
    assert(!launcher(nullptr, "com.miui.home"));
    assert(!launcher("com.miui.home", "com.miui.home:remote"));
    assert(!launcher("other", "com.miui.home"));
    assert(target_image("/data/app/launcher/lib/arm64/libapp.so"));
    assert(target_image("libhyper_os_flutter.so"));
    assert(!target_image("libapp.so.fake")); assert(!target_image(nullptr));
    char out[129] = {};
    const unsigned char valid[] = {4,0,0,0,4,0,0,0,3,0,0,0,'G','N','U',0,0xde,0xad,0xbe,0xef};
    assert(build_id(valid, sizeof(valid), out, sizeof(out)));
    assert(!std::strcmp(out, "deadbeef"));
    for (size_t n = 0; n < sizeof(valid); ++n) assert(!build_id(valid, n, out, sizeof(out)));
    assert(!build_id(valid, sizeof(valid), out, 8));
    unsigned char invalid[20]; std::memcpy(invalid, valid, 20);
    std::memset(invalid, 255, 4); assert(!build_id(invalid, 20, out, sizeof(out)));
    assert(!build_id(nullptr, 20, out, sizeof(out)));
}
