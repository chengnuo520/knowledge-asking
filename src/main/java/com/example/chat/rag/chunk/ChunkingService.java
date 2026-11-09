package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.CodeBlock;
import com.example.chat.rag.parser.model.HeadingBlock;
import com.example.chat.rag.parser.model.ListBlock;
import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.TableBlock;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分块入口：解析产出的 Block 列表 → 成品块。
 * <p>
 * 流程固定为维护标题路径 → 按类型分发产草稿 → 按节打包 → 统一装配。
 */
@Service
public class ChunkingService {

    private static final Map<Class<? extends Block>, BlockChunker<?>> CHUNKERS = Map.of(
            HeadingBlock.class, new HeadingChunker(),
            ParagraphBlock.class, new ParagraphChunker(),
            ListBlock.class, new ListChunker(),
            TableBlock.class, new TableChunker(),
            CodeBlock.class, new CodeChunker());

    private final ChunkBudget chunkBudget;

    public ChunkingService(ChunkBudget chunkBudget) {
        this.chunkBudget = chunkBudget;
    }

    public List<Chunk> chunk(List<Block> blocks, String sourceFile) {
        return chunk(blocks, chunkBudget, sourceFile);
    }

    /**
     * 指定预算分块，便于测试与按文档覆盖配置。
     */
    public List<Chunk> chunk(List<Block> blocks, ChunkBudget budget, String sourceFile) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        HeadingHandler headingHandler = new HeadingHandler();
        List<ChunkDraft> drafts = new ArrayList<>();
        for (Block block : blocks) {
            if (block instanceof HeadingBlock heading) {
                headingHandler.apply(heading);
            }
            drafts.addAll(chunkOne(block, new ChunkContext(headingHandler.currentPath(), budget)));
        }
        return ChunkAssembler.assembleAll(ChunkPacker.pack(drafts, budget), sourceFile);
    }

    @SuppressWarnings("unchecked")
    private static List<ChunkDraft> chunkOne(Block block, ChunkContext ctx) {
        BlockChunker<Block> chunker = (BlockChunker<Block>) CHUNKERS.get(block.getClass());
        if (chunker == null) {
            throw new IllegalStateException("没有 chunker 认领 Block 类型：" + block.getClass().getName());
        }
        return chunker.chunk(block, ctx);
    }
}
