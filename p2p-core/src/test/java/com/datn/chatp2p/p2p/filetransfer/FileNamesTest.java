package com.datn.chatp2p.p2p.filetransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileNamesTest {

    @TempDir
    Path dir;

    @Test
    void stripsPathTraversal() {
        assertEquals("evil.exe", FileNames.sanitize("../../evil.exe"));
        assertEquals("evil.exe", FileNames.sanitize("..\\..\\evil.exe"));
        assertEquals("x.dll", FileNames.sanitize("C:\\Windows\\system32\\x.dll"));
        assertEquals("passwd", FileNames.sanitize("/etc/passwd"));
    }

    @Test
    void replacesForbiddenAndControlCharacters() {
        assertEquals("a_b_c_.txt", FileNames.sanitize("a<b>c?.txt"));
        assertEquals("a_b_c_d_e.txt", FileNames.sanitize("a:b\"c|d*e.txt"));
        assertEquals("tab_new_line.txt", FileNames.sanitize("tab\tnew\nline.txt"));
    }

    @Test
    void prefixesWindowsDeviceNames() {
        assertEquals("_CON.txt", FileNames.sanitize("CON.txt"));
        assertEquals("_nul", FileNames.sanitize("nul"));
        assertEquals("_com1.tar.gz", FileNames.sanitize("com1.tar.gz"));
        assertEquals("console.txt", FileNames.sanitize("console.txt")); // chi trung dung ten moi doi
    }

    @Test
    void emptyOrDotOnlyNamesFallBackToDefault() {
        assertEquals("file", FileNames.sanitize(null));
        assertEquals("file", FileNames.sanitize(""));
        assertEquals("file", FileNames.sanitize("."));
        assertEquals("file", FileNames.sanitize(".."));
        assertEquals("file", FileNames.sanitize("dir/"));
        assertEquals("file", FileNames.sanitize(" . . "));
    }

    @Test
    void stripsTrailingDotsAndSpaces() {
        assertEquals("report.pdf", FileNames.sanitize("report.pdf. . "));
    }

    @Test
    void keepsVietnameseAndNormalNames() {
        assertEquals("báo cáo.pdf", FileNames.sanitize("báo cáo.pdf"));
        assertEquals(".gitignore", FileNames.sanitize(".gitignore"));
        assertEquals("anh (1).png", FileNames.sanitize("anh (1).png"));
    }

    @Test
    void truncatesLongNamesButKeepsExtension() {
        String sanitized = FileNames.sanitize("a".repeat(300) + ".pdf");

        assertEquals(FileNames.MAX_NAME_LENGTH, sanitized.length());
        assertTrue(sanitized.endsWith(".pdf"));
    }

    @Test
    void uniqueTargetReturnsNameAsIsWhenFree() {
        assertEquals(dir.resolve("a.txt"), FileNames.uniqueTarget(dir, "a.txt"));
    }

    @Test
    void uniqueTargetNeverOverwritesExistingFiles() throws Exception {
        Files.createFile(dir.resolve("a.txt"));
        Files.createFile(dir.resolve("a (1).txt"));

        assertEquals(dir.resolve("a (2).txt"), FileNames.uniqueTarget(dir, "a.txt"));
    }

    @Test
    void uniqueTargetWorksWithoutExtension() throws Exception {
        Files.createFile(dir.resolve("README"));

        assertEquals(dir.resolve("README (1)"), FileNames.uniqueTarget(dir, "README"));
    }
}
