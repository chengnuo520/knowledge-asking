package com.example.chat.rag.parser.model;

public sealed interface Block permits HeadingBlock, ParagraphBlock, ListBlock, TableBlock, CodeBlock {
}
