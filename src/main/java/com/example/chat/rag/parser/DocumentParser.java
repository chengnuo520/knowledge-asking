package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.ParsedDocument;

import java.util.Map;
import java.util.Set;

public interface DocumentParser {

    String parserType();

    Set<String> supportedMimeTypes();

    default boolean supports(String mimeType) {
        return supportedMimeTypes().contains(mimeType);
    }

    ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options);
}
