package com.example.chat.rag.parser;

import com.example.chat.rag.parser.model.ParsedDocument;
import com.example.chat.rag.parser.model.TableBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvDocumentParserTest {

    private final CsvDocumentParser parser = new CsvDocumentParser();

    @Test
    void usesFirstRecordAsHeaders() throws IOException {
        ParsedDocument document = parser.parse(read("docs/faq.csv"), "text/csv", Map.of());

        assertEquals(1, document.blocks().size());
        TableBlock table = (TableBlock) document.blocks().get(0);
        assertEquals(List.of("问题", "答案"), table.headers());
        assertEquals(3, table.rows().size());
        assertEquals("退货期限是多久", table.rows().get(0).get(0));
        assertEquals("15 天内可无理由退货", table.rows().get(0).get(1));
    }

    private static byte[] read(String path) throws IOException {
        InputStream in = CsvDocumentParserTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试夹具不存在: " + path);
        }
        try (in) {
            return in.readAllBytes();
        }
    }
}
