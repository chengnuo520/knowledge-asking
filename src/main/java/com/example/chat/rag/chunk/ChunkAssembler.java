package com.example.chat.rag.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 块装配器：草稿 → 成品块的唯一通道。
 * <p>
 * content 原样保留，章节上下文只补进 embeddingText：路径自根向下，直到某一级已出现在 content 中，
 * 只拼在此之前（content 中缺失）的层级。
 */
public final class ChunkAssembler {

    private static final String CONTEXT_SEPARATOR = "\n";

    private static final String OUTLINE_SEPARATOR = " / ";

    private ChunkAssembler() {
    }

    public static List<Chunk> assembleAll(List<ChunkDraft> drafts, String sourceFile) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<Chunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            ChunkMetadata metadata = new ChunkMetadata(draft.outlinePath(), sourceFile, Map.of());
            chunks.add(new Chunk(
                    UUID.randomUUID().toString(),
                    i,
                    draft.content(),
                    composeEmbeddingText(draft.outlinePath(), draft.effectiveBody(), draft.content()),
                    metadata));
        }
        return chunks;
    }

    private static String composeEmbeddingText(List<String> outlinePath, String body, String content) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, missingOutlinePrefix(outlinePath, content));
        appendIfPresent(sb, body);
        return sb.toString();
    }

    /**
     * 取 content 尚未覆盖的那截章节路径：路径自根向下，到首个命中的层级为止。
     */
    private static String missingOutlinePrefix(List<String> outlinePath, String content) {
        if (outlinePath == null || outlinePath.isEmpty()) {
            return null;
        }
        String text = content == null ? "" : content;
        int keep = 0;
        while (keep < outlinePath.size() && !text.contains(outlinePath.get(keep))) {
            keep++;
        }
        return keep == 0 ? null : String.join(OUTLINE_SEPARATOR, outlinePath.subList(0, keep));
    }

    private static void appendIfPresent(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(CONTEXT_SEPARATOR);
        }
        sb.append(part.strip());
    }
}
