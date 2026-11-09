import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkBudget;
import com.example.chat.rag.chunk.ChunkingService;
import com.example.chat.rag.config.RagProperties;
import com.example.chat.rag.ingest.IngestResult;
import com.example.chat.rag.ingest.InMemoryChunkStore;
import com.example.chat.rag.ingest.IngestionService;
import com.example.chat.rag.parser.CsvDocumentParser;
import com.example.chat.rag.parser.DocumentParser;
import com.example.chat.rag.parser.MarkdownDocumentParser;
import com.example.chat.rag.parser.MimeTypeDetector;
import com.example.chat.rag.parser.ParserRegistry;
import com.example.chat.rag.parser.PlainTextDocumentParser;
import com.example.chat.rag.parser.TikaDocumentParser;
import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.ParsedDocument;
import org.apache.tika.Tika;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 实操取证程序：不启动 Spring、不连数据库，直接驱动生产代码的摄取链路。
 */
public class EvidenceRun {

    private static final List<DocumentParser> PARSERS = List.of(
            new MarkdownDocumentParser(),
            new PlainTextDocumentParser(),
            new CsvDocumentParser(),
            new TikaDocumentParser());

    public static void main(String[] args) throws Exception {
        Path fixture = Path.of("src/test/resources/docs/保修政策.md");
        Path csv = Path.of("src/test/resources/docs/faq.csv");
        byte[] mdBytes = Files.readAllBytes(fixture);

        MimeTypeDetector detector = new MimeTypeDetector();
        ParserRegistry registry = new ParserRegistry(PARSERS);
        ChunkingService chunking = new ChunkingService(ChunkBudget.defaults());
        InMemoryChunkStore store = new InMemoryChunkStore();
        IngestionService ingestion = new IngestionService(registry, detector, chunking, store, props());

        line("【1】上传校验：三类非法输入在 ingest 入口被拦下");
        tryIngest(ingestion, "empty.md", new byte[0]);
        tryIngest(ingestion, "big.txt", new byte[22_000_000]);
        tryIngest(ingestion, "pom.xml", Files.readAllBytes(Path.of("pom.xml")));

        line("");
        line("【2】MIME 探测：字节探测 + 扩展名回退");
        String raw = new Tika().detect(mdBytes, "保修政策.md");
        System.out.println("  Tika 原始探测      : " + raw);
        System.out.println("  MimeTypeDetector   : " + detector.detect(mdBytes, "保修政策.md"));
        System.out.println("  ParserRegistry 命中: " + registry.require(detector.detect(mdBytes, "保修政策.md")).parserType());
        System.out.println("  未知 MIME 请求      : " + describe(() -> registry.require("application/zip")));

        line("");
        line("【3】默认预算（1024/128/50/3）下的整篇摄取");
        IngestResult first = ingestion.ingest(fixture.getFileName().toString(), mdBytes);
        System.out.println("  " + first);

        line("");
        line("【4】幂等与重灌");
        IngestResult again = ingestion.ingest(fixture.getFileName().toString(), mdBytes);
        System.out.println("  原样重传  : " + again);
        byte[] extended = (new String(mdBytes, StandardCharsets.UTF_8) + "\n补充：进水损坏不在保修范围内。\n")
                .getBytes(StandardCharsets.UTF_8);
        IngestResult changed = ingestion.ingest(fixture.getFileName().toString(), extended);
        System.out.println("  同名改内容: " + changed);

        line("");
        line("【5】紧凑预算（32/4/50/1）下的分块结构：content 与 embeddingText 的差异");
        DocumentParser parser = registry.require(detector.detect(mdBytes, "保修政策.md"));
        ParsedDocument parsed = parser.parse(mdBytes, "text/markdown", Map.of("sourceFile", "保修政策.md"));
        List<Chunk> chunks = new ChunkingService(new ChunkBudget(32, 4, 50, 1))
                .chunk(parsed.blocks(), "保修政策.md");
        System.out.println("  Block 数 = " + parsed.blocks().size() + "，Chunk 数 = " + chunks.size());
        for (Chunk c : chunks) {
            System.out.println("  ---- chunk #" + c.index() + "  outlinePath=" + c.metadata().outlinePath());
            System.out.println("       content      = " + quote(c.content()));
            System.out.println("       embeddingText= " + quote(c.embeddingText()));
        }

        line("");
        line("【6】CSV：一张表 = 一个 TableBlock，content 保留表头，embeddingText 变成 列名: 值");
        byte[] csvBytes = Files.readAllBytes(csv);
        List<Chunk> csvChunks = new ChunkingService(ChunkBudget.defaults())
                .chunk(registry.require(detector.detect(csvBytes, "faq.csv"))
                        .parse(csvBytes, "text/csv", Map.of()).blocks(), "faq.csv");
        System.out.println("  Chunk 数 = " + csvChunks.size());
        for (Chunk c : csvChunks) {
            System.out.println("       content      = " + quote(c.content()));
            System.out.println("       embeddingText= " + quote(c.embeddingText()));
        }
    }

    private static RagProperties props() {
        RagProperties p = new RagProperties();
        p.setMaxFileSizeMb(20);
        return p;
    }

    private static void tryIngest(IngestionService ingestion, String name, byte[] bytes) {
        System.out.println("  " + name + " (" + bytes.length + " 字节) -> " + describe(() -> ingestion.ingest(name, bytes)));
    }

    private static String describe(ThrowingSupplier supplier) {
        try {
            Object value = supplier.get();
            return String.valueOf(value);
        } catch (Exception e) {
            return "拒绝 [" + e.getClass().getSimpleName() + "] " + e.getMessage();
        }
    }

    private static String quote(String text) {
        String flat = text.replace("\n", "\\n");
        return "\"" + (flat.length() > 120 ? flat.substring(0, 120) + "…" : flat) + "\"";
    }

    private static void line(String text) {
        System.out.println(text);
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }
}
