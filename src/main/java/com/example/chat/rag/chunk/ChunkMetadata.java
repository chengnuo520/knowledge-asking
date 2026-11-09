package com.example.chat.rag.chunk;

import java.util.List;
import java.util.Map;

/**
 * 块元数据：章节路径、来源文件与扩展位。
 */
public record ChunkMetadata(List<String> outlinePath, String sourceFile, Map<String, Object> extras) {

    public ChunkMetadata {
        outlinePath = outlinePath == null ? List.of() : List.copyOf(outlinePath);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public static ChunkMetadata empty() {
        return new ChunkMetadata(List.of(), null, Map.of());
    }
}
