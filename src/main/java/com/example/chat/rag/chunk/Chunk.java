package com.example.chat.rag.chunk;

/**
 * 分块产物：content 为文档原貌，embeddingText 为补全章节上下文后的向量文本。
 */
public record Chunk(String chunkId, int index, String content, String embeddingText, ChunkMetadata metadata) {
}
