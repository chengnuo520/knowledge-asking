package com.example.chat.rag.ingest.store;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkMetadata;
import com.example.chat.rag.ingest.DocumentRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只在 RAG_DB_TEST=true 且 PostgreSQL 可用时执行；未设置时整类跳过，不影响离线构建。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAG_DB_TEST", matches = "true")
class JdbcChunkStoreTest {

    private static final String DOC_NAME = "jdbc-test-doc.md";

    private static final String OTHER_DOC_NAME = "jdbc-test-doc-other.md";

    @Autowired
    private JdbcChunkStore chunkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String docId;

    @BeforeEach
    void setUp() {
        removeTestDocuments();
        docId = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        removeTestDocuments();
    }

    @Test
    void replaceDocumentPersistsDocumentAndChunks() {
        List<Chunk> chunks = List.of(
                chunk(0, "第一块内容", List.of("保修政策", "手机")),
                chunk(1, "第二块内容", List.of("保修政策", "维修")));

        chunkStore.replaceDocument(record(docId, DOC_NAME, "hash-1", 2), chunks);

        DocumentRecord document = chunkStore.findById(docId).orElseThrow();
        assertEquals(DOC_NAME, document.name());
        assertEquals("text/markdown", document.mimeType());
        assertEquals(2, document.chunkCount());
        assertTrue(chunkStore.findByName(DOC_NAME).isPresent());
        assertTrue(chunkStore.listDocuments().stream().anyMatch(item -> item.docId().equals(docId)));

        List<Chunk> loaded = chunkStore.listChunks(docId);
        assertEquals(2, loaded.size());
        assertEquals(0, loaded.get(0).index());
        assertEquals("第一块内容", loaded.get(0).content());
        assertEquals(List.of("保修政策", "手机"), loaded.get(0).metadata().outlinePath());
        assertEquals(1, loaded.get(1).index());
    }

    @Test
    void replaceDocumentOverwritesChunksForSameDocId() {
        chunkStore.replaceDocument(record(docId, DOC_NAME, "hash-1", 2),
                List.of(chunk(0, "旧块一", List.of()), chunk(1, "旧块二", List.of())));

        chunkStore.replaceDocument(record(docId, DOC_NAME, "hash-2", 1),
                List.of(chunk(0, "新块", List.of("保修政策"))));

        DocumentRecord document = chunkStore.findById(docId).orElseThrow();
        assertEquals("hash-2", document.contentHash());
        assertEquals(1, document.chunkCount());
        List<Chunk> loaded = chunkStore.listChunks(docId);
        assertEquals(1, loaded.size());
        assertEquals("新块", loaded.get(0).content());
    }

    @Test
    void deleteDocumentCascadesChunksAndKeepsOtherDocuments() {
        String otherDocId = UUID.randomUUID().toString();
        chunkStore.replaceDocument(record(docId, DOC_NAME, "hash-1", 1),
                List.of(chunk(0, "待删文档的块", List.of("保修政策"))));
        chunkStore.replaceDocument(record(otherDocId, OTHER_DOC_NAME, "hash-2", 1),
                List.of(chunk(0, "保留文档的块", List.of("保修政策"))));

        assertTrue(chunkStore.deleteDocument(docId));

        assertFalse(chunkStore.findById(docId).isPresent());
        assertTrue(chunkStore.listChunks(docId).isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_chunks WHERE doc_id = ?", Integer.class, UUID.fromString(docId)));
        assertTrue(chunkStore.findById(otherDocId).isPresent());
        assertEquals(1, chunkStore.listChunks(otherDocId).size());

        assertFalse(chunkStore.deleteDocument(docId));
    }

    private void removeTestDocuments() {
        jdbcTemplate.update("DELETE FROM rag_documents WHERE name = ?", DOC_NAME);
        jdbcTemplate.update("DELETE FROM rag_documents WHERE name = ?", OTHER_DOC_NAME);
    }

    private static DocumentRecord record(String docId, String name, String hash, int chunkCount) {
        return new DocumentRecord(docId, name, "text/markdown", 11L, hash, chunkCount, Instant.now());
    }

    private static Chunk chunk(int index, String content, List<String> outlinePath) {
        return new Chunk(UUID.randomUUID().toString(), index, content, "向量文本-" + content,
                new ChunkMetadata(outlinePath, DOC_NAME, Map.of()));
    }
}
