# AgentScope 迁移 Spec

## Why

现有项目基于 Spring AI 1.0.3，只覆盖「用户输入 → ChatModel → 文本」这一层。后续要做的三件事——RAG 知识库、多轮长期记忆、MCP 工具——需要 Agent 循环与工具编排能力，而 Spring AI 1.0.3 只提供 ChatModel / VectorStore / ChatMemory，没有 ReAct 循环。

AgentScope Java 2.0.2（JDK 17+、Maven Central、Apache 2.0）原生提供 `ReActAgent`、`ModelRegistry`、多模态 `DataBlock`、长期记忆（`LongTermMemory`）、`Knowledge` 接口与 MCP 接入，且已在本机 Maven 仓库有 `agentscope-core` 与 `agentscope-extensions-model-openai` 的 2.0.2 缓存。

本轮先把模型接入层与 HTTP 接口迁到 AgentScope，保持现有三个 API 的对外行为不变，为后续 RAG / 记忆 / MCP 打好底座。

## What Changes

- `pom.xml`：移除 Spring AI BOM、`spring-ai-starter-model-openai`、`spring-ai-starter-model-ollama`；引入 `io.agentscope:agentscope-core:2.0.2` 与 `io.agentscope:agentscope-extensions-model-openai:2.0.2`
- `ChatModelFactory`：由创建 Spring AI `ChatModel` 改为创建 AgentScope `io.agentscope.core.model.Model`（`OpenAIChatModel` 显式 builder，保留 `base-url / api-key / model-name / temperature` 的配置驱动）
- `ChatController`：由 `model.call(String)` 改为「每请求构建 `ReActAgent` → 构造 `Msg` → `call` → 取文本 → 关闭 agent」
- 图片识别：Spring AI `Media` 改为 AgentScope `DataBlock` + `Base64Source`
- `application.yml`：删除 `spring.autoconfigure.exclude` 的 Spring AI 自动装配排除项
- `README.md` 与 `docs/` 下文档中的 Spring AI 描述改为 AgentScope
- **BREAKING**：内部模型接入 API 由 Spring AI 类型变为 AgentScope 类型（仅项目内部，不涉及对外 HTTP 契约）

本轮非目标：

- 不做 RAG 文档摄取 / 检索 / RAG 问答（另开 spec，参照 `docs/superpowers/specs/2026-11-06-rag-ingestion-design.md`）
- 不引入 `agentscope-harness`、`Toolkit`、MCP、`LongTermMemory`、`Knowledge`
- 不引入 `agentscope-spring-boot-starter*`，继续手动装配，保持配置驱动
- 不改 `/api/models`、`/api/chat`、`/api/vision` 的请求与响应结构
- 不做流式（SSE）输出

已定的后续决策（记录，本轮不实现）：

- RAG 向量存储：PostgreSQL + pgvector（用户本地已有 Postgres 实例）
- 嵌入服务：阿里百炼 DashScope（OpenAI 兼容端点）
- 分块链路：参照设计文档的 block-aware 方案，不简化为固定长度滑窗

## Impact

- Affected specs: 模型接入层、多模态图片识别、模型注册表
- Affected code:
  - `pom.xml`
  - `src/main/java/com/example/chat/ChatModelFactory.java`
  - `src/main/java/com/example/chat/ChatController.java`
  - `src/main/resources/application.yml`
  - `README.md`、`docs/*.md`
- 不变：`ChatConfig.java`、`ModelDefinition.java`、`DemoApplication.java`、`static/index.html`、`docker-compose.yml`

## ADDED Requirements

### Requirement: AgentScope 模型注册表

系统 SHALL 使用 AgentScope 的 `Model` 构建 `chat.models` 配置中的每个模型，支持 OpenAI 兼容端点（DeepSeek）与本地 Ollama 端点，并保留 `id / name / base-url / api-key / model-name / temperature` 六个配置字段。

#### Scenario: 列出模型

- **WHEN** 客户端调用 `GET /api/models`
- **THEN** 返回 `id / name / modelName` 三个字段
- **AND** 响应中不包含 `apiKey`

#### Scenario: 指定模型问答

- **WHEN** 客户端调用 `POST /api/chat`，body 为 `{"message":"你好","model":"remote-deepseek"}`
- **THEN** 使用该 `model` 对应的 AgentScope `Model` 执行问答
- **AND** 返回 `{"reply": "..."}`

#### Scenario: 未知模型

- **WHEN** 请求中的 `model` 不在配置列表内
- **THEN** 返回 `{"error": "未知模型: <id>"}`，不发起模型调用

#### Scenario: 空消息

- **WHEN** `message` 为空或全空白
- **THEN** 返回 `{"error": "message cannot be empty"}`

### Requirement: 多模态图片识别

系统 SHALL 使用 AgentScope 消息模型构造「文本 + 图片」多模态消息，图片以 base64 编码的 `DataBlock` 承载。

#### Scenario: 上传图片并提问

- **WHEN** 客户端以 `multipart/form-data` 调用 `POST /api/vision`，携带 `image`、`message`、`model`
- **THEN** 构造包含 `TextBlock` 与 `DataBlock(Base64Source)` 的 `Msg`
- **AND** 返回 `{"reply": "..."}`

#### Scenario: 非图片文件

- **WHEN** 上传文件的 content-type 不是 `image/*`
- **THEN** 返回 `{"error": "只支持图片文件"}`

#### Scenario: 空文件

- **WHEN** 上传文件为空
- **THEN** 返回 `{"error": "请上传图片"}`

### Requirement: 每请求 Agent 生命周期

系统 SHALL 在每次请求内构建 `ReActAgent` 实例，执行结束后关闭，避免跨请求复用有状态的 Agent 实例。

#### Scenario: 请求结束释放资源

- **WHEN** 一次 `/api/chat` 或 `/api/vision` 请求完成（成功或异常）
- **THEN** 该请求内构建的 `ReActAgent` 已关闭
- **AND** `Model` 实例仍从缓存复用，不重复构建

### Requirement: 模型配置与调用方式一致

系统 SHALL 保证 AgentScope 的 HTTP 端点拼接方式与现有 `base-url` 配置兼容（`base-url` 不带 `/v1`）。

#### Scenario: Ollama 本地模型

- **WHEN** `base-url` 为 `http://localhost:11434`
- **THEN** 实际请求地址为 `http://localhost:11434/v1/chat/completions`
- **AND** 若实际拼接结果不符，通过 `endpoint-path` 配置修正，而不是硬编码

## MODIFIED Requirements

### Requirement: 模型调用层

原实现使用 Spring AI 的 `OpenAiApi` + `OpenAiChatModel` + `ChatModel.call(String)`。

改造后：

- 模型由 `OpenAIChatModel.builder().apiKey().baseUrl().modelName().stream(false).generateOptions(GenerateOptions.builder().temperature(...).build()).build()` 创建
- 问答由 `ReActAgent.builder().name(...).model(...).build()` + `agent.call(Msg).block()` 完成
- 回复文本由 `Msg.getTextContent()` 提取

## REMOVED Requirements

### Requirement: Spring AI 自动装配排除

**Reason**：项目不再依赖 Spring AI，`spring.autoconfigure.exclude` 中的 8 个排除项失去意义。

**Migration**：直接删除 `application.yml` 中的 `spring.autoconfigure.exclude` 整段；若未来重新引入 Spring AI 组件，须重新评估自动装配冲突。

### Requirement: Spring AI 依赖

**Reason**：`spring-ai-bom`、`spring-ai-starter-model-openai`、`spring-ai-starter-model-ollama` 与 AgentScope 模型层重复。

**Migration**：由 `io.agentscope:agentscope-core` + `io.agentscope:agentscope-extensions-model-openai` 替代；Ollama 通过 OpenAI 兼容端点接入，不再需要 Ollama starter。
