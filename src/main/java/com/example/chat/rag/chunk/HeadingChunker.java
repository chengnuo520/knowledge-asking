package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.HeadingBlock;

import java.util.List;

/**
 * 标题 chunker：标题按原文位置回到正文，并作为打包阶段的分节标记。
 */
public class HeadingChunker implements BlockChunker<HeadingBlock> {

    @Override
    public Class<HeadingBlock> blockType() {
        return HeadingBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(HeadingBlock block, ChunkContext ctx) {
        if (block == null || block.text() == null || block.text().isBlank()) {
            return List.of();
        }
        return List.of(new ChunkDraft(block.text(), null, ctx.outlinePath(), true, false));
    }
}
