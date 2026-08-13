package org.example.utils;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePathResolverTest {
    @Test
    void convertsRelativePathToCanonicalAbsolutePath() throws Exception {
        File resolvedFile = FilePathResolver.toAbsoluteFile("etc/../etc/config.properties");

        assertTrue(resolvedFile.isAbsolute());
        assertEquals(new File("etc/config.properties").getCanonicalPath(), resolvedFile.getPath());
    }

    @Test
    void rejectsBlankPathBeforeAnyFileOperation() {
        assertThrows(IllegalArgumentException.class, () -> FilePathResolver.toAbsoluteFile("  "));
    }
}
