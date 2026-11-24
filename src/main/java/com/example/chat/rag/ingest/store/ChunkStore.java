package com.example.chat.rag.ingest.store;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.ingest.DocumentRecord;

import java.util.List;
import java.util.Optional;

/**
 * 文档与块的存储边界：文档级增删查 + 块级增改删，不含相似度检索。
 *
 * <p>块级写操作都必须同步维护 {@code rag_documents.chunk_count}，
 * 否则列表页的分块数会与实际不符。
 */
public interface ChunkStore {

    void replaceDocument(DocumentRecord doc, List<Chunk> chunks);

    boolean deleteDocument(String docId);

    List<DocumentRecord> listDocuments();

    Optional<DocumentRecord> findByName(String name);

    Optional<DocumentRecord> findById(String docId);

    List<Chunk> listChunks(String docId);

    Optional<Chunk> findChunk(String docId, String chunkId);

    /**
     * 改写块正文：content 与 embedding_text 一起更新，同时清空 embedding
     * （旧的向量已与正文不对应，留着会污染后续检索）。
     *
     * @return 是否命中该块
     */
    boolean updateChunk(String docId, String chunkId, String content, String embeddingText);

    /**
     * 批量删除块，并在同一事务内重算 chunk_count。
     *
     * @return 实际删除的块数
     */
    int deleteChunks(String docId, List<String> chunkIds);

    /**
     * 追加一个块到末尾（序号取当前最大值 + 1），并同步 chunk_count。
     */
    Chunk appendChunk(String docId, String content, String embeddingText, List<String> outlinePath, String sourceFile);
}
