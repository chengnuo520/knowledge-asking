package com.example.chat.rag.parser.model;

import java.util.List;

public record TableBlock(List<String> headers, List<List<String>> rows) implements Block {
    public TableBlock {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
