package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.MarkdownDocumentParser;
import com.example.chat.rag.parser.model.Block;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingServiceTest {

    @Test
    void chunksMarkdownFixtureWithHeadingPathAndTableKeyValue() throws IOException {
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        List<Block> blocks = parser.parse(read("docs/保修政策.md"), "text/markdown", Map.of()).blocks();
        // 夹具很短，用紧凑预算让章节边界与表格切分都能观察到
        ChunkBudget budget = new ChunkBudget(32, 4, 50, 1);

        List<Chunk> chunks = new ChunkingService(budget).chunk(blocks, budget, "保修政策.md");

        assertFalse(chunks.isEmpty());

        Chunk returns = chunks.stream()
                .filter(chunk -> chunk.metadata().outlinePath().contains("退换货"))
                .filter(chunk -> chunk.content().contains("退换货"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少退换货章节的块"));
        assertTrue(returns.embeddingText().startsWith("保修政策 / 手机"),
                "实际向量文本: " + returns.embeddingText());
        assertFalse(returns.content().contains("保修政策 / 手机"));

        Chunk table = chunks.stream()
                .filter(chunk -> chunk.content().contains("商品 | 期限 | 说明"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少带表头的表格块"));
        assertTrue(table.content().contains("商品 | 期限 | 说明"));
        assertTrue(table.embeddingText().contains("商品: 手机"),
                "实际向量文本: " + table.embeddingText());
        assertTrue(table.embeddingText().contains("期限: 15 天"));
    }

    private static byte[] read(String path) throws IOException {
        InputStream in = ChunkingServiceTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试夹具不存在: " + path);
        }
        try (in) {
            return in.readAllBytes();
        }
    }
}
