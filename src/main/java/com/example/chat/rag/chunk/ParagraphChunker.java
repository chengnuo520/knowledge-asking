package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.ParagraphBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落 chunker：长度不超过容忍上限时整段保留，否则按 maxChars 交给 TextSplitter 做边界切分。
 */
public class ParagraphChunker implements BlockChunker<ParagraphBlock> {

    @Override
    public Class<ParagraphBlock> blockType() {
        return ParagraphBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(ParagraphBlock block, ChunkContext ctx) {
        if (block == null || block.text() == null || block.text().isBlank()) {
            return List.of();
        }
        ChunkBudget budget = ctx.budget();
        if (block.text().length() <= budget.toleranceChars()) {
            return List.of(new ChunkDraft(block.text(), null, ctx.outlinePath(), false, false));
        }

        List<String> pieces = TextSplitter.split(block.text(), budget.maxChars(), budget.overlapChars());
        List<ChunkDraft> drafts = new ArrayList<>(pieces.size());
        for (String piece : pieces) {
            drafts.add(new ChunkDraft(piece, null, ctx.outlinePath(), false, true));
        }
        return drafts;
    }
}
