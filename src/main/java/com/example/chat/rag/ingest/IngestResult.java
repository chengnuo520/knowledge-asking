package com.example.chat.rag.ingest;

/**
 * 摄取结果：命中幂等时 skipped=true，四段耗时全为 0。
 */
public record IngestResult(String docId, String docName, int chunkCount, boolean skipped, Timings timings) {

    public record Timings(long detectMs, long parseMs, long chunkMs, long indexMs) {
    }
}
