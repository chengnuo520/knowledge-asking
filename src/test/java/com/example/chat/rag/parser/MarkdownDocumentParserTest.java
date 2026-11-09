package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.CodeBlock;
import com.example.chat.rag.parser.model.HeadingBlock;
import com.example.chat.rag.parser.model.ListBlock;
import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.ParsedDocument;
import com.example.chat.rag.parser.model.TableBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void parsesHeadingsListsTableAndCode() throws IOException {
        ParsedDocument document = parser.parse(
                read("docs/保修政策.md"), "text/markdown", Map.of());

        List<Block> blocks = document.blocks();

        List<HeadingBlock> headings = blocks.stream()
                .filter(HeadingBlock.class::isInstance)
                .map(HeadingBlock.class::cast)
                .toList();
        assertEquals(3, headings.size());
        assertEquals(1, headings.get(0).level());
        assertEquals("保修政策", headings.get(0).text());
        assertEquals(2, headings.get(1).level());
        assertEquals("手机", headings.get(1).text());
        assertEquals(3, headings.get(2).level());
        assertEquals("退换货", headings.get(2).text());

        List<ListBlock> lists = blocks.stream()
                .filter(ListBlock.class::isInstance)
                .map(ListBlock.class::cast)
                .toList();
        assertEquals(1, lists.size());
        assertFalse(lists.get(0).ordered());
        assertEquals(List.of("电子发票", "订单截图"), lists.get(0).items());

        List<TableBlock> tables = blocks.stream()
                .filter(TableBlock.class::isInstance)
                .map(TableBlock.class::cast)
                .toList();
        assertEquals(1, tables.size());
        assertEquals(List.of("商品", "期限", "说明"), tables.get(0).headers());
        assertEquals(2, tables.get(0).rows().size());
        assertEquals("15 天", tables.get(0).rows().get(0).get(1));
        assertEquals("7 天", tables.get(0).rows().get(1).get(1));

        List<CodeBlock> codes = blocks.stream()
                .filter(CodeBlock.class::isInstance)
                .map(CodeBlock.class::cast)
                .toList();
        assertEquals(1, codes.size());
        assertEquals("bash", codes.get(0).language());
        assertEquals("curl -X POST https://example.com/api/refund", codes.get(0).text());

        List<ParagraphBlock> paragraphs = blocks.stream()
                .filter(ParagraphBlock.class::isInstance)
                .map(ParagraphBlock.class::cast)
                .toList();
        assertEquals(3, paragraphs.size());
        assertTrue(paragraphs.stream().noneMatch(p -> p.text().contains("电子发票")));
    }

    private static byte[] read(String path) throws IOException {
        InputStream in = MarkdownDocumentParserTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试夹具不存在: " + path);
        }
        try (in) {
            return in.readAllBytes();
        }
    }
}
