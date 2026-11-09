package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.ParagraphBlock;
import com.example.chat.rag.parser.model.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlainTextDocumentParserTest {

    private final PlainTextDocumentParser parser = new PlainTextDocumentParser();

    @Test
    void splitsParagraphsOnBlankLines() throws IOException {
        ParsedDocument document = parser.parse(read("docs/notes.txt"), "text/plain", Map.of());

        assertEquals(3, document.blocks().size());
        assertEquals("本政策适用于在本平台购买的所有电子产品。",
                ((ParagraphBlock) document.blocks().get(0)).text());
        assertEquals("手机自签收之日起 15 天内可申请无理由退货。",
                ((ParagraphBlock) document.blocks().get(1)).text());
        assertEquals("保修期内请保留购买凭证和订单截图。",
                ((ParagraphBlock) document.blocks().get(2)).text());
    }

    private static byte[] read(String path) throws IOException {
        InputStream in = PlainTextDocumentParserTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试夹具不存在: " + path);
        }
        try (in) {
            return in.readAllBytes();
        }
    }
}
