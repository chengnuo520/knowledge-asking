package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.Block;

import java.util.List;

/**
 * Block 分块器：一种 Block 类型对应一个实现。
 */
public interface BlockChunker<T extends Block> {

    Class<T> blockType();

    List<ChunkDraft> chunk(T block, ChunkContext ctx);
}
