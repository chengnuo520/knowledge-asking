package com.example.chat.rag.ingest;

import java.time.Instant;

/**
 * 已入库文档的元信息快照，对应 rag_documents 一行。
 */
public record DocumentRecord(String docId, String name, String mimeType, long size,
                             String contentHash, int chunkCount, Instant ingestedAt) {
}
