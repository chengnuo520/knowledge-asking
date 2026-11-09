# Tasks

- [x] Task 1: 依赖与配置迁移
  - [x] SubTask 1.1: `pom.xml` 移除 `spring-ai-bom` dependencyManagement 与 `spring-ai-starter-model-openai`、`spring-ai-starter-model-ollama` 两个依赖
  - [x] SubTask 1.2: `pom.xml` 新增 `agentscope.version=2.0.2` 属性，引入 `io.agentscope:agentscope-core` 与 `io.agentscope:agentscope-extensions-model-openai`
  - [x] SubTask 1.3: `application.yml` 删除 `spring.autoconfigure.exclude` 整段，其余 `chat.models` 配置保持不变
  - [x] SubTask 1.4: 执行 `mvn -o dependency:tree` 确认 AgentScope 依赖可解析（首次离线因仓库标记失败，联网 `mvn compile` 拉取传递依赖后落盘，随后离线构建成功）

- [x] Task 2: ChatModelFactory 改为 AgentScope Model 构建
  - [x] SubTask 2.1: 返回值类型由 Spring AI `ChatModel` 改为 `io.agentscope.core.model.Model`
  - [x] SubTask 2.2: 用 `OpenAIChatModel.builder().apiKey().baseUrl().modelName().stream(false).generateOptions(GenerateOptions.builder().temperature(...).build()).build()` 构建
  - [x] SubTask 2.3: 保留 `ConcurrentHashMap` 缓存与「未知模型抛 `IllegalArgumentException("未知模型: " + modelId)`」的行为
  - [x] SubTask 2.4: 兼容 `api-key` 为空时回落到 `"ollama"`（Ollama 不校验 key）

- [x] Task 3: ChatController 改为 ReActAgent + Msg
  - [x] SubTask 3.1: `chat` 接口改为「构建 `ReActAgent` → `Msg.builder().textContent(message).build()` → `agent.call(msg).block()` → `reply.getTextContent()`」
  - [x] SubTask 3.2: `vision` 接口改为 `Msg` 携带 `TextBlock` + `DataBlock(Base64Source.builder().data(base64).mediaType(contentType).build())`
  - [x] SubTask 3.3: Agent 使用 try-with-resources 关闭；`ReActAgent` 实现 `AutoCloseable`
  - [x] SubTask 3.4: 保持三个接口的响应结构与错误文案不变（`{"reply":...}` / `{"error":...}`）；另将 `ModelDefinition.getApiKey()` 标注 `@JsonIgnore`，避免 `/api/models` 输出 `apiKey` 字段

- [x] Task 4: 编译与离线单测
  - [x] SubTask 4.1: `mvn -o compile` 通过
  - [x] SubTask 4.2: 新增 `ChatModelFactoryTest`：配置三个模型时能拿到 Model 实例，未知 id 抛 `IllegalArgumentException`（不发起网络请求）
  - [x] SubTask 4.3: 新增 `AgentMessageTest`：文本消息与图片消息（`DataBlock` + base64）能正确构造并取出文本
  - [x] SubTask 4.4: `mvn -o test` 通过（5 个测试，0 失败），且不依赖网络与真实模型

- [x] Task 5: 端到端冒烟验证
  - [x] SubTask 5.1: 启动应用，`GET /api/models` 返回 3 个模型且不含 `apiKey`
  - [x] SubTask 5.2: `POST /api/chat` 指定 `remote-deepseek`，确认返回非空 `reply`
  - [x] SubTask 5.3: 本机 Ollama 不可达（`localhost:11434` 拒绝连接），按约定记录「环境不具备，跳过」
  - [x] SubTask 5.4: `POST /api/vision` 上传 1x1 PNG（指定 `remote-deepseek`），返回非空文本描述
  - [x] SubTask 5.5: chat 与 vision 均成功请求到 `https://api.deepseek.com/v1/chat/completions`，确认 `base-url` 不含 `/v1` 的拼接方式正确；Ollama 路径因服务不可达未实测

- [x] Task 6: 文档更新
  - [x] SubTask 6.1: `README.md` 中 Spring AI 技术栈描述改为 AgentScope Java 2.0.2
  - [x] SubTask 6.2: `docs/` 下 4 份文档中涉及的 Spring AI API 示例改为 AgentScope 写法（历史选型对比章节保留并标注为历史）
  - [x] SubTask 6.3: 在 README 补「后续计划」：RAG（Postgres + pgvector + DashScope embedding）、长期记忆、MCP

# Task Dependencies

- Task 2 依赖 Task 1
- Task 3 依赖 Task 2
- Task 4 依赖 Task 1、Task 2、Task 3
- Task 5 依赖 Task 4
- Task 6 无代码依赖，可与 Task 4/5 并行
