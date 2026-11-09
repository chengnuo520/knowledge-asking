package com.example.chat.rag.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParserRegistry {

    private static final Logger log = LoggerFactory.getLogger(ParserRegistry.class);

    private final Map<String, DocumentParser> routes;

    public ParserRegistry(List<DocumentParser> parsers) {
        Map<String, DocumentParser> table = new HashMap<>();
        for (DocumentParser parser : parsers) {
            for (String mimeType : parser.supportedMimeTypes()) {
                DocumentParser existing = table.putIfAbsent(mimeType, parser);
                if (existing != null) {
                    throw new IllegalStateException("MIME 类型冲突: " + mimeType
                            + " 同时被解析器 " + existing.parserType()
                            + " 与 " + parser.parserType() + " 认领");
                }
            }
        }
        this.routes = Map.copyOf(table);
        routes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> log.info("RAG parser routes: {} -> {}",
                        entry.getKey(), entry.getValue().parserType()));
    }

    public DocumentParser require(String mimeType) {
        DocumentParser parser = routes.get(mimeType);
        if (parser == null) {
            throw new UnsupportedDocumentTypeException("不支持的 MIME 类型: " + mimeType);
        }
        return parser;
    }
}
