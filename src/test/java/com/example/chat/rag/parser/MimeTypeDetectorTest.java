package com.example.chat.rag.parser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MimeTypeDetectorTest {

    private final MimeTypeDetector detector = new MimeTypeDetector();

    @Test
    void detectsPlainTextFromTxtExtension() {
        assertEquals("text/plain", detector.detect(
                "本政策适用于所有电子产品。".getBytes(StandardCharsets.UTF_8), "notes.txt"));
    }

    @Test
    void emptyBytesFallBackToExtension() {
        assertEquals("text/plain", detector.detect(new byte[0], "NOTES.TXT"));
    }

    @Test
    void unknownExtensionStillReturnsNonBlankMime() {
        String mimeType = detector.detect("hello world".getBytes(StandardCharsets.UTF_8), "report.zzz");

        assertNotNull(mimeType);
        assertFalse(mimeType.isBlank());
    }

    @Test
    void unknownNameWithoutBytesReturnsOctetStream() {
        assertEquals("application/octet-stream", detector.detect(new byte[0], "report.zzz"));
    }
}
