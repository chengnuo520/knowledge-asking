package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.CodeBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码块 chunker：不超过容忍上限时整块保留，超出时才按行切分，尽量在空行处落刀。
 */
public class CodeChunker implements BlockChunker<CodeBlock> {

    @Override
    public Class<CodeBlock> blockType() {
        return CodeBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(CodeBlock block, ChunkContext ctx) {
        if (block == null || block.text() == null || block.text().isBlank()) {
            return List.of();
        }
        ChunkBudget budget = ctx.budget();
        List<String> segments = block.text().length() <= budget.toleranceChars()
                ? List.of(block.text())
                : splitByLines(block.text(), budget.maxChars());

        boolean multiple = segments.size() > 1;
        List<ChunkDraft> drafts = new ArrayList<>(segments.size());
        for (String segment : segments) {
            drafts.add(new ChunkDraft(segment, null, ctx.outlinePath(), false, multiple));
        }
        return drafts;
    }

    /**
     * 按行累加切分：单行超预算时整行独立成块；当前片段已攒到目标一半以上时，遇到空行优先落块。
     */
    private static List<String> splitByLines(String code, int maxChars) {
        int limit = Math.max(1, maxChars);
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : code.split("\n", -1)) {
            int addition = current.isEmpty() ? line.length() : current.length() + 1 + line.length();
            if (!current.isEmpty() && addition > limit) {
                segments.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
            if (line.isBlank() && current.length() >= limit / 2) {
                segments.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments.isEmpty() ? List.of(code) : segments;
    }
}
