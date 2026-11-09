package com.example.chat.rag.chunk;

import java.util.List;

/**
 * 单个 Block 的分块上下文：当前章节路径 + 预算。
 */
public record ChunkContext(List<String> outlinePath, ChunkBudget budget) {

    public ChunkContext {
        outlinePath = outlinePath == null ? List.of() : List.copyOf(outlinePath);
    }
}
