package com.example.chat.rag.chunk;

/**
 * 分块预算：maxChars 是目标体量，toleranceChars 是为保完整性允许撑到的上限。
 *
 * @param maxChars        目标块大小
 * @param overlapChars    相邻片段重叠字符数，同时作为 TextSplitter 边界回溯的最大距离
 * @param rowsPerChunk    表格类每块包含的数据行上限
 * @param toleranceFactor 允许超出 maxChars 的倍数
 */
public record ChunkBudget(int maxChars, int overlapChars, int rowsPerChunk, int toleranceFactor) {

    public static final int MAX_CHARS_LIMIT = 8192;

    public static final int ROWS_PER_CHUNK_LIMIT = 1000;

    public static final int OVERLAP_DIVISOR = 8;

    /** 容忍倍数上限，乘出来的值仍受 {@link #MAX_CHARS_LIMIT} 封顶 */
    public static final int TOLERANCE_FACTOR_LIMIT = 8;

    public ChunkBudget {
        if (maxChars <= 0 || maxChars > MAX_CHARS_LIMIT) {
            throw new IllegalArgumentException(
                    "maxChars 必须落在 (0, " + MAX_CHARS_LIMIT + "]，实际 " + maxChars);
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException(
                    "overlapChars 必须落在 [0, maxChars) 区间，实际 " + overlapChars);
        }
        if (rowsPerChunk <= 0 || rowsPerChunk > ROWS_PER_CHUNK_LIMIT) {
            throw new IllegalArgumentException(
                    "rowsPerChunk 必须落在 (0, " + ROWS_PER_CHUNK_LIMIT + "]，实际 " + rowsPerChunk);
        }
        if (toleranceFactor < 1 || toleranceFactor > TOLERANCE_FACTOR_LIMIT) {
            throw new IllegalArgumentException(
                    "toleranceFactor 必须落在 [1, " + TOLERANCE_FACTOR_LIMIT + "] 区间，实际 " + toleranceFactor);
        }
    }

    /** 系统默认预算：1024 / 128 / 50 / 3 */
    public static ChunkBudget defaults() {
        return new ChunkBudget(1024, 128, 50, 3);
    }

    /** 为保完整性允许撑到的字符数，仍受 {@link #MAX_CHARS_LIMIT} 封顶 */
    public int toleranceChars() {
        return Math.min(maxChars * toleranceFactor, MAX_CHARS_LIMIT);
    }
}
