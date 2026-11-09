package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserRegistryTest {

    @Test
    void routesByExactMimeType() {
        ParserRegistry registry = new ParserRegistry(List.of(
                parser("markdown", "text/markdown"),
                parser("plaintext", "text/plain")));

        assertEquals("markdown", registry.require("text/markdown").parserType());
        assertEquals("plaintext", registry.require("text/plain").parserType());
    }

    @Test
    void rejectsUnknownMimeType() {
        ParserRegistry registry = new ParserRegistry(List.of(parser("markdown", "text/markdown")));

        UnsupportedDocumentTypeException exception = assertThrows(
                UnsupportedDocumentTypeException.class,
                () -> registry.require("application/x-unknown"));

        assertTrue(exception.getMessage().contains("application/x-unknown"));
    }

    @Test
    void failsOnDuplicateMimeClaim() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new ParserRegistry(List.of(
                parser("first", "text/conflict"),
                parser("second", "text/conflict"))));

        assertTrue(exception.getMessage().contains("text/conflict"));
        assertTrue(exception.getMessage().contains("first"));
        assertTrue(exception.getMessage().contains("second"));
    }

    @Test
    void supportsExactMimeTypeOnly() {
        DocumentParser parser = parser("markdown", "text/markdown");

        assertTrue(parser.supports("text/markdown"));
        assertFalse(parser.supports("text/*"));
    }

    private static DocumentParser parser(String parserType, String... mimeTypes) {
        return new DocumentParser() {
            @Override
            public String parserType() {
                return parserType;
            }

            @Override
            public Set<String> supportedMimeTypes() {
                return Set.of(mimeTypes);
            }

            @Override
            public ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options) {
                return new ParsedDocument(List.of(), Map.of());
            }
        };
    }
}
