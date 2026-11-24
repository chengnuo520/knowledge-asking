package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkMetadata;
import com.example.chat.rag.ingest.store.ChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeAdminServiceTest {

    private static final String DOC_ID = "11111111-1111-1111-1111-111111111111";

    private ChunkStore store;

    private KnowledgeAdminService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryChunkStore();
        service = new KnowledgeAdminService(store);
        store.replaceDocument(
                new DocumentRecord(DOC_ID, "保修政策.md", "text/markdown", 512, "hash", 2, Instant.now()),
                List.of(
                        new Chunk("c1", 0, "退货期限为 15 天。", "退货期限为 15 天。",
                                new ChunkMetadata(List.of("保修政策", "退货"), "保修政策.md", null)),
                        new Chunk("c2", 1, "换货期限为 7 天。", "换货期限为 7 天。",
                                new ChunkMetadata(List.of("保修政策", "换货"), "保修政策.md", null))));
    }

    @Test
    void updateChunkRebuildsEmbeddingTextWithMissingOutlinePrefix() {
        // 路径自根向下补，遇到已出现在正文里的层级就停：正文含"退货"（可退货），
        // 所以只补根级"保修政策"
        Chunk updated = service.updateChunk(DOC_ID, "c1", "手机 30 天内可退货。");

        assertEquals("手机 30 天内可退货。", updated.content());
        assertEquals("保修政策\n手机 30 天内可退货。", updated.embeddingText());
    }

    @Test
    void updateChunkKeepsPathWhenContentAlreadyCoversIt() {
        // 正文自带章节名时，只补缺失的那一级
        Chunk updated = service.updateChunk(DOC_ID, "c2", "换货：签收后 10 天内可换。");

        assertEquals("保修政策\n换货：签收后 10 天内可换。", updated.embeddingText());
    }

    @Test
    void updateChunkRejectsBlankContent() {
        assertThrows(IngestionException.class, () -> service.updateChunk(DOC_ID, "c1", "   "));
    }

    @Test
    void updateChunkOnUnknownChunkThrowsNotFound() {
        assertThrows(ChunkNotFoundException.class, () -> service.updateChunk(DOC_ID, "nope", "任意内容"));
    }

    @Test
    void createChunkAppendsToTailAndSyncsCount() {
        Chunk created = service.createChunk(DOC_ID, "新增：延保服务需单独购买。");

        assertEquals(2, created.index());
        assertEquals(3, store.findById(DOC_ID).orElseThrow().chunkCount());
        assertEquals(3, store.listChunks(DOC_ID).size());
    }

    @Test
    void deleteChunksSyncsChunkCount() {
        int deleted = service.deleteChunks(DOC_ID, List.of("c1"));

        assertEquals(1, deleted);
        assertEquals(1, store.findById(DOC_ID).orElseThrow().chunkCount());
        assertTrue(store.findChunk(DOC_ID, "c1").isEmpty());
    }

    @Test
    void deleteChunksRequiresSelection() {
        assertThrows(IngestionException.class, () -> service.deleteChunks(DOC_ID, List.of()));
    }

    @Test
    void deleteDocumentsReportsActualCount() {
        store.replaceDocument(
                new DocumentRecord("22222222-2222-2222-2222-222222222222", "b.md", "text/markdown", 10, "h2", 0, Instant.now()),
                List.of());

        int deleted = service.deleteDocuments(List.of(DOC_ID, "missing-id"));

        assertEquals(1, deleted);
        assertFalse(store.findById(DOC_ID).isPresent());
    }
}
