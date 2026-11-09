package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.ingest.store.ChunkStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试专用内存存储，行为与 JdbcChunkStore 对齐：按 docId 覆盖，按名字查文档。
 */
public class InMemoryChunkStore implements ChunkStore {

    private final Map<String, DocumentRecord> documents = new ConcurrentHashMap<>();

    private final Map<String, List<Chunk>> chunksByDoc = new ConcurrentHashMap<>();

    @Override
    public void replaceDocument(DocumentRecord doc, List<Chunk> chunks) {
        documents.put(doc.docId(), doc);
        chunksByDoc.put(doc.docId(), List.copyOf(chunks == null ? List.of() : chunks));
    }

    @Override
    public boolean deleteDocument(String docId) {
        chunksByDoc.remove(docId);
        return documents.remove(docId) != null;
    }

    @Override
    public List<DocumentRecord> listDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(DocumentRecord::ingestedAt).reversed())
                .toList();
    }

    @Override
    public Optional<DocumentRecord> findByName(String name) {
        return documents.values().stream()
                .filter(document -> document.name().equals(name))
                .findFirst();
    }

    @Override
    public Optional<DocumentRecord> findById(String docId) {
        return Optional.ofNullable(documents.get(docId));
    }

    @Override
    public List<Chunk> listChunks(String docId) {
        return chunksByDoc.getOrDefault(docId, List.of()).stream()
                .sorted(Comparator.comparingInt(Chunk::index))
                .toList();
    }
}
