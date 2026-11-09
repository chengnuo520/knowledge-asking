package com.example.chat.rag.parser.model;

public record CodeBlock(String language, String text) implements Block {
}
