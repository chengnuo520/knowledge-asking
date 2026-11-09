package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkBudget;
import com.example.chat.rag.chunk.ChunkingService;
import com.example.chat.rag.config.RagProperties;
import com.example.chat.rag.parser.CsvDocumentParser;
import com.example.chat.rag.parser.MarkdownDocumentParser;
import com.example.chat.rag.parser.MimeTypeDetector;
import com.example.chat.rag.parser.ParserRegistry;
import com.example.chat.rag.parser.PlainTextDocumentParser;
import com.example.chat.rag.parser.TikaDocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionServiceTest {

    private InMemoryChunkStore store;

    private IngestionService service;

    private RagProperties properties;

    @BeforeEach
    void setUp() {
        store = new InMemoryChunkStore();
        properties = new RagProperties();
        ParserRegistry registry = new ParserRegistry(List.of(
                new MarkdownDocumentParser(),
                new PlainTextDocumentParser(),
                new CsvDocumentParser(),
                new TikaDocumentParser()));
        service = new IngestionService(
                registry,
                new MimeTypeDetector(),
                new ChunkingService(new ChunkBudget(1024, 128, 50, 3)),
                store,
                properties);
    }

    @Test
    void ingestsMarkdownFixture() throws IOException {
        IngestResult result = service.ingest("保修政策.md", read("docs/保修政策.md"));

        assertFalse(result.skipped());
        assertTrue(result.chunkCount() > 0);
        assertEquals("保修政策.md", result.docName());
        // Tika 可能把 Markdown 内容识别为 text/markdown 或 text/x-web-markdown，均由 Markdown 解析器认领
        assertTrue(store.findById(result.docId()).orElseThrow().mimeType().contains("markdown"));
        assertEquals(result.chunkCount(), store.listChunks(result.docId()).size());
    }

    @Test
    void skipsSameNameAndSameContent() throws IOException {
        byte[] bytes = read("docs/保修政策.md");
        IngestResult first = service.ingest("保修政策.md", bytes);
        int chunkCount = first.chunkCount();

        IngestResult second = service.ingest("保修政策.md", bytes);

        assertTrue(second.skipped());
        assertEquals(first.docId(), second.docId());
        assertEquals(chunkCount, second.chunkCount());
        assertEquals(chunkCount, store.listChunks(first.docId()).size());
        assertEquals(new IngestResult.Timings(0, 0, 0, 0), second.timings());
    }

    @Test
    void replacesChunksWhenSameNameHasDifferentContent() throws IOException {
        IngestResult first = service.ingest("保修政策.md", read("docs/保修政策.md"));
        assertTrue(first.chunkCount() > 0);

        byte[] changed = "# 保修政策\n\n## 维修\n\n维修网点信息已更新，请就近前往。\n"
                .getBytes(StandardCharsets.UTF_8);
        IngestResult second = service.ingest("保修政策.md", changed);

        assertFalse(second.skipped());
        assertEquals(first.docId(), second.docId());
        assertEquals(1, store.listDocuments().size());
        List<Chunk> chunks = store.listChunks(first.docId());
        assertEquals(second.chunkCount(), chunks.size());
        assertTrue(chunks.stream().noneMatch(chunk -> chunk.content().contains("无理由退货")),
                "旧块应被整体替换");
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("维修网点")),
                "新内容应已入库");
    }

    @Test
    void treatsSameContentWithDifferentNamesAsSeparateDocuments() throws IOException {
        byte[] bytes = read("docs/保修政策.md");

        IngestResult first = service.ingest("保修政策-副本A.md", bytes);
        IngestResult second = service.ingest("保修政策-副本B.md", bytes);

        assertNotEquals(first.docId(), second.docId());
        assertEquals(2, store.listDocuments().size());
        assertEquals(first.chunkCount(), store.listChunks(first.docId()).size());
        assertEquals(second.chunkCount(), store.listChunks(second.docId()).size());
    }

    @Test
    void rejectsEmptyFile() {
        IngestionException exception = assertThrows(IngestionException.class,
                () -> service.ingest("empty.md", new byte[0]));

        assertTrue(exception.getMessage().contains("文件为空"), exception.getMessage());
    }

    @Test
    void rejectsFileExceedingSizeLimit() {
        properties.setMaxFileSizeMb(1);
        byte[] bytes = new byte[(int) (1.1 * 1024 * 1024)];
        Arrays.fill(bytes, (byte) 'a');

        IngestionException exception = assertThrows(IngestionException.class,
                () -> service.ingest("big.txt", bytes));

        assertTrue(exception.getMessage().contains("MB"), exception.getMessage());
    }

    @Test
    void rejectsUnsupportedExtensionAndListsAllowedOnes() {
        IngestionException exception = assertThrows(IngestionException.class,
                () -> service.ingest("photo.png", "hello".getBytes(StandardCharsets.UTF_8)));

        String message = exception.getMessage();
        assertTrue(message.contains(".md"), message);
        assertTrue(message.contains(".txt"), message);
        assertTrue(message.contains(".csv"), message);
        assertTrue(message.contains(".pdf"), message);
        assertTrue(message.contains(".docx"), message);
    }

    @Test
    void rejectsUnknownMimeTypeWhenFilenameIsMissing() {
        // 无文件名 → 使用 upload.bin，跳过扩展名白名单，最终落到 MIME 路由
        IngestionException exception = assertThrows(IngestionException.class,
                () -> service.ingest("   ", new byte[]{0x50, 0x4b, 0x03, 0x04}));

        assertTrue(exception.getMessage().contains("不支持的 MIME 类型"), exception.getMessage());
    }

    private static byte[] read(String path) throws IOException {
        InputStream in = IngestionServiceTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("测试夹具不存在: " + path);
        }
        try (in) {
            return in.readAllBytes();
        }
    }
}
