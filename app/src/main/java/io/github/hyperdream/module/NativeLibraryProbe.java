package io.github.hyperdream.module;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only ZIP metadata checks, not a native loader, ELF parser, or compatibility certificate. */
final class NativeLibraryProbe {
    private static final String[] ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86"};
    private static final String[] LIBRARIES = {"libapp.so", "libflutter.so", "libhyper_os_flutter.so"};

    static final class Result {
        final boolean appLibrary;
        final boolean engineLibrary;
        final boolean complete;
        final List<String> evidence;

        Result(boolean appLibrary, boolean engineLibrary, boolean complete, List<String> evidence) {
            this.appLibrary = appLibrary;
            this.engineLibrary = engineLibrary;
            this.complete = complete;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        }

        boolean hasFlutterIndicators() {
            return appLibrary && engineLibrary;
        }
    }

    private NativeLibraryProbe() {}

    static Result inspect(File[] apks) {
        boolean app = false;
        boolean engine = false;
        boolean complete = apks != null && apks.length > 0 && apks.length <= 32;
        List<String> evidence = new ArrayList<>();
        if (apks == null) {
            return new Result(false, false, false, evidence);
        }
        for (int index = 0; index < Math.min(apks.length, 32); index++) {
            if (apks[index] == null) {
                complete = false;
                continue;
            }
            try (ZipFile zip = new ZipFile(apks[index])) {
                for (String abi : ABIS) {
                    for (String library : LIBRARIES) {
                        String path = "lib/" + abi + "/" + library;
                        ZipEntry entry = zip.getEntry(path);
                        if (entry == null || entry.isDirectory()) {
                            continue;
                        }
                        app |= "libapp.so".equals(library);
                        engine |= !"libapp.so".equals(library);
                        if (evidence.size() < 12) {
                            evidence.add(path + " (bytes=" + entry.getSize()
                                    + ", ZIP CRC32=" + Long.toHexString(entry.getCrc()) + ")");
                        }
                    }
                }
            } catch (IOException | SecurityException inaccessible) {
                complete = false;
            }
        }
        return new Result(app, engine, complete, evidence);
    }
}
