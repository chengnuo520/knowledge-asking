package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkMetadata;
import com.example.chat.rag.ingest.store.ChunkStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试专用内存存储，行为与 JdbcChunkStore 对齐：
 * 按 docId 覆盖文档与块、按名字查文档、块级写操作同步 chunk_count。
 */
public class InMemoryChunkStore implements ChunkStore {

    private final Map<String, DocumentRecord> documents = new ConcurrentHashMap<>();

    private final Map<String, List<Chunk>> chunksByDoc = new ConcurrentHashMap<>();

    @Override
    public void replaceDocument(DocumentRecord doc, List<Chunk> chunks) {
        documents.put(doc.docId(), doc);
        chunksByDoc.put(doc.docId(), new ArrayList<>(chunks == null ? List.of() : chunks));
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

    @Override
    public Optional<Chunk> findChunk(String docId, String chunkId) {
        return chunksByDoc.getOrDefault(docId, List.of()).stream()
                .filter(chunk -> chunk.chunkId().equals(chunkId))
                .findFirst();
    }

    @Override
    public boolean updateChunk(String docId, String chunkId, String content, String embeddingText) {
        List<Chunk> chunks = mutableChunks(docId);
        if (chunks == null) {
            return false;
        }
        for (int i = 0; i < chunks.size(); i++) {
            Chunk current = chunks.get(i);
            if (current.chunkId().equals(chunkId)) {
                chunks.set(i, new Chunk(current.chunkId(), current.index(), content, embeddingText, current.metadata()));
                chunksByDoc.put(docId, chunks);
                syncChunkCount(docId);
                return true;
            }
        }
        return false;
    }

    @Override
    public int deleteChunks(String docId, List<String> chunkIds) {
        List<Chunk> chunks = mutableChunks(docId);
        if (chunks == null || chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }
        Set<String> targets = new HashSet<>(chunkIds);
        int before = chunks.size();
        List<Chunk> kept = chunks.stream().filter(chunk -> !targets.contains(chunk.chunkId())).toList();
        chunksByDoc.put(docId, new ArrayList<>(kept));
        int deleted = before - kept.size();
        if (deleted > 0) {
            syncChunkCount(docId);
        }
        return deleted;
    }

    @Override
    public Chunk appendChunk(String docId, String content, String embeddingText,
                             List<String> outlinePath, String sourceFile) {
        List<Chunk> chunks = mutableChunks(docId);
        if (chunks == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        int nextIndex = chunks.stream().mapToInt(Chunk::index).max().orElse(-1) + 1;
        Chunk chunk = new Chunk(
                UUID.randomUUID().toString(),
                nextIndex,
                content,
                embeddingText,
                new ChunkMetadata(outlinePath, sourceFile, Map.of()));
        chunks.add(chunk);
        chunksByDoc.put(docId, chunks);
        syncChunkCount(docId);
        return chunk;
    }

    private List<Chunk> mutableChunks(String docId) {
        List<Chunk> chunks = chunksByDoc.get(docId);
        if (chunks == null) {
            return null;
        }
        return new ArrayList<>(chunks);
    }

    private void syncChunkCount(String docId) {
        DocumentRecord doc = documents.get(docId);
        if (doc == null) {
            return;
        }
        int count = chunksByDoc.getOrDefault(docId, List.of()).size();
        documents.put(docId, new DocumentRecord(doc.docId(), doc.name(), doc.mimeType(),
                doc.size(), doc.contentHash(), count, doc.ingestedAt()));
    }
}
