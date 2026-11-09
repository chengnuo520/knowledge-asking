package com.example.chat.rag.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkAssemblerTest {

    @Test
    void prefixOnlyFillsMissingOutlineLevels() {
        List<ChunkDraft> drafts = List.of(
                new ChunkDraft("退换货", null, List.of("保修政策", "手机", "退换货"), false, false));

        List<Chunk> chunks = ChunkAssembler.assembleAll(drafts, "保修政策.md");

        assertEquals(1, chunks.size());
        assertEquals("退换货", chunks.get(0).content());
        assertEquals("保修政策 / 手机\n退换货", chunks.get(0).embeddingText());
        assertFalse(chunks.get(0).content().contains("保修政策 / 手机"));
    }

    @Test
    void noPrefixWhenContentAlreadyContainsFirstLevel() {
        List<ChunkDraft> drafts = List.of(
                new ChunkDraft("保修政策\n\n本政策适用于所有电子产品。", null,
                        List.of("保修政策", "手机"), false, false));

        Chunk chunk = ChunkAssembler.assembleAll(drafts, "保修政策.md").get(0);

        assertEquals(chunk.content(), chunk.embeddingText());
    }

    @Test
    void tableKeyValueBodyIsUsedForEmbedding() {
        List<ChunkDraft> drafts = List.of(
                new ChunkDraft("商品 | 期限\n手机 | 15 天", "商品: 手机\n期限: 15 天", List.of(), false, false));

        Chunk chunk = ChunkAssembler.assembleAll(drafts, "表格.csv").get(0);

        assertEquals("商品 | 期限\n手机 | 15 天", chunk.content());
        assertEquals("商品: 手机\n期限: 15 天", chunk.embeddingText());
    }

    @Test
    void assignsIncreasingIndexAndMetadata() {
        List<ChunkDraft> drafts = List.of(
                new ChunkDraft("第一段", null, List.of("章节"), false, false),
                new ChunkDraft("第二段", null, List.of("章节"), false, false));

        List<Chunk> chunks = ChunkAssembler.assembleAll(drafts, "来源.md");

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        assertTrue(chunks.get(0).chunkId() != null && !chunks.get(0).chunkId().isBlank());
        assertFalse(chunks.get(0).chunkId().equals(chunks.get(1).chunkId()));
        assertEquals("来源.md", chunks.get(0).metadata().sourceFile());
        assertEquals(List.of("章节"), chunks.get(0).metadata().outlinePath());
    }
}
