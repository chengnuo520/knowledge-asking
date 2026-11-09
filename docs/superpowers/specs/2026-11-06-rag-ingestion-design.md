# RAG 文档摄取与检索 — 设计文档

- 日期：2026-11-06
- 目标仓库：`F:\coding demo`
- 参考实现：`F:\raagent\ragent`（Java / Spring Boot 企业级 Agentic RAG 平台）
- 状态：待评审

---

## 0. 一句话

把 `ragent` 里「文档解析 → 结构化 Block → 按预算的 block-aware 分块 → 向量化 → 索引」这条核心链路，移植成一个 Spring Boot + Spring AI 的可运行学习项目：上传文档后可以检索、可以看到每个块为什么被召回，并能用 RAG 问答验证检索质量。

---

## 1. 背景与目标

### 1.1 为什么做

现有 `F:\coding demo` 是一个最小问答 Demo：`ChatController` + `ChatModelFactory`，只有「用户输入 → 模型 → 返回」，没有任何知识库能力。目标是把 `ragent` 中真正有学习价值的文档预处理设计搬过来，而不是搬一整套生产基础设施。

### 1.2 学习目标（按重要性）

1. 理解「结构化解析 → 结构化分块」为什么优于「抽纯文本 → 固定长度滑窗」。
2. 理解 `content` 与 `embeddingText` 分离对检索质量的影响。
3. 理解分块预算（块大小、重叠、表格行数、容忍倍数）如何影响召回。
4. 理解一次完整摄取的五步：identity → parse → chunk → embed → index。
5. 能通过检索调试页和评测集，观察上述决策的实际效果。

### 1.3 成功标准

- 上传 `.md / .txt / .csv / .pdf / .docx` 后，能得到可检索的块。
- 能查看每个块的 `content`、`embeddingText`、章节路径、相似度分数。
- 问「手机退换货期限是多久」时，能召回包含「15 天」的块，并给出带 `[1]` 引用的回答。
- 重复上传同一文件不会重复灌库；重启后数据仍在。
- 用 10~20 条评测问题对比 256 / 512 / 1024 三种块大小的 Hit@K / MRR。
- `mvn test` 在无网络、无外部模型时也能通过（用 FakeEmbeddingModel）。

---

## 2. 非目标

- 不引入 PostgreSQL / pgvector、Redis、MQ、MinerU、Redisson。
- 不做多租户、权限、审计、限流、分布式锁。
- 不做图片 VLM 解析、Excel POI 解析（P2 可选）。
- 不做完整的 DAG 流水线引擎（Fetcher → Parser → Enhancer → Chunker → Enricher → Indexer）；P0 只有一条固定五步链路。
- 不做生产级并发、分片、增量索引调度。
- 不替换现有 `/api/chat`、`/api/vision` 的行为。

### 2.1 Anti-goals（技术上能跑但算失败）

- 为了“像原项目”而引入 PG/Redis/MinerU，导致跑不起来。
- 把分块简化成 Spring AI 的 `TokenTextSplitter`，丢掉 block-aware 的核心。
- 只做向量检索，却没有任何手段观察分块质量（没有调试接口、没有评测）。
- 让 `content` 被 `embeddingText` 的合成前缀污染。

---

## 3. 既定决策（本轮已定，不再讨论）

| 决策 | 选择 | 理由 |
|---|---|---|
| 技术栈 | 保持 Spring Boot 3.3.5 + Java 17 + Spring AI 1.0.3 | 目标项目现状，不引第二套 AI SDK |
| 向量存储 | **自实现 `JsonChunkStore`**（内存 + 本地 JSON 文件），不引 `spring-ai-vector-store` | 五步分离更清楚；`content`/`embeddingText` 分离更直接；持久化可控；余弦相似度本身就是学习点 |
| 模型接入 | 复用现有 `OpenAiApi` 手动构建方式，新增 `EmbeddingModel` Bean | 与 `ChatModelFactory` 一致；不启用 Spring AI 自动配置 |
| 解析库 | Tika 3.2.3（MIME 探测 + PDF/DOCX 文本）+ commonmark 0.22.0（Markdown AST）+ commons-csv 1.14.1 | 都是轻量、成熟、与原项目一致的选择 |
| 分块 | 移植 `ragent` 的 block-aware 分块核心 | 这是最值得学的部分，不简化 |
| 解析档位 | P0 只保留 FAST，砍掉 FAST/FIDELITY 二维 | 没有 MinerU，档位是空概念；保留 MIME 路由即可 |
| 服务分层 | `Controller → IngestionService / RetrievalService / RagService → 纯 Java 领域类` | 领域类不依赖 Spring，方便单测 |
| 测试 | 用 `FakeEmbeddingModel` 做离线测试；真实评测用环境变量开关 | `mvn test` 不应依赖网络 |
| 默认嵌入模型 | `bge-m3`（1024 维，中文效果好）；`nomic-embed-text`（768 维）作为轻量替代 | 语料和评测问题都是中文；模型太弱会把“分块问题”误判成“模型问题” |

---

## 4. 总体架构

```
┌──────────────────────────── 摄取（写路径） ────────────────────────────┐
│                                                                        │
│  POST /api/rag/documents                                               │
│        │                                                               │
│        ▼                                                               │
│  IngestionService                                                      │
│   ① MimeTypeDetector ── Tika ──► mimeType                              │
│   ② ParserRegistry.require(mime) ──► DocumentParser                    │
│        └─► List<Block>（Heading / Paragraph / List / Table / Code）     │
│   ③ ChunkingService                                                    │
│        ├─ HeadingHandler 维护 outlinePath                              │
│        ├─ 各 BlockChunker 产 ChunkDraft                                │
│        ├─ ChunkPacker 按节打包                                         │
│        └─ ChunkAssembler 产 Chunk(content + embeddingText + metadata)  │
│   ④ EmbeddingService.embedAll(embeddingTexts) ──► List<float[]>        │
│   ⑤ ChunkStore.replaceDocument(doc, embeddedChunks)                    │
│        └─ JsonChunkStore：内存 Map + data/chunks.json（原子写）         │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────── 检索（读路径） ────────────────────────────┐
│                                                                        │
│  POST /api/rag/search                    POST /api/rag/chat            │
│        │                                       │                       │
│        ▼                                       ▼                       │
│  RetrievalService.search                RagService.answer              │
│    ├─ embed(query)                      ├─ RetrievalService.search     │
│    ├─ cosine(query, chunks)             ├─ RagPromptBuilder            │
│    └─ topK ScoredChunk                  ├─ ChatModelFactory.getOrCreate│
│                                         └─ reply + references          │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 包结构

```text
com.example.chat
├── ChatController.java                 # 现有，不动
├── ChatModelFactory.java               # 现有，不动
├── ChatConfig.java                     # 现有，不动
├── DemoApplication.java                # 现有，可能需要 @ConfigurationPropertiesScan
└── rag
    ├── config
    │   ├── RagProperties.java          # @ConfigurationProperties(prefix = "rag")
    │   └── RagConfig.java              # EmbeddingModel Bean、ObjectMapper 复用
    ├── parser
    │   ├── DocumentParser.java
    │   ├── ParserRegistry.java
    │   ├── MimeTypeDetector.java
    │   ├── MarkdownDocumentParser.java
    │   ├── PlainTextDocumentParser.java
    │   ├── CsvDocumentParser.java
    │   ├── TikaDocumentParser.java
    │   └── model
    │       ├── Block.java              # sealed interface
    │       ├── HeadingBlock.java
    │       ├── ParagraphBlock.java
    │       ├── ListBlock.java
    │       ├── TableBlock.java
    │       ├── CodeBlock.java
    │       └── ParsedDocument.java
    ├── chunk
    │   ├── ChunkingService.java
    │   ├── ChunkBudget.java
    │   ├── ChunkContext.java
    │   ├── HeadingHandler.java
    │   ├── ChunkDraft.java
    │   ├── Chunk.java
    │   ├── ChunkMetadata.java
    │   ├── ChunkAssembler.java
    │   ├── ChunkPacker.java
    │   ├── BlockChunker.java
    │   ├── HeadingChunker.java
    │   ├── ParagraphChunker.java
    │   ├── ListChunker.java
    │   ├── TableChunker.java
    │   ├── CodeChunker.java
    │   └── TextSplitter.java
    ├── ingest
    │   ├── IngestionService.java
    │   ├── IngestResult.java
    │   ├── DocumentRecord.java
    │   └── store
    │       ├── ChunkStore.java
    │       ├── JsonChunkStore.java
    │       ├── EmbeddedChunk.java
    │       ├── ScoredChunk.java
    │       └── StoreSnapshot.java      # JSON 持久化结构
    ├── retrieve
    │   ├── RetrievalService.java
    │   ├── RagService.java
    │   ├── RagPromptBuilder.java
    │   └── RagAnswer.java
    └── web
        ├── RagDocumentController.java
        ├── RagSearchController.java
        └── RagChatController.java
```

> 命名说明：不要让自研的 `Chunk` / `DocumentRecord` 与 Spring AI 的 `org.springframework.ai.document.Document` 混用。P0 不使用 Spring AI 的 `Document` 与 `VectorStore`。

---

## 6. 数据模型

### 6.1 Block（sealed interface）

```java
public sealed interface Block permits HeadingBlock, ParagraphBlock, ListBlock, TableBlock, CodeBlock {
    // 可选：Provenance（sourceFile、页码）先不做，P1 再加
}

public record HeadingBlock(int level, String text) implements Block {}
public record ParagraphBlock(String text) implements Block {}
public record ListBlock(List<String> items, boolean ordered) implements Block {}
public record TableBlock(List<String> headers, List<List<String>> rows) implements Block {}
public record CodeBlock(String language, String text) implements Block {}
```

### 6.2 ParsedDocument

```java
public record ParsedDocument(List<Block> blocks, Map<String, Object> metadata) {}
```

### 6.3 Chunk

```java
public record Chunk(
        String chunkId,
        int index,
        String content,        // 展示文本，保持原文
        String embeddingText,  // 向量文本，允许补章节前缀 / 表格 KV 渲染
        ChunkMetadata metadata
) {}

public record ChunkMetadata(
        List<String> outlinePath,   // ["保修政策", "手机", "退换货"]
        String sourceFile,
        Map<String, Object> extras
) {}
```

### 6.4 存储模型

```java
public record DocumentRecord(
        String docId,
        String name,          // 原始文件名
        String mimeType,
        long size,
        String contentHash,   // SHA-256
        int chunkCount,
        Instant ingestedAt
) {}

public record EmbeddedChunk(Chunk chunk, float[] vector) {}
public record ScoredChunk(Chunk chunk, double score) {}
```

JSON 文件 `data/chunks.json`：

```json
{
  "version": 1,
  "embeddingModel": "bge-m3",
  "dimension": 1024,
  "documents": [
    {
      "docId": "uuid",
      "name": "保修政策.md",
      "mimeType": "text/markdown",
      "size": 1234,
      "contentHash": "sha256...",
      "chunkCount": 3,
      "ingestedAt": "2026-11-06T10:00:00Z"
    }
  ],
  "chunks": [
    {
      "chunkId": "uuid",
      "docId": "uuid",
      "index": 0,
      "content": "### 退换货\n7 天内可无理由退货。...",
      "embeddingText": "保修政策 / 手机\n### 退换货\n...",
      "outlinePath": ["保修政策", "手机", "退换货"],
      "sourceFile": "保修政策.md",
      "vector": [0.12, -0.03]
    }
  ]
}
```

---

## 7. 解析层

### 7.1 DocumentParser

```java
public interface DocumentParser {
    String parserType();
    boolean supports(String mimeType);
    ParsedDocument parse(byte[] bytes, String mimeType, Map<String, Object> options);
}
```

### 7.2 ParserRegistry

- 构造时收集所有 `DocumentParser`，建立 `精确 MIME → parser` 与 `大类前缀 → parser`（如 `text/*`）两张表。
- 同一键被两个 parser 认领 → 启动失败（不要静默覆盖）。
- `require(mimeType)`：精确优先，再通配，都找不到抛 `UnsupportedDocumentTypeException`；不允许静默回落到 Tika。
- 启动日志打印路由表，方便观察。
- P0 不注册任何通配认领；通配表只保留为扩展点（以后接 HTML 时再用）。`.txt` 与 `.md` 都走精确 MIME。
- P0 不支持解析档位（FAST/FIDELITY）；`spec.parseProfile()` 这类概念不引入。

### 7.3 MimeTypeDetector

- 用 Tika：`new Tika().detect(bytes, filename)`。
- 文件名只用于辅助探测，最终以字节探测为准。
- 探测结果为空或 `application/octet-stream` 时，按扩展名回退；仍无法确定则拒绝。

### 7.4 各解析器

| 解析器 | MIME | 实现 |
|---|---|---|
| `MarkdownDocumentParser` | `text/markdown`、`text/x-markdown`、`text/x-web-markdown` | commonmark AST + `TablesExtension`；Heading/Paragraph/BulletList/OrderedList/FencedCodeBlock/TableBlock |
| `PlainTextDocumentParser` | `text/plain` | 按空行切 `ParagraphBlock` |
| `CsvDocumentParser` | `text/csv`、`application/csv` | commons-csv 读第一条为表头，其余为行，产 `TableBlock` |
| `TikaDocumentParser` | `application/pdf`、`application/msword`、`application/vnd.openxmlformats-officedocument.wordprocessingml.document`、`application/rtf` | Tika `AutoDetectParser` 抽纯文本，按空行切 `ParagraphBlock` |

**归属规则（定死，避免启动期认领冲突）：**

- `text/plain` 的唯一 owner 是 `PlainTextDocumentParser`，Markdown 不认领它。
- Tika **不认领 `text/*` 通配**，只认领上面列出的精确 MIME。P0 不支持 HTML，避免通配把 `.txt`/`.md` 的路由搅乱。
- 精确 MIME 优先于通配；如果两个 parser 认领同一个精确 MIME，启动失败。
- `.md` 走结构解析，`.txt` 走空行段落解析——两种行为不一样，这是有意为之。

**Markdown 解析细节**

- 用 `org.commonmark.parser.Parser.builder().extensions(List.of(TablesExtension.create())).build()`。
- 用一个 `AbstractVisitor` 收集 Block，文本提取用一个递归 helper（处理 Emphasis / Strong / Code / Link 的文本）。
- 列表：`BulletList` / `OrderedList` 收集每项文本为 `ListBlock`，不把列表拆成多个段落。
- 表格：GFM 表格 → `TableBlock(headers, rows)`；表格前一段若是“表头说明”，保留为独立 ParagraphBlock，由 Packer 在打包时把前导语并入表格块（原项目 `pollLeadIn` 的逻辑）。
- 代码块：`FencedCodeBlock` → `CodeBlock(language, literal)`；不要按预算切代码块，除非超过 `toleranceChars`。

**Tika 解析的已知边界**

- PDF / DOCX 只能拿到纯文本，没有标题层级和表格结构，全部变成 `ParagraphBlock`。
- 这是 P0 接受的降级；设计文档不假装它能保留版面。若以后要 PDF 结构化，P2 再引入 MinerU 或 PDF 版面解析。

---

## 8. 分块层

> 这一层是移植重点。请优先阅读 `F:\raagent\ragent` 的对应源码（见第 18 节），按原逻辑改写类型与包名，不要重新发明。

### 8.1 ChunkBudget

```java
public record ChunkBudget(int maxChars, int overlapChars, int rowsPerChunk, int toleranceFactor) {
    public static final int MAX_CHARS_LIMIT = 8192;
    public static final int ROWS_PER_CHUNK_LIMIT = 1000;
    public static final int OVERLAP_DIVISOR = 8;

    public static ChunkBudget defaults() {
        return new ChunkBudget(1024, 128, 50, 3);
    }
    public int toleranceChars() {
        return Math.min(maxChars * toleranceFactor, MAX_CHARS_LIMIT);
    }
}
```

校验规则（构造期直接抛 `IllegalArgumentException`）：

- `maxChars > 0` 且 `<= 8192`
- `0 <= overlapChars < maxChars`
- `rowsPerChunk > 0` 且 `<= 1000`
- `toleranceFactor` 在 `[1, 8]`

P0 支持「整文档单块」哨兵 `maxChars = -1`（可选；若实现，构造 `ChunkBudget.wholeDocument()`，`isWholeDocument()` 判定）。

### 8.2 HeadingHandler

- 维护当前标题路径 `List<String>`。
- 遇到 `HeadingBlock(level, text)`：
  - 移除路径中 `level` 及更深的层级；
  - 若当前层已有标题，替换；否则追加；
  - 得到新路径，供后续 Block 使用。
- 例：`# 保修政策` → `[保修政策]`；`## 手机` → `[保修政策, 手机]`；`### 退换货` → `[保修政策, 手机, 退换货]`；再遇 `## 维修` → `[保修政策, 维修]`。

### 8.3 BlockChunker 与各实现

```java
public interface BlockChunker<T extends Block> {
    Class<T> blockType();
    List<ChunkDraft> chunk(T block, ChunkContext ctx);
}

public record ChunkContext(List<String> outlinePath, ChunkBudget budget) {}
```

- `HeadingChunker`：产出一个 `ChunkDraft(heading=true)`，内容为标题文本（或 `### 标题`）。
- `ParagraphChunker`：先按 `toleranceChars` 调 `TextSplitter.split`；若切出多片，再按 `maxChars` 重切；每片一个 draft。
- `ListChunker`：列表项按预算分组；每组渲染成 `- item` / `1. item`；不要把列表项拆成两半。
- `TableChunker`：按 `rowsPerChunk` 与预算切行；每块都带完整表头（展示文本）；`embeddingBody` 用 `列名: 值` 逐行渲染；单行超预算时整行原子成块。
- `CodeChunker`：代码块整体保留；只有超过 `toleranceChars` 才按行切，且尽量在空行处切。

### 8.4 TextSplitter

移植原项目 `TextSplitter`，保留：

- 边界回溯顺序：换行 → 中文句末 `。！？；` → 英文句末 `. ! ?`；
- `overlapChars` 同时作为回溯最大距离；
- URL 断行修复、CJK 软换行合并；
- 强制推进，避免片段重复或死循环。

### 8.5 ChunkPacker

移植原项目 `ChunkPacker`，核心规则：

1. 按标题把 drafts 切成「节」：标题起一节，标题之前的散块自成一节。
2. 一节总长 `<= toleranceChars()` → 原子节：整节并进当前块，或整节独立成块。
3. 一节 `> toleranceChars()` → 节内贪心切分：
   - 单个 draft 已经是 `piece` 或自身 `>= maxChars` → 原样落块，只把紧邻的前导语捎进去；
   - 否则累加到 `maxChars` 为止。
4. `minChars = maxChars / 4`：落块时如果不足 `minChars`，并回上一块（前提是不超 `toleranceChars`）。
5. 块与块之间不做重叠；重叠只发生在 `TextSplitter` 切超长段落时。
6. 合并多块时：
   - `content` 用 `\n\n` 拼接；
   - `embeddingBody` 逐块取「显式 body 优先，否则回落 content」；
   - `outlinePath` 取各块的公共前缀；
   - `assets` P0 不做。

### 8.6 ChunkAssembler

```java
public static Chunk assemble(int index, ChunkDraft draft, String sourceFile) {
    String content = draft.content();
    String embeddingText = composeEmbeddingText(draft.metadata(), draft.effectiveBody(), content);
    return new Chunk(UUID.randomUUID().toString(), index, content, embeddingText, metadata);
}
```

- `content` 原样保留。
- `embeddingText` = 「正文尚未覆盖的章节路径前缀」+ `body`。
- 取前缀的算法：
  - 路径从根向下；
  - 从路径开头逐级检查，直到某一级文本出现在 `content` 中为止；
  - 只拼「content 里没有的那几级」；如果第一级就出现在 content 中，不拼。
- 例：`outlinePath = [保修政策, 手机, 退换货]`，`content` 含 `### 退换货`，则前缀为 `保修政策 / 手机`。
- 表格的 `embeddingBody` 由 `TableChunker` 提供；段落没有显式 body，回落 `content`。

### 8.7 ChunkingService

```java
public List<Chunk> chunk(List<Block> blocks, ChunkBudget budget, String sourceFile)
```

- `budget.isWholeDocument()` → 全部 Block 渲染成一个 chunk。
- 否则：
  1. 遍历 blocks，维护 `HeadingHandler`；
  2. 对每个 block 查 `BlockChunker`，产出 drafts；
  3. `ChunkPacker.pack(drafts, budget)`；
  4. `ChunkAssembler.assembleAll`。

### 8.8 默认值与调参建议

- 默认 `maxChars=1024, overlap=128, rowsPerChunk=50, toleranceFactor=3`。
- 评测对比建议跑 `256 / 512 / 1024` 三档；重叠按 `maxChars / 8` 等比。
- 1024 不是“越大越好”：块太大会稀释语义；块太小会让同一章节的碎片占满 topK。

---

## 9. 向量与存储

### 9.1 EmbeddingModel

复用现有手动构建方式：

```java
@Bean
public EmbeddingModel embeddingModel(RagProperties props) {
    OpenAiApi api = OpenAiApi.builder()
            .baseUrl(props.getEmbedding().getBaseUrl())
            .apiKey(props.getEmbedding().getApiKey())
            .build();
    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
            .model(props.getEmbedding().getModel())
            .build();
    return OpenAiEmbeddingModel.builder()
            .openAiApi(api)
            .defaultOptions(options)
            .build();
}
```

> 实现前先确认 Spring AI 1.0.3 的 `OpenAiEmbeddingModel` / `OpenAiEmbeddingOptions` builder 方法名；如果与上面不同，按实际 API 调整。不要启用自动配置（`application.yml` 已排除 `OpenAiEmbeddingAutoConfiguration`）。

### 9.2 EmbeddingService

```java
public List<float[]> embedAll(List<String> texts) {
    // 按 batchSize 分批调用 embeddingModel.embed(batch)
    // 逐条校验：vector != null && vector.length == dimension
    // 维度不一致直接抛异常，不静默截断/补零
}
```

- `dimension` 来自 `rag.embedding.dimension`；若配置为 0，用第一次返回的维度推断并记录 WARN。
- 发生维度不一致时，错误信息要包含：模型名、期望维度、实际维度、当前分区（本项目只有一个库，写 `default`）。
- 快照里记录了 `embeddingModel` 与 `dimension`。启动加载时若与当前配置不一致，**直接失败**，提示“嵌入模型/维度与已有索引不一致，请删除 `data/chunks.json` 后重新灌库”。不允许同维度换模型后静默混用——那会得到看起来合理但没有意义的相似度。

### 9.3 ChunkStore

```java
public interface ChunkStore {
    void replaceDocument(DocumentRecord doc, List<EmbeddedChunk> chunks);
    void deleteDocument(String docId);
    List<ScoredChunk> search(float[] queryVector, int topK);
    List<DocumentRecord> listDocuments();
    Optional<DocumentRecord> findByName(String name);
    Optional<DocumentRecord> findById(String docId);
    List<Chunk> listChunks(String docId);
}
```

### 9.4 JsonChunkStore

- 内存：`Map<String, DocumentRecord> documents`、`Map<String, StoredChunk> chunks`。
- 启动 `@PostConstruct`：若 `data/chunks.json` 存在，读入；否则空。
- 写操作采用 **copy-on-write + 原子替换**，保证内存与磁盘不 diverged：
  1. `StoreSnapshot next = current.deepCopy()`；
  2. 在 `next` 上做增删改；
  3. 把 `next` 序列化到 `chunks.json.tmp`；
  4. `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`；
  5. 文件替换成功后，才把 `volatile StoreSnapshot current` 指向 `next`。
  6. 任一步失败：丢弃 `next`，`current` 与磁盘上的旧文件都不变，向上抛异常。
- 读操作直接读 `current`，不加锁（引用替换是原子的）；写操作 `synchronized`。
- `replaceDocument`：
  1. 删除该 docId 的旧 chunks；
  2. 写入新 chunks；
  3. 更新 documents 映射；
  4. 持久化。
- `search`：
  - 对每个 chunk 计算余弦相似度；
  - 按分数降序，取 topK；
  - 空库返回空列表；
  - 分数相同按 docName + chunkIndex 稳定排序。
- 所有公开方法 `synchronized`（Demo 单机足够；注释里写清楚这是学习版的取舍）。
- `data/` 加入 `.gitignore`。

### 9.5 余弦相似度

```java
double cosine(float[] a, float[] b) {
    double dot = 0, na = 0, nb = 0;
    for (int i = 0; i < a.length; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
    return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
}
```

维度不一致时直接抛异常，不跳过。

---

## 10. 摄取流程（IngestionService）

```java
public IngestResult ingest(String filename, byte[] bytes) {
    // 1. 校验大小（默认 20MB）与扩展名白名单：.md/.txt/.csv/.pdf/.docx
    //    先按扩展名拒绝；再用 Tika 探测 MIME，交给 ParserRegistry；Registry 不认领同样拒绝
    // 2. contentHash = SHA-256(bytes)
    // 3. 查 documents：同名 + 同 hash + chunkCount > 0 → skipped=true
    // 4. docId：同名旧文档沿用旧 docId；否则 UUID
    // 5. mimeType = MimeTypeDetector.detect(bytes, filename)
    // 6. blocks = ParserRegistry.require(mimeType).parse(...)
    // 7. chunks = ChunkingService.chunk(blocks, budget, filename)
    // 8. vectors = EmbeddingService.embedAll(chunks.map(embeddingText))
    // 9. store.replaceDocument(record, embeddedChunks)
    // 10. 返回 timings
}
```

### 10.1 IngestResult

```java
public record IngestResult(
        String docId,
        String docName,
        int chunkCount,
        boolean skipped,
        Timings timings
) {
    public record Timings(long detectMs, long parseMs, long chunkMs, long embedMs, long indexMs) {}
}
```

### 10.2 幂等与重灌

- 同名 + 同 hash → 直接返回 `skipped=true`，不重新嵌入。
- 同名 + 不同 hash → 沿用旧 docId，整体替换该文档的 chunks（先删后建）。
- 不同名 + 同内容 → 视为两份文档（文件名是身份的一部分）。
- 解析失败不写 store；文档列表不出现半成品。

---

## 11. 检索与 RAG 问答

### 11.1 RetrievalService

```java
public List<ScoredChunk> search(String query, int topK) {
    float[] q = embeddingService.embedAll(List.of(query)).get(0);
    return chunkStore.search(q, topK);
}
```

- `topK` 默认取 `rag.search.top-k`；接口可覆盖。
- 空查询直接返回空列表。
- 可选 `minScore`：`rag.search.min-score`，默认 0（不过滤）；不要默认设高阈值，否则学习时看不到低分块。

### 11.2 RagService

```java
public RagAnswer answer(String question, String modelId, int topK) {
    List<ScoredChunk> hits = retrievalService.search(question, topK);
    if (hits.isEmpty()) {
        return new RagAnswer("知识库中没有找到相关资料，请先上传文档或换个问法。", List.of());
    }
    String system = promptBuilder.systemPrompt();
    String user = promptBuilder.userPrompt(question, hits);
    ChatModel chatModel = chatModelFactory.getOrCreate(modelId);
    ChatClient client = ChatClient.builder(chatModel).build();
    String reply = client.prompt().system(system).user(user).call().content();
    return new RagAnswer(reply, toReferences(hits));
}
```

- 模型选择复用 `ChatModelFactory`，默认 `remote-deepseek`。
- `ChatClient` 构建失败时回退到 `chatModel.call(...)`；实现者按 1.0.3 实际 API 决定。
- 返回引用：

```java
public record Reference(int index, String docName, List<String> outlinePath, String chunkId, double score) {}
public record RagAnswer(String reply, List<Reference> references) {}
```

---

## 12. 提示词设计

提示词放在 `src/main/resources/prompts/`，由 `RagPromptBuilder` 启动时读取，不要在 Java 里硬编码长文本。

### 12.1 `rag-answer-system.md`

```markdown
你是知识库问答助手。你只能依据【参考资料】回答问题。

规则：
1. 先给结论，再给依据。
2. 每个事实性结论后用 [编号] 标注来源，例如 [1]。
3. 参考资料不足以回答时，明确说“根据现有资料无法确定”，并说明缺少什么信息。
4. 不得使用参考资料之外的知识，不得编造参数、期限、价格、政策。
5. 如果多份资料冲突，指出冲突并分别标注来源。
6. 回答简洁；不要复述整段资料，只引用支撑结论的部分。
```

### 12.2 `rag-context-template.md`

```markdown
【参考资料】
[1] 来源：{docName} | 章节：{outlinePath}
{content}

[2] 来源：{docName} | 章节：{outlinePath}
{content}

【问题】
{question}
```

- `outlinePath` 用 ` / ` 连接；为空时写“无”。
- `content` 是块原文，不是 `embeddingText`。
- 每条参考资料固定编号，回答里的 `[n]` 与此对应。

### 12.3 `chunk-enrich.md`（P2，可选）

```markdown
你是文档整理助手。为下面这段内容生成 1 句不超过 60 字的摘要，以及最多 5 个关键词。
只输出 JSON，不要输出解释：
{"summary": "...", "keywords": ["...", "..."]}

内容：
{content}
```

用途：灌库时把 `summary` 和 `keywords` 拼到 `embeddingText` 前面，再用评测集对比加与不加的 Hit@1。展示文本不变。

### 12.4 提示词调试建议

- 每次改提示词，都跑同一套评测问题，记录 Hit@K 与引用正确率。
- 不要同时改提示词和分块参数，否则无法归因。

---

## 13. REST API

统一错误格式：`{"error": "..."}`，与现有 `ChatController` 一致。

### 13.1 上传并摄取

```http
POST /api/rag/documents
Content-Type: multipart/form-data
file=@保修政策.md
```

响应：

```json
{
  "docId": "uuid",
  "docName": "保修政策.md",
  "chunkCount": 3,
  "skipped": false,
  "timings": {"detectMs": 3, "parseMs": 12, "chunkMs": 4, "embedMs": 800, "indexMs": 2}
}
```

错误：

- 文件为空 / 超过 20MB → 400
- 扩展名不在 `.md/.txt/.csv/.pdf/.docx` → 400，消息列出这五个扩展名
- 扩展名通过但 Tika 探测的 MIME 无解析器认领 → 400，消息包含该 MIME
- 文件名缺失 → 使用 `upload.bin`，随后按 MIME 判定；无法判定则 400

### 13.2 文档列表

```http
GET /api/rag/documents
```

响应：

```json
{"documents":[{"docId":"uuid","name":"保修政策.md","mimeType":"text/markdown","chunkCount":3,"size":1234,"ingestedAt":"2026-11-06T10:00:00Z"}]}
```

### 13.3 删除文档

```http
DELETE /api/rag/documents/{docId}
```

响应：`{"deleted": true}`。

### 13.4 查看文档的块（学习用）

```http
GET /api/rag/documents/{docId}/chunks
```

响应：

```json
{
  "chunks": [
    {
      "chunkId": "uuid",
      "index": 0,
      "outlinePath": ["保修政策", "手机", "退换货"],
      "content": "### 退换货\n7 天内可无理由退货。...",
      "embeddingText": "保修政策 / 手机\n### 退换货\n...",
      "charCount": 210
    }
  ]
}
```

### 13.5 检索调试

```http
POST /api/rag/search
Content-Type: application/json

{"query":"手机退换货期限","topK":5}
```

响应：

```json
{
  "results": [
    {
      "score": 0.83,
      "docName": "保修政策.md",
      "outlinePath": ["保修政策", "手机", "退换货"],
      "chunkIndex": 0,
      "content": "...",
      "chunkId": "uuid"
    }
  ]
}
```

### 13.6 RAG 问答

```http
POST /api/rag/chat
Content-Type: application/json

{"message":"手机退换货期限是多久？","model":"remote-deepseek","topK":5}
```

响应：

```json
{
  "reply": "手机质量问题可 15 天内退换 [1]。",
  "references": [
    {"index":1,"docName":"保修政策.md","outlinePath":["保修政策","手机","退换货"],"chunkId":"uuid","score":0.83}
  ]
}
```

---

## 14. 配置

`application.yml` 追加：

```yaml
rag:
  data-dir: ${RAG_DATA_DIR:data}
  max-file-size-mb: 20
  chunk:
    max-chars: 1024
    overlap-chars: 128
    rows-per-chunk: 50
    tolerance-factor: 3
  search:
    top-k: 5
    min-score: 0.0
  embedding:
    base-url: ${EMBEDDING_BASE_URL:${OLLAMA_BASE_URL:http://localhost:11434}}
    api-key: ${EMBEDDING_API_KEY:ollama}
    model: ${EMBEDDING_MODEL:bge-m3}
    dimension: ${EMBEDDING_DIMENSION:1024}
    batch-size: 16
```

- `RagProperties` 用 `@ConfigurationProperties(prefix = "rag")`。
- 在 `DemoApplication` 或 `RagConfig` 上加 `@EnableConfigurationProperties(RagProperties.class)`。
- `.env.example` 增加 `EMBEDDING_*` 说明；不要提交真实 key。
- README 增加：`ollama pull nomic-embed-text`（或换用其它 OpenAI 兼容嵌入服务并改 `EMBEDDING_DIMENSION`）。

---

## 15. 依赖（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-client-chat</artifactId>
</dependency>

<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>3.2.3</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>3.2.3</version>
</dependency>

<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.22.0</version>
</dependency>

<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-csv</artifactId>
    <version>1.14.1</version>
</dependency>
```

- 不引 Lombok；用 Java records。
- 不引 `spring-ai-vector-store`（P0 自实现 `JsonChunkStore`）。
- `spring-ai-client-chat` 若已被 `spring-ai-starter-model-openai` 传递引入，可省略显式声明；实现时用 `mvn dependency:tree` 确认。

---

## 16. UI（P1）

在现有 `static/index.html` 上加一个「知识库」页签，保持原生 JS，无构建依赖：

1. **上传区**：文件选择 + 上传按钮；显示 `chunkCount`、`skipped`、各阶段耗时。
2. **文档列表**：名称、块数、大小、上传时间；删除按钮。
3. **块预览**：点文档展开，显示每块的 `content` 与 `embeddingText`（两个 tab），以及 `outlinePath`。
4. **检索调试**：输入 query + topK，展示分数、来源、章节路径、`content`。
5. **问答开关**：聊天区增加「使用知识库」复选框；勾选后走 `/api/rag/chat`，答案下方展示引用列表。

样式沿用现有页面，不引入前端框架。

---

## 17. 评测（P1）

### 17.1 评测集

`src/test/resources/eval/questions.jsonl`（或项目根 `eval/questions.jsonl`）：

```jsonl
{"question":"手机退换货期限是多久？","expectedDoc":"保修政策.md","expectedKeywords":["15 天"]}
{"question":"无理由退货几天内可以办理？","expectedDoc":"保修政策.md","expectedKeywords":["7 天"]}
```

- 10~20 条即可；问题要能明确对应到某个文档。
- `expectedKeywords` 用包含匹配，不要求完全相等。

### 17.2 指标

- **Hit@K**：`expectedDoc` 是否出现在 topK 结果中。
- **MRR**：第一个命中 `expectedDoc` 的排名的倒数，未命中为 0。
- **Keyword Hit**：topK 结果合并后是否包含任一/全部 `expectedKeywords`。

### 17.3 运行方式

- 新增 `RagEvalTest`（JUnit 5）：
  - 用 `@EnabledIfEnvironmentVariable(named = "RAG_EVAL", matches = "true")` 控制是否跑真实嵌入；
  - 对 `[256, 512, 1024]` 三种预算分别建临时 `JsonChunkStore`，灌入 `src/test/resources/docs/` 下的固定文档；
  - 跑评测集，输出 markdown 表格到控制台；
  - 不断言具体分数（嵌入模型会变），只断言「1024 档 Hit@3 >= 256 档」这类方向性结论可选，默认只打印。
- 默认 `mvn test` 不跑真实嵌入；单元测试用 `FakeEmbeddingModel`。

### 17.4 FakeEmbeddingModel

```java
public class FakeEmbeddingModel implements EmbeddingModel {
    // 词法哈希向量：文本 -> 字符 unigram + 字符 bigram
    // 每个 token 哈希到固定桶（建议 256），计频后做 L2 归一化
    // 目的：同一文本稳定；"15 天" 与含 "15 天" 的块有词法重叠
}
```

- 不能只做 SHA-256：哈希向量之间没有词法关系，`search("15 天")` 无法命中含 “15 天” 的块，离线测试就失去意义。
- 词法哈希让离线测试可以验证“关键词召回”和“排序”两件事；语义质量仍由真实嵌入的 `RagEvalTest` 验证。
- 用途：测试解析、分块、存储、检索链路；不测试语义质量。

---

## 18. 实施阶段

### P0：核心链路（必须先完成）

| # | 任务 | 产出 | 验收 |
|---|---|---|---|
| 1 | 加依赖、`RagProperties`、`EmbeddingModel` Bean | `pom.xml`、`RagConfig` | 应用启动；`/actuator` 不需要；Bean 可注入 |
| 2 | Block 模型 + 4 个解析器 + Registry | `parser/` | `.md/.txt/.csv/.pdf/.docx` 能解析；未知 MIME 抛错 |
| 3 | 分块核心（Budget/HeadingHandler/Chunkers/Packer/Assembler/TextSplitter） | `chunk/` | 单测覆盖：短文档合并、长节切分、表格保整、最小块合并 |
| 4 | `JsonChunkStore` + `IngestionService` | `ingest/` | 上传→chunkCount>0；重启后仍在；重复上传 skipped |
| 5 | `RetrievalService` + `RagService` + 提示词 | `retrieve/`、`prompts/` | 检索返回 topK；RAG 回答带 `[n]` 引用 |
| 6 | REST Controller | `web/` | 13 节的 6 个接口可用 |
| 7 | 单测 + 固定测试文档 | `src/test/` | `mvn test` 离线通过 |
| 8 | README 更新 | `README.md` | 修正 LangChain4j 描述；补 RAG 使用步骤 |

### P1：可观察性与评测

| # | 任务 | 产出 | 验收 |
|---|---|---|---|
| 9 | 知识库 UI | `index.html` | 上传、列表、块预览、检索调试可用 |
| 10 | 评测集 + `RagEvalTest` | `eval/questions.jsonl`、测试 | 输出 256/512/1024 对比表 |
| 11 | Docker 数据卷 | `docker-compose.yml`、`.env.example` | 重启容器数据不丢 |

### P2：可选扩展

- 向量 + 关键词混合检索 + RRF。
- `chunk-enrich` 提示词 + 加/不加的 A/B 评测。
- `PgVectorStore` / pgvector 适配器（实现同一个 `ChunkStore` 接口）。
- Excel POI、图片 VLM。
- 多轮追问改写。

---

## 19. 测试与验收

### 19.1 单元测试

| 测试 | 覆盖 |
|---|---|
| `MarkdownDocumentParserTest` | 标题层级、列表、GFM 表格、代码块 |
| `ParserRegistryTest` | 精确 MIME、通配、未知 MIME 显式抛错、重复认领启动失败 |
| `TextSplitterTest` | 中文句末、英文句末、换行回溯、重叠、URL 不断行、CJK 软换行 |
| `ChunkPackerTest` | 短节合并、长节切分、原子表、最小块并回、容忍上限 |
| `ChunkAssemblerTest` | 章节前缀只补缺失部分；表格 KV；content 不被污染 |
| `JsonChunkStoreTest` | 增删改查、持久化重载、余弦排序、维度不一致抛错 |
| `IngestionServiceTest` | 同名同 hash 跳过、同名不同 hash 替换、未知 MIME 拒绝 |

### 19.2 集成测试

- `RagPipelineTest`：用 `FakeEmbeddingModel`（词法哈希）+ 固定 md/csv 文档，断言：
  - 上传后 `chunkCount > 0`；
  - `search("15 天")` 返回的块包含 `15 天`；
  - `outlinePath` 包含 `退换货`；
  - 删除文档后检索结果为空。
  - 这些断言能成立，前提是 Fake 用词法哈希而不是 SHA-256；它验证的是“分块 + 存储 + 检索”的链路，不是语义质量。
- `RagEvalTest`：真实嵌入，`RAG_EVAL=true` 时才跑。

### 19.3 手动验收（真实模型）

1. `ollama pull bge-m3`（1024 维；轻量替代 `nomic-embed-text` 是 768 维，换用时同步改 `EMBEDDING_MODEL` 与 `EMBEDDING_DIMENSION`）。
2. 启动应用，上传 `保修政策.md`。
3. `GET /api/rag/documents/{docId}/chunks`：确认 `content` 与 `embeddingText` 不同。
4. `POST /api/rag/search`：确认 top1 是退换货块，`outlinePath` 正确。
5. `POST /api/rag/chat`：确认回答包含 `[1]`，且 `references` 指向该块。
6. 重启应用，重复 3~5，确认数据仍在。
7. 再上传同一文件，确认 `skipped=true`。

---

## 20. 风险与边界

| 风险 | 影响 | 处理 |
|---|---|---|
| PDF/DOCX 经 Tika 只有纯文本，无标题/表格结构 | 分块质量不如 Markdown | P0 接受；README 写清楚；P2 再引入版面解析 |
| Ollama 未拉取 `bge-m3` | 启动后第一次上传失败 | README + 错误信息明确提示 `ollama pull bge-m3` |
| 换嵌入模型但维度相同 | 新旧向量静默混用，相似度失真 | 快照记录 `embeddingModel` + `dimension`，启动不一致直接失败 |
| 嵌入服务不支持 OpenAI 兼容 `/v1/embeddings` | 无法生成向量 | 只承诺 OpenAI 兼容端点；README 给出 Ollama 示例 |
| JSON 单文件持久化随块数增长变慢 | 大文档上传慢 | Demo 限制 20MB；注释说明生产应换 pgvector |
| 中文分词/句末标点遗漏 | 切点不理想 | 单测覆盖常见标点；先保证不切在 URL/数字中间 |
| Spring AI 1.0.3 API 与文档示例有差异 | 编译失败 | 实现时以本地 jar 的 API 为准；本节给的是目标形态 |
| 同名文件内容变化导致 docId 复用 | 旧引用失效 | 重灌时整体替换；引用以 chunkId 为准，客户端不缓存 |
| 并发上传同一文件 | 数据竞争 | `JsonChunkStore` 方法同步；Demo 不追求多用户并发 |

---

## 21. 源码参考（从 ragent 移植）

| 目标类 | ragent 源码 |
|---|---|
| `TextSplitter` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\text\TextSplitter.java` |
| `ChunkBudget` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\model\ChunkBudget.java` |
| `ChunkPacker` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\ChunkPacker.java` |
| `ChunkAssembler` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\model\ChunkAssembler.java` |
| `HeadingHandler` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\HeadingHandler.java` |
| `ParagraphChunker` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\ParagraphChunker.java` |
| `TableChunker` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\TableChunker.java` |
| `ListChunker` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\ListChunker.java` |
| `CodeChunker` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\blockaware\CodeChunker.java` |
| `ChunkingService` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\ChunkingService.java` |
| `MarkdownDocumentParser` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\parser\MarkdownDocumentParser.java` |
| `CsvDocumentParser` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\parser\CsvDocumentParser.java` |
| `TikaDocumentParser` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\parser\TikaDocumentParser.java` |
| `MimeTypeDetector` | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\parser\mime\MimeTypeDetector.java` |
| `DefaultIngestionKernel`（五步骨架） | `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\ingest\DefaultIngestionKernel.java` |

移植原则：

- 保留算法与不变量（预算、边界、章节前缀、表格 KV、最小块合并）。
- 去掉 Spring 注解、MyBatis、审计、缓存、多租户。
- 把 `ChunkMetadata` 的 assets / provenance 先简化为 `sourceFile`。
- 不要照搬 `HtmlTableChunker`、`ImageChunker`、`MinerU`。

---

## 22. 留给实现者决定的事

1. `OpenAiEmbeddingModel` 在 Spring AI 1.0.3 的确切 builder API（以本地 jar 为准）。
2. `RagService` 用 `ChatClient` 还是 `ChatModel.call(Prompt)`；两者选更稳的一个。
3. 是否在 P0 就支持「整文档单块」哨兵；建议支持，成本很低。
4. CSV 的编码探测（UTF-8 BOM / GBK）；P0 先支持 UTF-8，遇到乱码再补。
5. 测试用 PDF fixture 是否提交（二进制文件）；建议提交一个 1 页小 PDF，或用 Markdown 测试代替。
6. UI 的具体布局与样式；保持原生 JS、不引框架即可。

---

## 23. Definition of Done

- [ ] `mvn test` 离线通过。
- [ ] 离线测试能验证关键词召回（FakeEmbeddingModel 词法哈希），而不是只验证“能跑通”。
- [ ] 修改 `EMBEDDING_MODEL` 后启动，若与 `data/chunks.json` 记录不一致，应用明确失败并提示重新灌库。
- [ ] 启动应用，上传 `.md / .txt / .csv / .pdf / .docx` 各一份，均能检索。
- [ ] 块预览能看到 `content` 与 `embeddingText` 的差异。
- [ ] 相同文件重复上传返回 `skipped=true`。
- [ ] 重启后文档与检索结果仍在。
- [ ] RAG 回答带 `[n]` 引用，引用能对应到具体块。
- [ ] 评测输出 256/512/1024 三档对比表。
- [ ] README 更新：技术栈改为 Spring AI、补 RAG 配置与运行步骤。
- [ ] `.env.example` 补嵌入模型变量；不提交真实 key。
