# 文档处理链路（解析 + 分块 + 入库）Spec

## Why

现有项目只有 `/api/chat`、`/api/vision`，没有知识库能力。按 `docs/superpowers/specs/2026-11-06-rag-ingestion-design.md` 的 block-aware 思路，先完成**可观察的文档处理链路**：上传 → MIME 探测 → 结构化解析 → block-aware 分块 → PostgreSQL 入库 → 块预览。这样可以在接嵌入服务之前，先确认解析结构与分块质量是否达标。

向量化（DashScope `text-embedding-v4`）、相似度检索与 RAG 问答属于第二个功能（知识库检索），本轮只保留 `embedding vector(1024)` 空列，不写入向量。

## What Changes

- 新增 `com.example.chat.rag` 包，分层为 `config / parser / chunk / ingest / web`，领域类不依赖 Spring，便于离线单测
- `pom.xml` 增加 Tika 3.2.3、commonmark 0.22.0、commons-csv 1.14.1、`spring-boot-starter-jdbc`、PostgreSQL 驱动
- `application.yml` 增加 `rag.*`、`spring.datasource.*`、multipart 上限与 SQL 初始化配置
- 新增 `src/main/resources/schema.sql`：`vector` 扩展、`rag_documents`、`rag_chunks` 表与索引
- 新增 4 个文档接口：上传摄取、文档列表、块预览、删除文档
- `docker-compose.yml` 增加 `pgvector/pgvector:pg16` 的 `postgres` 服务；`.env.example` 增加数据库变量
- 新增学习文档 `docs/04-文档处理链路.md`，并在 README 中补充知识库使用说明
- 不改动现有 `/api/chat`、`/api/vision`、`/api/models`

本轮非目标：

- 不做嵌入调用、向量写入、相似度检索、RAG 问答（第二个功能）
- 不做知识库 UI、评测集、重排序、混合检索
- 不做图片 VLM、Excel POI、MinerU 版面解析
- 不做多租户、权限、审计、限流、分布式锁
- 不引入 Spring AI、JPA、Flyway、Redis、MQ
- 不做「整文档单块」哨兵、`minScore` 过滤、`chunk-enrich` 摘要

## Impact

- Affected specs：文档解析能力、block-aware 分块能力、知识库存储、文档管理 REST API
- Affected code：
  - `pom.xml`
  - `src/main/resources/application.yml`
  - `src/main/resources/schema.sql`（新增）
  - `src/main/java/com/example/chat/rag/**`（新增）
  - `docker-compose.yml`、`.env.example`、`README.md`
  - `docs/04-文档处理链路.md`（新增）
- 不变：`ChatController.java`、`ChatModelFactory.java`、`ChatConfig.java`、`ModelDefinition.java`、`static/index.html`

运行前置：PostgreSQL 5432 可用，且已安装 `vector` 扩展。推荐用新增的 `docker compose up -d postgres`；使用本机 Postgres 时需手动执行 `CREATE EXTENSION IF NOT EXISTS vector;`。数据库不可用时应用启动失败，并在日志中给出明确原因。

## ADDED Requirements

### Requirement: 文档上传校验

系统 SHALL 在上传入口校验文件，校验失败返回 HTTP 400 与统一错误结构 `{"error":"..."}`，不得进入解析与入库。

#### Scenario: 空文件

- **WHEN** `POST /api/rag/documents` 的 `file` 为空
- **THEN** 返回 400，`{"error":"文件为空"}`

#### Scenario: 扩展名不支持

- **WHEN** 文件扩展名不在 `.md / .txt / .csv / .pdf / .docx`
- **THEN** 返回 400，错误信息列出这五种扩展名

#### Scenario: 文件超过大小限制

- **WHEN** 文件大小超过 `rag.max-file-size-mb`（默认 20MB）
- **THEN** 返回 400，错误信息包含 `20MB` 上限

#### Scenario: 文件名缺失

- **WHEN** 上传文件没有文件名
- **THEN** 使用 `upload.bin` 继续处理；若仍无法判定 MIME，返回 400

### Requirement: MIME 探测与解析器路由

系统 SHALL 使用 Tika 的 `detect(bytes, filename)` 探测 MIME，并按「已知扩展名回退 + 精确 MIME 路由」选择解析器。任何解析器都不得静默回落。

#### Scenario: Markdown 被 Tika 识别为纯文本

- **WHEN** `.md` 文件被 Tika 探测为 `text/plain`、`application/octet-stream` 或空
- **THEN** 使用扩展名映射 `text/markdown`，交给 `MarkdownDocumentParser`

#### Scenario: CSV 被 Tika 识别为纯文本

- **WHEN** `.csv` 文件被 Tika 探测为 `text/plain`
- **THEN** 使用扩展名映射 `text/csv`，交给 `CsvDocumentParser`

#### Scenario: 字节内容与扩展名冲突

- **WHEN** `.md` 文件实际是 PDF，Tika 探测到 `application/pdf` 且已有解析器认领
- **THEN** 以探测结果为准，交给 `TikaDocumentParser`

#### Scenario: 无解析器认领

- **WHEN** 探测到的 MIME 没有任何解析器认领
- **THEN** 返回 400，错误信息包含该 MIME

#### Scenario: 启动期路由冲突

- **WHEN** 两个解析器认领同一个精确 MIME
- **THEN** 应用启动失败并说明冲突的 MIME

#### Scenario: 启动日志可观察

- **WHEN** 应用启动成功
- **THEN** 日志打印「MIME → 解析器」路由表

### Requirement: 结构化解析

系统 SHALL 把文档解析为不可变 `Block` 列表，Block 类型为 `Heading / Paragraph / List / Table / Code`，并用 `ParsedDocument(blocks, metadata)` 承载。

#### Scenario: Markdown 结构解析

- **WHEN** 解析 `.md` 文件
- **THEN** 标题生成 `HeadingBlock(level, text)`，列表生成 `ListBlock(items, ordered)`，GFM 表格生成 `TableBlock(headers, rows)`，围栏代码生成 `CodeBlock(language, text)`

#### Scenario: 纯文本解析

- **WHEN** 解析 `.txt` 文件
- **THEN** 按空行切分为 `ParagraphBlock`

#### Scenario: CSV 解析

- **WHEN** 解析 `.csv` 文件
- **THEN** 第一条记录作为 `headers`，其余记录作为 `rows`，产出一个 `TableBlock`

#### Scenario: PDF / DOCX 降级解析

- **WHEN** 解析 `.pdf`、`.doc`、`.docx`、`.rtf`
- **THEN** 使用 Tika 抽取纯文本，按空行切分为 `ParagraphBlock`；不承诺保留标题层级与表格结构

### Requirement: Block-aware 分块

系统 SHALL 按 `ChunkBudget(maxChars, overlapChars, rowsPerChunk, toleranceFactor)` 对 Block 进行结构感知分块，产出 `Chunk(chunkId, index, content, embeddingText, metadata)`。

默认预算为 `1024 / 128 / 50 / 3`。构造期校验：`maxChars` 在 `1..8192`、`0 <= overlapChars < maxChars`、`rowsPerChunk` 在 `1..1000`、`toleranceFactor` 在 `1..8`，非法配置抛 `IllegalArgumentException`。`toleranceChars() = min(maxChars * toleranceFactor, 8192)`。

#### Scenario: 标题路径维护

- **WHEN** 依次遇到 `# 保修政策`、`## 手机`、`### 退换货`、`## 维修`
- **THEN** 对应 `outlinePath` 依次为 `[保修政策]`、`[保修政策, 手机]`、`[保修政策, 手机, 退换货]`、`[保修政策, 维修]`

#### Scenario: 章节前缀只补缺失部分

- **WHEN** 块 `outlinePath=[保修政策, 手机, 退换货]`，`content` 已包含 `### 退换货`
- **THEN** `embeddingText` 之前的章节前缀为 `保修政策 / 手机`，不重复写入 `退换货`
- **AND** 若 `content` 已包含第一级标题，则不拼任何前缀

#### Scenario: 展示文本不被污染

- **WHEN** 任意块被组装
- **THEN** `content` 保持原文，不包含为向量化补的章节前缀
- **AND** 前缀只出现在 `embeddingText`

#### Scenario: 表格保留表头

- **WHEN** `TableBlock` 按 `rowsPerChunk` 或预算切分
- **THEN** 每个块 `content` 都带完整表头
- **AND** `embeddingText` 用 `列名: 值` 逐行渲染
- **AND** 单行超过预算时整行原子成块

#### Scenario: 长段落切分

- **WHEN** 段落超过 `toleranceChars()`
- **THEN** 优先在换行、中文句末 `。！？；`、英文句末 `. ! ?` 处切分
- **AND** 相邻片段带 `overlapChars` 重叠，不切断 URL，不产生死循环

#### Scenario: 短节合并与最小块并回

- **WHEN** 一节总长不超过 `toleranceChars()`
- **THEN** 整节合并进当前块或独立成块
- **AND** 落块时若不足 `maxChars / 4`，在不超 `toleranceChars()` 的前提下并回上一块

#### Scenario: 代码块原子性

- **WHEN** 代码块不超过 `toleranceChars()`
- **THEN** 整块保留，不按预算切分；仅超过容忍上限时按行切分

### Requirement: PostgreSQL + pgvector 持久化

系统 SHALL 用 PostgreSQL 持久化文档与块，并在启动时通过 `schema.sql` 幂等初始化结构：

- `rag_documents(doc_id uuid pk, name text unique, mime_type text, size bigint, content_hash text, chunk_count int, ingested_at timestamptz)`
- `rag_chunks(chunk_id uuid pk, doc_id uuid fk on delete cascade, chunk_index int, content text, embedding_text text, outline_path text[], source_file text, embedding vector(1024) null)`
- `rag_documents` 以 `name` 唯一；`rag_chunks(doc_id, chunk_index)` 唯一；`doc_id` 建索引
- `embedding` 列本轮保持 `NULL`，供第二个功能回填

#### Scenario: 重启后数据仍在

- **WHEN** 应用重启
- **THEN** `GET /api/rag/documents` 仍返回重启前已入库的文档

#### Scenario: 替换文档

- **WHEN** 同名文档内容变化后重新上传
- **THEN** 沿用旧 `docId`，删除该文档全部旧块后写入新块，整个过程在一个事务内完成

#### Scenario: 删除文档

- **WHEN** 删除某文档
- **THEN** 该文档的块被级联删除，其他文档不受影响

#### Scenario: 数据库不可用

- **WHEN** 启动时无法连接 PostgreSQL
- **THEN** 应用启动失败，日志明确提示检查数据库连接与 `vector` 扩展

### Requirement: 摄取编排与幂等

系统 SHALL 按固定顺序编排：校验 → SHA-256 内容哈希 → 查重 → MIME 探测 → 解析 → 分块 → 入库，并返回各阶段耗时。

#### Scenario: 同名同内容重复上传

- **WHEN** 同名文件内容哈希与已入库记录一致且 `chunkCount > 0`
- **THEN** 返回 `skipped=true`，不重新解析、不重新写库

#### Scenario: 同名不同内容

- **WHEN** 同名文件内容哈希变化
- **THEN** 返回 `skipped=false`，沿用旧 `docId` 并整体替换块

#### Scenario: 不同名同内容

- **WHEN** 两个不同文件名但内容相同
- **THEN** 视为两份独立文档

#### Scenario: 解析失败不落库

- **WHEN** 解析或分块阶段抛错
- **THEN** 返回 400/500 错误，`rag_documents` 与 `rag_chunks` 均不出现该文档的半成品记录

#### Scenario: 返回耗时

- **WHEN** 上传成功
- **THEN** 返回 `timings.detectMs / parseMs / chunkMs / indexMs`

### Requirement: 文档管理 REST API

系统 SHALL 提供以下接口，成功响应为 JSON，错误统一为 `{"error":"..."}`。

#### Scenario: 上传并摄取

- **WHEN** `POST /api/rag/documents`（multipart，字段 `file`）
- **THEN** 返回 `{"docId","docName","chunkCount","skipped","timings":{...}}`

#### Scenario: 文档列表

- **WHEN** `GET /api/rag/documents`
- **THEN** 返回 `{"documents":[{"docId","name","mimeType","chunkCount","size","ingestedAt"}]}`

#### Scenario: 块预览

- **WHEN** `GET /api/rag/documents/{docId}/chunks`
- **THEN** 每块返回 `chunkId / index / outlinePath / content / embeddingText / charCount`
- **AND** 能看出 `content` 与 `embeddingText` 的差异
- **AND** 文档不存在时返回 404 `{"error":"文档不存在"}`

#### Scenario: 删除文档

- **WHEN** `DELETE /api/rag/documents/{docId}`
- **THEN** 返回 `{"deleted":true}`；文档不存在时返回 404

### Requirement: 离线测试

系统 SHALL 保证 `mvn -o test` 在无网络、无 PostgreSQL、无嵌入服务时通过。

#### Scenario: 离线单测

- **WHEN** 执行 `mvn -o test`
- **THEN** 解析、分块、组装、摄取编排的单测全部通过
- **AND** 用测试专用 `InMemoryChunkStore` 代替数据库

#### Scenario: 数据库集成测试可选

- **WHEN** 环境变量 `RAG_DB_TEST=true` 且 PostgreSQL 可用
- **THEN** 运行 `JdbcChunkStore` 的增删改查与级联删除测试
- **AND** 未设置该变量时自动跳过，不导致构建失败

### Requirement: 学习文档

系统 SHALL 产出 `docs/04-文档处理链路.md`，按功能点组织，每个功能点都包含：**要解决的问题、具体步骤（对应代码位置与关键配置）、验收逻辑（可直接执行的命令与预期结果）**。

#### Scenario: 覆盖本轮全部功能点

- **WHEN** 阅读学习文档
- **THEN** 至少覆盖：上传校验、MIME 探测与路由、Markdown/TXT/CSV/PDF/DOCX 解析、Block 模型、分块预算、标题路径、各类 Chunker、TextSplitter、ChunkPacker、ChunkAssembler、PostgreSQL 入库、幂等重灌、4 个 REST 接口
- **AND** 每个功能点都能照文档复现并对照预期结果

#### Scenario: 说明与第二个功能的边界

- **WHEN** 文档提到 `embeddingText` 与 `embedding` 列
- **THEN** 明确说明本轮回填向量尚未实现，第二个功能将使用 DashScope `text-embedding-v4`（1024 维）回填并检索

## MODIFIED Requirements

无。

## REMOVED Requirements

无。
