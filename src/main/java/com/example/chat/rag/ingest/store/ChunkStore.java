package com.example.chat.rag.ingest.store;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.ingest.DocumentRecord;

import java.util.List;
import java.util.Optional;

/**
 * 文档与块的存储边界；本轮只做增删查，不含相似度检索。
 */
public interface ChunkStore {

    void replaceDocument(DocumentRecord doc, List<Chunk> chunks);

    boolean deleteDocument(String docId);

    List<DocumentRecord> listDocuments();

    Optional<DocumentRecord> findByName(String name);

    Optional<DocumentRecord> findById(String docId);

    List<Chunk> listChunks(String docId);
}
