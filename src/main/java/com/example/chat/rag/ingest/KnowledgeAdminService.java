package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkAssembler;
import com.example.chat.rag.ingest.store.ChunkStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台管理侧的写操作编排：块的编辑/新建/删除、文档批量删除。
 *
 * <p>这里只负责校验与口径统一（正文长度、embeddingText 重建、chunk_count 同步），
 * 事务边界在 {@link ChunkStore} 的实现里。
 */
@Service
public class KnowledgeAdminService {

    /** 单块正文上限：超过这个长度的块已经失去检索意义，基本是切分出了问题 */
    private static final int MAX_CONTENT_CHARS = 20_000;

    private final ChunkStore chunkStore;

    public KnowledgeAdminService(ChunkStore chunkStore) {
        this.chunkStore = chunkStore;
    }

    public Chunk updateChunk(String docId, String chunkId, String content) {
        String normalized = normalizeContent(content);
        Chunk existing = chunkStore.findChunk(docId, chunkId)
                .orElseThrow(() -> new ChunkNotFoundException("分块不存在"));

        List<String> outlinePath = existing.metadata() == null ? List.of() : existing.metadata().outlinePath();
        String embeddingText = ChunkAssembler.composeEmbeddingText(outlinePath, normalized);

        if (!chunkStore.updateChunk(docId, chunkId, normalized, embeddingText)) {
            throw new ChunkNotFoundException("分块不存在");
        }
        return chunkStore.findChunk(docId, chunkId).orElse(existing);
    }

    public Chunk createChunk(String docId, String content) {
        DocumentRecord doc = requireDocument(docId);
        String normalized = normalizeContent(content);
        String embeddingText = ChunkAssembler.composeEmbeddingText(List.of(), normalized);
        return chunkStore.appendChunk(docId, normalized, embeddingText, List.of(), doc.name());
    }

    /**
     * @return 实际删除的块数
     */
    public int deleteChunks(String docId, List<String> chunkIds) {
        requireDocument(docId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            throw new IngestionException("请选择要删除的分块");
        }
        return chunkStore.deleteChunks(docId, chunkIds);
    }

    /**
     * @return 实际删除的文档数（块随外键级联删除）
     */
    public int deleteDocuments(List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            throw new IngestionException("请选择要删除的文档");
        }
        int deleted = 0;
        for (String docId : docIds) {
            if (chunkStore.deleteDocument(docId)) {
                deleted++;
            }
        }
        return deleted;
    }

    private DocumentRecord requireDocument(String docId) {
        return chunkStore.findById(docId)
                .orElseThrow(() -> new ChunkNotFoundException("文档不存在"));
    }

    private static String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IngestionException("分块内容不能为空");
        }
        String trimmed = content.strip();
        if (trimmed.length() > MAX_CONTENT_CHARS) {
            throw new IngestionException("分块内容超过 " + MAX_CONTENT_CHARS + " 字上限，请拆分后再保存");
        }
        return trimmed;
    }
}
