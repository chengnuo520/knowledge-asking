package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.ListBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 列表 chunker：按渲染后的字符体量贪心分组，绝不从项中间切断。
 */
public class ListChunker implements BlockChunker<ListBlock> {

    @Override
    public Class<ListBlock> blockType() {
        return ListBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(ListBlock block, ChunkContext ctx) {
        if (block == null || block.items() == null || block.items().isEmpty()) {
            return List.of();
        }
        List<String> items = block.items();
        int maxChars = ctx.budget().maxChars();
        int budgetChars = Math.max(1,
                renderedLength(block) <= ctx.budget().toleranceChars()
                        ? ctx.budget().toleranceChars()
                        : maxChars);

        List<List<String>> groups = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<String> group = new ArrayList<>();
        int start = 1;
        int cost = 0;
        for (int i = 0; i < items.size(); i++) {
            int itemCost = renderItem(block, i + 1, items.get(i)).length() + 1;
            if (!group.isEmpty() && cost + itemCost > budgetChars) {
                groups.add(group);
                starts.add(start);
                group = new ArrayList<>();
                start = i + 1;
                cost = 0;
            }
            group.add(items.get(i));
            cost += itemCost;
        }
        groups.add(group);
        starts.add(start);

        boolean multiple = groups.size() > 1;
        List<ChunkDraft> drafts = new ArrayList<>(groups.size());
        for (int g = 0; g < groups.size(); g++) {
            drafts.add(new ChunkDraft(
                    renderGroup(block, starts.get(g), groups.get(g)),
                    null,
                    ctx.outlinePath(),
                    false,
                    multiple));
        }
        return drafts;
    }

    private static String renderGroup(ListBlock block, int startNumber, List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(renderItem(block, startNumber + i, items.get(i)));
        }
        return sb.toString();
    }

    private static int renderedLength(ListBlock block) {
        int total = 0;
        List<String> items = block.items();
        for (int i = 0; i < items.size(); i++) {
            total += renderItem(block, i + 1, items.get(i)).length() + 1;
        }
        return total;
    }

    private static String renderItem(ListBlock block, int number, String item) {
        return block.ordered() ? number + ". " + item : "- " + item;
    }
}
