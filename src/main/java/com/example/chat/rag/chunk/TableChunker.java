package com.example.chat.rag.chunk;

import com.example.chat.rag.parser.model.TableBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格 chunker：每块都带完整表头，展示文本是 {@code 列 | 列} 的表格行，检索正文用 {@code 列名: 值}。
 * <p>
 * 行按 rowsPerChunk 与 maxChars 分组；单行超过预算时整行原子成块，绝不从行中间切断。
 */
public class TableChunker implements BlockChunker<TableBlock> {

    @Override
    public Class<TableBlock> blockType() {
        return TableBlock.class;
    }

    @Override
    public List<ChunkDraft> chunk(TableBlock block, ChunkContext ctx) {
        if (block == null) {
            return List.of();
        }
        List<String> headers = block.headers() == null ? List.of() : block.headers();
        List<List<String>> rows = block.rows() == null ? List.of() : block.rows();
        if (headers.isEmpty() && rows.isEmpty()) {
            return List.of();
        }
        if (rows.isEmpty()) {
            return List.of(new ChunkDraft(renderContent(headers, List.of()), null, ctx.outlinePath(), false, false));
        }

        int maxRows = Math.max(1, ctx.budget().rowsPerChunk());
        int maxChars = Math.max(1, ctx.budget().maxChars());

        List<List<List<String>>> groups = new ArrayList<>();
        List<List<String>> group = new ArrayList<>();
        int groupCost = 0;
        for (List<String> row : rows) {
            int rowCost = renderRecord(headers, row).length();
            boolean overCap = group.size() >= maxRows;
            boolean overBudget = !group.isEmpty() && groupCost + rowCost > maxChars;
            if (overCap || overBudget) {
                groups.add(group);
                group = new ArrayList<>();
                groupCost = 0;
            }
            group.add(row);
            groupCost += rowCost;
        }
        groups.add(group);

        boolean multiple = groups.size() > 1;
        List<ChunkDraft> drafts = new ArrayList<>(groups.size());
        for (List<List<String>> rowsInGroup : groups) {
            drafts.add(new ChunkDraft(
                    renderContent(headers, rowsInGroup),
                    headers.isEmpty() ? null : renderBody(headers, rowsInGroup),
                    ctx.outlinePath(),
                    false,
                    multiple));
        }
        return drafts;
    }

    private static String renderContent(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        if (!headers.isEmpty()) {
            sb.append(String.join(" | ", headers));
        }
        for (List<String> row : rows) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(String.join(" | ", row));
        }
        return sb.toString();
    }

    /**
     * 每行渲染为若干 {@code 列名: 值} 行；行内换行用 {@code \n}，记录之间用空行分隔。
     */
    private static String renderBody(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : rows) {
            String record = renderRecord(headers, row);
            if (record.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(record);
        }
        return sb.toString();
    }

    private static String renderRecord(List<String> headers, List<String> row) {
        StringBuilder record = new StringBuilder();
        for (int c = 0; c < row.size(); c++) {
            String value = row.get(c);
            if (value == null || value.isEmpty()) {
                continue;
            }
            String key = c < headers.size() ? headers.get(c) : "";
            if (!record.isEmpty()) {
                record.append('\n');
            }
            if (!key.isEmpty()) {
                record.append(oneLine(key)).append(": ");
            }
            record.append(oneLine(value));
        }
        return record.toString();
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\r\\n|\\r|\\n", " ");
    }
}
