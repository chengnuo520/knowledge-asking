package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.CodeBlock;
import com.example.chat.rag.parser.model.HeadingBlock;
import com.example.chat.rag.parser.model.ListBlock;
import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.ParsedDocument;
import com.example.chat.rag.parser.model.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    /**
     * 标题与段落的 inline 文本提取；GFM 表格单元与列表项不在其支持节点集合内，需走 {@link #extractInlineText}
     */
    private static final TextContentRenderer RENDERER = TextContentRenderer.builder().build();

    @Override
    public String parserType() {
        return "markdown";
    }

    @Override
    public Set<String> supportedMimeTypes() {
        return Set.of("text/markdown", "text/x-markdown", "text/x-web-markdown");
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options) {
        List<Block> blocks = new ArrayList<>();
        if (bytes != null && bytes.length > 0) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            Document document = (Document) PARSER.parse(text);
            BlockExtractingVisitor visitor = new BlockExtractingVisitor();
            document.accept(visitor);
            blocks = visitor.blocks;
        }
        return new ParsedDocument(blocks, metadata(blocks, mimeType));
    }

    private Map<String, Object> metadata(List<Block> blocks, String mimeType) {
        return Map.of(
                "parser", parserType(),
                "mimeType", mimeType == null ? "" : mimeType,
                "blocks", blocks.size()
        );
    }

    private static String stripTrailingNewline(String text) {
        if (text == null) {
            return "";
        }
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }

    private static String extractInlineText(Node parent) {
        StringBuilder builder = new StringBuilder();
        Node child = parent.getFirstChild();
        while (child != null) {
            appendInline(builder, child);
            child = child.getNext();
        }
        return builder.toString();
    }

    private static void appendInline(StringBuilder builder, Node node) {
        if (node instanceof Text text) {
            builder.append(text.getLiteral());
        } else if (node instanceof Code code) {
            builder.append(code.getLiteral());
        } else if (node instanceof Link || node instanceof Image) {
            builder.append(extractInlineText(node));
        } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            builder.append('\n');
        } else if (node.getFirstChild() != null) {
            Node child = node.getFirstChild();
            while (child != null) {
                appendInline(builder, child);
                child = child.getNext();
            }
        }
    }

    private static final class BlockExtractingVisitor extends AbstractVisitor {

        private List<Block> blocks = new ArrayList<>();

        @Override
        public void visit(Heading heading) {
            String text = RENDERER.render(heading).strip();
            if (!text.isEmpty()) {
                blocks.add(new HeadingBlock(heading.getLevel(), text));
            }
        }

        @Override
        public void visit(Paragraph paragraph) {
            // 列表项内的段落归 ListBlock，不单独产出 ParagraphBlock
            if (paragraph.getParent() instanceof ListItem) {
                return;
            }
            String text = RENDERER.render(paragraph).strip();
            if (!text.isEmpty()) {
                blocks.add(new ParagraphBlock(text));
            }
        }

        @Override
        public void visit(BulletList bulletList) {
            addListBlock(bulletList, false);
        }

        @Override
        public void visit(OrderedList orderedList) {
            addListBlock(orderedList, true);
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            blocks.add(new CodeBlock(
                    codeBlock.getInfo(),
                    stripTrailingNewline(codeBlock.getLiteral())
            ));
        }

        @Override
        public void visit(CustomBlock customBlock) {
            // GFM 表格的 TableBlock 是 CustomBlock；与自定义 model.TableBlock 重名，使用全限定名
            if (customBlock instanceof org.commonmark.ext.gfm.tables.TableBlock tableBlock) {
                addTableBlock(tableBlock);
            }
            // 其它自定义块本轮跳过，不递归，避免其内部段落被重复产出
        }

        private void addListBlock(Node listNode, boolean ordered) {
            List<String> items = new ArrayList<>();
            Node child = listNode.getFirstChild();
            while (child != null) {
                if (child instanceof ListItem listItem) {
                    items.add(extractInlineText(listItem).strip());
                }
                child = child.getNext();
            }
            if (!items.isEmpty()) {
                blocks.add(new ListBlock(items, ordered));
            }
        }

        private void addTableBlock(org.commonmark.ext.gfm.tables.TableBlock tableBlock) {
            List<String> headers = new ArrayList<>();
            List<List<String>> rows = new ArrayList<>();

            Node child = tableBlock.getFirstChild();
            while (child != null) {
                if (child instanceof TableHead head) {
                    Node rowNode = head.getFirstChild();
                    while (rowNode != null) {
                        if (rowNode instanceof TableRow row) {
                            headers.addAll(extractCellTexts(row));
                        }
                        rowNode = rowNode.getNext();
                    }
                } else if (child instanceof TableBody body) {
                    Node rowNode = body.getFirstChild();
                    while (rowNode != null) {
                        if (rowNode instanceof TableRow row) {
                            rows.add(extractCellTexts(row));
                        }
                        rowNode = rowNode.getNext();
                    }
                }
                child = child.getNext();
            }

            blocks.add(new TableBlock(headers, rows));
        }

        private List<String> extractCellTexts(TableRow row) {
            List<String> cells = new ArrayList<>();
            Node cell = row.getFirstChild();
            while (cell != null) {
                if (cell instanceof TableCell tableCell) {
                    cells.add(extractInlineText(tableCell).strip());
                }
                cell = cell.getNext();
            }
            return cells;
        }
    }
}
