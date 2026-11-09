# AgentScope Chat — 多模型问答 + 图片识别 + 文档知识库 Web 项目

基于 **Spring Boot + AgentScope Java 2.0.2** 实现的知识问答 Web 应用。
配置驱动多模型切换（远程 DeepSeek / 本地 Ollama），支持图片识别（Vision）与文档处理链路（上传 → 解析 → block-aware 分块 → PostgreSQL 入库 → 块预览）。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.3.5 |
| AI 框架 | AgentScope Java 2.0.2（`io.agentscope:agentscope-core` + `agentscope-extensions-model-openai`，OpenAI 兼容协议） |
| 文档解析 | Apache Tika 3.2.3（MIME 探测 + PDF/DOC/DOCX/RTF 文本抽取）、commonmark 0.22.0（Markdown + GFM 表格）、commons-csv 1.14.1 |
| 持久化 | Spring JDBC（`spring-boot-starter-jdbc`）+ PostgreSQL 16 + pgvector（`pgvector/pgvector:pg16`） |
| 前端     | 原生 HTML / CSS / JavaScript（无构建依赖） |
| 容器化   | Docker + docker-compose |
| 基础镜像 | `maven:3.9.9-eclipse-temurin-17`（构建） / `eclipse-temurin:17-jre-alpine`（运行） |

## 功能

- ✅ 多模型切换（远程 DeepSeek + 本地 Ollama，配置驱动，零代码加模型）
- ✅ 图片识别 Vision（Qwen3-VL-4B + OpenAI 兼容端点）
- ✅ 文档处理链路：上传 → 解析 → block-aware 分块 → PostgreSQL 入库 → 块预览（`/api/rag/documents*`）
- ✅ 简洁聊天 UI（侧边栏模型下拉框 + 输入框 📎 上传图片）
- ✅ 接口限流（`/api/**` 固定窗口计数，超限 429 + `Retry-After`，可按 IP 隔离，配置见 `rate-limit.*`）
- ✅ 机器人回复 Markdown 渲染（标题/列表/代码块/行内代码/粗斜体/引用；先转义后渲染，防 prompt 注入型 XSS）

## 当前模型配置

| Model ID | 显示名 | 来源 | 支持 Vision |
|---|---|---|---|
| `remote-deepseek` | DeepSeek Flash (远程) | api.deepseek.com | ✅ |
| `ollama-deepseek-r1` | DeepSeek R1 (本地/Ollama) | localhost:11434 | ❌ |
| `ollama-qwen3-vl` | Qwen3-VL-4B (本地/Ollama) | localhost:11434 | ✅ |

> 所有模型都走 **OpenAI 兼容协议**，由 `agentscope-extensions-model-openai` 的 `OpenAIChatModel` 统一接入：每个模型通过显式 builder 创建（`apiKey` / `baseUrl` / `modelName`），再交给 `ReActAgent` 执行问答。
> 详见 [docs/01-模型切换机制.md](docs/01-模型切换机制.md)。

## 项目结构

```
.
├── Dockerfile                      # 多阶段镜像构建
├── docker-compose.yml              # 容器编排
├── .env.example                    # 环境变量模板
├── .dockerignore
├── pom.xml                         # Maven 依赖 (AgentScope + Tika/commonmark/commons-csv/JDBC)
├── README.md
├── docs/                           # 功能点学习文档
│   ├── 01-模型切换机制.md
│   ├── 02-图片识别Vision.md
│   ├── 02-为什么用springai.md
│   ├── 03-新增功能-接口限流.md        # 新增功能的实现教程 + 验收记录
│   ├── 04-新增功能-回复Markdown渲染.md
│   └── evidence-复现/                # 上述功能的截图、日志、脚本、桩服务
└── src/main/
    ├── java/com/example/chat/
    │   ├── DemoApplication.java       # Spring Boot 主入口
    │   ├── ChatConfig.java           # @ConfigurationProperties 绑定模型列表
    │   ├── ChatModelFactory.java     # OpenAIChatModel 工厂 + ConcurrentHashMap 缓存
    │   ├── ChatController.java       # /api/chat + /api/vision + /api/models
    │   ├── ModelDefinition.java      # 模型配置 POJO
    │   ├── ratelimit/                # 接口限流（RateLimitProperties + RateLimitFilter）
    │   └── rag/                      # 文档处理链路
    │       ├── config/               # RagProperties + ChunkBudget Bean
    │       ├── parser/               # MimeTypeDetector / ParserRegistry / 四个解析器 / model(Block)
    │       ├── chunk/                # HeadingHandler / 五个 Chunker / TextSplitter / Packer / Assembler
    │       ├── ingest/               # IngestionService / 记录模型 / store(JdbcChunkStore)
    │       └── web/                  # RagDocumentController
    └── resources/
        ├── application.yml           # chat.models + rag.* + rate-limit.* + 数据源/multipart
        ├── schema.sql                # pgvector 扩展 + rag_documents/rag_chunks
        └── static/
            └── index.html            # 前端页面（下拉框 + 📎 图片上传 + Markdown 渲染）
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT`       | `8080` | 服务监听端口 |
| `DEEPSEEK_API_KEY`   | — (必填) | DeepSeek 平台 API Key |
| `RATE_LIMIT_LIMIT`   | `60` | 每个窗口允许的 `/api/**` 请求数（`rate-limit.limit`） |
| `RATE_LIMIT_WINDOW_SECONDS` | `60` | 限流窗口长度（秒） |
| `RATE_LIMIT_ENABLED` | `true` | 是否开启接口限流 |
| `OLLAMA_BASE_URL`   | `http://localhost:11434/v1` | Ollama 的 OpenAI 兼容端点（AgentScope 不会自动补 `/v1`） |
| `RAG_DB_URL`        | `jdbc:postgresql://localhost:5432/ragdb` | 文档链路使用的 PostgreSQL JDBC 连接串 |
| `RAG_DB_USERNAME`   | `postgres` | 数据库用户名 |
| `RAG_DB_PASSWORD`   | `postgres` | 数据库密码 |
| `RAG_DB_NAME`       | `ragdb` | Docker Compose 建库名（用于 `POSTGRES_DB` 与 `RAG_DB_URL` 插值；应用本身不读取） |

> 完整模型配置见 `application.yml` 的 `chat.models` 数组，可自由增删。
> `.env.example` 只有 `RAG_DB_NAME / RAG_DB_USERNAME / RAG_DB_PASSWORD` 的占位值；`RAG_DB_URL` 由 `docker-compose.yml` 在容器内拼出。请按实际环境修改，不要提交真实密钥。

## 快速开始（Docker）

```bash
# 1. 复制并编辑环境变量
cp .env.example .env
# 填入 DEEPSEEK_API_KEY，并按需修改 RAG_DB_* 占位值

# 2. 启动 PostgreSQL（pgvector）
docker compose up -d postgres

# 3. 构建并启动应用
docker compose up -d --build

# 4. 查看日志
docker compose logs -f
```

启动成功后浏览器访问：<http://localhost:8083>（compose 把容器 8080 映射到宿主机 8083）。

> 文档处理链路依赖 PostgreSQL。数据库不可用时应用会**启动失败**：`spring.sql.init.mode=always` 会在启动阶段执行 `schema.sql`，Hikari 初始化拿不到连接即退出。请先启动数据库。

## 本地开发（非 Docker）

需要 JDK 17+、Maven 3.9+、PostgreSQL 16（含 pgvector），以及**本地运行的 Ollama**（可选，只想用远程 DeepSeek 则不需要）。

```bash
# 0. 启动 PostgreSQL（二选一）
docker compose up -d postgres
# 或使用本机 PostgreSQL：
#   psql -U postgres -c "CREATE DATABASE ragdb;"
#   psql -U postgres -d ragdb -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 1. 设置 API Key 与数据库变量（Windows PowerShell）
$env:DEEPSEEK_API_KEY="your-key-here"
$env:RAG_DB_URL="jdbc:postgresql://localhost:5432/ragdb"   # 不设置则用 application.yml 默认值
$env:RAG_DB_USERNAME="postgres"
$env:RAG_DB_PASSWORD="postgres"

# 2. 本地 Ollama 拉模型（可选）
ollama pull deepseek-r1:latest
ollama pull modelscope.cn/Qwen/Qwen3-VL-4B-Instruct-GGUF:latest

# 3. 编译并运行
mvn spring-boot:run
```

访问：<http://localhost:8080>

## API 接口

### GET `/api/models`

返回可用模型列表（**不暴露 apiKey/baseUrl**）：

```json
[
  {"id":"remote-deepseek","name":"DeepSeek Flash (远程)","modelName":"deepseek-flash"},
  {"id":"ollama-deepseek-r1","name":"DeepSeek R1 (本地/Ollama)","modelName":"deepseek-r1:latest"}
]
```

### POST `/api/chat`

纯文本对话：

```json
// 请求
{ "message": "你好", "model": "remote-deepseek" }

// 成功
{ "reply": "你好！我是 DeepSeek..." }

// 错误
{ "error": "模型调用失败: ..." }
```

### POST `/api/vision`

图片识别（`multipart/form-data`）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `image` | MultipartFile | 图片文件 |
| `message` | String | 问题，默认"描述这张图片" |
| `model` | String | 模型 ID，默认 `ollama-qwen3-vl` |

```bash
curl -F "image=@test.png" -F "message=这张图是什么颜色" -F "model=ollama-qwen3-vl" \
     http://localhost:8080/api/vision
```

### 文档处理 API

本轮只做「上传 → 解析 → block-aware 分块 → 入库 → 预览」，不调用嵌入、不写向量、不做检索。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/rag/documents` | `multipart/form-data`，字段 `file`；返回 `docId / docName / chunkCount / skipped / timings` |
| `GET` | `/api/rag/documents` | 文档列表（`docId / name / mimeType / chunkCount / size / ingestedAt`） |
| `GET` | `/api/rag/documents/{docId}/chunks` | 块预览（`chunkId / index / outlinePath / content / embeddingText / charCount`） |
| `DELETE` | `/api/rag/documents/{docId}` | 删除文档（块级联删除），返回 `{"deleted":true}` |

```powershell
curl.exe -F "file=@src/test/resources/docs/保修政策.md" http://localhost:8080/api/rag/documents
# 预期：{"docId":"...","docName":"保修政策.md","chunkCount":1,"skipped":false,"timings":{...}}
```

错误统一为 `{"error":"..."}`：上传校验/解析失败 400，文档不存在 404，其它异常 500。详见 [docs/04-文档处理链路.md](docs/04-文档处理链路.md)。

## 加新模型（零代码）

只需两步，不用改 Java：

**1.** 在 `application.yml` 的 `chat.models` 下加一条：

```yaml
    - id: remote-qwen
      name: "Qwen Plus (阿里云)"
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY:}
      model-name: qwen-plus
      temperature: 0.7
```

**2.** Docker 部署则在 `docker-compose.yml` 的 `environment` 加对应变量，重启容器即可。

详见 [docs/01-模型切换机制.md](docs/01-模型切换机制.md)。

## 后续计划

- **知识库检索**：本轮已完成文档解析 + block-aware 分块 + PostgreSQL 入库；下一步是「知识库检索：DashScope 嵌入 + pgvector 相似度 + RAG 问答」，即用阿里百炼 DashScope `text-embedding-v4`（1024 维）回填 `rag_chunks.embedding`，实现 topK 召回与带引用问答。
- **多轮对话长期记忆**：跨会话保留用户偏好与关键事实，问答时自动召回
- **MCP 工具接入**：通过 MCP 协议让 Agent 调用外部工具与数据源

这三个能力将分别由 AgentScope 的 `Knowledge`、`LongTermMemory` 和 MCP 支持实现，无需再引入其他 AI 框架。

## 注意事项

- AgentScope 不参与 Spring Boot 自动装配，`OpenAIChatModel` 全部在 `ChatModelFactory` 里通过显式 builder 创建，因此多模型动态切换不需要 exclude 任何自动配置类。
- base-url 必须指向可用的 OpenAI 兼容端点（AgentScope 不会自动补 `/v1`），每个模型的 `baseUrl` + `apiKey` + `modelName` 都写在 `chat.models` 里。
- 每次问答创建 `ReActAgent`（用 try-with-resources 关闭），用户消息封装成 `Msg` 后调用 `agent.call(msg).block()` 取回复；图片消息用 `DataBlock` + `Base64Source` 传递 Base64 数据。
- 本项目仅保证**基础问答 + 图片识别 + 文档解析/分块/入库**能力，不含生产环境优化、权限校验、多轮对话记忆、流式输出等扩展功能；文档链路本轮不调用嵌入、不写向量、不提供检索。
