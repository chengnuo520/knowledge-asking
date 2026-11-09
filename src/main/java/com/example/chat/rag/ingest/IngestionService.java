package com.example.chat.rag.ingest;

import com.example.chat.rag.chunk.Chunk;
import com.example.chat.rag.chunk.ChunkingService;
import com.example.chat.rag.config.RagProperties;
import com.example.chat.rag.ingest.store.ChunkStore;
import com.example.chat.rag.parser.DocumentParser;
import com.example.chat.rag.parser.MimeTypeDetector;
import com.example.chat.rag.parser.ParserRegistry;
import com.example.chat.rag.parser.UnsupportedDocumentTypeException;
import com.example.chat.rag.parser.model.Block;
import com.example.chat.rag.parser.model.ParsedDocument;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 摄取编排：校验 → 内容哈希 → 查重 → MIME 探测 → 解析 → 分块 → 入库，顺序固定。
 * <p>
 * 解析/分块失败时不会触碰存储；只有全部分块成功后才整体替换文档与块。
 */
@Service
public class IngestionService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "txt", "csv", "pdf", "docx");

    private static final String SUPPORTED_EXTENSIONS = ".md/.txt/.csv/.pdf/.docx";

    private static final String DEFAULT_FILENAME = "upload.bin";

    private final ParserRegistry parserRegistry;

    private final MimeTypeDetector mimeTypeDetector;

    private final ChunkingService chunkingService;

    private final ChunkStore chunkStore;

    private final RagProperties properties;

    public IngestionService(ParserRegistry parserRegistry,
                            MimeTypeDetector mimeTypeDetector,
                            ChunkingService chunkingService,
                            ChunkStore chunkStore,
                            RagProperties properties) {
        this.parserRegistry = parserRegistry;
        this.mimeTypeDetector = mimeTypeDetector;
        this.chunkingService = chunkingService;
        this.chunkStore = chunkStore;
        this.properties = properties;
    }

    public IngestResult ingest(String filename, byte[] bytes) {
        // ① 校验：空文件 / 大小上限 / 扩展名
        if (bytes == null || bytes.length == 0) {
            throw new IngestionException("文件为空");
        }
        int maxFileSizeMb = properties.getMaxFileSizeMb();
        long maxBytes = (long) maxFileSizeMb * 1024L * 1024L;
        if (bytes.length > maxBytes) {
            throw new IngestionException("文件超过 " + maxFileSizeMb + "MB 限制");
        }

        boolean filenameMissing = filename == null || filename.isBlank();
        String name = filenameMissing ? DEFAULT_FILENAME : filename.strip();
        if (!filenameMissing) {
            String extension = extensionOf(name);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new IngestionException("不支持的扩展名: ." + extension + "，仅支持 " + SUPPORTED_EXTENSIONS);
            }
        }

        // ② 内容哈希 + ③ 同名查重（同 hash 且已有块 → 幂等跳过）
        String contentHash = sha256(bytes);
        Optional<DocumentRecord> existing = chunkStore.findByName(name);
        if (existing.isPresent()) {
            DocumentRecord record = existing.get();
            if (record.contentHash().equals(contentHash) && record.chunkCount() > 0) {
                return new IngestResult(record.docId(), name, record.chunkCount(), true,
                        new IngestResult.Timings(0, 0, 0, 0));
            }
        }
        String docId = existing.map(DocumentRecord::docId).orElseGet(() -> UUID.randomUUID().toString());

        // ④ MIME 探测 + 解析器路由
        long detectStart = System.nanoTime();
        String mimeType = mimeTypeDetector.detect(bytes, name);
        DocumentParser parser;
        try {
            parser = parserRegistry.require(mimeType);
        } catch (UnsupportedDocumentTypeException e) {
            throw new IngestionException("不支持的 MIME 类型: " + mimeType);
        }
        long detectMs = elapsedMillis(detectStart);

        // ⑤ 结构化解析
        long parseStart = System.nanoTime();
        ParsedDocument parsed = parser.parse(bytes, mimeType, Map.of("sourceFile", name));
        List<Block> blocks = parsed.blocks();
        long parseMs = elapsedMillis(parseStart);

        // ⑥ 分块
        long chunkStart = System.nanoTime();
        List<Chunk> chunks = chunkingService.chunk(blocks, name);
        long chunkMs = elapsedMillis(chunkStart);

        // ⑦ 入库：此时解析与分块都已成功，失败则由存储层抛出，不返回成功
        long indexStart = System.nanoTime();
        chunkStore.replaceDocument(
                new DocumentRecord(docId, name, mimeType, bytes.length, contentHash, chunks.size(), Instant.now()),
                chunks);
        long indexMs = elapsedMillis(indexStart);

        return new IngestResult(docId, name, chunks.size(), false,
                new IngestResult.Timings(detectMs, parseMs, chunkMs, indexMs));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
