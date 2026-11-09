package com.example.chat.rag.parser.model;

import java.util.List;
import java.util.Map;

public record ParsedDocument(List<Block> blocks, Map<String, Object> metadata) {
    public ParsedDocument {
        blocks = List.copyOf(blocks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
