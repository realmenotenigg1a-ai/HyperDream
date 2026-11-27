package io.github.hyperdream.module;

import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Synthetic ZIPs, not a launcher binary or evidence of native compatibility. */
public final class NativeLibraryProbeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private File apk(String... entries) throws Exception {
        File file = temporary.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void findsNativePairAcrossBaseAndSplitsWithoutExtractingContent() throws Exception {
        NativeLibraryProbe.Result result = NativeLibraryProbe.inspect(new File[]{
                apk("classes.dex", "lib/arm64-v8a/libapp.so"),
                apk("lib/arm64-v8a/libhyper_os_flutter.so")});
        assertTrue(result.complete);
        assertTrue(result.hasFlutterIndicators());
        assertEquals(2, result.evidence.size());
        assertTrue(result.evidence.get(0).contains("ZIP CRC32="));
    }

    @Test
    public void aloneOrMisplacedLibraryIsNotACompletePair() throws Exception {
        NativeLibraryProbe.Result result = NativeLibraryProbe.inspect(new File[]{
                apk("lib/arm64-v8a/libapp.so", "assets/libflutter.so")});
        assertTrue(result.appLibrary);
        assertFalse(result.engineLibrary);
        assertFalse(result.hasFlutterIndicators());
    }

    @Test
    public void inaccessiblePathsMakeAbsenceInconclusive() throws Exception {
        NativeLibraryProbe.Result result = NativeLibraryProbe.inspect(new File[]{
                apk("classes.dex"), new File(temporary.getRoot(), "missing.apk")});
        assertFalse(result.complete);
        assertFalse(result.hasFlutterIndicators());
        assertFalse(NativeLibraryProbe.inspect(null).complete);
        assertFalse(NativeLibraryProbe.inspect(new File[0]).complete);
    }

    @Test
    public void excessiveSplitInputIsBoundedAndReportedIncomplete() {
        NativeLibraryProbe.Result result = NativeLibraryProbe.inspect(new File[33]);
        assertFalse(result.complete);
        assertTrue(result.evidence.size() <= 12);
    }
}
