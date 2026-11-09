package com.example.chat.rag.parser.model;

import java.util.List;

public record ListBlock(List<String> items, boolean ordered) implements Block {
    public ListBlock {
        items = List.copyOf(items);
    }
}
