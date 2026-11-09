package com.example.chat.rag.parser;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class MimeTypeDetector {

    private static final Tika TIKA = new Tika();

    private static final Map<String, String> EXTENSION_MIME_TYPES = Map.of(
            "md", "text/markdown",
            "txt", "text/plain",
            "csv", "text/csv",
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "rtf", "application/rtf"
    );

    public String detect(byte[] bytes, String filename) {
        String detected = "";
        if (bytes != null && bytes.length > 0) {
            detected = filename == null || filename.isBlank()
                    ? TIKA.detect(bytes)
                    : TIKA.detect(bytes, filename);
        }

        String byExtension = EXTENSION_MIME_TYPES.get(extensionOf(filename));
        if (byExtension != null && (detected == null || detected.isBlank()
                || "application/octet-stream".equals(detected))) {
            detected = byExtension;
        } else if ("text/plain".equals(detected)
                && ("text/markdown".equals(byExtension) || "text/csv".equals(byExtension))) {
            detected = byExtension;
        }

        return detected == null || detected.isBlank() ? "application/octet-stream" : detected;
    }

    static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
