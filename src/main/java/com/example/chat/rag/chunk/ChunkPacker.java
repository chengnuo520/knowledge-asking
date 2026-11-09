package com.example.chat.rag.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 块打包器：以相邻两个标题之间为一节，按体量把节装配成块。
 * <p>
 * 切口只落在节边界上，故块与块之间不做重叠；整节超出容忍上限时才在节内按 maxChars 贪心切分。
 */
public final class ChunkPacker {

    private static final String SEPARATOR = "\n\n";

    private static final int MIN_CHARS_DIVISOR = 4;

    private ChunkPacker() {
    }

    /**
     * 打包成块：短节整节并入当前块（不超容忍上限），长节在节内按 maxChars 贪心切分。
     */
    public static List<ChunkDraft> pack(List<ChunkDraft> drafts, ChunkBudget budget) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        if (drafts.size() == 1) {
            return List.copyOf(drafts);
        }

        int maxChars = budget.maxChars();
        int minChars = Math.max(1, maxChars / MIN_CHARS_DIVISOR);
        List<ChunkDraft> result = new ArrayList<>();
        List<ChunkDraft> buffer = new ArrayList<>();

        for (List<ChunkDraft> section : splitSections(drafts)) {
            int sectionLen = totalLength(section);
            if (sectionLen > budget.toleranceChars()) {
                buffer = packWithin(buffer, section, budget, minChars, result);
                continue;
            }
            if (!buffer.isEmpty()
                    && totalLength(buffer) + SEPARATOR.length() + sectionLen > budget.toleranceChars()) {
                flush(buffer, result, budget, minChars);
                buffer = new ArrayList<>();
            }
            buffer.addAll(section);
        }
        flush(buffer, result, budget, minChars);
        return result;
    }

    /**
     * 按标题切节：标题起一节，标题之前的散块自成一节。
     */
    private static List<List<ChunkDraft>> splitSections(List<ChunkDraft> drafts) {
        List<List<ChunkDraft>> sections = new ArrayList<>();
        List<ChunkDraft> current = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (draft.heading() && !current.isEmpty()) {
                sections.add(current);
                current = new ArrayList<>();
            }
            current.add(draft);
        }
        if (!current.isEmpty()) {
            sections.add(current);
        }
        return sections;
    }

    /**
     * 节内切分：逐草稿贪心累加到 maxChars；piece 或自身已达 maxChars 的草稿原样落块。
     *
     * @param carried 上一节留下的缓冲区，一并参与本节累加
     * @return 本节结束时尚未落块的残留缓冲区
     */
    private static List<ChunkDraft> packWithin(List<ChunkDraft> carried, List<ChunkDraft> section,
                                               ChunkBudget budget, int minChars, List<ChunkDraft> result) {
        int maxChars = budget.maxChars();
        List<ChunkDraft> buffer = new ArrayList<>(carried);
        for (ChunkDraft draft : section) {
            int addLen = contentLength(draft);
            if (draft.piece() || addLen >= maxChars) {
                flush(buffer, result, budget, minChars);
                result.add(draft);
                buffer = new ArrayList<>();
                continue;
            }
            if (!buffer.isEmpty() && totalLength(buffer) + SEPARATOR.length() + addLen > maxChars) {
                flush(buffer, result, budget, minChars);
                buffer = new ArrayList<>();
            }
            buffer.add(draft);
        }
        return buffer;
    }

    /**
     * 缓冲区落块：不足 maxChars/4 的块在不超容忍上限时并回上一块。
     */
    private static void flush(List<ChunkDraft> buffer, List<ChunkDraft> result,
                              ChunkBudget budget, int minChars) {
        if (buffer.isEmpty()) {
            return;
        }
        ChunkDraft packed = buffer.size() == 1 ? buffer.get(0) : merge(buffer);
        if (!result.isEmpty() && contentLength(packed) < minChars) {
            ChunkDraft previous = result.get(result.size() - 1);
            if (contentLength(previous) + SEPARATOR.length() + contentLength(packed)
                    <= budget.toleranceChars()) {
                result.set(result.size() - 1, merge(List.of(previous, packed)));
                return;
            }
        }
        result.add(packed);
    }

    /**
     * 合并多块：content 与 body 分别用空行拼接，章节路径取公共前缀，heading/piece 清零。
     */
    private static ChunkDraft merge(List<ChunkDraft> parts) {
        StringBuilder content = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (ChunkDraft draft : parts) {
            appendPart(content, draft.content());
            appendPart(body, draft.effectiveBody());
        }
        List<String> path = parts.get(0).outlinePath().subList(0, commonPrefixLength(parts));
        return new ChunkDraft(content.toString(), body.toString(), path, false, false);
    }

    private static int commonPrefixLength(List<ChunkDraft> drafts) {
        List<String> first = drafts.get(0).outlinePath();
        int common = first.size();
        for (ChunkDraft draft : drafts) {
            common = Math.min(common, commonPrefixLength(first, draft.outlinePath()));
        }
        return common;
    }

    private static int commonPrefixLength(List<String> a, List<String> b) {
        int limit = Math.min(a.size(), b.size());
        int i = 0;
        while (i < limit && Objects.equals(a.get(i), b.get(i))) {
            i++;
        }
        return i;
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(SEPARATOR);
        }
        sb.append(part);
    }

    private static int totalLength(List<ChunkDraft> drafts) {
        int len = 0;
        for (int i = 0; i < drafts.size(); i++) {
            len += (i == 0 ? 0 : SEPARATOR.length()) + contentLength(drafts.get(i));
        }
        return len;
    }

    private static int contentLength(ChunkDraft draft) {
        return draft.content() == null ? 0 : draft.content().length();
    }
}
