# Tasks

- [x] Task 1: 依赖、配置与数据库基础设施
  - [x] SubTask 1.1: `pom.xml` 引入 `tika-core:3.2.3`、`tika-parsers-standard-package:3.2.3`、`commonmark:0.22.0`、`commonmark-ext-gfm-tables:0.22.0`、`commons-csv:1.14.1`、`spring-boot-starter-jdbc`、`org.postgresql:postgresql`（runtime）
  - [x] SubTask 1.2: `application.yml` 增加 `rag.max-file-size-mb`、`rag.chunk.*`、`spring.datasource.*`（`RAG_DB_URL/RAG_DB_USERNAME/RAG_DB_PASSWORD` 可覆盖）、multipart 上限、`spring.sql.init.mode=always`
  - [x] SubTask 1.3: 新增 `RagProperties`（`@ConfigurationProperties(prefix="rag")`），在配置类上 `@EnableConfigurationProperties`；只包含本轮用到的字段，不提前加 search/embedding 配置
  - [x] SubTask 1.4: 新增 `src/main/resources/schema.sql`：`CREATE EXTENSION IF NOT EXISTS vector`、`rag_documents`、`rag_chunks`（含 `embedding vector(1024)` 空列）、唯一约束与 `doc_id` 索引
  - [x] SubTask 1.5: `docker-compose.yml` 增加 `postgres` 服务（`pgvector/pgvector:pg16`，端口 5432，具名数据卷），应用服务补 `RAG_DB_*` 环境变量；`.env.example` 增加数据库变量
  - [x] SubTask 1.6: `mvn compile` 通过，`mvn dependency:tree` 确认 Tika / commonmark / JDBC / PG 驱动无冲突

- [x] Task 2: 解析层
  - [x] SubTask 2.1: 新增 `rag/parser/model`：`Block`（sealed interface）与 `HeadingBlock / ParagraphBlock / ListBlock / TableBlock / CodeBlock`、`ParsedDocument`
  - [x] SubTask 2.2: 新增 `DocumentParser` 接口、`ParserRegistry`（精确 MIME 路由、重复认领启动失败、`require()` 未知 MIME 抛 `UnsupportedDocumentTypeException`、启动打印路由表）
  - [x] SubTask 2.3: 新增 `MimeTypeDetector`：Tika 探测 + 已知扩展名回退（`.md→text/markdown`、`.csv→text/csv`、`.txt→text/plain`、`.pdf→application/pdf`、`.docx→...wordprocessingml.document`），具体 MIME 优先于回退
  - [x] SubTask 2.4: 新增 `MarkdownDocumentParser`（commonmark + GFM 表格）、`PlainTextDocumentParser`、`CsvDocumentParser`（commons-csv）、`TikaDocumentParser`（PDF/DOC/DOCX/RTF）
  - [x] SubTask 2.5: 新增 `MarkdownDocumentParserTest`、`CsvDocumentParserTest`、`PlainTextDocumentParserTest`、`ParserRegistryTest`；测试夹具放 `src/test/resources/docs/`（`.md/.txt/.csv`，尽量补 1 页 `.pdf` 与最小 `.docx`）

- [x] Task 3: 分块层（参照 ragent 源码移植，保留算法与不变量）
  - [x] SubTask 3.1: `ChunkBudget`：默认 `1024/128/50/3`、构造期校验、`toleranceChars()`
  - [x] SubTask 3.2: `HeadingHandler`、`ChunkContext`、`ChunkDraft`、`Chunk`、`ChunkMetadata`
  - [x] SubTask 3.3: 从 `F:\raagent\ragent\rag\src\main\java\com\nageoffer\ai\ragent\core\chunk\text\TextSplitter.java` 移植 `TextSplitter`
  - [x] SubTask 3.4: `BlockChunker` 接口与 `HeadingChunker / ParagraphChunker / ListChunker / TableChunker / CodeChunker`
  - [x] SubTask 3.5: 从 `...\core\chunk\blockaware\ChunkPacker.java` 移植 `ChunkPacker`（分节、原子节、贪心切分、`maxChars/4` 并回、无块间重叠）
  - [x] SubTask 3.6: 从 `...\core\chunk\model\ChunkAssembler.java` 移植 `ChunkAssembler`（`content` 原样、章节前缀只补 `content` 中缺失的层级、表格 KV body）
  - [x] SubTask 3.7: `ChunkingService` 串联 HeadingHandler → Chunker → Packer → Assembler；补 `TextSplitterTest`、`ChunkPackerTest`、`ChunkAssemblerTest`、`ChunkingServiceTest`

- [x] Task 4: 存储与摄取
  - [x] SubTask 4.1: 新增 `ChunkStore` 接口：`replaceDocument / deleteDocument / listDocuments / findByName / findById / listChunks`（不提前加 search）
  - [x] SubTask 4.2: 新增 `JdbcChunkStore`：`JdbcTemplate` 实现，`replaceDocument` 与 `deleteDocument` 用事务；`outline_path` 用 `text[]`；`embedding` 列本轮写 `NULL`
  - [x] SubTask 4.3: 新增 `DocumentRecord`、`IngestResult`（`detectMs/parseMs/chunkMs/indexMs`）
  - [x] SubTask 4.4: 新增 `IngestionService`：扩展名/大小校验、SHA-256、同名同 hash 跳过、同名不同 hash 沿用 `docId` 替换、未知 MIME 拒绝、失败不落库
  - [x] SubTask 4.5: 新增测试专用 `InMemoryChunkStore` 与 `IngestionServiceTest`（覆盖跳过/替换/不同名同内容/未知 MIME/超限）；新增 `JdbcChunkStoreTest`，用 `@EnabledIfEnvironmentVariable(named="RAG_DB_TEST", matches="true")` 控制

- [x] Task 5: 文档管理 REST API
  - [x] SubTask 5.1: 新增 `RagDocumentController`：`POST /api/rag/documents`、`GET /api/rag/documents`、`GET /api/rag/documents/{id}/chunks`、`DELETE /api/rag/documents/{docId}`
  - [x] SubTask 5.2: 统一错误结构 `{"error":"..."}`；404 处理文档不存在；`MaxUploadSizeExceededException` 返回 400 且提示 20MB 上限
  - [x] SubTask 5.3: 确认控制器不依赖 Spring AI/AgentScope，仅依赖 `IngestionService` 与 `ChunkStore`；编译通过

- [x] Task 6: 离线测试与构建验证
  - [x] SubTask 6.1: `mvn test`（首次联网拉依赖）通过
  - [x] SubTask 6.2: `mvn -o test` 在无网络、无 PostgreSQL 时通过（`JdbcChunkStoreTest` 自动跳过）
  - [x] SubTask 6.3: 确认现有 `ChatModelFactoryTest`、`AgentMessageTest` 仍通过，未被新依赖影响

- [ ] Task 7: 学习文档与工程文件更新
  - [ ] SubTask 7.1: 新增 `docs/04-文档处理链路.md`：按功能点写「要解决的问题 / 具体步骤 / 验收逻辑」；覆盖上传校验、MIME 路由、五类解析、Block 模型、分块预算、标题路径、各 Chunker、TextSplitter、ChunkPacker、ChunkAssembler、PostgreSQL 入库、幂等重灌、4 个 REST 接口；验收给出 curl 命令、预期 JSON/日志
  - [ ] SubTask 7.2: 文档明确本轮不做向量化，第二个功能用 DashScope `text-embedding-v4`（1024 维）回填 `embedding` 列并实现检索
  - [ ] SubTask 7.3: 更新 `README.md`（功能、技术栈、项目结构、环境变量、PostgreSQL 启动步骤、文档接口、docs 列表）
  - [ ] SubTask 7.4: 更新 `.env.example` 与 `docker-compose.yml` 说明，确保不提交真实密钥

- [ ] Task 8: 端到端验收（需要 PostgreSQL + pgvector）
  - [ ] SubTask 8.1: 启动数据库（`docker compose up -d postgres` 或本机 Postgres 并确认 `vector` 扩展）
  - [ ] SubTask 8.2: 启动应用，上传 `.md/.txt/.csv` 夹具，能返回 `chunkCount > 0`
  - [ ] SubTask 8.3: 上传 `.pdf/.docx` 夹具（若夹具不可用则用用户提供的文件），确认经 Tika 解析后入库
  - [ ] SubTask 8.4: `GET /api/rag/documents/{docId}/chunks`，确认 `content` 与 `embeddingText` 有差异且 `outlinePath` 正确
  - [ ] SubTask 8.5: 重复上传同一文件，确认 `skipped=true`；修改内容后重传，确认沿用 `docId` 且块被替换
  - [ ] SubTask 8.6: 重启应用，确认文档与块仍在；删除文档后确认级联删除
  - [ ] SubTask 8.7: 确认 `/api/models`、`/api/chat`、`/api/vision` 行为不变

# Task Dependencies

- Task 2 依赖 Task 1
- Task 3 依赖 Task 2（Block 模型）
- Task 4 依赖 Task 2、Task 3
- Task 5 依赖 Task 4
- Task 6 依赖 Task 2、Task 3、Task 4、Task 5
- Task 7 依赖 Task 5（接口契约确定后可开始，内容需与 Task 4 实现一致）
- Task 8 依赖 Task 5、Task 6，且需要 PostgreSQL + pgvector 可用
